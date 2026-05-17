package com.aoaojiao.catmq.admin.service;

import com.aoaojiao.catmq.admin.dto.response.MetricsResponse;
import com.aoaojiao.catmq.common.cache.CommonCache;
import com.aoaojiao.catmq.common.model.CatmqTopicModel;
import com.aoaojiao.catmq.common.model.QueueModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 指标采集服务
 * 定时采集系统指标数据
 *
 * @author DD
 */
@Service
public class MetricsService {

    private static final Logger log = LoggerFactory.getLogger(MetricsService.class);

    /**
     * 消息发送计数器（按 Topic 分组）
     */
    private final Map<String, AtomicLong> topicSendCounters = new ConcurrentHashMap<>();

    /**
     * 消息消费计数器（按 Topic 分组）
     */
    private final Map<String, AtomicLong> topicConsumeCounters = new ConcurrentHashMap<>();

    /**
     * 写延迟记录（纳秒）
     */
    private final List<Long> writeLatencyRecords = Collections.synchronizedList(new ArrayList<>());

    /**
     * 消费延迟记录（纳秒）
     */
    private final List<Long> consumeLatencyRecords = Collections.synchronizedList(new ArrayList<>());

    /**
     * 当前活跃连接数
     */
    private final AtomicLong activeConnectionCount = new AtomicLong(0);

    /**
     * 总连接数
     */
    private final AtomicLong totalConnectionCount = new AtomicLong(0);

    /**
     * 上一次采集的发送计数
     */
    private final Map<String, Long> lastSendCounts = new ConcurrentHashMap<>();

    /**
     * 上一次采集的消费计数
     */
    private final Map<String, Long> lastConsumeCounts = new ConcurrentHashMap<>();

    /**
     * 上一次采集时间
     */
    private volatile long lastCollectTime = System.currentTimeMillis();

    /**
     * 获取完整的指标数据
     */
    public MetricsResponse getMetrics() {
        return MetricsResponse.builder()
                .timestamp(System.currentTimeMillis())
                .throughput(collectThroughput())
                .queueDepth(collectQueueDepth())
                .latency(collectLatency())
                .connection(collectConnection())
                .build();
    }

    /**
     * 采集吞吐量指标
     */
    private MetricsResponse.ThroughputMetrics collectThroughput() {
        long now = System.currentTimeMillis();
        double elapsed = (now - lastCollectTime) / 1000.0; // 秒
        if (elapsed <= 0) {
            elapsed = 1.0;
        }

        Map<String, Double> topicSendRates = new HashMap<>();
        Map<String, Double> topicConsumeRates = new HashMap<>();

        double totalSendRate = 0;
        double totalConsumeRate = 0;

        // 获取所有 Topic
        List<CatmqTopicModel> topics = CommonCache.getCatmqTopicModelList();
        long totalSend = 0;
        long totalConsume = 0;

        for (CatmqTopicModel topic : topics) {
            String topicName = topic.getTopic();
            AtomicLong sendCounter = topicSendCounters.get(topicName);
            AtomicLong consumeCounter = topicConsumeCounters.get(topicName);

            long currentSend = sendCounter != null ? sendCounter.get() : 0;
            long currentConsume = consumeCounter != null ? consumeCounter.get() : 0;

            // 计算速率
            long lastSend = lastSendCounts.getOrDefault(topicName, 0L);
            long lastConsume = lastConsumeCounts.getOrDefault(topicName, 0L);

            double sendRate = (currentSend - lastSend) / elapsed;
            double consumeRate = (currentConsume - lastConsume) / elapsed;

            topicSendRates.put(topicName, sendRate);
            topicConsumeRates.put(topicName, consumeRate);

            totalSendRate += sendRate;
            totalConsumeRate += consumeRate;
            totalSend += currentSend;
            totalConsume += currentConsume;

            // 更新上一次计数
            lastSendCounts.put(topicName, currentSend);
            lastConsumeCounts.put(topicName, currentConsume);
        }

        lastCollectTime = now;

        return MetricsResponse.ThroughputMetrics.builder()
                .sendRate(totalSendRate)
                .consumeRate(totalConsumeRate)
                .totalSendCount(totalSend)
                .totalConsumeCount(totalConsume)
                .topicSendRates(topicSendRates)
                .topicConsumeRates(topicConsumeRates)
                .build();
    }

    /**
     * 采集队列深度指标
     */
    private MetricsResponse.QueueDepthMetrics collectQueueDepth() {
        Map<String, Long> topicQueueDepth = new HashMap<>();
        String maxDepthTopic = null;
        long maxDepth = 0;
        long totalDepth = 0;

        List<CatmqTopicModel> topics = CommonCache.getCatmqTopicModelList();
        for (CatmqTopicModel topic : topics) {
            List<QueueModel> queues = topic.getQueueModelList();
            if (queues != null) {
                long topicDepth = 0;
                for (QueueModel queue : queues) {
                    long maxOffset = CommonCache.getQueueMaxOffset(topic.getTopic(), queue.getId());
                    long minOffset = 0;
                    long depth = maxOffset - minOffset;
                    topicQueueDepth.put(topic.getTopic() + "#" + queue.getId(), depth);
                    topicDepth += depth;
                    totalDepth += depth;

                    if (topicDepth > maxDepth) {
                        maxDepth = topicDepth;
                        maxDepthTopic = topic.getTopic();
                    }
                }
            }
        }

        return MetricsResponse.QueueDepthMetrics.builder()
                .topicQueueDepth(topicQueueDepth)
                .maxDepthTopic(maxDepthTopic)
                .maxDepth(maxDepth)
                .totalDepth(totalDepth)
                .build();
    }

