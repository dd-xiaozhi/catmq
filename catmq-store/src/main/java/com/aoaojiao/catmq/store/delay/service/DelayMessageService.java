package com.aoaojiao.catmq.store.delay.service;

import com.aoaojiao.catmq.common.cache.CommonCache;
import com.aoaojiao.catmq.store.config.MessageStoreConfig;
import com.aoaojiao.catmq.store.core.CommitLogAppendHandler;
import com.aoaojiao.catmq.store.core.ConsumerQueueManager;
import com.aoaojiao.catmq.store.core.DispatchMessageService;
import com.aoaojiao.catmq.store.delay.model.DelayMessage;
import com.aoaojiao.catmq.store.delay.timer.TimeWheel;
import com.aoaojiao.catmq.store.model.DispatchRequest;
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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 延迟消息服务
 * 负责管理延迟消息的存储、调度和分发
 * 基于时间轮实现高效延迟任务调度
 *
 * @author DD
 */
public class DelayMessageService {

    private static final Logger log = LoggerFactory.getLogger(DelayMessageService.class);

    /**
     * 服务运行状态
     */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 存储配置
     */
    private MessageStoreConfig config;

    /**
     * 延迟消息持久化路径
     */
    private String delayMessageDir;

    /**
     * 延迟消息索引（messageId -> DelayMessage）
     */
    private final ConcurrentHashMap<String, DelayMessage> delayMessageIndex = new ConcurrentHashMap<>();

    /**
     * 延迟消息文件（按主题分区）
     */
    private final ConcurrentHashMap<String, File> delayMessageFiles = new ConcurrentHashMap<>();

    /**
     * 分发服务（用于延迟消息到期后分发到 ConsumerQueue）
     */
    private DispatchMessageService dispatchMessageService;

    /**
     * 消费者队列管理器
     */
    private ConsumerQueueManager consumerQueueManager;

    /**
     * 基础延迟时间（毫秒）
     */
    private static final long BASE_DELAY_MS = 1000;

    /**
     * 时间轮（秒级精度）
     */
    private TimeWheel secondWheel;

    /**
     * 时间轮（毫秒级精度）
     */
    private TimeWheel milliWheel;

    /**
     * 无参构造方法（用于测试）
     */
    public DelayMessageService() {
        // 仅初始化时间轮，不进行持久化
        initTimeWheel();
    }

