package com.aoaojiao.catmq.client.consumer;

import com.aoaojiao.catmq.client.model.*;
import com.aoaojiao.catmq.client.netty.ConnectionManager;
import com.aoaojiao.catmq.client.config.ClientConfig;
import com.alibaba.fastjson2.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 消息消费者
 * 用于从 Broker 拉取并消费消息
 *
 * @author DD
 */
public class MessageConsumer {

    private static final Logger log = LoggerFactory.getLogger(MessageConsumer.class);

    private final ClientConfig config;
    private final ConnectionManager connectionManager;
    private final AtomicLong requestIdGenerator = new AtomicLong(0);

    /**
     * 订阅信息
     * Key: topic
     * Value: Subscription
     */
    private final Map<String, Subscription> subscriptions = new ConcurrentHashMap<>();

    /**
     * 消费进度管理
     * Key: topic#queueId
     * Value: offset
     */
    private final Map<String, AtomicLong> offsetCache = new ConcurrentHashMap<>();

    /**
     * 消费者状态
     */
    private volatile boolean started = false;

    /**
     * 拉取线程
     */
    private ScheduledExecutorService pullExecutor;

    /**
     * 消费者组
     */
    private String consumerGroup;

    public MessageConsumer() {
        this(ClientConfig.defaultConfig());
    }

    public MessageConsumer(ClientConfig config) {
        this.config = config;
        this.connectionManager = new ConnectionManager(config);
    }

    /**
     * 启动消费者
     */
    public void start() {
        if (started) {
            log.warn("Consumer already started");
            return;
        }

        try {
            // 初始化连接
            connectionManager.getConnection();
            started = true;
            log.info("MessageConsumer started successfully");
        } catch (Exception e) {
            log.error("Failed to start consumer", e);
            throw new RuntimeException("Failed to start consumer", e);
        }
    }

