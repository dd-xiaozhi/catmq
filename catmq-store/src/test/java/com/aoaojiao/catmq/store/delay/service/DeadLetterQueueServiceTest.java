package com.aoaojiao.catmq.store.delay.service;

import com.aoaojiao.catmq.store.delay.model.DeadLetterMessage;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * 死信队列服务测试
 *
 * @author DD
 */
public class DeadLetterQueueServiceTest {

    private DeadLetterQueueService dlqService;
    private String testStorePath;

    @Before
    public void setUp() {
        dlqService = new DeadLetterQueueService();
        testStorePath = "target/test-store-" + System.currentTimeMillis();
        new File(testStorePath).mkdirs();
        dlqService.init(testStorePath);
        dlqService.start();
    }

    @After
    public void tearDown() {
        dlqService.shutdown();
    }

    @Test
    public void testSubmitDeadLetter() {
        String messageId = "dlq_msg_001";
        String topic = "test_topic";
        String consumerGroup = "test_consumer";

        DeadLetterMessage msg = DeadLetterMessage.builder()
                .messageId(messageId)
                .originalMessageId("original_msg_001")
                .topic(topic)
                .queueId(0)
                .physicalOffset(0L)
                .size(100)
                .tagCode(12345L)
                .consumerGroup(consumerGroup)
                .deadLetterReason("测试失败")
                .build();

        boolean submitted = dlqService.submitDeadLetter(msg);
        assertTrue("应该提交成功", submitted);

        // 验证消息已被记录
        DeadLetterMessage retrieved = dlqService.getDeadLetterMessage(messageId);
        assertNotNull("应该能获取到消息", retrieved);
        assertEquals("消息ID应该匹配", messageId, retrieved.getMessageId());
        assertEquals("主题应该匹配", topic, retrieved.getTopic());
        assertEquals("原因应该匹配", "测试失败", retrieved.getDeadLetterReason());
    }

    @Test
    public void testGetDeadLetterMessagesByTopic() {
        String topic = "topic_test";

        for (int i = 0; i < 5; i++) {
            DeadLetterMessage msg = DeadLetterMessage.builder()
                    .messageId("dlq_" + i)
                    .topic(topic)
                    .queueId(0)
                    .deadLetterReason("测试失败 " + i)
                    .build();
            dlqService.submitDeadLetter(msg);
        }

        List<DeadLetterMessage> messages = dlqService.getDeadLetterMessagesByTopic(topic);
        assertEquals("应该有5条死信消息", 5, messages.size());
    }

    @Test
    public void testGetDeadLetterMessagesByConsumerGroup() {
        String consumerGroup = "consumer_consumer_test";

        for (int i = 0; i < 3; i++) {
            DeadLetterMessage msg = DeadLetterMessage.builder()
                    .messageId("dlq_cg_" + i)
                    .topic("test_topic")
                    .consumerGroup(consumerGroup)
                    .deadLetterReason("测试失败")
                    .build();
            dlqService.submitDeadLetter(msg);
        }

        List<DeadLetterMessage> messages = dlqService.getDeadLetterMessagesByConsumerGroup(consumerGroup);
        assertEquals("应该有3条死信消息", 3, messages.size());
    }

    @Test
    public void testPendingDeadLetters() {
        // 提交一些死信
        for (int i = 0; i < 5; i++) {
            DeadLetterMessage msg = DeadLetterMessage.builder()
                    .messageId("dlq_pending_" + i)
                    .topic("test_topic")
                    .consumerGroup("test_consumer")
                    .deadLetterReason("测试失败")
                    .build();
            dlqService.submitDeadLetter(msg);
        }

        List<DeadLetterMessage> pending = dlqService.getPendingDeadLetters();
        assertEquals("应该有5条待处理死信消息", 5, pending.size());
        assertTrue("所有消息应该都是待处理状态", pending.stream().allMatch(DeadLetterMessage::isPending));
    }

    @Test
    public void testProcessDeadLetterRetry() {
        String messageId = "dlq_process_001";

        DeadLetterMessage msg = DeadLetterMessage.builder()
                .messageId(messageId)
                .topic("test_topic")
                .consumerGroup("test_consumer")
                .deadLetterReason("测试失败")
                .build();

        dlqService.submitDeadLetter(msg);

        boolean processed = dlqService.processDeadLetter(messageId, "RETRY", 1000L, "准备重试");
        assertTrue("应该处理成功", processed);

        DeadLetterMessage retrieved = dlqService.getDeadLetterMessage(messageId);
        assertNotNull("消息应该还在索引中", retrieved);
        assertEquals("状态应该是处理中", 1, retrieved.getStatus());
    }

