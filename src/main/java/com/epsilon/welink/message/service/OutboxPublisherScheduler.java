package com.epsilon.welink.message.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxPublisherScheduler {

    private final MessageOutboxService messageOutboxService;

    @Value("${welink.outbox.batch-size:100}")
    private int batchSize;

    public OutboxPublisherScheduler(MessageOutboxService messageOutboxService) {
        this.messageOutboxService = messageOutboxService;
    }

    @Scheduled(fixedDelayString = "${welink.outbox.publish-interval-ms:3000}")
    public void publishDueOutboxEvents() {
        messageOutboxService.publishDueEvents(batchSize);
    }
}
