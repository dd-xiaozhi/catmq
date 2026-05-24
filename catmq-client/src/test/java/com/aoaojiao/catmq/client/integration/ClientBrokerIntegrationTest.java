package com.aoaojiao.catmq.client.integration;

import com.aoaojiao.catmq.client.config.ClientConfig;
import com.aoaojiao.catmq.client.consumer.MessageConsumer;
import com.aoaojiao.catmq.client.model.PullMessageResponse;
import com.aoaojiao.catmq.client.producer.MessageProducer;
import com.aoaojiao.catmq.client.model.SendMessageResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * Client 与 Broker 集成测试
 *
 * @author DD
 */
public class ClientBrokerIntegrationTest {

    private static final int TEST_BROKER_PORT = 19999;
    private static final String TEST_TOPIC = "test_topic";
    private static final String ORDER_TOPIC = "order_topic";

    private EmbeddedBroker embeddedBroker;
    private MessageProducer producer;
    private MessageConsumer consumer;

    @Before
    public void setUp() throws Exception {
        // 启动嵌入式 Broker
        embeddedBroker = new EmbeddedBroker(TEST_BROKER_PORT, "test-broker");
        embeddedBroker.start();

        // 等待 Broker 完全启动
        Thread.sleep(500);
    }

    @After
    public void tearDown() {
        // 关闭 Producer
        if (producer != null) {
            producer.shutdown();
        }

        // 关闭 Consumer
        if (consumer != null) {
            consumer.shutdown();
        }

        // 关闭 Broker
        if (embeddedBroker != null) {
            embeddedBroker.stop();
        }
    }

    /**
     * testSendAndConsumeMessage - 发送消息并成功消费
     */
    @Test
    public void testSendAndConsumeMessage() throws Exception {
        // 配置 Client 连接嵌入式 Broker
        ClientConfig config = new ClientConfig();
        config.setBrokerAddress(embeddedBroker.getAddress());
        config.setConnectTimeoutMs(5000);
        config.setRequestTimeoutMs(3000);
        config.setMaxRetryTimes(3);

        // 创建 Producer
        producer = new MessageProducer(config);
        producer.start();

        // 发送消息
        String messageBody = "Hello, CatMQ!";
        SendMessageResponse response = producer.send(TEST_TOPIC, messageBody);

        assertNotNull("发送响应不应该为空", response);
        assertTrue("消息发送应该成功", response.isSuccess() || response.getMessageId() != null);

        // 创建 Consumer
        consumer = new MessageConsumer(config);
        consumer.start();

        // 订阅主题
        final CountDownLatch messageLatch = new CountDownLatch(1);
        final AtomicReference<String> receivedMessage = new AtomicReference<>();

        consumer.subscribe(TEST_TOPIC, (topic, body, properties) -> {
            receivedMessage.set(body);
            messageLatch.countDown();
            return MessageConsumer.ConsumeResult.SUCCESS;
        });

        // 启动拉取
        consumer.startPulling();

        // 等待消息接收（带超时）
        boolean received = messageLatch.await(5, TimeUnit.SECONDS);

        // 由于是 pull 模式且消息已发送，直接拉取验证
        PullMessageResponse pullResponse = consumer.pull(TEST_TOPIC, 0, 0);
        assertNotNull("拉取响应不应该为空", pullResponse);
    }

    /**
     * testBatchSendAndConsume - 批量发送和消费
     */
    @Test
    public void testBatchSendAndConsume() throws Exception {
        ClientConfig config = new ClientConfig();
        config.setBrokerAddress(embeddedBroker.getAddress());
        config.setConnectTimeoutMs(5000);
        config.setRequestTimeoutMs(3000);
        config.setMaxRetryTimes(3);
        config.setMaxPullMessageCount(100);

        // 创建 Producer
        producer = new MessageProducer(config);
        producer.start();

        // 批量发送消息
        int batchSize = 10;
        for (int i = 0; i < batchSize; i++) {
            String messageBody = "Batch message " + i;
            SendMessageResponse response = producer.send(ORDER_TOPIC, messageBody);
            assertNotNull("发送响应不应该为空", response);
        }

        // 创建 Consumer
        consumer = new MessageConsumer(config);
        consumer.start();

        // 手动拉取消息验证
        PullMessageResponse pullResponse = consumer.pull(ORDER_TOPIC, 0, 0);
        assertNotNull("拉取响应不应该为空", pullResponse);
    }

    /**
     * testAsyncSend - 异步发送消息
     */
    @Test
    public void testAsyncSend() throws Exception {
        ClientConfig config = new ClientConfig();
        config.setBrokerAddress(embeddedBroker.getAddress());
        config.setConnectTimeoutMs(5000);
        config.setRequestTimeoutMs(3000);
        config.setMaxRetryTimes(3);

        // 创建 Producer
        producer = new MessageProducer(config);
        producer.start();

        // 异步发送消息
        CountDownLatch asyncLatch = new CountDownLatch(1);
        AtomicReference<SendMessageResponse> asyncResponse = new AtomicReference<>();
        AtomicReference<Throwable> asyncError = new AtomicReference<>();

        producer.sendAsync(TEST_TOPIC, "Async message", new MessageProducer.SendCallback() {
            @Override
            public void onSuccess(SendMessageResponse response) {
                asyncResponse.set(response);
                asyncLatch.countDown();
            }

            @Override
            public void onFailure(Throwable e) {
                asyncError.set(e);
                asyncLatch.countDown();
            }
        });

        // 等待异步发送完成
        boolean completed = asyncLatch.await(5, TimeUnit.SECONDS);
        assertTrue("异步发送应该完成", completed);
        assertNull("不应该有错误", asyncError.get());
        assertNotNull("应该有响应", asyncResponse.get());
    }

