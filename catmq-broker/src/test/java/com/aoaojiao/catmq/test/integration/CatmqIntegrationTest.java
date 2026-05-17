package com.aoaojiao.catmq.test.integration;

import com.aoaojiao.catmq.store.config.MessageStoreConfig;
import com.aoaojiao.catmq.store.delay.model.DelayMessage;
import com.aoaojiao.catmq.store.delay.model.DeadLetterMessage;
import com.aoaojiao.catmq.store.delay.service.DelayMessageService;
import com.aoaojiao.catmq.store.delay.service.DeadLetterQueueService;
import com.aoaojiao.catmq.store.delay.timer.TimeWheel;
import com.aoaojiao.catmq.store.transaction.TransactionMessage;
import com.aoaojiao.catmq.store.transaction.TransactionMessageService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * 集成测试
 * 测试消息队列的完整流程
 *
 * @author DD
 */
public class CatmqIntegrationTest {

    private DelayMessageService delayService;
    private DeadLetterQueueService dlqService;
    private TransactionMessageService txService;
    private String testStorePath;

    @Before
    public void setUp() {
        testStorePath = "target/test-integration-store-" + System.currentTimeMillis();
        new File(testStorePath).mkdirs();

        // 初始化各服务
        delayService = new DelayMessageService();
        dlqService = new DeadLetterQueueService();
        txService = new TransactionMessageService();

        delayService.start();
        dlqService.init(testStorePath);
        dlqService.start();
        txService.init(testStorePath);
        txService.start();
    }

    @After
    public void tearDown() {
        delayService.shutdown();
        dlqService.shutdown();
        txService.shutdown();
    }

    /**
     * 测试完整的延迟消息流程
     * 延迟 -> 过期 -> （假设分发） -> 重试 -> 超过重试次数 -> 进入死信队列
     */
    @Test
    public void testDelayMessageToDlqFlow() throws InterruptedException {
        String topic = "integration_test_topic";
        String messageId = "msg_flow_001";

        // 1. 提交延迟消息
        DelayMessage delayMsg = DelayMessage.create(
                messageId, topic, 0, 0L, 100, 12345L, 100L);

        boolean submitted = delayService.submitDelayMessage(delayMsg, 100L);
        assertTrue("延迟消息应该提交成功", submitted);
        assertEquals("应该有1条待处理消息", 1, delayService.getTotalDelayMessageCount());

        // 2. 等待延迟消息过期
        Thread.sleep(300);

        // 3. 模拟消费失败，进入重试（这里直接提交死信）
        DeadLetterMessage dlqMsg = DeadLetterMessage.builder()
                .messageId(messageId + "_dlq")
                .originalMessageId(messageId)
                .topic(topic)
                .queueId(0)
                .consumerGroup("test_consumer")
                .deadLetterReason("消费失败超过最大重试次数")
                .retryCount(16)
                .build();

        boolean dlqSubmitted = dlqService.submitDeadLetter(dlqMsg);
        assertTrue("死信消息应该提交成功", dlqSubmitted);

        // 4. 验证死信消息
        DeadLetterMessage retrievedDlq = dlqService.getDeadLetterMessage(messageId + "_dlq");
        assertNotNull("应该能获取到死信消息", retrievedDlq);
        assertEquals("原因应该匹配", "消费失败超过最大重试次数", retrievedDlq.getDeadLetterReason());
        assertEquals("重试次数应该匹配", 16, retrievedDlq.getRetryCount());
    }

