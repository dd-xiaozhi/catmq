package com.aoaojiao.catmq.store.delay.service;

import com.aoaojiao.catmq.store.delay.model.DelayMessage;
import com.aoaojiao.catmq.store.delay.model.RetryMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 重试消息服务
 * 负责管理消费失败消息的重试逻辑
 * 支持指数退避策略和最大重试次数限制
 *
 * @author DD
 */
public class RetryMessageService {

    private static final Logger log = LoggerFactory.getLogger(RetryMessageService.class);

    /**
     * 服务运行状态
     */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 重试消息索引（messageId -> RetryMessage）
     * 按消费者组分组
     */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, RetryMessage>> retryMessageIndex =
            new ConcurrentHashMap<>();

    /**
     * 待重试消息优先级队列（按下次重试时间排序）
     * Key: topic#queueId#consumerGroup
     */
    private final ConcurrentHashMap<String, PriorityQueue<RetryMessage>> retryQueueMap =
            new ConcurrentHashMap<>();

    /**
     * 调度线程池
     */
    private ScheduledExecutorService scheduler;

    /**
     * 重试目录
     */
    private String retryDir;

    /**
     * 基础延迟时间（毫秒）- 默认 1 秒
     */
    private long baseDelayMs = 1000;

    /**
     * 最大延迟时间（毫秒）- 默认 60 秒
     */
    private long maxDelayMs = 60000;

    /**
     * 扫描间隔（毫秒）
     */
    private long scanIntervalMs = 1000;

    /**
     * 默认最大重试次数
     */
    private int defaultMaxRetryCount = 16;

    /**
     * 死信队列服务（用于超过最大重试次数时转入死信）
     */
    private DeadLetterQueueService dlqService;

    /**
     * 延迟消息服务（用于提交延迟重试消息）
     */
    private DelayMessageService delayMessageService;

    /**
     * 初始化重试服务
     */
    public void init(String retryDir) {
        this.retryDir = retryDir + File.separator + "retry";
        File dir = new File(this.retryDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // 初始化调度线程
        this.scheduler = Executors.newScheduledThreadPool(1,
                r -> new Thread(r, "RetryMessageService-Scheduler"));

        // 加载已存在的重试消息
        loadRetryMessages();

        log.info("RetryMessageService initialized: retryDir={}", this.retryDir);
    }

    /**
     * 设置死信队列服务
     */
    public void setDlqService(DeadLetterQueueService dlqService) {
        this.dlqService = dlqService;
    }

    /**
     * 设置延迟消息服务
     */
    public void setDelayMessageService(DelayMessageService delayMessageService) {
        this.delayMessageService = delayMessageService;
    }

    /**
     * 启动服务
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            // 启动重试扫描任务
            scheduler.scheduleAtFixedRate(this::scanRetryMessages,
                    scanIntervalMs, scanIntervalMs, TimeUnit.MILLISECONDS);
            log.info("RetryMessageService started");
        }
    }

    /**
     * 停止服务
     */
    public void shutdown() {
        if (running.compareAndSet(true, false)) {
            // 停止调度
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }

            // 持久化所有重试消息
            persistAllRetryMessages();

            log.info("RetryMessageService shutdown");
        }
    }