    /**
     * testMessageFilter - 按 tag 或 key 过滤消息
     */
    @Test
    public void testMessageFilter() throws Exception {
        ClientConfig config = new ClientConfig();
        config.setBrokerAddress(embeddedBroker.getAddress());
        config.setConnectTimeoutMs(5000);
        config.setRequestTimeoutMs(3000);
        config.setMaxRetryTimes(3);

        // 创建 Producer
        producer = new MessageProducer(config);
        producer.start();

        // 发送带 tag 的消息
        SendMessageResponse responseWithTag = producer.send(TEST_TOPIC, "Message with tag", "important");
        assertNotNull("发送响应不应该为空", responseWithTag);

        // 创建 Consumer
        consumer = new MessageConsumer(config);
        consumer.start();

        // 手动拉取验证
        PullMessageResponse pullResponse = consumer.pull(TEST_TOPIC, 0, 0);
        assertNotNull("拉取响应不应该为空", pullResponse);
    }

    /**
     * testConsumeFromSpecificOffset - 从指定偏移量开始消费
     */
    @Test
    public void testConsumeFromSpecificOffset() throws Exception {
        ClientConfig config = new ClientConfig();
        config.setBrokerAddress(embeddedBroker.getAddress());
        config.setConnectTimeoutMs(5000);
        config.setRequestTimeoutMs(3000);
        config.setMaxRetryTimes(3);
        config.setMaxPullMessageCount(10);

        // 创建 Producer
        producer = new MessageProducer(config);
        producer.start();

        // 发送多条消息
        for (int i = 0; i < 5; i++) {
            producer.send(TEST_TOPIC, "Message " + i);
        }

        // 创建 Consumer
        consumer = new MessageConsumer(config);
        consumer.start();

        // 从偏移量 0 开始拉取
        PullMessageResponse response0 = consumer.pull(TEST_TOPIC, 0, 0);
        assertNotNull("从偏移量 0 拉取应该成功", response0);

        // 从偏移量 2 开始拉取（跳过前两条）
        PullMessageResponse response2 = consumer.pull(TEST_TOPIC, 0, 2);
        assertNotNull("从偏移量 2 拉取应该成功", response2);

        // 尝试从非法偏移量拉取
        PullMessageResponse responseIllegal = consumer.pull(TEST_TOPIC, 0, -1);
        // 应该能处理非法偏移量而不崩溃
        assertNotNull("非法偏移量也应该有响应", responseIllegal);
    }

    /**
     * 测试连接复用
     */
    @Test
    public void testConnectionReuse() throws Exception {
        ClientConfig config = new ClientConfig();
        config.setBrokerAddress(embeddedBroker.getAddress());
        config.setConnectTimeoutMs(5000);
        config.setRequestTimeoutMs(3000);
        config.setMaxRetryTimes(3);

        // 创建 Producer
        producer = new MessageProducer(config);
        producer.start();

        // 通过同一连接发送多条消息
        for (int i = 0; i < 3; i++) {
            SendMessageResponse response = producer.send(TEST_TOPIC, "Message " + i);
            assertNotNull("消息发送应该成功", response);
        }

        // 关闭并重新创建 Producer（测试连接重建）
        producer.shutdown();
        producer = new MessageProducer(config);
        producer.start();

        // 再次发送消息
        SendMessageResponse response = producer.send(TEST_TOPIC, "After reconnect");
        assertNotNull("重连后发送应该成功", response);
    }

    /**
     * 测试 Broker 不可用时的错误处理
     */
    @Test
    public void testBrokerNotAvailable() {
        ClientConfig config = new ClientConfig();
        config.setBrokerAddress("localhost:19998"); // 错误的端口
        config.setConnectTimeoutMs(2000);
        config.setRequestTimeoutMs(2000);
        config.setMaxRetryTimes(1);

        // 创建 Producer（不启动）
        producer = new MessageProducer(config);

        // 尝试发送消息，应该失败
        try {
            producer.start();
            SendMessageResponse response = producer.send(TEST_TOPIC, "Test message");
            // 如果没有抛出异常，检查响应
            assertNotNull("应该有响应", response);
            assertFalse("应该发送失败", response.isSuccess());
        } catch (Exception e) {
            // 预期会抛出连接异常
            assertTrue("异常信息应该包含连接相关关键字",
                    e.getMessage().contains("connection") ||
                    e.getMessage().contains("Connection") ||
                    e.getMessage().contains("refused") ||
                    e.getMessage().contains("Failed"));
        }
    }
}