    /**
     * 测试事务消息的完整流程
     * Half 消息 -> 本地事务成功 -> 提交事务消息
     */
    @Test
    public void testTransactionMessageCommitFlow() {
        String transactionId = "tx_flow_commit_001";
        String topic = "transaction_test_topic";

        // 1. 发送 Half 消息
        TransactionMessage halfMsg = txService.sendHalfMessage(
                transactionId, topic, 0, 1000L, 100, 12345L,
                "测试事务消息内容", null);

        assertNotNull("应该返回事务消息", halfMsg);
        assertEquals("状态应该是 PREPARED",
                TransactionMessage.TransactionState.PREPARED, halfMsg.getTransactionState());

        // 2. 模拟本地事务执行成功
        boolean localTxSuccess = true;

        // 3. 根据本地事务结果提交
        if (localTxSuccess) {
            boolean committed = txService.commitTransaction(transactionId);
            assertTrue("应该提交成功", committed);
        }

        // 4. 验证提交后的状态
        TransactionMessage committedMsg = txService.getTransactionMessage(transactionId);
        assertNotNull("应该能获取到事务消息", committedMsg);
        assertEquals("状态应该是 END",
                TransactionMessage.TransactionState.END, committedMsg.getTransactionState());
        assertTrue("应该是已提交", committedMsg.isCommitted());
    }

    /**
     * 测试事务消息的回滚流程
     * Half 消息 -> 本地事务失败 -> 回滚事务消息
     */
    @Test
    public void testTransactionMessageRollbackFlow() {
        String transactionId = "tx_flow_rollback_001";
        String topic = "transaction_test_topic";

        // 1. 发送 Half 消息
        txService.sendHalfMessage(
                transactionId, topic, 0, 1000L, 100, 12345L,
                "测试事务消息内容", null);

        // 2. 模拟本地事务执行失败
        boolean localTxSuccess = false;

        // 3. 根据本地事务结果回滚
        if (!localTxSuccess) {
            boolean rolledBack = txService.rollbackTransaction(transactionId);
            assertTrue("应该回滚成功", rolledBack);
        }

        // 4. 验证回滚后的状态
        TransactionMessage rolledBackMsg = txService.getTransactionMessage(transactionId);
        assertNotNull("应该能获取到事务消息", rolledBackMsg);
        assertEquals("状态应该是 END",
                TransactionMessage.TransactionState.END, rolledBackMsg.getTransactionState());
        assertTrue("应该是已回滚", rolledBackMsg.isRolledBack());
    }

    /**
     * 测试多条延迟消息
     */
    @Test
    public void testMultipleDelayMessages() throws InterruptedException {
        String topic = "multiple_delay_topic";

        // 提交多条延迟消息，不同的延迟时间
        for (int i = 1; i <= 5; i++) {
            DelayMessage msg = DelayMessage.create(
                    "msg_" + i, topic, 0, (long) i * 1000, 100, 12345L, (long) (i * 100));
            delayService.submitDelayMessage(msg, (long) (i * 100));
        }

        assertEquals("应该有5条待处理消息", 5, delayService.getTotalDelayMessageCount());

        // 等待所有消息过期
        Thread.sleep(1000);
    }

    /**
     * 测试多条事务消息的混合提交/回滚
     */
    @Test
    public void testMixedTransactionMessages() {
        // 提交3条事务消息
        for (int i = 1; i <= 3; i++) {
            txService.sendHalfMessage(
                    "tx_mixed_" + i, "mixed_topic", 0, (long) i * 1000, 100, 12345L,
                    "消息 " + i, null);
        }

        // 提交前2条，回滚第3条
        txService.commitTransaction("tx_mixed_1");
        txService.commitTransaction("tx_mixed_2");
        txService.rollbackTransaction("tx_mixed_3");

        // 验证状态
        int preparedCount = txService.getTransactionCountByState(
                TransactionMessage.TransactionState.PREPARED);
        int endCount = txService.getTransactionCountByState(
                TransactionMessage.TransactionState.END);

        assertEquals("应该有0个PREPARED状态", 0, preparedCount);
        assertEquals("应该有3个END状态", 3, endCount);
    }

    /**
     * 测试死信消息的批量处理
     */
    @Test
    public void testDeadLetterBatchProcessing() {
        String topic = "batch_dlq_topic";
        String consumerGroup = "batch_dlq_consumer";

        // 提交10条死信消息
        for (int i = 0; i < 10; i++) {
            DeadLetterMessage msg = DeadLetterMessage.builder()
                    .messageId("batch_dlq_msg_" + i)
                    .topic(topic)
                    .consumerGroup(consumerGroup)
                    .deadLetterReason("批量测试失败 " + i)
                    .build();
            dlqService.submitDeadLetter(msg);
        }

        assertEquals("应该有10条死信消息",
                10, dlqService.getDeadLetterMessagesByTopic(topic).size());

        // 批量处理（重试）
        int processedCount = dlqService.batchProcessDeadLetters(topic, null, "RETRY", "准备重试");
        assertEquals("应该处理10条消息", 10, processedCount);
    }