    @Test
    public void testProcessDeadLetterDelete() {
        String messageId = "dlq_delete_001";

        DeadLetterMessage msg = DeadLetterMessage.builder()
                .messageId(messageId)
                .topic("test_topic")
                .consumerGroup("test_consumer")
                .deadLetterReason("测试失败")
                .build();

        dlqService.submitDeadLetter(msg);

        boolean processed = dlqService.processDeadLetter(messageId, "DELETE", 0L, "无需重试");
        assertTrue("应该处理成功", processed);

        DeadLetterMessage retrieved = dlqService.getDeadLetterMessage(messageId);
        assertNull("消息应该已删除", retrieved);
    }

    @Test
    public void testProcessDeadLetterIgnore() {
        String messageId = "dlq_ignore_001";

        DeadLetterMessage msg = DeadLetterMessage.builder()
                .messageId(messageId)
                .topic("test_topic")
                .consumerGroup("test_consumer")
                .deadLetterReason("测试失败")
                .build();

        dlqService.submitDeadLetter(msg);

        boolean processed = dlqService.processDeadLetter(messageId, "IGNORE", 0L, "忽略处理");
        assertTrue("应该处理成功", processed);

        DeadLetterMessage retrieved = dlqService.getDeadLetterMessage(messageId);
        assertNotNull("消息应该还在索引中", retrieved);
        assertEquals("状态应该是已处理", 2, retrieved.getStatus());
    }

    @Test
    public void testProcessNonExistMessage() {
        boolean processed = dlqService.processDeadLetter("non_exist", "RETRY", 1000L, "");
        assertFalse("不存在的消息处理应该失败", processed);
    }

    @Test
    public void testBatchProcessDeadLetters() {
        String topic = "batch_topic";
        for (int i = 0; i < 10; i++) {
            DeadLetterMessage msg = DeadLetterMessage.builder()
                    .messageId("dlq_batch_" + i)
                    .topic(topic)
                    .consumerGroup("batch_consumer")
                    .deadLetterReason("批量测试失败")
                    .build();
            dlqService.submitDeadLetter(msg);
        }

        assertEquals("处理前应该有10条消息", 10, dlqService.getDeadLetterCount(topic));

        int processedCount = dlqService.batchProcessDeadLetters(topic, null, "DELETE", "批量删除");
        assertEquals("应该处理10条消息", 10, processedCount);
        assertEquals("处理后应该有0条消息", 0, dlqService.getDeadLetterCount(topic));
    }

    @Test
    public void testDeadLetterCounts() {
        for (int i = 0; i < 7; i++) {
            DeadLetterMessage msg = DeadLetterMessage.builder()
                    .messageId("dlq_count_" + i)
                    .topic("count_topic")
                    .deadLetterReason("计数测试失败")
                    .build();
            dlqService.submitDeadLetter(msg);
        }

        assertEquals("topic应该有7条消息", 7, dlqService.getDeadLetterCount("count_topic"));
        assertEquals("总共应该有7条消息", 7, dlqService.getTotalDeadLetterCount());
    }

    @Test
    public void testGetDeadLetterStats() {
        // 按主题分布
        dlqService.submitDeadLetter(DeadLetterMessage.builder()
                .messageId("dlq_stats_1").topic("topic_a").deadLetterReason("测试").build());
        dlqService.submitDeadLetter(DeadLetterMessage.builder()
                .messageId("dlq_stats_2").topic("topic_a").deadLetterReason("测试").build());
        dlqService.submitDeadLetter(DeadLetterMessage.builder()
                .messageId("dlq_stats_3").topic("topic_b").deadLetterReason("测试").build());

        Map<String, Integer> topicStats = dlqService.getDeadLetterStatsByTopic();
        assertTrue("应该包含topic_a", topicStats.containsKey("topic_a"));
        assertTrue("应该包含topic_b", topicStats.containsKey("topic_b"));
        assertEquals("topic_a应该有2条", 2, (int)topicStats.get("topic_a"));
        assertEquals("topic_b应该有1条", 1, (int)topicStats.get("topic_b"));
    }

    @Test
    public void testDlqTopic() {
        assertNotNull("应该有死信主题", dlqService.getDlqTopic());
        assertTrue("应该包含DLQ前缀", dlqService.getDlqTopic().startsWith("%DLQ%."));
    }

    @Test
    public void testStatus() {
        String status = dlqService.getStatus();
        assertNotNull("应该返回状态字符串", status);
        assertTrue("状态应该包含running信息", status.contains("running"));
        assertTrue("状态应该包含消息数量信息", status.contains("totalDeadLetters"));
    }
}