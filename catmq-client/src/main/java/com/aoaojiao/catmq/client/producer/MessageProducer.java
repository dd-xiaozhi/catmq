package com.aoaojiao.catmq.client.producer;

import com.aoaojiao.catmq.client.model.SendMessageResponse;
import com.aoaojiao.catmq.client.model.ClientRequest;
import com.aoaojiao.catmq.client.model.ClientResponse;
import com.aoaojiao.catmq.client.model.SendMessageRequest;
import com.aoaojiao.catmq.client.netty.ConnectionManager;
import com.aoaojiao.catmq.client.config.ClientConfig;
import com.alibaba.fastjson2.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 消息生产者
 * 用于发送消息到 Broker
 *
 * @author DD
 */
public class MessageProducer {

    private static final Logger log = LoggerFactory.getLogger(MessageProducer.class);

    private final ClientConfig config;
    private final ConnectionManager connectionManager;
    private final AtomicLong requestIdGenerator = new AtomicLong(0);

    /**
     * 生产者状态
     */
    private volatile boolean started = false;

    public MessageProducer() {
        this(ClientConfig.defaultConfig());
    }

    public MessageProducer(ClientConfig config) {
        this.config = config;
        this.connectionManager = new ConnectionManager(config);
    }

    /**
     * 启动生产者
     */
    public void start() {
        if (started) {
            log.warn("Producer already started");
            return;
        }

        try {
            // 初始化连接
            connectionManager.getConnection();
            started = true;
            log.info("MessageProducer started successfully");
        } catch (Exception e) {
            log.error("Failed to start producer", e);
            throw new RuntimeException("Failed to start producer", e);
        }
    }

    /**
     * 关闭生产者
     */
    public void shutdown() {
        if (!started) {
            return;
        }

        log.info("Shutting down MessageProducer...");
        connectionManager.shutdownAll();
        started = false;
        log.info("MessageProducer shutdown completed");
    }

    /**
     * 同步发送消息（字符串）
     *
     * @param topic  主题
     * @param body   消息内容
     * @return 发送结果
     */
    public SendMessageResponse send(String topic, String body) {
        return send(topic, body.getBytes(StandardCharsets.UTF_8), null);
    }

    /**
     * 同步发送消息（字节数组）
     *
     * @param topic  主题
     * @param body   消息体
     * @return 发送结果
     */
    public SendMessageResponse send(String topic, byte[] body) {
        return send(topic, body, null);
    }

    /**
     * 同步发送消息（带标签）
     *
     * @param topic  主题
     * @param body   消息体
     * @param tags   标签（可选）
     * @return 发送结果
     */
    public SendMessageResponse send(String topic, byte[] body, String tags) {
        checkStarted();

        // 构建发送请求
        SendMessageRequest request = SendMessageRequest.builder()
                .topic(topic)
                .body(body)
                .tags(tags)
                .build();

        // 序列化请求
        byte[] payload = JSON.toJSONBytes(request);

        // 构建 ClientRequest
        long requestId = generateRequestId();
        ClientRequest clientRequest = ClientRequest.builder()
                .requestId(requestId)
                .requestType(ClientRequest.SEND_MESSAGE)
                .payload(payload)
                .build();

        try {
            // 发送请求
            ClientResponse response = connectionManager.getConnection().sendRequestWithRetry(clientRequest);

            if (response.isSuccess()) {
                return JSON.parseObject(response.getPayload(), SendMessageResponse.class);
            } else {
                return SendMessageResponse.builder()
                        .success(false)
                        .errorMessage(ClientResponse.getStatusName(response.getStatusCode()))
                        .build();
            }
        } catch (Exception e) {
            log.error("Send message failed: topic={}", topic, e);
            return SendMessageResponse.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    /**
     * 异步发送消息（字符串）
     *
     * @param topic  主题
     * @param body   消息内容
     * @param callback 回调
     */
    public void sendAsync(String topic, String body, SendCallback callback) {
        sendAsync(topic, body.getBytes(StandardCharsets.UTF_8), null, callback);
    }

    /**
     * 异步发送消息（带标签）
     *
     * @param topic   主题
     * @param body    消息体
     * @param tags    标签（可选）
     * @param callback 回调
     */
    public void sendAsync(String topic, byte[] body, String tags, SendCallback callback) {
        checkStarted();

        // 构建发送请求
        SendMessageRequest request = SendMessageRequest.builder()
                .topic(topic)
                .body(body)
                .tags(tags)
                .build();

        // 序列化请求
        byte[] payload = JSON.toJSONBytes(request);

        // 构建 ClientRequest
        final long requestId = generateRequestId();
        ClientRequest clientRequest = ClientRequest.builder()
                .requestId(requestId)
                .requestType(ClientRequest.SEND_MESSAGE)
                .payload(payload)
                .build();

        // 异步发送
        CompletableFuture.runAsync(() -> {
            try {
                ClientResponse response = connectionManager.getConnection().sendRequestWithRetry(clientRequest);

                if (response.isSuccess()) {
                    SendMessageResponse sendResponse = JSON.parseObject(response.getPayload(), SendMessageResponse.class);
                    callback.onSuccess(sendResponse);
                } else {
                    callback.onFailure(new RuntimeException(ClientResponse.getStatusName(response.getStatusCode())));
                }
            } catch (Exception e) {
                log.error("Async send message failed: topic={}", topic, e);
                callback.onFailure(e);
            }
        });
    }

    /**
     * 生成请求 ID
     */
    private long generateRequestId() {
        return requestIdGenerator.incrementAndGet();
    }

    /**
     * 检查生产者是否已启动
     */
    private void checkStarted() {
        if (!started) {
            throw new IllegalStateException("Producer not started");
        }
    }

    /**
     * 发送回调接口
     */
    public interface SendCallback {
        /**
         * 发送成功
         */
        void onSuccess(SendMessageResponse response);

        /**
         * 发送失败
         */
        void onFailure(Throwable e);
    }

    /**
     * 构建者
     */
    public static class Builder {
        private ClientConfig config = ClientConfig.defaultConfig();

        public Builder setBrokerAddress(String address) {
            config.setBrokerAddress(address);
            return this;
        }

        public Builder setConnectTimeoutMs(int timeout) {
            config.setConnectTimeoutMs(timeout);
            return this;
        }

        public Builder setRequestTimeoutMs(int timeout) {
            config.setRequestTimeoutMs(timeout);
            return this;
        }

        public Builder setMaxRetryTimes(int times) {
            config.setMaxRetryTimes(times);
            return this;
        }

        public MessageProducer build() {
            return new MessageProducer(config);
        }
    }
}