    /**
     * 提交消费失败的消息进行重试
     *
     * @param delayMessage 延迟消息
     * @param errorMsg     错误信息
     * @param consumerGroup 消费者组
     * @return 是否提交成功
     */
    public boolean submitForRetry(DelayMessage delayMessage, String errorMsg, String consumerGroup) {
        if (!running.get()) {
            log.warn("RetryMessageService is not running");
            return false;
        }

        // 检查是否超过最大重试次数
        if (delayMessage.isExceedMaxRetry()) {
            // 转入死信队列
            toDeadLetterQueue(delayMessage, errorMsg, consumerGroup,
                    "Exceed max retry count: " + delayMessage.getRetryCount());
            return false;
        }

        // 创建重试消息
        int nextRetryCount = delayMessage.getRetryCount() + 1;
        long delay = delayMessage.calculateRetryDelay(baseDelayMs);

        RetryMessage retryMessage = RetryMessage.builder()
                .messageId(delayMessage.getMessageId() + "_retry_" + nextRetryCount)
                .originalMessageId(delayMessage.getMessageId())
                .topic(delayMessage.getTopic())
                .queueId(delayMessage.getQueueId())
                .physicalOffset(delayMessage.getPhysicalOffset())
                .size(delayMessage.getSize())
                .tagCode(delayMessage.getTagCode())
                .retryCount(nextRetryCount)
                .maxRetryCount(delayMessage.getMaxRetryCount())
                .nextRetryTime(System.currentTimeMillis() + delay)
                .errorMessage(errorMsg)
                .createTime(System.currentTimeMillis())
                .updateTime(System.currentTimeMillis())
                .properties(delayMessage.getProperties())
                .consumerGroup(consumerGroup)
                .finished(false)
                .deadLetter(false)
                .build();

        // 添加到索引
        String indexKey = buildIndexKey(delayMessage.getTopic(), delayMessage.getQueueId(), consumerGroup);
        retryMessageIndex.computeIfAbsent(indexKey, k -> new ConcurrentHashMap<>())
                .put(retryMessage.getMessageId(), retryMessage);

        // 添加到优先级队列
        retryQueueMap.computeIfAbsent(indexKey, k -> new PriorityQueue<>(
                Comparator.comparingLong(RetryMessage::getNextRetryTime)))
                .add(retryMessage);

        // 持久化
        persistRetryMessage(retryMessage);

        log.info("Message submitted for retry: messageId={}, retryCount={}, delayMs={}, topic={}, queueId={}",
                retryMessage.getMessageId(), nextRetryCount, delay, delayMessage.getTopic(), delayMessage.getQueueId());

        return true;
    }

    /**
     * 提交重试消息（直接提交）
     */
    public boolean submitForRetry(RetryMessage retryMessage) {
        if (!running.get()) {
            log.warn("RetryMessageService is not running");
            return false;
        }

        // 检查是否超过最大重试次数
        if (retryMessage.isExceedMaxRetry()) {
            // 转入死信队列
            toDeadLetterQueue(retryMessage, retryMessage.getErrorMessage(),
                    retryMessage.getConsumerGroup(), "Exceed max retry count");
            return false;
        }

        // 添加到索引
        String indexKey = buildIndexKey(retryMessage.getTopic(), retryMessage.getQueueId(),
                retryMessage.getConsumerGroup());
        retryMessageIndex.computeIfAbsent(indexKey, k -> new ConcurrentHashMap<>())
                .put(retryMessage.getMessageId(), retryMessage);

        // 添加到优先级队列
        retryQueueMap.computeIfAbsent(indexKey, k -> new PriorityQueue<>(
                Comparator.comparingLong(RetryMessage::getNextRetryTime)))
                .add(retryMessage);

        // 持久化
        persistRetryMessage(retryMessage);

        log.info("Retry message submitted: messageId={}, retryCount={}, nextRetryTime={}",
                retryMessage.getMessageId(), retryMessage.getRetryCount(), retryMessage.getNextRetryTime());

        return true;
    }

    /**
     * 扫描并处理待重试消息
     */
    private void scanRetryMessages() {
        long now = System.currentTimeMillis();

        for (Map.Entry<String, PriorityQueue<RetryMessage>> entry : retryQueueMap.entrySet()) {
            String indexKey = entry.getKey();
            PriorityQueue<RetryMessage> queue = entry.getValue();

            if (queue.isEmpty()) {
                continue;
            }

            // 取出所有已到期的重试消息
            List<RetryMessage> toRetry = new ArrayList<>();
            RetryMessage peek = queue.peek();

            while (peek != null && peek.getNextRetryTime() <= now) {
                queue.poll();
                toRetry.add(peek);
                peek = queue.peek();
            }

            // 处理到期的重试消息
            for (RetryMessage retryMessage : toRetry) {
                try {
                    processRetryMessage(retryMessage);
                } catch (Exception e) {
                    log.error("Process retry message error: messageId={}", retryMessage.getMessageId(), e);
                }
            }
        }
    }

