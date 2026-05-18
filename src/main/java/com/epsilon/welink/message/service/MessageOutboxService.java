package com.epsilon.welink.message.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.epsilon.welink.message.constant.MessageOutboxConstants;
import com.epsilon.welink.message.entity.Message;
import com.epsilon.welink.message.entity.MessageOutbox;
import com.epsilon.welink.message.mapper.MessageOutboxMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class MessageOutboxService {

    private final MessageOutboxMapper messageOutboxMapper;
    private final MessageService messageService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${welink.outbox.max-retries:6}")
    private int maxRetries;

    @Value("${welink.outbox.base-retry-delay-seconds:5}")
    private int baseRetryDelaySeconds;

    public MessageOutboxService(MessageOutboxMapper messageOutboxMapper,
                                MessageService messageService,
                                KafkaTemplate<String, Object> kafkaTemplate) {
        this.messageOutboxMapper = messageOutboxMapper;
        this.messageService = messageService;
        this.kafkaTemplate = kafkaTemplate;
    }

    public Long createEvent(String msgId, Long targetUserId, String topic) {
        MessageOutbox existing = messageOutboxMapper.selectOne(new LambdaQueryWrapper<MessageOutbox>()
                .eq(MessageOutbox::getMsgId, msgId)
                .eq(MessageOutbox::getTargetUserId, targetUserId)
                .eq(MessageOutbox::getTopic, topic));
        if (existing != null) {
            return existing.getId();
        }

        MessageOutbox outbox = new MessageOutbox();
        outbox.setMsgId(msgId);
        outbox.setTargetUserId(targetUserId);
        outbox.setTopic(topic);
        outbox.setStatus(MessageOutboxConstants.STATUS_PENDING);
        outbox.setRetryCount(0);
        outbox.setNextRetryAt(LocalDateTime.now());
        try {
            messageOutboxMapper.insert(outbox);
            return outbox.getId();
        } catch (DuplicateKeyException e) {
            MessageOutbox duplicate = messageOutboxMapper.selectOne(new LambdaQueryWrapper<MessageOutbox>()
                    .eq(MessageOutbox::getMsgId, msgId)
                    .eq(MessageOutbox::getTargetUserId, targetUserId)
                    .eq(MessageOutbox::getTopic, topic));
            return duplicate != null ? duplicate.getId() : null;
        }
    }

    public void publishDueEvents(int limit) {
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<MessageOutbox> query = new LambdaQueryWrapper<>();
        query.in(MessageOutbox::getStatus, MessageOutboxConstants.STATUS_PENDING, MessageOutboxConstants.STATUS_FAILED)
                .le(MessageOutbox::getNextRetryAt, now)
                .orderByAsc(MessageOutbox::getNextRetryAt)
                .last("limit " + limit);
        List<MessageOutbox> outboxList = messageOutboxMapper.selectList(query);
        publishBatch(outboxList);
    }

    private void publishBatch(List<MessageOutbox> outboxList) {
        if (outboxList == null || outboxList.isEmpty()) {
            return;
        }

        for (MessageOutbox outbox : outboxList) {
            if (outbox.getRetryCount() != null && outbox.getRetryCount() >= maxRetries) {
                continue;
            }

            Message message = messageService.getMessageByMsgId(outbox.getMsgId());
            if (message == null) {
                markFailed(outbox, "message not found");
                continue;
            }

            try {
                outbox.setStatus(MessageOutboxConstants.STATUS_PUBLISHING);
                outbox.setNextRetryAt(LocalDateTime.now().plusSeconds(baseRetryDelaySeconds));
                messageOutboxMapper.updateById(outbox);
            } catch (Exception e) {
                markFailedById(outbox.getId(), e.getMessage());
            }
        }

        for (MessageOutbox outbox : outboxList) {
            if (outbox.getStatus() != MessageOutboxConstants.STATUS_PUBLISHING) {
                continue;
            }

            Message message = messageService.getMessageByMsgId(outbox.getMsgId());
            if (message == null) {
                continue;
            }

            kafkaTemplate.send(outbox.getTopic(), String.valueOf(outbox.getTargetUserId()), message)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            markPublished(outbox.getId());
                        } else {
                            markFailedById(outbox.getId(), ex.getMessage());
                        }
                    });
        }
    }

    private void publishSingle(MessageOutbox outbox) {
        if (outbox.getRetryCount() != null && outbox.getRetryCount() >= maxRetries) {
            return;
        }

        Message message = messageService.getMessageByMsgId(outbox.getMsgId());
        if (message == null) {
            markFailed(outbox, "message not found");
            return;
        }

        try {
            outbox.setStatus(MessageOutboxConstants.STATUS_PUBLISHING);
            outbox.setNextRetryAt(LocalDateTime.now().plusSeconds(baseRetryDelaySeconds));
            messageOutboxMapper.updateById(outbox);
            kafkaTemplate.send(outbox.getTopic(), String.valueOf(outbox.getTargetUserId()), message)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            markPublished(outbox.getId());
                        } else {
                            markFailedById(outbox.getId(), ex.getMessage());
                        }
                    });
        } catch (Exception e) {
            markFailedById(outbox.getId(), e.getMessage());
        }
    }

    private void markPublished(Long outboxId) {
        MessageOutbox latest = messageOutboxMapper.selectById(outboxId);
        if (latest == null) {
            return;
        }
        latest.setStatus(MessageOutboxConstants.STATUS_PUBLISHED);
        latest.setLastError(null);
        messageOutboxMapper.updateById(latest);
    }

    private void markFailedById(Long outboxId, String error) {
        MessageOutbox latest = messageOutboxMapper.selectById(outboxId);
        if (latest == null) {
            return;
        }
        markFailed(latest, error);
    }

    private void markFailed(MessageOutbox outbox, String error) {
        int currentRetry = outbox.getRetryCount() == null ? 0 : outbox.getRetryCount();
        int nextRetry = currentRetry + 1;
        outbox.setRetryCount(nextRetry);
        outbox.setStatus(MessageOutboxConstants.STATUS_FAILED);
        long delaySeconds = (long) baseRetryDelaySeconds * (1L << Math.min(nextRetry, 6));
        outbox.setNextRetryAt(LocalDateTime.now().plusSeconds(delaySeconds));
        if (error != null && error.length() > 500) {
            outbox.setLastError(error.substring(0, 500));
        } else {
            outbox.setLastError(error);
        }
        messageOutboxMapper.updateById(outbox);
        log.warn("Outbox publish failed: id={}, msgId={}, retryCount={}, error={}",
                outbox.getId(), outbox.getMsgId(), nextRetry, outbox.getLastError());
    }
}
