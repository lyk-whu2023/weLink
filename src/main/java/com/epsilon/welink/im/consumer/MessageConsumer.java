package com.epsilon.welink.im.consumer;

import com.epsilon.welink.im.dto.ImRetryMessage;
import com.epsilon.welink.im.service.IMService;
import com.epsilon.welink.message.entity.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

// 消息消费者，监听Kafka主题并处理消息
@Slf4j
@Component
public class MessageConsumer {

    private final IMService imService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${welink.im.kafka-retry.max-retries:3}")
    private int maxRetries;

    public MessageConsumer(IMService imService, KafkaTemplate<String, Object> kafkaTemplate) {
        this.imService = imService;
        this.kafkaTemplate = kafkaTemplate;
    }

    // 处理私聊消息 
    @KafkaListener(topics = "im-private-message", groupId = "welink-im")
    public void consumePrivateMessage(Message message,
                                      @Header(KafkaHeaders.RECEIVED_KEY) Long targetUserId) {
        log.info("Received private message from Kafka: msgId={}, toUserId={}",
                message.getMsgId(), targetUserId);
        if (!imService.pushMessage(targetUserId, message)) {
            publishRetry("im-private-retry", message, targetUserId, 1);
        }
    }

    // 处理群聊消息 
    @KafkaListener(topics = "im-group-message", groupId = "welink-im")
    public void consumeGroupMessage(Message message,
                                    @Header(KafkaHeaders.RECEIVED_KEY) Long targetUserId) {
        log.info("Received group message from Kafka: msgId={}, groupId={}, targetUserId={}",
                message.getMsgId(), message.getGroupId(), targetUserId);
        if (!imService.pushMessage(targetUserId, message)) {
            publishRetry("im-group-retry", message, targetUserId, 1);
        }
    }

    @KafkaListener(topics = "im-private-retry", groupId = "welink-im-retry")
    public void consumePrivateRetry(ImRetryMessage retryMessage) {
        handleRetry(retryMessage, "im-private-retry");
    }

    @KafkaListener(topics = "im-group-retry", groupId = "welink-im-retry")
    public void consumeGroupRetry(ImRetryMessage retryMessage) {
        handleRetry(retryMessage, "im-group-retry");
    }

    private void handleRetry(ImRetryMessage retryMessage, String topic) {
        if (retryMessage == null || retryMessage.getMessage() == null || retryMessage.getTargetUserId() == null) {
            return;
        }
        int retryCount = retryMessage.getRetryCount() == null ? 0 : retryMessage.getRetryCount();
        boolean pushed = imService.pushMessage(retryMessage.getTargetUserId(), retryMessage.getMessage());
        if (pushed) {
            log.info("Retry delivery success: topic={}, msgId={}, targetUserId={}, retryCount={}",
                    topic, retryMessage.getMessage().getMsgId(), retryMessage.getTargetUserId(), retryCount);
            return;
        }

        if (retryCount >= maxRetries) {
            log.warn("Retry exceeded max retries: topic={}, msgId={}, targetUserId={}, retryCount={}",
                    topic, retryMessage.getMessage().getMsgId(), retryMessage.getTargetUserId(), retryCount);
            return;
        }
        publishRetry(topic, retryMessage.getMessage(), retryMessage.getTargetUserId(), retryCount + 1);
    }

    private void publishRetry(String topic, Message message, Long targetUserId, int retryCount) {
        ImRetryMessage retryMessage = new ImRetryMessage();
        retryMessage.setMessage(message);
        retryMessage.setTargetUserId(targetUserId);
        retryMessage.setRetryCount(retryCount);
        kafkaTemplate.send(topic, String.valueOf(targetUserId), retryMessage);
    }
}
