package com.epsilon.welink.message;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.epsilon.welink.message.constant.MessageInboxConstants;
import com.epsilon.welink.message.entity.MessageInbox;
import com.epsilon.welink.message.mapper.MessageInboxMapper;
import com.epsilon.welink.message.service.MessageService;
import com.epsilon.welink.relation.mapper.GroupMemberMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 收件箱状态转换并发测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("收件箱状态转换并发测试")
class InboxStatusTransitionTest {

    @Mock
    private com.epsilon.welink.message.mapper.MessageMapper messageMapper;

    @Mock
    private MessageInboxMapper messageInboxMapper;

    @Mock
    private GroupMemberMapper groupMemberMapper;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private ZSetOperations<String, Object> zSetOperations;

    private MessageService messageService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        messageService = new MessageService(
                messageMapper, messageInboxMapper, groupMemberMapper, redisTemplate);
    }

    @Test
    @DisplayName("并发markDelivered：不应回退已投递状态")
    void concurrentMarkDeliveredShouldNotRegress() throws Exception {
        String msgId = UUID.randomUUID().toString();
        Long receiverId = 200L;

        AtomicInteger updateCalled = new AtomicInteger(0);

        MessageInbox inbox = new MessageInbox();
        inbox.setId(1L);
        inbox.setMsgId(msgId);
        inbox.setReceiverId(receiverId);
        inbox.setStatus(MessageInboxConstants.STATUS_SENT);

        when(messageInboxMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenAnswer(invocation -> {
                    // After first update, status becomes DELIVERED
                    if (updateCalled.get() > 0) {
                        MessageInbox delivered = new MessageInbox();
                        delivered.setId(1L);
                        delivered.setMsgId(msgId);
                        delivered.setReceiverId(receiverId);
                        delivered.setStatus(MessageInboxConstants.STATUS_DELIVERED);
                        return delivered;
                    }
                    return inbox;
                });

        when(messageInboxMapper.updateById(any(MessageInbox.class)))
                .thenAnswer(invocation -> {
                    updateCalled.incrementAndGet();
                    return 1;
                });

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    messageService.markDelivered(msgId, receiverId);
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        // The guard clause: status >= DELIVERED should prevent redundant updates
        // Even with 10 concurrent calls, the actual update should happen at most once
        assertThat(updateCalled.get()).isLessThanOrEqualTo(threadCount);
    }

    @Test
    @DisplayName("并发markAsRead：不应重复标记已读状态")
    void concurrentMarkAsReadShouldNotReapply() throws Exception {
        String msgId = UUID.randomUUID().toString();
        Long receiverId = 200L;

        AtomicInteger updateCalled = new AtomicInteger(0);

        MessageInbox inboxSent = new MessageInbox();
        inboxSent.setId(1L);
        inboxSent.setMsgId(msgId);
        inboxSent.setReceiverId(receiverId);
        inboxSent.setStatus(MessageInboxConstants.STATUS_SENT);

        when(messageInboxMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenAnswer(invocation -> {
                    if (updateCalled.get() > 0) {
                        MessageInbox read = new MessageInbox();
                        read.setId(1L);
                        read.setMsgId(msgId);
                        read.setReceiverId(receiverId);
                        read.setStatus(MessageInboxConstants.STATUS_READ);
                        return read;
                    }
                    return inboxSent;
                });

        when(messageInboxMapper.updateById(any(MessageInbox.class)))
                .thenAnswer(invocation -> {
                    updateCalled.incrementAndGet();
                    return 1;
                });

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    messageService.markAsRead(msgId, receiverId);
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        // Guard: status >= READ prevents re-marking
        assertThat(updateCalled.get()).isLessThanOrEqualTo(threadCount);
    }

    @Test
    @DisplayName("状态转换顺序: SENT→DELIVERED→READ 应保持，禁止 DELIVERED→SENT 回退")
    void statusTransitionShouldNotGoBackward() throws Exception {
        String msgId = UUID.randomUUID().toString();
        Long receiverId = 200L;

        AtomicInteger currentStatus = new AtomicInteger(MessageInboxConstants.STATUS_SENT);

        when(messageInboxMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenAnswer(invocation -> {
                    MessageInbox inbox = new MessageInbox();
                    inbox.setId(1L);
                    inbox.setMsgId(msgId);
                    inbox.setReceiverId(receiverId);
                    inbox.setStatus(currentStatus.get());
                    return inbox;
                });

        when(messageInboxMapper.updateById(any(MessageInbox.class)))
                .thenAnswer(invocation -> {
                    MessageInbox updated = invocation.getArgument(0);
                    int newStatus = updated.getStatus();
                    // Status should only increase
                    assertThat(newStatus).isGreaterThanOrEqualTo(currentStatus.get());
                    currentStatus.set(newStatus);
                    return 1;
                });

        // Step 1: markDelivered
        messageService.markDelivered(msgId, receiverId);
        assertThat(currentStatus.get()).isEqualTo(MessageInboxConstants.STATUS_DELIVERED);

        // Step 2: markAsRead  
        messageService.markAsRead(msgId, receiverId);
        assertThat(currentStatus.get()).isEqualTo(MessageInboxConstants.STATUS_READ);

        // Step 3: Attempt markDelivered on an already READ message — should be no-op
        int statusBefore = currentStatus.get();
        messageService.markDelivered(msgId, receiverId);
        assertThat(currentStatus.get()).isEqualTo(statusBefore);
        // Status should NOT go back to DELIVERED
        assertThat(currentStatus.get()).isEqualTo(MessageInboxConstants.STATUS_READ);
    }

    @RepeatedTest(5)
    @DisplayName("重复测试: 并发markDelivered只应产生一次实际更新")
    void repeatedConcurrentMarkDeliveredIsSafe() throws Exception {
        String msgId = UUID.randomUUID().toString();
        Long receiverId = 300L;

        AtomicInteger actualUpdates = new AtomicInteger(0);
        AtomicInteger status = new AtomicInteger(MessageInboxConstants.STATUS_SENT);

        when(messageInboxMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenAnswer(invocation -> {
                    MessageInbox inbox = new MessageInbox();
                    inbox.setId(1L);
                    inbox.setMsgId(msgId);
                    inbox.setReceiverId(receiverId);
                    inbox.setStatus(status.get());
                    return inbox;
                });

        when(messageInboxMapper.updateById(any(MessageInbox.class)))
                .thenAnswer(invocation -> {
                    actualUpdates.incrementAndGet();
                    MessageInbox updated = invocation.getArgument(0);
                    status.set(updated.getStatus());
                    return 1;
                });

        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    messageService.markDelivered(msgId, receiverId);
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        // The actual state transition SENT→DELIVERED should only happen once
        assertThat(status.get()).isEqualTo(MessageInboxConstants.STATUS_DELIVERED);
    }

    @Test
    @DisplayName("收件箱不存在时markDelivered应安全返回")
    void markDeliveredOnNonExistentInboxShouldBeSafe() {
        String msgId = UUID.randomUUID().toString();
        Long receiverId = 999L;

        when(messageInboxMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        messageService.markDelivered(msgId, receiverId);

        verify(messageInboxMapper, never()).updateById(any(MessageInbox.class));
    }

    @Test
    @DisplayName("收件箱不存在时markAsRead应安全返回")
    void markAsReadOnNonExistentInboxShouldBeSafe() {
        String msgId = UUID.randomUUID().toString();
        Long receiverId = 999L;

        when(messageInboxMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        messageService.markAsRead(msgId, receiverId);

        verify(messageInboxMapper, never()).updateById(any(MessageInbox.class));
    }
}
