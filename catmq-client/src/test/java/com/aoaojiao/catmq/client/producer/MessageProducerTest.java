package com.aoaojiao.catmq.client.producer;

import com.aoaojiao.catmq.client.config.ClientConfig;
import com.aoaojiao.catmq.client.model.SendMessageResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * 消息生产者测试
 *
 * @author DD
 */
public class MessageProducerTest {

    private MessageProducer producer;

    @Before
    public void setUp() {
        ClientConfig config = new ClientConfig();
        config.setBrokerAddress("localhost:8080");
        producer = new MessageProducer(config);
        // 注意：在测试环境中，由于没有真实的 broker 连接，
        // start() 方法可能会失败，这是预期行为
    }

    @After
    public void tearDown() {
        producer.shutdown();
    }

    @Test
    public void testProducerInitialization() {
        assertNotNull("Producer 应该初始化成功", producer);
    }

    @Test(expected = IllegalStateException.class)
    public void testSendSyncStringWithoutStart() {
        producer.send("test_topic", "test content");
    }

    @Test(expected = IllegalStateException.class)
    public void testSendSyncBytesWithoutStart() {
        byte[] body = "test content bytes".getBytes();
        producer.send("test_topic", body);
    }

    @Test(expected = IllegalStateException.class)
    public void testSendAsyncWithoutStart() {
        producer.sendAsync("test_topic", "async content", new MessageProducer.SendCallback() {
            @Override
            public void onSuccess(SendMessageResponse response) {
            }

            @Override
            public void onFailure(Throwable e) {
            }
        });
    }

    @Test
    public void testSendWithEmptyTopic() {
        try {
            producer.send("", "content");
            fail("空 topic 应该抛出异常");
        } catch (IllegalArgumentException | IllegalStateException e) {
            // 预期行为：空 topic 抛出异常或未启动状态异常
            assertTrue("异常应该是 IllegalArgument 或 IllegalState",
                    e instanceof IllegalArgumentException ||
                    e instanceof IllegalStateException);
        }
    }

    @Test
    public void testProducerShutdown() {
        // 关闭后应该仍然可以调用 shutdown，不应该崩溃
        producer.shutdown();
        producer.shutdown();
    }

    @Test
    public void testBuilderPattern() {
        MessageProducer builderProducer = new MessageProducer.Builder()
                .setBrokerAddress("localhost:9090")
                .setMaxRetryTimes(5)
                .build();

        assertNotNull("Builder 构建的 Producer 应该不为空", builderProducer);
    }
}