    /**
     * 关闭消费者
     */
    public void shutdown() {
        if (!started) {
            return;
        }

        log.info("Shutting down MessageConsumer...");

        // 停止拉取线程
        if (pullExecutor != null) {
            pullExecutor.shutdown();
            try {
                if (!pullExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    pullExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                pullExecutor.shutdownNow();
            }
        }

        connectionManager.shutdownAll();
        started = false;
        log.info("MessageConsumer shutdown completed");
    }

    /**
     * 订阅主题
     *
     * @param topic    主题
     * @param listener 消息监听器
     */
    public void subscribe(String topic, MessageListener listener) {
        subscribe(topic, 0, listener);
    }

    /**
     * 订阅主题（指定队列）
     *
     * @param topic    主题
     * @param queueId  队列 ID
     * @param listener 消息监听器
     */
    public void subscribe(String topic, int queueId, MessageListener listener) {
        checkStarted();

        Subscription subscription = new Subscription();
        subscription.setTopic(topic);
        subscription.setQueueId(queueId);
        subscription.setListener(listener);

        subscriptions.put(topic, subscription);
        log.info("Subscribed topic: {}, queueId: {}", topic, queueId);
    }

    /**
     * 开始拉取消息
     */
    public void startPulling() {
        checkStarted();

        if (pullExecutor == null) {
            pullExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "catmq-consumer-pull-thread");
                t.setDaemon(true);
                return t;
            });
        }

        // 定时拉取消息
        pullExecutor.scheduleAtFixedRate(() -> {
            try {
                for (Subscription subscription : subscriptions.values()) {
                    pullMessage(subscription);
                }
            } catch (Exception e) {
                log.error("Pull message error", e);
            }
        }, 0, config.getPullIntervalMs(), TimeUnit.MILLISECONDS);

        log.info("Started pulling messages");
    }

    /**
     * 拉取消息
     */
    private void pullMessage(Subscription subscription) {
        String topic = subscription.getTopic();
        int queueId = subscription.getQueueId();

        // 获取当前偏移量
        long offset = getOffset(topic, queueId);

        // 构建拉取请求
        PullMessageRequest request = PullMessageRequest.builder()
                .topic(topic)
                .queueId(queueId)
                .offset(offset)
                .maxMsgCount(config.getMaxPullMessageCount())
                .maxMsgSize(1024 * 1024)  // 1MB
                .build();

        // 序列化请求
        byte[] payload = JSON.toJSONBytes(request);

        // 构建 ClientRequest
        long requestId = generateRequestId();
        ClientRequest clientRequest = ClientRequest.builder()
                .requestId(requestId)
                .requestType(ClientRequest.PULL_MESSAGE)
                .payload(payload)
                .build();

        try {
            // 发送请求
            ClientResponse response = connectionManager.getConnection().sendRequestWithRetry(clientRequest);

            if (response.isSuccess()) {
                PullMessageResponse pullResponse = JSON.parseObject(response.getPayload(), PullMessageResponse.class);
                processPullResponse(pullResponse, subscription);
            } else {
                log.warn("Pull message failed: topic={}, status={}", topic, response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Pull message error: topic={}", topic, e);
        }
    }

    /**
     * 处理拉取响应
     */
    private void processPullResponse(PullMessageResponse pullResponse, Subscription subscription) {
        if (pullResponse.getMessages() == null || pullResponse.getMessages().isEmpty()) {
            // 没有新消息
            return;
        }

        List<PullMessage> messages = pullResponse.getMessages();

        for (PullMessage message : messages) {
            try {
                // 获取消息内容
                String body = new String(message.getBody(), StandardCharsets.UTF_8);

                // 调用监听器处理消息
                ConsumeResult result = subscription.getListener().consume(message.getTopic(), body, message.getProperties());

                if (result == ConsumeResult.SUCCESS) {
                    // 更新偏移量
                    updateOffset(message.getTopic(), message.getQueueId(), message.getPhysicalOffset() + 1);
                }
            } catch (Exception e) {
                log.error("Consume message error: topic={}, offset={}",
                        message.getTopic(), message.getPhysicalOffset(), e);
            }
        }

        log.debug("Consumed {} messages from topic: {}", messages.size(), subscription.getTopic());
    }

    /**
     * 手动拉取消息（不自动提交偏移）
     *
     * @param topic   主题
     * @param queueId 队列 ID
     * @param offset  起始偏移
     * @return 拉取结果
     */
    public PullMessageResponse pull(String topic, int queueId, long offset) {
        checkStarted();

        // 构建拉取请求
        PullMessageRequest request = PullMessageRequest.builder()
                .topic(topic)
                .queueId(queueId)
                .offset(offset)
                .maxMsgCount(config.getMaxPullMessageCount())
                .maxMsgSize(1024 * 1024)
                .build();

        // 序列化请求
        byte[] payload = JSON.toJSONBytes(request);

        // 构建 ClientRequest
        long requestId = generateRequestId();
        ClientRequest clientRequest = ClientRequest.builder()
                .requestId(requestId)
                .requestType(ClientRequest.PULL_MESSAGE)
                .payload(payload)
                .build();

        try {
            ClientResponse response = connectionManager.getConnection().sendRequestWithRetry(clientRequest);

            if (response.isSuccess()) {
                return JSON.parseObject(response.getPayload(), PullMessageResponse.class);
            } else {
                return PullMessageResponse.builder()
                        .status(PullMessageResponse.PullStatus.OFFSET_ILLEGAL)
                        .messages(Collections.emptyList())
                        .build();
            }
        } catch (Exception e) {
            log.error("Pull message failed: topic={}", topic, e);
            return PullMessageResponse.builder()
                    .status(PullMessageResponse.PullStatus.OFFSET_ILLEGAL)
                    .messages(Collections.emptyList())
                    .build();
        }
    }

    /**
     * 获取当前偏移量
     */
    private long getOffset(String topic, int queueId) {
        String key = buildOffsetKey(topic, queueId);
        AtomicLong offset = offsetCache.get(key);
        return offset != null ? offset.get() : 0;
    }

    /**
     * 更新偏移量
     */
    private void updateOffset(String topic, int queueId, long offset) {
        String key = buildOffsetKey(topic, queueId);
        offsetCache.computeIfAbsent(key, k -> new AtomicLong()).set(offset);

        // 提交到 Broker
        commitOffset(topic, queueId, offset);
    }

    /**
     * 提交偏移量到 Broker
     */
    private void commitOffset(String topic, int queueId, long offset) {
        Map<String, Object> request = new HashMap<>();
        request.put("topic", topic);
        request.put("queueId", queueId);
        request.put("offset", offset);

        byte[] payload = JSON.toJSONBytes(request);

        long requestId = generateRequestId();
        ClientRequest clientRequest = ClientRequest.builder()
                .requestId(requestId)
                .requestType(ClientRequest.COMMIT_OFFSET)
                .payload(payload)
                .build();

        try {
            connectionManager.getConnection().sendRequest(clientRequest, config.getRequestTimeoutMs());
        } catch (Exception e) {
            log.error("Commit offset failed: topic={}, queueId={}, offset={}", topic, queueId, offset, e);
        }
    }

    /**
     * 构建偏移量缓存 Key
     */
    private String buildOffsetKey(String topic, int queueId) {
        return topic + "#" + queueId;
    }

    /**
     * 生成请求 ID
     */
    private long generateRequestId() {
        return requestIdGenerator.incrementAndGet();
    }

    /**
     * 检查消费者是否已启动
     */
    private void checkStarted() {
        if (!started) {
            throw new IllegalStateException("Consumer not started");
        }
    }

    // ==================== 内部类 ====================

    /**
     * 订阅信息
     */
    private static class Subscription {
        private String topic;
        private int queueId;
        private MessageListener listener;

        public String getTopic() {
            return topic;
        }

        public void setTopic(String topic) {
            this.topic = topic;
        }

        public int getQueueId() {
            return queueId;
        }

        public void setQueueId(int queueId) {
            this.queueId = queueId;
        }

        public MessageListener getListener() {
            return listener;
        }

        public void setListener(MessageListener listener) {
            this.listener = listener;
        }
    }

    /**
     * 消费结果
     */
    public enum ConsumeResult {
        /**
         * 消费成功
         */
        SUCCESS,
        /**
         * 消费失败，稍后重试
         */
        RETRY_LATER,
        /**
         * 跳过消息
         */
        SKIP
    }

    /**
     * 消息监听器接口
     */
    public interface MessageListener {
        /**
         * 消费消息
         *
         * @param topic     主题
         * @param body      消息体
         * @param properties 消息属性
         * @return 消费结果
         */
        ConsumeResult consume(String topic, String body, String properties);
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

        public Builder setPullIntervalMs(long interval) {
            config.setPullIntervalMs(interval);
            return this;
        }

        public Builder setMaxPullMessageCount(int count) {
            config.setMaxPullMessageCount(count);
            return this;
        }

        public Builder setConsumerGroup(String group) {
            return this;
        }

        public MessageConsumer build() {
            return new MessageConsumer(config);
        }
    }
}