    /**
     * 处理重试消息
     * 将消息发送到延迟队列，等待延迟后重新投递给消费者
     */
    private void processRetryMessage(RetryMessage retryMessage) {
        // 检查是否超过最大重试次数
        if (retryMessage.isExceedMaxRetry()) {
            toDeadLetterQueue(retryMessage, retryMessage.getErrorMessage(),
                    retryMessage.getConsumerGroup(), "Exceed max retry count");
            return;
        }

        // 创建延迟消息
        DelayMessage delayMessage = DelayMessage.builder()
                .messageId(retryMessage.getMessageId())
                .topic(retryMessage.getTopic())
                .queueId(retryMessage.getQueueId())
                .physicalOffset(retryMessage.getPhysicalOffset())
                .size(retryMessage.getSize())
                .tagCode(retryMessage.getTagCode())
                .retryCount(retryMessage.getRetryCount())
                .maxRetryCount(retryMessage.getMaxRetryCount())
                .properties(retryMessage.getProperties())
                .originalMessageId(retryMessage.getOriginalMessageId())
                .build();

        // 提交到延迟消息服务
        if (delayMessageService != null) {
            long delay = retryMessage.calculateNextDelay(baseDelayMs);
            delayMessageService.submitDelayMessage(delayMessage, delay);
            log.info("Retry message submitted to delay service: messageId={}, delay={}ms",
                    retryMessage.getMessageId(), delay);
        } else {
            log.warn("DelayMessageService not available, cannot submit retry message");
        }

        // 从索引中移除（延迟消息服务会处理后续的重试）
        String indexKey = buildIndexKey(retryMessage.getTopic(), retryMessage.getQueueId(),
                retryMessage.getConsumerGroup());
        retryMessageIndex.get(indexKey).remove(retryMessage.getMessageId());
    }

    /**
     * 标记消息重试成功（消费者消费成功后调用）
     *
     * @param messageId      消息 ID
     * @param consumerGroup  消费者组
     */
    public void markRetrySuccess(String messageId, String consumerGroup) {
        for (Map<String, RetryMessage> index : retryMessageIndex.values()) {
            RetryMessage message = index.remove(messageId);
            if (message != null) {
                message.markFinished();
                log.info("Retry message success: messageId={}, consumerGroup={}",
                        message.getOriginalMessageId(), consumerGroup);
                return;
            }
        }
    }

    /**
     * 获取重试消息
     *
     * @param messageId     消息 ID
     * @param consumerGroup 消费者组
     * @return 重试消息
     */
    public RetryMessage getRetryMessage(String messageId, String consumerGroup) {
        for (Map<String, RetryMessage> index : retryMessageIndex.values()) {
            RetryMessage message = index.get(messageId);
            if (message != null) {
                return message;
            }
        }
        return null;
    }

    /**
     * 获取主题的重试消息数量
     */
    public int getRetryMessageCount(String topic, int queueId, String consumerGroup) {
        String indexKey = buildIndexKey(topic, queueId, consumerGroup);
        ConcurrentHashMap<String, RetryMessage> index = retryMessageIndex.get(indexKey);
        return index != null ? index.size() : 0;
    }

    /**
     * 获取所有重试消息数量
     */
    public int getTotalRetryMessageCount() {
        return retryMessageIndex.values().stream()
                .mapToInt(Map::size)
                .sum();
    }

    /**
     * 转入死信队列
     */
    private void toDeadLetterQueue(RetryMessage retryMessage, String errorMsg, String consumerGroup, String reason) {
        if (dlqService != null) {
            // 将 RetryMessage 转换为死信消息
            com.aoaojiao.catmq.store.delay.model.DeadLetterMessage dlqMessage =
                    com.aoaojiao.catmq.store.delay.model.DeadLetterMessage.fromRetryMessage(retryMessage, reason);
            dlqMessage.setConsumerGroup(consumerGroup);
            dlqMessage.setErrorMessage(errorMsg);

            dlqService.submitDeadLetter(dlqMessage);
            log.info("Message moved to dead letter queue: originalMessageId={}, reason={}",
                    retryMessage.getOriginalMessageId(), reason);
        } else {
            log.warn("DeadLetterQueueService not available, cannot move to DLQ: messageId={}",
                    retryMessage.getMessageId());
        }

        // 从索引中移除
        String indexKey = buildIndexKey(retryMessage.getTopic(), retryMessage.getQueueId(), consumerGroup);
        ConcurrentHashMap<String, RetryMessage> index = retryMessageIndex.get(indexKey);
        if (index != null) {
            index.remove(retryMessage.getMessageId());
        }
    }

