package com.aoaojiao.catmq.store.delay.service;

import com.aoaojiao.catmq.store.delay.model.DeadLetterMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 死信队列服务（DLQ - Dead Letter Queue）
 * 负责管理超过最大重试次数的消息
 * 提供死信消息的存储、查询和手动处理功能
 *
 * @author DD
 */
public class DeadLetterQueueService {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterQueueService.class);

    /**
     * 死信主题前缀
     */
    private static final String DLQ_TOPIC_PREFIX = "%DLQ%.";

    /**
     * 服务运行状态
     */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 死信消息索引（messageId -> DeadLetterMessage）
     */
    private final ConcurrentHashMap<String, DeadLetterMessage> dlqIndex = new ConcurrentHashMap<>();

    /**
     * 按主题分组的死信消息
     */
    private final ConcurrentHashMap<String, Set<String>> topicDlqIndex = new ConcurrentHashMap<>();

    /**
     * 按消费者组分组的死信消息
     */
    private final ConcurrentHashMap<String, Set<String>> consumerGroupDlqIndex = new ConcurrentHashMap<>();

    /**
     * 死信目录
     */
    private String dlqDir;

    /**
     * 死信主题
     */
    private String dlqTopic;

    /**
     * 日期格式化器
     */
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");

    /**
     * 初始化死信队列服务
     */
    public void init(String storePathRootDir) {
        init2(storePathRootDir, DLQ_TOPIC_PREFIX + "default");
    }

    /**
     * 初始化死信队列服务
     *
     * @param storePathRootDir 存储根目录
     * @param dlqTopic         死信主题
     */
    public void init2(String storePathRootDir, String dlqTopic) {
        this.dlqTopic = dlqTopic;
        this.dlqDir = storePathRootDir + File.separator + "dlq" + File.separator + dlqTopic;

        File dir = new File(dlqDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // 加载已存在的死信消息
        loadDeadLetterMessages();

        log.info("DeadLetterQueueService initialized: dlqDir={}, dlqTopic={}", dlqDir, this.dlqTopic);
    }

    /**
     * 启动服务
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            log.info("DeadLetterQueueService started: dlqTopic={}", dlqTopic);
        }
    }

    /**
     * 停止服务
     */
    public void shutdown() {
        if (running.compareAndSet(true, false)) {
            // 持久化所有死信消息
            persistAllDeadLetterMessages();
            log.info("DeadLetterQueueService shutdown");
        }
    }

    /**
     * 提交死信消息
     *
     * @param message 死信消息
     * @return 是否提交成功
     */
    public boolean submitDeadLetter(DeadLetterMessage message) {
        if (!running.get()) {
            log.warn("DeadLetterQueueService is not running");
            return false;
        }

        // 设置默认值
        if (message.getMessageId() == null) {
            message.setMessageId(generateMessageId(message.getOriginalMessageId()));
        }
        if (message.getDeadLetterTime() == 0) {
            message.setDeadLetterTime(System.currentTimeMillis());
        }
        if (message.getCreateTime() == 0) {
            message.setCreateTime(System.currentTimeMillis());
        }
        message.markDeadLetter(message.getDeadLetterReason());

        // 添加到索引
        dlqIndex.put(message.getMessageId(), message);

        // 按主题索引
        topicDlqIndex.computeIfAbsent(message.getTopic(), k -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
                .add(message.getMessageId());

        // 按消费者组索引
        if (message.getConsumerGroup() != null) {
            consumerGroupDlqIndex.computeIfAbsent(message.getConsumerGroup(),
                    k -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
                    .add(message.getMessageId());
        }

        // 持久化
        persistDeadLetterMessage(message);

        log.info("Dead letter submitted: messageId={}, originalMessageId={}, topic={}, reason={}",
                message.getMessageId(), message.getOriginalMessageId(), message.getTopic(),
                message.getDeadLetterReason());

        return true;
    }

    /**
     * 获取死信消息
     *
     * @param messageId 消息 ID
     * @return 死信消息
     */
    public DeadLetterMessage getDeadLetterMessage(String messageId) {
        return dlqIndex.get(messageId);
    }

    /**
     * 获取主题的所有死信消息
     *
     * @param topic 主题
     * @return 死信消息列表
     */
    public List<DeadLetterMessage> getDeadLetterMessagesByTopic(String topic) {
        Set<String> messageIds = topicDlqIndex.get(topic);
        if (messageIds == null || messageIds.isEmpty()) {
            return Collections.emptyList();
        }

        return messageIds.stream()
                .map(dlqIndex::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 获取消费者组的所有死信消息
     *
     * @param consumerGroup 消费者组
     * @return 死信消息列表
     */
    public List<DeadLetterMessage> getDeadLetterMessagesByConsumerGroup(String consumerGroup) {
        Set<String> messageIds = consumerGroupDlqIndex.get(consumerGroup);
        if (messageIds == null || messageIds.isEmpty()) {
            return Collections.emptyList();
        }

        return messageIds.stream()
                .map(dlqIndex::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有未处理的死信消息
     *
     * @return 死信消息列表
     */
    public List<DeadLetterMessage> getPendingDeadLetters() {
        return dlqIndex.values().stream()
                .filter(DeadLetterMessage::isPending)
                .collect(Collectors.toList());
    }

    /**
     * 获取死信消息数量
     */
    public int getDeadLetterCount(String topic) {
        Set<String> messageIds = topicDlqIndex.get(topic);
        return messageIds != null ? messageIds.size() : 0;
    }

    /**
     * 获取所有死信消息数量
     */
    public int getTotalDeadLetterCount() {
        return dlqIndex.size();
    }

    /**
     * 处理死信消息（重新投递）
     *
     * @param messageId     消息 ID
     * @param action        处理动作（RETRY, DELETE, IGNORE）
     * @param delayMs       延迟时间（毫秒），仅对 RETRY 有效
     * @param resultMessage 处理结果说明
     * @return 是否处理成功
     */
    public boolean processDeadLetter(String messageId, String action, long delayMs, String resultMessage) {
        DeadLetterMessage message = dlqIndex.get(messageId);
        if (message == null) {
            log.warn("Dead letter message not found: {}", messageId);
            return false;
        }

        switch (action.toUpperCase()) {
            case "RETRY":
                // 标记为处理中
                message.markProcessing();
                // 持久化状态变更
                persistDeadLetterMessage(message);
                log.info("Dead letter marked for retry: messageId={}, delayMs={}", messageId, delayMs);
                return true;

            case "DELETE":
                // 标记为已处理并从索引中移除
                message.markProcessed("DELETED: " + resultMessage);
                removeFromIndexes(messageId, message.getTopic(), message.getConsumerGroup());
                persistDeadLetterMessage(message);
                log.info("Dead letter deleted: messageId={}", messageId);
                return true;

            case "IGNORE":
                // 标记为已处理，但不删除
                message.markProcessed("IGNORED: " + resultMessage);
                persistDeadLetterMessage(message);
                log.info("Dead letter ignored: messageId={}", messageId);
                return true;

            default:
                log.warn("Unknown action: {}", action);
                return false;
        }
    }

    /**
     * 批量处理死信消息
     *
     * @param topic        主题（可选）
     * @param consumerGroup 消费者组（可选）
     * @param action       处理动作
     * @param resultMessage 处理结果说明
     * @return 处理的死信数量
     */
    public int batchProcessDeadLetters(String topic, String consumerGroup, String action, String resultMessage) {
        List<DeadLetterMessage> toProcess;

        if (topic != null && !topic.isEmpty()) {
            toProcess = getDeadLetterMessagesByTopic(topic);
        } else if (consumerGroup != null && !consumerGroup.isEmpty()) {
            toProcess = getDeadLetterMessagesByConsumerGroup(consumerGroup);
        } else {
            toProcess = new ArrayList<>(dlqIndex.values());
        }

        int count = 0;
        for (DeadLetterMessage message : toProcess) {
            if (processDeadLetter(message.getMessageId(), action, 0, resultMessage)) {
                count++;
            }
        }

        log.info("Batch processed dead letters: action={}, count={}", action, count);
        return count;
    }

    /**
     * 从索引中移除
     */
    private void removeFromIndexes(String messageId, String topic, String consumerGroup) {
        dlqIndex.remove(messageId);

        Set<String> topicSet = topicDlqIndex.get(topic);
        if (topicSet != null) {
            topicSet.remove(messageId);
        }

        if (consumerGroup != null) {
            Set<String> cgSet = consumerGroupDlqIndex.get(consumerGroup);
            if (cgSet != null) {
                cgSet.remove(messageId);
            }
        }
    }

    /**
     * 持久化死信消息到磁盘
     */
    private void persistDeadLetterMessage(DeadLetterMessage message) {
        try {
            // 按日期和主题分目录存储
            String dateStr = dateFormat.format(new Date(message.getDeadLetterTime()));
            String dirPath = dlqDir + File.separator + dateStr;
            File dir = new File(dirPath);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String fileName = message.getTopic() + "_" + message.getQueueId() + ".json";
            String filePath = dirPath + File.separator + fileName;

            try (PrintWriter writer = new PrintWriter(new FileWriter(filePath, true))) {
                writer.println(formatToJson(message));
            }
        } catch (IOException e) {
            log.error("Persist dead letter message error: messageId={}", message.getMessageId(), e);
        }
    }

    /**
     * 持久化所有死信消息
     */
    private void persistAllDeadLetterMessages() {
        for (DeadLetterMessage message : dlqIndex.values()) {
            persistDeadLetterMessage(message);
        }
        log.info("All dead letter messages persisted");
    }

    /**
     * 加载已存在的死信消息
     */
    private void loadDeadLetterMessages() {
        File dir = new File(dlqDir);
        if (!dir.exists()) {
            return;
        }

        // 递归加载所有日期目录
        loadDeadLetterMessagesFromDir(dir);
    }

    /**
     * 从目录加载死信消息
     */
    private void loadDeadLetterMessagesFromDir(File dir) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                // 递归加载子目录
                loadDeadLetterMessagesFromDir(file);
            } else if (file.getName().endsWith(".json")) {
                try {
                    List<String> lines = Files.readAllLines(file.toPath());
                    for (String line : lines) {
                        if (line.trim().isEmpty()) {
                            continue;
                        }
                        DeadLetterMessage message = parseFromJson(line);
                        if (message != null) {
                            // 只加载未处理的消息
                            if (message.isPending() || message.getStatus() == 0) {
                                dlqIndex.put(message.getMessageId(), message);
                                topicDlqIndex.computeIfAbsent(message.getTopic(),
                                        k -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
                                        .add(message.getMessageId());
                                if (message.getConsumerGroup() != null) {
                                    consumerGroupDlqIndex.computeIfAbsent(message.getConsumerGroup(),
                                            k -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
                                            .add(message.getMessageId());
                                }
                            }
                        }
                    }
                    log.info("Loaded dead letter messages from file: {}", file.getName());
                } catch (Exception e) {
                    log.error("Load dead letter messages error: file={}", file.getName(), e);
                }
            }
        }
    }

    /**
     * 生成死信消息 ID
     */
    private String generateMessageId(String originalMessageId) {
        return (originalMessageId != null ? originalMessageId : "unknown") + "_dlq_" + System.currentTimeMillis();
    }

    /**
     * 格式化消息为 JSON
     */
    private String formatToJson(DeadLetterMessage message) {
        return com.alibaba.fastjson2.JSON.toJSONString(message);
    }

    /**
     * 从 JSON 解析消息
     */
    private DeadLetterMessage parseFromJson(String json) {
        try {
            return com.alibaba.fastjson2.JSON.parseObject(json, DeadLetterMessage.class);
        } catch (Exception e) {
            log.error("Parse dead letter message error: {}", json, e);
            return null;
        }
    }

    /**
     * 获取死信主题
     */
    public String getDlqTopic() {
        return dlqTopic;
    }

    /**
     * 获取服务状态
     */
    public String getStatus() {
        return String.format("DeadLetterQueueService{ running=%s, totalDeadLetters=%d, dlqTopic=%s }",
                running.get(), dlqIndex.size(), dlqTopic);
    }

    /**
     * 获取按主题分组的死信统计
     */
    public Map<String, Integer> getDeadLetterStatsByTopic() {
        Map<String, Integer> stats = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : topicDlqIndex.entrySet()) {
            stats.put(entry.getKey(), entry.getValue().size());
        }
        return stats;
    }

    /**
     * 获取按消费者组分组的死信统计
     */
    public Map<String, Integer> getDeadLetterStatsByConsumerGroup() {
        Map<String, Integer> stats = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : consumerGroupDlqIndex.entrySet()) {
            stats.put(entry.getKey(), entry.getValue().size());
        }
        return stats;
    }
}