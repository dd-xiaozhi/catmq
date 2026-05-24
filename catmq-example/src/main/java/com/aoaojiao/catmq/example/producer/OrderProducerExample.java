package com.aoaojiao.catmq.example.producer;

import com.aoaojiao.catmq.client.config.ClientConfig;
import com.aoaojiao.catmq.client.model.SendMessageResponse;
import com.aoaojiao.catmq.client.producer.MessageProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 消息生产者示例
 * 演示如何发送订单消息到消息队列
 *
 * @author DD
 */
public class OrderProducerExample {

    private static final Logger log = LoggerFactory.getLogger(OrderProducerExample.class);

    private static final String TOPIC = "order_topic";
    private static final String BROKER_ADDRESS = "localhost:9091";

    public static void main(String[] args) {
        log.info("========== CatMQ 生产者示例 ==========");

        // 1. 创建生产者配置
        ClientConfig config = ClientConfig.defaultConfig();
        config.setBrokerAddress(BROKER_ADDRESS);
        config.setConnectTimeoutMs(5000);
        config.setRequestTimeoutMs(3000);

        // 2. 创建生产者实例
        MessageProducer producer = new MessageProducer(config);

        try {
            // 3. 启动生产者
            producer.start();
            log.info("生产者启动成功");

            // 4. 发送消息
            for (int i = 1; i <= 10; i++) {
                sendOrderMessage(producer, i);
                Thread.sleep(500);
            }

            log.info("所有消息发送完成");

        } catch (InterruptedException e) {
            log.error("生产者运行异常", e);
            Thread.currentThread().interrupt();
        } finally {
            // 5. 关闭生产者
            producer.shutdown();
            log.info("生产者已关闭");
        }
    }

    /**
     * 发送订单消息
     */
    private static void sendOrderMessage(MessageProducer producer, int orderId) {
        // 构建订单消息 JSON
        String orderMessage = buildOrderMessage(orderId);

        // 发送同步消息
        SendMessageResponse response = producer.send(TOPIC, orderMessage);

        if (response.isSuccess()) {
            log.info("订单消息发送成功: orderId={}, messageId={}, offset={}",
                    orderId, response.getMessageId(), response.getPhysicalOffset());
        } else {
            log.error("订单消息发送失败: orderId={}, error={}",
                    orderId, response.getErrorMessage());
        }
    }

    /**
     * 构建订单消息 JSON
     */
    private static String buildOrderMessage(int orderId) {
        return String.format(
                "{\"orderId\":%d,\"orderNo\":\"%s\",\"amount\":%.2f,\"status\":\"PAID\",\"items\":[%s]}",
                orderId,
                "ORD" + System.currentTimeMillis() + orderId,
                99.99 + orderId,
                "{\"productId\":1001,\"name\":\"商品A\",\"quantity\":1},{\"productId\":1002,\"name\":\"商品B\",\"quantity\":2}"
        );
    }
}
