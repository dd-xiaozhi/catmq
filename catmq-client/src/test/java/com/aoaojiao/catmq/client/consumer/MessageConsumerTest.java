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
        // 注意：在测试环境中，由于没有真实的 broker 连接，
        // start() 方法可能会失败，这是预期行为
        // 不需要 try-catch，因为我们需要测试能正确报告错误
    }

    @After
    public void tearDown() {
        consumer.shutdown();
    }

    @Test
    public void testConsumerInitialization() {
        assertNotNull("Consumer 应该初始化成功", consumer);
    }

    @Test(expected = IllegalStateException.class)
    public void testSubscribeWithoutStart() {
        // 未启动的消费者应该抛出异常
        consumer.subscribe("test_topic", (t, body, props) -> {
            return MessageConsumer.ConsumeResult.SUCCESS;
        });
    }

    @Test
    public void testPullMessageWithoutStart() {
        try {
            consumer.pull("test_topic", 0, 0L);
            fail("应该抛出异常");
        } catch (IllegalStateException e) {
            // 预期行为
            assertTrue(true);
        }
    }

    @Test
    public void testSubscribeEmptyTopic() {
        try {
            consumer.subscribe("", (t, body, props) -> MessageConsumer.ConsumeResult.SUCCESS);
            fail("空 topic 应该抛出异常");
        } catch (IllegalArgumentException | IllegalStateException e) {
            // 预期行为
            assertTrue("异常应该是 IllegalArgument 或 IllegalState",
                    e instanceof IllegalArgumentException ||
                    e instanceof IllegalStateException);
        }
    }

    @Test
    public void testConsumerShutdown() {
        consumer.shutdown();
        // 关闭后应该仍然可以调用 shutdown，不应该崩溃
        consumer.shutdown();
    }

    @Test
    public void testBuilderPattern() {
        MessageConsumer builderConsumer = new MessageConsumer.Builder()
                .setBrokerAddress("localhost:9090")
                .setPullIntervalMs(500)
                .setMaxPullMessageCount(20)
                .build();

        assertNotNull("Builder 构建的 Consumer 应该不为空", builderConsumer);
    }

    @Test
    public void testConsumeResultEnum() {
        assertEquals("SUCCESS", MessageConsumer.ConsumeResult.SUCCESS.name());
        assertEquals("RETRY_LATER", MessageConsumer.ConsumeResult.RETRY_LATER.name());
        assertEquals("SKIP", MessageConsumer.ConsumeResult.SKIP.name());
    }
}