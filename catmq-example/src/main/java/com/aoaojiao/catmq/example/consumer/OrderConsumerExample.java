package com.aoaojiao.catmq.example.consumer;

import com.aoaojiao.catmq.client.config.ClientConfig;
import com.aoaojiao.catmq.client.consumer.MessageConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 消息消费者示例
 * 演示如何订阅和消费订单消息
 *
 * @author DD
 */
public class OrderConsumerExample {

    private static final Logger log = LoggerFactory.getLogger(OrderConsumerExample.class);

    private static final String TOPIC = "order_topic";
    private static final String BROKER_ADDRESS = "localhost:9091";

    private static int consumedCount = 0;

    public static void main(String[] args) {
        log.info("========== CatMQ 消费者示例 ==========");

        // 1. 创建消费者配置
        ClientConfig config = ClientConfig.defaultConfig();
        config.setBrokerAddress(BROKER_ADDRESS);
        config.setConnectTimeoutMs(5000);
        config.setPullIntervalMs(1000);

        // 2. 创建消费者实例
        MessageConsumer consumer = new MessageConsumer(config);

        try {
            // 3. 启动消费者
            consumer.start();
            log.info("消费者启动成功");

            // 4. 订阅主题
            consumer.subscribe(TOPIC, (topic, body, properties) -> {
                return handleOrderMessage(topic, body, properties);
            });

            // 5. 开始拉取消息
            consumer.startPulling();
            log.info("开始拉取消息...");

            // 保持运行
            Thread.sleep(30000);
            log.info("演示结束，共消费 {} 条消息", consumedCount);

        } catch (InterruptedException e) {
            log.error("消费者运行异常", e);
            Thread.currentThread().interrupt();
        } finally {
            // 6. 关闭消费者
            consumer.shutdown();
            log.info("消费者已关闭");
        }
    }

    /**
     * 处理订单消息
     */
    private static MessageConsumer.ConsumeResult handleOrderMessage(String topic, String body, String properties) {
        consumedCount++;
        log.info("收到订单消息 #{}: topic={}, body={}", consumedCount, topic, body);

        try {
            // 解析消息
            // 这里可以添加业务逻辑，如：
            // 1. 更新订单状态
            // 2. 发送通知
            // 3. 触发后续流程

            log.info("订单消息处理成功 #{}", consumedCount);
            return MessageConsumer.ConsumeResult.SUCCESS;

        } catch (RuntimeException e) {
            log.error("订单消息处理失败", e);
            // 处理失败，可以返回 RETRY_LATER 或 SKIP
            return MessageConsumer.ConsumeResult.RETRY_LATER;
        }
    }
}
