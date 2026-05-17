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
    }

    @After
    public void tearDown() {
        producer.shutdown();
    }

    @Test
    public void testProducerInitialization() {
        assertNotNull("Producer 应该初始化成功", producer);
    }

    @Test
    public void testSendSyncWithTestMode() {
        // 启用测试模式
        producer.setTestMode(true);

        SendMessageResponse response = producer.send("test_topic", "test content".getBytes());

        assertNotNull("应该返回响应", response);
        assertTrue("应该成功", response.isSuccess());
        assertNotNull("应该有消息 ID", response.getMessageId());
    }

    @Test
    public void testSendAsyncWithTestMode() throws InterruptedException {
        producer.setTestMode(true);

        final boolean[] success = {false};
        final String[] receivedMsgId = new String[1];

        producer.sendAsync("test_topic", "async content".getBytes(), new SendCallback() {
            @Override
            public void onSuccess(SendMessageResponse response) {
                success[0] = true;
                receivedMsgId[0] = response.getMessageId();
            }

            @Override
            public void onFailure(Throwable e) {
                success[0] = false;
            }
        });

        // 等待异步完成
        Thread.sleep(100);

        assertTrue("异步发送应该成功", success[0]);
        assertNotNull("应该有消息 ID", receivedMsgId[0]);
    }

    @Test
    public void testSendWithProperties() {
        producer.setTestMode(true);

        java.util.Map<String, String> properties = new java.util.HashMap<>();
        properties.put("key1", "value1");
        properties.put("key2", "value2");

        SendMessageResponse response = producer.send("test_topic", "content".getBytes(), properties);

        assertTrue("应该成功", response.isSuccess());
        assertNotNull("应该有消息 ID", response.getMessageId());
    }

    @Test
    public void testSendEmptyTopic() {
        producer.setTestMode(true);

        try {
            producer.send("", "content".getBytes());
            fail("空 topic 应该抛出异常");
        } catch (IllegalArgumentException e) {
            assertTrue("异常信息应该包含 topic", e.getMessage().contains("topic"));
        }
    }

    @Test
    public void testSendNullContent() {
        producer.setTestMode(true);

        try {
            producer.send("test_topic", null);
            fail("null content 应该抛出异常");
        } catch (IllegalArgumentException e) {
            assertTrue("异常信息应该包含 content", e.getMessage().contains("content"));
        }
    }

    @Test
    public void testProducerShutdown() {
        producer.shutdown();
        // 关闭后应该仍然可以调用（但可能失败），不应该崩溃
        try {
            producer.shutdown();
        } catch (Exception e) {
            // 允许重复调用 shutdown
        }
    }

    @Test
    public void testBatchSend() {
        producer.setTestMode(true);

        int batchSize = 10;
        int successCount = 0;

        for (int i = 0; i < batchSize; i++) {
            SendMessageResponse response = producer.send("test_topic", ("msg_" + i).getBytes());
            if (response.isSuccess()) {
                successCount++;
            }
        }

        assertEquals("所有消息应该发送成功", batchSize, successCount);
    }
}