    /**
     * 采集延迟指标
     */
    private MetricsResponse.LatencyMetrics collectLatency() {
        double writeAvg = 0, writeMax = 0, writeMin = 0;
        double consumeAvg = 0, consumeMax = 0, consumeMin = 0;

        if (!writeLatencyRecords.isEmpty()) {
            List<Long> sorted = new ArrayList<>(writeLatencyRecords);
            Collections.sort(sorted);
            writeAvg = sorted.stream().mapToLong(Long::longValue).average().orElse(0) / 1_000_000.0; // 转换为毫秒
            writeMax = sorted.get(sorted.size() - 1) / 1_000_000.0;
            writeMin = sorted.get(0) / 1_000_000.0;
            // 保留最近 1000 条记录
            if (sorted.size() > 1000) {
                writeLatencyRecords.subList(0, sorted.size() - 1000).clear();
            }
        }

        if (!consumeLatencyRecords.isEmpty()) {
            List<Long> sorted = new ArrayList<>(consumeLatencyRecords);
            Collections.sort(sorted);
            consumeAvg = sorted.stream().mapToLong(Long::longValue).average().orElse(0) / 1_000_000.0;
            consumeMax = sorted.get(sorted.size() - 1) / 1_000_000.0;
            consumeMin = sorted.get(0) / 1_000_000.0;
            if (sorted.size() > 1000) {
                consumeLatencyRecords.subList(0, sorted.size() - 1000).clear();
            }
        }

        return MetricsResponse.LatencyMetrics.builder()
                .writeLatencyAvg(writeAvg)
                .writeLatencyMax(writeMax)
                .writeLatencyMin(writeMin)
                .consumeLatencyAvg(consumeAvg)
                .consumeLatencyMax(consumeMax)
                .consumeLatencyMin(consumeMin)
                .build();
    }

    /**
     * 采集连接数指标
     */
    private MetricsResponse.ConnectionMetrics collectConnection() {
        // 这里应该从 Netty 连接管理器获取真实的连接数
        // 目前返回模拟数据
        return MetricsResponse.ConnectionMetrics.builder()
                .activeConnectionCount((int) activeConnectionCount.get())
                .totalConnectionCount((int) totalConnectionCount.get())
                .producerCount((int) (totalConnectionCount.get() / 3))
                .consumerCount((int) (totalConnectionCount.get() * 2 / 3))
                .build();
    }

    // ==================== 数据上报接口 ====================

    /**
     * 记录消息发送
     */
    public void recordSend(String topic, long count) {
        topicSendCounters.computeIfAbsent(topic, k -> new AtomicLong())
                .addAndGet(count);
    }

    /**
     * 记录消息消费
     */
    public void recordConsume(String topic, long count) {
        topicConsumeCounters.computeIfAbsent(topic, k -> new AtomicLong())
                .addAndGet(count);
    }

    /**
     * 记录写入延迟
     */
    public void recordWriteLatency(long latencyNanos) {
        writeLatencyRecords.add(latencyNanos);
    }

    /**
     * 记录消费延迟
     */
    public void recordConsumeLatency(long latencyNanos) {
        consumeLatencyRecords.add(latencyNanos);
    }

    /**
     * 更新连接数
     */
    public void updateConnectionCount(int active, int total) {
        this.activeConnectionCount.set(active);
        this.totalConnectionCount.set(total);
    }

    // ==================== 定时任务 ====================

    /**
     * 定期清理过期的指标数据，防止内存泄漏
     * 每 10 分钟执行一次
     */
    @Scheduled(fixedRate = 600000)
    public void cleanupExpiredMetrics() {
        long now = System.currentTimeMillis();

        // 清理长时间未更新的 Topic 计数器
        Set<String> activeTopics = CommonCache.getCatmqTopicModelList().stream()
                .map(CatmqTopicModel::getTopic)
                .collect(Collectors.toSet());

        topicSendCounters.keySet().removeIf(topic -> !activeTopics.contains(topic));
        topicConsumeCounters.keySet().removeIf(topic -> !activeTopics.contains(topic));
        lastSendCounts.keySet().removeIf(topic -> !activeTopics.contains(topic));
        lastConsumeCounts.keySet().removeIf(topic -> !activeTopics.contains(topic));

        // 限制延迟记录大小
        if (writeLatencyRecords.size() > 10000) {
            writeLatencyRecords.subList(0, writeLatencyRecords.size() - 10000).clear();
        }
        if (consumeLatencyRecords.size() > 10000) {
            consumeLatencyRecords.subList(0, consumeLatencyRecords.size() - 10000).clear();
        }

        log.debug("指标数据清理完成");
    }
}