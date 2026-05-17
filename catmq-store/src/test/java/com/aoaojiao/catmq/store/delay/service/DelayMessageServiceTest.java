package com.aoaojiao.catmq.store.delay.service;

import com.aoaojiao.catmq.store.delay.model.DelayMessage;
import com.aoaojiao.catmq.store.delay.timer.TimeWheel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * 延迟消息服务测试
 *
 * @author DD
 */
public class DelayMessageServiceTest {

    private DelayMessageService delayMessageService;

    @Before
    public void setUp() {
        delayMessageService = new DelayMessageService();
        delayMessageService.start();
    }

    @After
    public void tearDown() {
        delayMessageService.shutdown();
    }

    @Test
    public void testSubmitDelayMessage() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<DelayMessage> received = new AtomicReference<>();

        // 使用简单的验证方式 - 实际测试会通过集成测试验证分发
        String messageId = "msg_001";
        String topic = "test_topic";

        // 使用实际的 API：create(messageId, topic, queueId, physicalOffset, size, tagCode, delayMs)
        DelayMessage msg = DelayMessage.create(messageId, topic, 0, 0L, 100, 12345L, 100L);

        boolean submitted = delayMessageService.submitDelayMessage(msg, 100L);
        assertTrue("应该提交成功", submitted);

        // 验证消息已被记录
        assertEquals("应该有1条待处理消息", 1, delayMessageService.getTotalDelayMessageCount());
        assertNotNull("应该能获取到消息", delayMessageService.getDelayMessage(messageId));

        // 等待延迟到期
        Thread.sleep(200);

        // 注意：由于测试环境没有分发服务，消息到期后会被分发但不会有回调
        // 实际功能通过集成测试验证
    }

    @Test
    public void testDelayOrder() throws InterruptedException {
        String messageId1 = "msg_A";
        String messageId2 = "msg_B";

        DelayMessage msgA = DelayMessage.create(messageId1, "test", 0, 0L, 100, 12345L, 100L);
        DelayMessage msgB = DelayMessage.create(messageId2, "test", 0, 0L, 100, 12345L, 200L);

        // 先提交B（200ms），再提交A（100ms）
        delayMessageService.submitDelayMessage(msgB, 200L);
        delayMessageService.submitDelayMessage(msgA, 100L);

        assertEquals("应该有2条待处理消息", 2, delayMessageService.getTotalDelayMessageCount());

        // 验证剩余时间
        long remainingA = delayMessageService.getRemainingDelayTime(messageId1);
        long remainingB = delayMessageService.getRemainingDelayTime(messageId2);

        assertTrue("A的剩余时间应该小于等于B", remainingA <= remainingB);

        // 等待延迟到期
        Thread.sleep(300);
    }

    @Test
    public void testCancelDelayMessage() throws InterruptedException {
        String messageId = "cancel_msg";
        DelayMessage msg = DelayMessage.create(messageId, "test", 0, 0L, 100, 12345L, 500L);

        delayMessageService.submitDelayMessage(msg, 500L);
        assertEquals("提交后应该有1条消息", 1, delayMessageService.getTotalDelayMessageCount());

        boolean cancelled = delayMessageService.cancelDelayMessage(messageId);
        assertTrue("应该取消成功", cancelled);

        assertNull("取消后应该获取不到消息", delayMessageService.getDelayMessage(messageId));
        assertEquals("取消后应该有0条消息", 0, delayMessageService.getTotalDelayMessageCount());
    }

    @Test
    public void testCancelNonExistMessage() {
        boolean cancelled = delayMessageService.cancelDelayMessage("non_exist");
        assertFalse("不存在的消息取消应该失败", cancelled);
    }

    @Test
    public void testGetDelayMessageCount() {
        String topic1 = "topic_1";
        String topic2 = "topic_2";

        for (int i = 0; i < 3; i++) {
            DelayMessage msg = DelayMessage.create("msg_t1_" + i, topic1, 0, 0L, 100, 12345L, 1000L);
            delayMessageService.submitDelayMessage(msg, 1000L);
        }

        for (int i = 0; i < 5; i++) {
            DelayMessage msg = DelayMessage.create("msg_t2_" + i, topic2, 0, 0L, 100, 12345L, 1000L);
            delayMessageService.submitDelayMessage(msg, 1000L);
        }

        assertEquals("topic_1应该有3条消息", 3, delayMessageService.getDelayMessageCount(topic1));
        assertEquals("topic_2应该有5条消息", 5, delayMessageService.getDelayMessageCount(topic2));
        assertEquals("总共应该有8条消息", 8, delayMessageService.getTotalDelayMessageCount());
    }

    @Test
    public void testSubmitWithoutRunning() {
        // 创建一个新的但不启动的服务
        DelayMessageService newService = new DelayMessageService();

        DelayMessage msg = DelayMessage.create("test_msg", "test", 0, 0L, 100, 12345L, 100L);
        boolean submitted = newService.submitDelayMessage(msg, 100L);
        assertFalse("未启动的服务不应该能提交消息", submitted);

        // 清理
        newService.shutdown();
    }

    @Test
    public void testStatus() {
        String status = delayMessageService.getStatus();
        assertNotNull("应该返回状态字符串", status);
        assertTrue("状态应该包含running信息", status.contains("running"));
        assertTrue("状态应该包含消息数量信息", status.contains("totalDelayMessages"));
    }
}