    /**
     * 转入死信队列（从 DelayMessage）
     */
    private void toDeadLetterQueue(DelayMessage delayMessage, String errorMsg, String consumerGroup, String reason) {
        if (dlqService != null) {
            com.aoaojiao.catmq.store.delay.model.DeadLetterMessage dlqMessage =
                    com.aoaojiao.catmq.store.delay.model.DeadLetterMessage.builder()
                            .messageId(delayMessage.getMessageId() + "_dlq")
                            .originalMessageId(delayMessage.getMessageId())
                            .topic(delayMessage.getTopic())
                            .queueId(delayMessage.getQueueId())
                            .physicalOffset(delayMessage.getPhysicalOffset())
                            .size(delayMessage.getSize())
                            .tagCode(delayMessage.getTagCode())
                            .consumerGroup(consumerGroup)
                            .deadLetterTime(System.currentTimeMillis())
                            .errorMessage(errorMsg)
                            .deadLetterReason(reason)
                            .retryCount(delayMessage.getRetryCount())
                            .maxRetryCount(delayMessage.getMaxRetryCount())
                            .createTime(delayMessage.getCreateTime())
                            .build();

            dlqService.submitDeadLetter(dlqMessage);
            log.info("Message moved to dead letter queue: originalMessageId={}, reason={}",
                    delayMessage.getMessageId(), reason);
        }
    }

    /**
     * 构建索引 key
     */
    private String buildIndexKey(String topic, int queueId, String consumerGroup) {
        return topic + "#" + queueId + "#" + consumerGroup;
    }

    /**
     * 持久化重试消息
     */
    private void persistRetryMessage(RetryMessage message) {
        try {
            String filePath = getRetryMessageFilePath(message.getTopic(), message.getConsumerGroup());
            try (PrintWriter writer = new PrintWriter(new FileWriter(filePath, true))) {
                writer.println(formatToJson(message));
            }
        } catch (IOException e) {
            log.error("Persist retry message error: messageId={}", message.getMessageId(), e);
        }
    }

    /**
     * 持久化所有重试消息
     */
    private void persistAllRetryMessages() {
        for (Map<String, RetryMessage> index : retryMessageIndex.values()) {
            for (RetryMessage message : index.values()) {
                persistRetryMessage(message);
            }
        }
        log.info("All retry messages persisted");
    }

    /**
     * 加载已存在的重试消息
     */
    private void loadRetryMessages() {
        File dir = new File(retryDir);
        if (!dir.exists()) {
            return;
        }

        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null || files.length == 0) {
            return;
        }

        for (File file : files) {
            try {
                List<String> lines = Files.readAllLines(file.toPath());
                for (String line : lines) {
                    if (line.trim().isEmpty()) {
                        continue;
                    }
                    RetryMessage message = parseFromJson(line);
                    if (message != null && !message.isDeadLetter() && !message.isFinished()) {
                        String indexKey = buildIndexKey(message.getTopic(), message.getQueueId(),
                                message.getConsumerGroup());
                        retryMessageIndex.computeIfAbsent(indexKey, k -> new ConcurrentHashMap<>())
                                .put(message.getMessageId(), message);

                        retryQueueMap.computeIfAbsent(indexKey, k -> new PriorityQueue<>(
                                Comparator.comparingLong(RetryMessage::getNextRetryTime)))
                                .add(message);
                    }
                }
                log.info("Loaded retry messages from file: {}", file.getName());
            } catch (Exception e) {
                log.error("Load retry messages error: file={}", file.getName(), e);
            }
        }
    }

    /**
     * 获取重试消息文件路径
     */
    private String getRetryMessageFilePath(String topic, String consumerGroup) {
        String fileName = topic + "_" + consumerGroup + "_retry.json";
        return retryDir + File.separator + fileName;
    }

    /**
     * 格式化消息为 JSON
     */
    private String formatToJson(RetryMessage message) {
        return com.alibaba.fastjson2.JSON.toJSONString(message);
    }

    /**
     * 从 JSON 解析消息
     */
    private RetryMessage parseFromJson(String json) {
        try {
            return com.alibaba.fastjson2.JSON.parseObject(json, RetryMessage.class);
        } catch (Exception e) {
            log.error("Parse retry message error: {}", json, e);
            return null;
        }
    }

    /**
     * 获取服务状态
     */
    public String getStatus() {
        return String.format("RetryMessageService{ running=%s, totalRetryMessages=%d }",
                running.get(), getTotalRetryMessageCount());
    }

    /**
     * 设置基础延迟时间
     */
    public void setBaseDelayMs(long baseDelayMs) {
        this.baseDelayMs = baseDelayMs;
    }

    /**
     * 设置最大延迟时间
     */
    public void setMaxDelayMs(long maxDelayMs) {
        this.maxDelayMs = maxDelayMs;
    }

    /**
     * 设置默认最大重试次数
     */
    public void setDefaultMaxRetryCount(int maxRetryCount) {
        this.defaultMaxRetryCount = maxRetryCount;
    }
}