    /**
     * 初始化延迟消息服务
     */
    public void init(MessageStoreConfig config,
                     DispatchMessageService dispatchMessageService,
                     ConsumerQueueManager consumerQueueManager) {
        this.config = config;
        this.dispatchMessageService = dispatchMessageService;
        this.consumerQueueManager = consumerQueueManager;

        // 初始化延迟消息存储目录
        this.delayMessageDir = config.getStorePathRootDir() + File.separator + "delayMessage";
        File dir = new File(delayMessageDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // 加载已存在的延迟消息
        loadDelayMessages();

        // 初始化时间轮
        initTimeWheel();

        log.info("DelayMessageService initialized: delayMessageDir={}", delayMessageDir);
    }

    /**
     * 初始化时间轮
     */
    private void initTimeWheel() {
        // 秒级时间轮：刻度 1 秒，60 个槽位（覆盖 1 分钟）
        this.secondWheel = new TimeWheel(1000, 60, (taskKey, data) -> {
            handleExpiredTask(taskKey, data);
        }, "SECOND");

        // 毫秒级时间轮：刻度 100 毫秒，100 个槽位（覆盖 10 秒）
        this.milliWheel = new TimeWheel(100, 100, (taskKey, data) -> {
            handleExpiredTask(taskKey, data);
        }, "MILLI");

        // 启动时间轮
        secondWheel.start();
        milliWheel.start();
    }

    /**
     * 启动服务
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            // 如果还未初始化（无参构造场景），则初始化时间轮
            if (secondWheel == null || milliWheel == null) {
                initTimeWheel();
            }
            log.info("DelayMessageService started");
        }
    }

    /**
     * 停止服务
     */
    public void shutdown() {
        if (running.compareAndSet(true, false)) {
            // 停止时间轮
            if (secondWheel != null) {
                secondWheel.stop();
            }
            if (milliWheel != null) {
                milliWheel.stop();
            }

            // 持久化延迟消息
            persistAllDelayMessages();

            log.info("DelayMessageService shutdown");
        }
    }

    /**
     * 提交延迟消息
     *
     * @param message       延迟消息
     * @param delayMs       延迟时间（毫秒）
     * @return 是否提交成功
     */
    public boolean submitDelayMessage(DelayMessage message, long delayMs) {
        if (!running.get()) {
            log.warn("DelayMessageService is not running");
            return false;
        }

        // 设置延迟信息
        long now = System.currentTimeMillis();
        message.setStartDelayTime(now);
        message.setExpireTime(now + delayMs);
        message.setCreateTime(now);

        // 生成唯一消息 ID
        if (message.getMessageId() == null) {
            message.setMessageId(generateMessageId());
        }

        // 添加到索引
        delayMessageIndex.put(message.getMessageId(), message);

        // 计算延迟级别，选择合适的时间轮
        if (delayMs < 10000) {
            // 小于 10 秒，使用毫秒级时间轮
            milliWheel.addTask(message.getMessageId(), delayMs, message);
        } else {
            // 大于等于 10 秒，使用秒级时间轮
            secondWheel.addTask(message.getMessageId(), delayMs, message);
        }

        // 持久化到磁盘
        persistDelayMessage(message);

        log.info("Delay message submitted: messageId={}, topic={}, delayMs={}, expireTime={}",
                message.getMessageId(), message.getTopic(), delayMs, message.getExpireTime());

        return true;
    }

    /**
     * 处理过期任务
     */
    private void handleExpiredTask(String taskKey, Object data) {
        try {
            if (!(data instanceof DelayMessage)) {
                log.warn("Invalid task data type: {}", data.getClass());
                return;
            }

            DelayMessage delayMessage = (DelayMessage) data;

            // 从索引中获取最新消息（可能已更新）
            DelayMessage latestMessage = delayMessageIndex.get(taskKey);
            if (latestMessage == null) {
                log.debug("Delay message not found in index: {}", taskKey);
                return;
            }

            // 检查消息是否仍然有效
            if (latestMessage.isDeadLetter() || latestMessage.isExceedMaxRetry()) {
                log.info("Delay message is dead letter or exceed max retry, skip: messageId={}",
                        taskKey);
                return;
            }

            // 分发到 ConsumerQueue
            dispatchToConsumerQueue(delayMessage);

            // 从索引中移除
            delayMessageIndex.remove(taskKey);

            // 标记持久化文件中的消息为已过期
            markMessageExpired(taskKey);

            log.info("Delay message expired and dispatched: messageId={}, topic={}, queueId={}",
                    taskKey, delayMessage.getTopic(), delayMessage.getQueueId());

        } catch (Exception e) {
            log.error("Handle expired task error: taskKey={}", taskKey, e);
        }
    }

    /**
     * 分发到 ConsumerQueue
     */
    private void dispatchToConsumerQueue(DelayMessage delayMessage) {
        if (dispatchMessageService != null) {
            DispatchRequest request = DispatchRequest.builder()
                    .topic(delayMessage.getTopic())
                    .queueId(delayMessage.getQueueId())
                    .physicalOffset(delayMessage.getPhysicalOffset())
                    .size(delayMessage.getSize())
                    .tagCode(delayMessage.getTagCode())
                    .timestamp(System.currentTimeMillis())
                    .build();

            dispatchMessageService.putDispatchRequest(request);
        }
    }

    /**
     * 取消延迟消息
     *
     * @param messageId 消息 ID
     * @return 是否取消成功
     */
    public boolean cancelDelayMessage(String messageId) {
        DelayMessage message = delayMessageIndex.get(messageId);
        if (message == null) {
            return false;
        }

        // 从时间轮中移除
        secondWheel.removeTask(messageId);
        milliWheel.removeTask(messageId);

        // 从索引中移除
        delayMessageIndex.remove(messageId);

        // 标记持久化文件中的消息为已取消
        markMessageCancelled(messageId);

        log.info("Delay message cancelled: messageId={}", messageId);
        return true;
    }

    /**
     * 获取延迟消息
     *
     * @param messageId 消息 ID
     * @return 延迟消息
     */
    public DelayMessage getDelayMessage(String messageId) {
        return delayMessageIndex.get(messageId);
    }

    /**
     * 获取主题的延迟消息数量
     */
    public int getDelayMessageCount(String topic) {
        return (int) delayMessageIndex.values().stream()
                .filter(m -> m.getTopic().equals(topic))
                .count();
    }

    /**
     * 获取所有延迟消息数量
     */
    public int getTotalDelayMessageCount() {
        return delayMessageIndex.size();
    }

    /**
     * 获取延迟消息的剩余时间
     */
    public long getRemainingDelayTime(String messageId) {
        DelayMessage message = delayMessageIndex.get(messageId);
        if (message == null) {
            return -1;
        }
        long remaining = message.getExpireTime() - System.currentTimeMillis();
        return Math.max(0, remaining);
    }

    /**
     * 持久化延迟消息到磁盘
     */
    private void persistDelayMessage(DelayMessage message) {
        try {
            File file = getOrCreateDelayMessageFile(message.getTopic());
            try (PrintWriter writer = new PrintWriter(new FileWriter(file, true))) {
                writer.println(formatMessageToJson(message));
            }
        } catch (IOException e) {
            log.error("Persist delay message error: messageId={}", message.getMessageId(), e);
        }
    }

    /**
     * 持久化所有延迟消息
     */
    private void persistAllDelayMessages() {
        for (DelayMessage message : delayMessageIndex.values()) {
            persistDelayMessage(message);
        }
        log.info("All delay messages persisted");
    }

    /**
     * 加载已存在的延迟消息
     */
    private void loadDelayMessages() {
        File dir = new File(delayMessageDir);
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
                    DelayMessage message = parseMessageFromJson(line);
                    if (message != null && !message.isDeadLetter()) {
                        // 重新调度到时间轮
                        long delay = message.getExpireTime() - System.currentTimeMillis();
                        if (delay > 0) {
                            delayMessageIndex.put(message.getMessageId(), message);
                            if (delay < 10000) {
                                milliWheel.addTask(message.getMessageId(), delay, message);
                            } else {
                                secondWheel.addTask(message.getMessageId(), delay, message);
                            }
                        }
                    }
                }
                log.info("Loaded delay messages from file: {}", file.getName());
            } catch (Exception e) {
                log.error("Load delay messages error: file={}", file.getName(), e);
            }
        }
    }

    /**
     * 标记消息已过期
     */
    private void markMessageExpired(String messageId) {
        // 可以维护一个过期消息的列表，用于清理
        log.debug("Message marked as expired: {}", messageId);
    }

    /**
     * 标记消息已取消
     */
    private void markMessageCancelled(String messageId) {
        log.debug("Message marked as cancelled: {}", messageId);
    }

    /**
     * 获取或创建延迟消息文件
     */
    private File getOrCreateDelayMessageFile(String topic) {
        return delayMessageFiles.computeIfAbsent(topic, t -> {
            String filePath = delayMessageDir + File.separator + topic + "_delay.json";
            File file = new File(filePath);
            if (!file.exists()) {
                try {
                    file.createNewFile();
                } catch (IOException e) {
                    log.error("Create delay message file error: {}", filePath, e);
                }
            }
            return file;
        });
    }

    /**
     * 格式化消息为 JSON
     */
    private String formatMessageToJson(DelayMessage message) {
        // 使用 Fastjson2 序列化
        return com.alibaba.fastjson2.JSON.toJSONString(message);
    }

    /**
     * 从 JSON 解析消息
     */
    private DelayMessage parseMessageFromJson(String json) {
        try {
            return com.alibaba.fastjson2.JSON.parseObject(json, DelayMessage.class);
        } catch (Exception e) {
            log.error("Parse delay message error: {}", json, e);
            return null;
        }
    }

    /**
     * 生成消息 ID
     */
    private String generateMessageId() {
        return UUID.randomUUID().toString().replace("-", "") + "_" + System.currentTimeMillis();
    }

    /**
     * 获取服务状态
     */
    public String getStatus() {
        return String.format("DelayMessageService{ running=%s, totalDelayMessages=%d, secondWheelTasks=%d, milliWheelTasks=%d }",
                running.get(), delayMessageIndex.size(),
                secondWheel != null ? secondWheel.getTaskCacheSize() : 0,
                milliWheel != null ? milliWheel.getTaskCacheSize() : 0);
    }
}