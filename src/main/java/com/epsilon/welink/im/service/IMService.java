package com.epsilon.welink.im.service;

import com.epsilon.welink.common.constant.RedisConstants;
import com.epsilon.welink.common.util.JwtUtil;
import com.epsilon.welink.message.constant.MessageInboxConstants;
import com.epsilon.welink.message.dto.MessageRequest;
import com.epsilon.welink.message.entity.Message;
import com.epsilon.welink.message.service.MessageOutboxService;
import com.epsilon.welink.message.service.MessageService;
import com.epsilon.welink.relation.entity.GroupMember;
import com.epsilon.welink.relation.service.RelationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class IMService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final JwtUtil jwtUtil;
    private final MessageService messageService;
    private final MessageOutboxService messageOutboxService;
    private final RelationService relationService;
    private final ObjectMapper objectMapper;

    @Value("${welink.im.send-rate-limit-per-second:3000}")
    private int sendRateLimitPerSecond;

    @Value("${welink.instance.id:instance-1}")
    private String instanceId;

    private final ConcurrentHashMap<Long, Channel> onlineUsers = new ConcurrentHashMap<>();

    // 存储 Channel 到 UserId 的反向映射，用于快速查找
    private final ConcurrentHashMap<Channel, Long> channelUserMap = new ConcurrentHashMap<>();

    public IMService(RedisTemplate<String, Object> redisTemplate,
                     JwtUtil jwtUtil,
                     MessageService messageService,
                     MessageOutboxService messageOutboxService,
                     RelationService relationService) {
        this.redisTemplate = redisTemplate;
        this.jwtUtil = jwtUtil;
        this.messageService = messageService;
        this.messageOutboxService = messageOutboxService;
        this.relationService = relationService;
        this.objectMapper = new ObjectMapper();
    }

    public void handleMessage(ChannelHandlerContext ctx, String message) {
        try {
            JsonNode jsonNode = objectMapper.readTree(message);
            String type = jsonNode.get("type").asText();

            switch (type) {
                case "auth" -> handleAuth(ctx, jsonNode);
                case "message" -> handleSendMessage(ctx, jsonNode);
                case "heartbeat" -> handleHeartbeat(ctx);
                case "ack" -> handleAck(ctx, jsonNode);
                default -> sendError(ctx, "Unknown message type");
            }
        } catch (Exception e) {
            log.error("Failed to handle message", e);
            sendError(ctx, "Invalid message format");
        }
    }

    private void handleAuth(ChannelHandlerContext ctx, JsonNode jsonNode) {
        String token = jsonNode.get("token").asText();
        if (!jwtUtil.validateToken(token)) {
            sendError(ctx, "Invalid token");
            ctx.close();
            return;
        }

        Long userId = jwtUtil.getUserId(token);
        
        // 如果用户已在线，先清理旧连接
        Channel oldChannel = onlineUsers.get(userId);
        if (oldChannel != null && oldChannel.isActive()) {
            channelUserMap.remove(oldChannel);
            oldChannel.close();
        }
        
        onlineUsers.put(userId, ctx.channel());
        channelUserMap.put(ctx.channel(), userId);

        redisTemplate.opsForValue().set(
                RedisConstants.USER_ONLINE_PREFIX + userId,
                "online",
                RedisConstants.ONLINE_TTL_SECONDS,
                java.util.concurrent.TimeUnit.SECONDS
        );

        redisTemplate.opsForValue().set(
                RedisConstants.IM_ROUTE_PREFIX + userId,
                instanceId,
                RedisConstants.ROUTE_TTL_SECONDS,
                java.util.concurrent.TimeUnit.SECONDS
        );

        sendSuccess(ctx, "auth", "Authentication successful");
        broadcastFriendStatus(userId, true);
        log.info("User {} authenticated and online", userId);
    }

    private void handleSendMessage(ChannelHandlerContext ctx, JsonNode jsonNode) {
        Long userId = getUserIdByChannel(ctx.channel());
        if (userId == null) {
            sendError(ctx, "Not authenticated");
            return;
        }
        if (!allowSend(userId)) {
            sendError(ctx, "Rate limit exceeded");
            return;
        }

        MessageRequest request = new MessageRequest();
        if (jsonNode.has("toUserId")) {
            request.setToUserId(jsonNode.get("toUserId").asLong());
        }
        if (jsonNode.has("groupId")) {
            request.setGroupId(jsonNode.get("groupId").asLong());
        }
        if (jsonNode.has("msgId")) {
            request.setMsgId(jsonNode.get("msgId").asText());
        }
        request.setMsgType(jsonNode.has("msgType") ? jsonNode.get("msgType").asInt() : 1);
        request.setContent(jsonNode.get("content").asText());

        Message savedMessage;
        if (request.getToUserId() != null) {
            savedMessage = messageService.savePrivateMessage(userId, request);
            routePrivateMessage(savedMessage);
        } else if (request.getGroupId() != null) {
            savedMessage = messageService.saveGroupMessage(userId, request);
            routeGroupMessage(savedMessage);
        } else {
            sendError(ctx, "Invalid message parameters");
            return;
        }

        sendSuccess(ctx, "message", savedMessage.getMsgId());
    }

    private void routePrivateMessage(Message message) {
        Long targetUserId = message.getToUserId();
        messageService.createInboxRecord(
                message.getMsgId(),
                targetUserId,
                MessageInboxConstants.CONVERSATION_PRIVATE
        );
        Channel targetChannel = onlineUsers.get(targetUserId);

        if (targetChannel != null && targetChannel.isActive()) {
            pushToChannel(targetChannel, targetUserId, message);
        } else if (isUserOnlineOnOtherInstance(targetUserId)) {
            publishToKafkaWithOutbox(message, targetUserId, "im-private-message");
        }
    }

    private void routeGroupMessage(Message message) {
        List<GroupMember> members = relationService.getGroupMembers(message.getGroupId());

        List<Long> offlineUserIds = new ArrayList<>();

        for (GroupMember member : members) {
            if (member.getUserId().equals(message.getFromUserId())) {
                continue;
            }

            Channel memberChannel = onlineUsers.get(member.getUserId());
            if (memberChannel != null && memberChannel.isActive()) {
                pushToChannel(memberChannel, member.getUserId(), message);
            } else if (isUserOnlineOnOtherInstance(member.getUserId())) {
                publishToKafkaWithOutbox(message, member.getUserId(), "im-group-message");
            } else {
                offlineUserIds.add(member.getUserId());
            }
        }

        if (!offlineUserIds.isEmpty()) {
            messageService.createInboxRecords(
                    message.getMsgId(),
                    offlineUserIds,
                    MessageInboxConstants.CONVERSATION_GROUP
            );
        }
    }

    private void handleHeartbeat(ChannelHandlerContext ctx) {
        Long userId = getUserIdByChannel(ctx.channel());
        if (userId != null) {
            redisTemplate.expire(
                    RedisConstants.USER_ONLINE_PREFIX + userId,
                    RedisConstants.ONLINE_TTL_SECONDS,
                    java.util.concurrent.TimeUnit.SECONDS
            );
            redisTemplate.expire(
                    RedisConstants.IM_ROUTE_PREFIX + userId,
                    RedisConstants.ROUTE_TTL_SECONDS,
                    java.util.concurrent.TimeUnit.SECONDS
            );
            sendSuccess(ctx, "heartbeat", "pong");
        }
    }

    private void handleAck(ChannelHandlerContext ctx, JsonNode jsonNode) {
        Long userId = getUserIdByChannel(ctx.channel());
        if (userId == null) {
            sendError(ctx, "Not authenticated");
            return;
        }
        if (!jsonNode.has("msgId")) {
            sendError(ctx, "msgId is required");
            return;
        }

        String msgId = jsonNode.get("msgId").asText();
        messageService.markAsRead(msgId, userId);
        log.info("Message {} marked as read by user {}", msgId, userId);
    }

    public void handleDisconnect(ChannelHandlerContext ctx) {
        Long userId = getUserIdByChannel(ctx.channel());
        if (userId != null) {
            onlineUsers.remove(userId);
            channelUserMap.remove(ctx.channel());
            redisTemplate.delete(RedisConstants.USER_ONLINE_PREFIX + userId);
            redisTemplate.delete(RedisConstants.IM_ROUTE_PREFIX + userId);
            broadcastFriendStatus(userId, false);
            log.info("User {} disconnected and offline", userId);
        }
    }

    public boolean pushMessage(Long userId, Message message) {
        Channel channel = onlineUsers.get(userId);
        if (channel != null && channel.isActive()) {
            pushToChannel(channel, userId, message);
            return true;
        }
        return false;
    }

    private void pushToChannel(Channel channel, Long receiverId, Message message) {
        channel.writeAndFlush(new TextWebSocketFrame(buildMessageJson(message))).addListener(future -> {
            if (future.isSuccess()) {
                if (message.getToUserId() != null) {
                    messageService.markDelivered(message.getMsgId(), receiverId);
                }
            } else {
                log.warn("Message push failed: msgId={}, receiverId={}", message.getMsgId(), receiverId);
            }
        });
    }

    private void publishToKafkaWithOutbox(Message message, Long targetUserId, String topic) {
        messageOutboxService.createEvent(message.getMsgId(), targetUserId, topic);
    }

    private boolean allowSend(Long userId) {
        String key = RedisConstants.IM_RATE_LIMIT_PREFIX + userId + ":" + (System.currentTimeMillis() / 1000);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, 2, TimeUnit.SECONDS);
        }
        return count == null || count <= sendRateLimitPerSecond;
    }

    private Long getUserIdByChannel(Channel channel) {
        return channelUserMap.get(channel);
    }

    private String buildMessageJson(Message message) {
        try {
            Map<String, Object> map = new HashMap<>();
            map.put("type", "message");
            map.put("msgId", message.getMsgId());
            map.put("fromUserId", message.getFromUserId());
            map.put("toUserId", message.getToUserId());
            map.put("groupId", message.getGroupId());
            map.put("groupSeq", message.getGroupSeq());
            map.put("msgType", message.getMsgType());
            map.put("content", message.getContent());
            map.put("createdAt", message.getCreatedAt() != null ? message.getCreatedAt().toString() : null);
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            log.error("Failed to build message json", e);
            return "";
        }
    }

    private void sendSuccess(ChannelHandlerContext ctx, String type, String data) {
        try {
            Map<String, Object> map = new HashMap<>();
            map.put("type", type);
            map.put("status", "success");
            map.put("data", data);
            ctx.writeAndFlush(new TextWebSocketFrame(objectMapper.writeValueAsString(map)));
        } catch (Exception e) {
            log.error("Failed to send success response", e);
        }
    }

    private void sendError(ChannelHandlerContext ctx, String message) {
        try {
            Map<String, Object> map = new HashMap<>();
            map.put("type", "error");
            map.put("message", message);
            ctx.writeAndFlush(new TextWebSocketFrame(objectMapper.writeValueAsString(map)));
        } catch (Exception e) {
            log.error("Failed to send error response", e);
        }
    }

    private void broadcastFriendStatus(Long userId, boolean isOnline) {
        List<Long> friendIds = relationService.getFriendIds(userId);
        if (friendIds.isEmpty()) {
            return;
        }

        String payload = buildSystemStatusJson(userId, isOnline);
        for (Long friendId : friendIds) {
            Channel friendChannel = onlineUsers.get(friendId);
            if (friendChannel != null && friendChannel.isActive()) {
                friendChannel.writeAndFlush(new TextWebSocketFrame(payload));
            }
        }
    }

    private String buildSystemStatusJson(Long userId, boolean isOnline) {
        try {
            Map<String, Object> map = new HashMap<>();
            map.put("type", "system");
            map.put("action", isOnline ? "online" : "offline");
            map.put("userId", userId);
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            log.error("Failed to build system status json", e);
            return "";
        }
    }

    private boolean isUserOnlineOnOtherInstance(Long userId) {
        Object route = redisTemplate.opsForValue().get(RedisConstants.IM_ROUTE_PREFIX + userId);
        if (!(route instanceof String routeInstance) || !StringUtils.hasText(routeInstance)) {
            return false;
        }
        return !instanceId.equals(routeInstance);
    }
}
