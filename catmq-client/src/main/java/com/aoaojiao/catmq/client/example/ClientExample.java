package com.aoaojiao.catmq.client.example;

import com.aoaojiao.catmq.client.config.ClientConfig;
import com.aoaojiao.catmq.client.consumer.MessageConsumer;
import com.aoaojiao.catmq.client.consumer.MessageConsumer.ConsumeResult;
import com.aoaojiao.catmq.client.consumer.MessageConsumer.MessageListener;
import com.aoaojiao.catmq.client.model.PullMessage;
import com.aoaojiao.catmq.client.model.PullMessageResponse;
import com.aoaojiao.catmq.client.producer.MessageProducer;
import com.aoaojiao.catmq.client.producer.MessageProducer.SendCallback;
import com.aoaojiao.catmq.client.model.SendMessageResponse;

/**
 * 客户端使用示例
 *
 * @author DD
 */
public class ClientExample {

    private static final String TOPIC = "order_pay_topic";

    /**
     * 生产者示例
     */
    public static void producerExample() {
        System.out.println("========== 生产者示例 ==========");

        // 创建生产者
        MessageProducer producer = new MessageProducer();

        try {
            // 启动生产者
            producer.start();

            // 同步发送消息
            SendMessageResponse response = producer.send(TOPIC, "Hello, CatMQ!");
            if (response.isSuccess()) {
                System.out.println("消息发送成功: messageId=" + response.getMessageId());
            } else {
                System.out.println("消息发送失败: " + response.getErrorMessage());
            }

            // 发送字节数组
            byte[] body = "Binary message".getBytes();
            SendMessageResponse response2 = producer.send(TOPIC, body, "TAG_A");
            System.out.println("二进制消息发送: " + (response2.isSuccess() ? "成功" : "失败"));

            // 异步发送消息
            producer.sendAsync(TOPIC, "Async message", new SendCallback() {
                @Override
                public void onSuccess(SendMessageResponse response) {
                    System.out.println("异步发送成功: messageId=" + response.getMessageId());
                }

                @Override
                public void onFailure(Throwable e) {
                    System.out.println("异步发送失败: " + e.getMessage());
                }
            });

        } finally {
            producer.shutdown();
        }
    }

    /**
     * 生产者示例（使用 Builder）
     */
    public static void producerWithBuilderExample() {
        System.out.println("========== 生产者 Builder 示例 ==========");

        // 使用 Builder 创建生产者
        ClientConfig config = new ClientConfig();
        config.setBrokerAddress("localhost:8080");
        config.setConnectTimeoutMs(5000);
        config.setRequestTimeoutMs(3000);
        config.setMaxRetryTimes(3);

        MessageProducer producer = new MessageProducer(config);

        try {
            producer.start();

            // 发送消息
            SendMessageResponse response = producer.send(TOPIC, "Builder message");
            System.out.println("消息发送: " + (response.isSuccess() ? "成功" : "失败"));

        } finally {
            producer.shutdown();
        }
    }

    /**
     * 消费者示例
     */
    public static void consumerExample() {
        System.out.println("========== 消费者示例 ==========");

        // 创建消费者
        MessageConsumer consumer = new MessageConsumer();

        try {
            // 启动消费者
            consumer.start();

            // 订阅主题
            consumer.subscribe(TOPIC, new MessageListener() {
                @Override
                public ConsumeResult consume(String topic, String body, String properties) {
                    System.out.println("收到消息: topic=" + topic + ", body=" + body);
                    // 处理业务逻辑
                    // ...

                    // 返回消费结果
                    return ConsumeResult.SUCCESS;
                }
            });

            // 开始拉取消息
            consumer.startPulling();

            // 保持主线程运行
            Thread.sleep(60000);

        } catch (Exception e) {
            System.err.println("消费者异常: " + e.getMessage());
        } finally {
            consumer.shutdown();
        }
    }

    /**
     * 手动拉取消息示例
     */
    public static void manualPullExample() {
        System.out.println("========== 手动拉取消息示例 ==========");

        MessageConsumer consumer = new MessageConsumer();

        try {
            consumer.start();

            // 手动拉取消息
            long offset = 0;
            while (true) {
                PullMessageResponse response = consumer.pull(TOPIC, 0, offset);

                if (response.isSuccess() && response.getMessages() != null) {
                    for (PullMessage message : response.getMessages()) {
                        System.out.println("消息: " + new String(message.getBody()));

                        // 处理完成后，更新 offset（自动在 consumer.pull 中完成）
                        offset = response.getNextBeginOffset();
                    }
                }

                // 没有新消息时，休眠一段时间
                if (!response.isSuccess()) {
                    Thread.sleep(1000);
                }
            }

        } catch (Exception e) {
            System.err.println("拉取异常: " + e.getMessage());
        } finally {
            consumer.shutdown();
        }
    }

    /**
     * 带配置的消费者示例
     */
    public static void configuredConsumerExample() {
        System.out.println("========== 带配置的消费者示例 ==========");

        ClientConfig config = new ClientConfig();
        config.setBrokerAddress("localhost:8080");
        config.setPullIntervalMs(500);
        config.setMaxPullMessageCount(20);

        MessageConsumer consumer = new MessageConsumer(config);

        try {
            consumer.start();

            consumer.subscribe(TOPIC, (topic, body, properties) -> {
                System.out.println("Lambda 消费消息: " + body);
                return ConsumeResult.SUCCESS;
            });

            consumer.startPulling();

            Thread.sleep(60000);

        } catch (Exception e) {
            System.err.println("消费者异常: " + e.getMessage());
        } finally {
            consumer.shutdown();
        }
    }

    /**
     * 主函数
     */
    public static void main(String[] args) {
        // 根据需求选择示例
        // 1. 生产者示例
        // producerExample();

        // 2. 带 Builder 的生产者示例
        // producerWithBuilderExample();

        // 3. 消费者示例（自动拉取）
        // consumerExample();

        // 4. 手动拉取消息示例
        // manualPullExample();

        // 5. 带配置的消费者示例
        // configuredConsumerExample();
    }
}