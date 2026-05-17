package com.aoaojiao.catmq.client.consumer;

import com.aoaojiao.catmq.client.config.ClientConfig;
import com.aoaojiao.catmq.client.model.PullMessageResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * 消息消费者测试
 *
 * @author DD
 */
public class MessageConsumerTest {

    private MessageConsumer consumer;

    @Before
    public void setUp() {
        ClientConfig config = new ClientConfig();
        config.setBrokerAddress("localhost:8080");
        consumer = new MessageConsumer(config);
    }

    @After
    public void tearDown() {
        consumer.shutdown();
    }

    @Test
    public void testConsumerInitialization() {
        assertNotNull("Consumer 应该初始化成功", consumer);
    }

    @Test
    public void testSubscribe() {
        String topic = "test_topic";

        consumer.subscribe(topic, (t, body, props) -> {
            // 消息处理逻辑
            return ConsumeResult.SUCCESS;
        });

        assertTrue("应该已订阅主题", consumer.isSubscribed(topic));
    }

    @Test
    public void testUnsubscribe() {
        String topic = "test_topic";

        consumer.subscribe(topic, (t, body, props) -> ConsumeResult.SUCCESS);
        assertTrue("订阅后应该已订阅", consumer.isSubscribed(topic));

        consumer.unsubscribe(topic);
        assertFalse("取消订阅后应该未订阅", consumer.isSubscribed(topic));
    }

    @Test
    public void testPullMessage() {
        consumer.setTestMode(true);

        PullMessageResponse response = consumer.pull("test_topic", 0, 0L);

        assertNotNull("应该返回响应", response);
        assertTrue("应该成功", response.isSuccess());
        assertNotNull("应该有消息列表", response.getMessages());
    }

    @Test
    public void testStartPulling() throws InterruptedException {
        consumer.setTestMode(true);

        String topic = "test_topic";
        final int[] messageCount = {0};

        consumer.subscribe(topic, (t, body, props) -> {
            messageCount[0]++;
            return ConsumeResult.SUCCESS;
        });

        consumer.startPulling();

        // 等待一段时间让拉取线程工作
        Thread.sleep(500);

        // 在测试模式下，应该能收到一些消息
        assertTrue("应该收到一些消息", messageCount[0] > 0);
    }

    @Test
    public void testConsumeWithRetry() throws InterruptedException {
        consumer.setTestMode(true);

        String topic = "retry_topic";
        final int[] retryCount = {0};

        consumer.subscribe(topic, (t, body, props) -> {
            int count = ++retryCount[0];
            if (count < 3) {
                return ConsumeResult.RETRY;
            }
            return ConsumeResult.SUCCESS;
        });

        consumer.startPulling();

        Thread.sleep(500);

        assertTrue("应该至少重试 3 次", retryCount[0] >= 3);
    }

    @Test
    public void testSubscribeEmptyTopic() {
        try {
            consumer.subscribe("", (t, body, props) -> ConsumeResult.SUCCESS);
            fail("空 topic 应该抛出异常");
        } catch (IllegalArgumentException e) {
            assertTrue("异常信息应该包含 topic", e.getMessage().contains("topic"));
        }
    }

    @Test
    public void testSubscribeNullListener() {
        try {
            consumer.subscribe("test_topic", null);
            fail("null listener 应该抛出异常");
        } catch (IllegalArgumentException e) {
            assertTrue("异常信息应该包含 listener", e.getMessage().contains("listener"));
        }
    }

    @Test
    public void testConsumerShutdown() {
        consumer.shutdown();
        // 关闭后应该仍然可以调用 shutdown，不应该崩溃
        try {
            consumer.shutdown();
        } catch (Exception e) {
            // 允许重复调用
        }
    }
}