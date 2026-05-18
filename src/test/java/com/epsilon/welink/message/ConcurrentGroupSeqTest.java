package com.epsilon.welink.message;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.epsilon.welink.message.dto.MessageRequest;
import com.epsilon.welink.message.entity.Message;
import com.epsilon.welink.message.mapper.MessageInboxMapper;
import com.epsilon.welink.message.mapper.MessageMapper;
import com.epsilon.welink.message.service.MessageService;
import com.epsilon.welink.relation.mapper.GroupMemberMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * 并发群聊序号一致性测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("并发群聊序号一致性测试")
class ConcurrentGroupSeqTest {

    @Mock
    private MessageMapper messageMapper;

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
    @DisplayName("Redis INCR应保证群聊序号严格递增不重复")
    void groupSeqShouldBeStrictlyIncreasing() throws Exception {
        Long groupId = 1000L;
        // Simulate Redis INCR atomicity
        AtomicLong seqCounter = new AtomicLong(0);

        when(redisTemplate.hasKey(contains("im:group:seq:"))).thenReturn(false);
        when(valueOperations.setIfAbsent(anyString(), anyLong())).thenReturn(true);
        when(valueOperations.increment(contains("im:group:seq:")))
                .thenAnswer(invocation -> seqCounter.incrementAndGet());

        when(messageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(messageMapper.insert(any(Message.class))).thenReturn(1);
        when(redisTemplate.expire(anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(zSetOperations.add(anyString(), any(), anyDouble())).thenReturn(true);

        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        ConcurrentHashMap<Long, Boolean> seqSet = new ConcurrentHashMap<>();

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    MessageRequest request = new MessageRequest();
                    request.setMsgId(UUID.randomUUID().toString());
                    request.setGroupId(groupId);
                    request.setContent("Message " + idx);
                    request.setMsgType(1);

                    Message result = messageService.saveGroupMessage(100L + idx, request);

                    if (result.getGroupSeq() != null) {
                        // Each seq should be unique
                        Boolean existed = seqSet.putIfAbsent(result.getGroupSeq(), true);
                        assertThat(existed).as("Duplicate seq: " + result.getGroupSeq()).isNull();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(seqSet.size()).isEqualTo(threadCount);

        // Verify all sequences from 1 to threadCount exist (no gaps in atomic counter)
        for (long seq = 1; seq <= threadCount; seq++) {
            assertThat(seqSet).containsKey(seq);
        }
    }

    @Test
    @DisplayName("大量并发群聊消息的序号应保持连续无间隙")
    void groupSeqShouldBeContiguousUnderLoad() throws Exception {
        Long groupId = 2000L;
        AtomicLong seqCounter = new AtomicLong(0);

        when(redisTemplate.hasKey(contains("im:group:seq:"))).thenReturn(false);
        when(valueOperations.setIfAbsent(anyString(), anyLong())).thenReturn(true);
        when(valueOperations.increment(contains("im:group:seq:")))
                .thenAnswer(invocation -> seqCounter.incrementAndGet());

        when(messageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(messageMapper.insert(any(Message.class))).thenReturn(1);
        when(redisTemplate.expire(anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(zSetOperations.add(anyString(), any(), anyDouble())).thenReturn(true);

        int totalMessages = 200;
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(totalMessages);
        Set<Long> seqValues = ConcurrentHashMap.newKeySet();

        for (int i = 0; i < totalMessages; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    MessageRequest request = new MessageRequest();
                    request.setMsgId(UUID.randomUUID().toString());
                    request.setGroupId(groupId);
                    request.setContent("Msg-" + idx);
                    request.setMsgType(1);

                    Message result = messageService.saveGroupMessage(100L, request);
                    if (result.getGroupSeq() != null) {
                        seqValues.add(result.getGroupSeq());
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(seqValues.size()).isEqualTo(totalMessages);

        // All seq values should be in range [1, totalMessages]
        long minSeq = seqValues.stream().min(Long::compareTo).orElse(0L);
        long maxSeq = seqValues.stream().max(Long::compareTo).orElse(0L);
        assertThat(minSeq).isEqualTo(1L);
        assertThat(maxSeq).isEqualTo((long) totalMessages);

        // Verify no gaps
        for (long seq = 1; seq <= totalMessages; seq++) {
            assertThat(seqValues).as("Missing seq: " + seq).contains(seq);
        }
    }

    @Test
    @DisplayName("群聊序号从已存在的最大序号继续递增")
    void groupSeqShouldContinueFromExistingMax() throws Exception {
        Long groupId = 3000L;
        long existingMaxSeq = 99L;
        AtomicLong seqCounter = new AtomicLong(existingMaxSeq);

        // Mock: seq key exists, so no setIfAbsent
        when(redisTemplate.hasKey(contains("im:group:seq:"))).thenReturn(true);
        when(valueOperations.increment(contains("im:group:seq:")))
                .thenAnswer(invocation -> seqCounter.incrementAndGet());

        when(messageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(messageMapper.insert(any(Message.class))).thenReturn(1);
        when(redisTemplate.expire(anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(zSetOperations.add(anyString(), any(), anyDouble())).thenReturn(true);

        MessageRequest request = new MessageRequest();
        request.setMsgId(UUID.randomUUID().toString());
        request.setGroupId(groupId);
        request.setContent("Next message");
        request.setMsgType(1);

        Message result = messageService.saveGroupMessage(100L, request);

        assertThat(result.getGroupSeq()).isEqualTo(existingMaxSeq + 1);
    }

    @Test
    @DisplayName("不同群的序号应独立递增互不干扰")
    void differentGroupSeqsShouldBeIndependent() throws Exception {
        Long groupA = 1000L;
        Long groupB = 2000L;

        AtomicLong seqA = new AtomicLong(0);
        AtomicLong seqB = new AtomicLong(100); // Group B starts at 100

        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(valueOperations.setIfAbsent(anyString(), anyLong())).thenReturn(true);
        when(redisTemplate.expire(anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(zSetOperations.add(anyString(), any(), anyDouble())).thenReturn(true);

        when(valueOperations.increment(contains(String.valueOf(groupA))))
                .thenAnswer(invocation -> seqA.incrementAndGet());
        when(valueOperations.increment(contains(String.valueOf(groupB))))
                .thenAnswer(invocation -> seqB.incrementAndGet());

        when(messageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(messageMapper.insert(any(Message.class))).thenReturn(1);

        MessageRequest reqA = new MessageRequest();
        reqA.setMsgId(UUID.randomUUID().toString());
        reqA.setGroupId(groupA);
        reqA.setContent("A");
        reqA.setMsgType(1);

        MessageRequest reqB = new MessageRequest();
        reqB.setMsgId(UUID.randomUUID().toString());
        reqB.setGroupId(groupB);
        reqB.setContent("B");
        reqB.setMsgType(1);

        Message resultA = messageService.saveGroupMessage(100L, reqA);
        Message resultB = messageService.saveGroupMessage(200L, reqB);

        assertThat(resultA.getGroupSeq()).isEqualTo(1L);
        assertThat(resultB.getGroupSeq()).isEqualTo(101L);
    }
}