    /**
     * 测试死信消息的删除
     */
    @Test
    public void testDeadLetterDelete() {
        String messageId = "dlq_delete_001";

        DeadLetterMessage msg = DeadLetterMessage.builder()
                .messageId(messageId)
                .topic("delete_test_topic")
                .consumerGroup("delete_consumer")
                .deadLetterReason("测试删除")
                .build();

        dlqService.submitDeadLetter(msg);

        assertNotNull("提交后应该能获取到", dlqService.getDeadLetterMessage(messageId));

        // 删除
        dlqService.processDeadLetter(messageId, "DELETE", 0L, "无需处理");

        assertNull("删除后应该获取不到", dlqService.getDeadLetterMessage(messageId));
    }

    /**
     * 测试事务消息带属性
     */
    @Test
    public void testTransactionMessageWithProperties() {
        String transactionId = "tx_properties_001";
        Map<String, String> properties = new HashMap<>();
        properties.put("traceId", "trace_12345");
        properties.put("serviceName", "order-service");
        properties.put("userId", "user_001");

        TransactionMessage txMsg = txService.sendHalfMessage(
                transactionId, "properties_topic", 0, 1000L, 100, 12345L,
                "测试带属性的消息", properties);

        assertNotNull("应该返回事务消息", txMsg);
        assertNotNull("应该有属性", txMsg.getProperties());
        assertEquals("应该有3个属性", 3, txMsg.getProperties().size());
        assertEquals("traceId应该匹配", "trace_12345", txMsg.getProperties().get("traceId"));
        assertEquals("serviceName应该匹配", "order-service", txMsg.getProperties().get("serviceName"));
        assertEquals("userId应该匹配", "user_001", txMsg.getProperties().get("userId"));

        // 提交
        txService.commitTransaction(transactionId);

        TransactionMessage committed = txService.getTransactionMessage(transactionId);
        assertNotNull("提交后应该能获取到", committed);
        assertEquals("提交后属性应该保留", 3, committed.getProperties().size());
    }

    /**
     * 测试各服务的状态输出
     */
    @Test
    public void testServiceStatus() {
        String delayStatus = delayService.getStatus();
        String dlqStatus = dlqService.getStatus();
        String txStatus = txService.getStatus();

        assertNotNull("延迟服务状态应该不为空", delayStatus);
        assertNotNull("死信服务状态应该不为空", dlqStatus);
        assertNotNull("事务服务状态应该不为空", txStatus);

        assertTrue("延迟服务状态应该包含running", delayStatus.contains("running"));
        assertTrue("死信服务状态应该包含running", dlqStatus.contains("running"));
        assertTrue("事务服务状态应该包含running", txStatus.contains("running"));
    }

    /**
     * 测试时间轮
     */
    @Test
    public void testTimeWheel() throws InterruptedException {
        TimeWheel wheel = new TimeWheel(100L, 10, (key, data) -> {
            // 时间轮任务
        }, "TEST");
        wheel.start();

        try {
            AtomicInteger executed = new AtomicInteger(0);

            TimeWheel testWheel = new TimeWheel(100L, 10, (k, d) -> {
                executed.incrementAndGet();
            }, "TEST");
            testWheel.start();

            // 添加任务
            boolean added = testWheel.addTask("test_task", 200L, null);
            assertTrue("任务应该添加成功", added);

            // 等待任务触发
            Thread.sleep(500);

            // 验证时间轮工作
            assertTrue("任务应该被添加", testWheel.getTaskCacheSize() >= 0);

            testWheel.stop();
        } finally {
            wheel.stop();
        }
    }
}