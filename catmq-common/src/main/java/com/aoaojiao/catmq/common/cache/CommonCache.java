package com.aoaojiao.catmq.common.cache;

import com.aoaojiao.catmq.common.model.CatmqTopicModel;
import com.aoaojiao.catmq.common.model.ConsumeQueueOffsetModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 公共缓存
 * 所有缓存会放在这里进行管理
 *
 * @author DD
 */
public class CommonCache {


    private static List<CatmqTopicModel> CATMQ_TOPIC_MODEL_CACHE = new ArrayList<>();
    private static ConsumeQueueOffsetModel CONSUME_QUEUE_OFFSET_MODEL_CACHE = new ConsumeQueueOffsetModel();

    /**
     * 队列最大偏移缓存
     * Key: topic#queueId
     * Value: maxOffset
     */
    private static final Map<String, AtomicLong> QUEUE_MAX_OFFSET_CACHE = new ConcurrentHashMap<>();

    public static List<CatmqTopicModel> getCatmqTopicModelList() {
        return CATMQ_TOPIC_MODEL_CACHE;
    }

    public static List<CatmqTopicModel> getCatmqTopicModelCache() {
        return CATMQ_TOPIC_MODEL_CACHE;
    }

    public static void setCatmqTopicModelCache(List<CatmqTopicModel> catmqTopicModelCache) {
        CATMQ_TOPIC_MODEL_CACHE = catmqTopicModelCache;
    }

    public static Map<String, CatmqTopicModel> getCatmqTopicModelMap() {
        return CATMQ_TOPIC_MODEL_CACHE.stream()
                .collect(Collectors.toMap(CatmqTopicModel::getTopic, it -> it));
    }

    public static ConsumeQueueOffsetModel getConsumeQueueOffsetModelCache() {
        return CONSUME_QUEUE_OFFSET_MODEL_CACHE;
    }

    public static void setConsumeQueueOffsetModelCache(ConsumeQueueOffsetModel consumeQueueOffsetModelCache) {
        CONSUME_QUEUE_OFFSET_MODEL_CACHE = consumeQueueOffsetModelCache;
    }

    // ==================== 队列偏移缓存方法 ====================

    /**
     * 获取队列最大偏移
     *
     * @param topic   主题
     * @param queueId 队列 ID
     * @return 最大偏移
     */
    public static long getQueueMaxOffset(String topic, int queueId) {
        String key = buildKey(topic, queueId);
        AtomicLong offset = QUEUE_MAX_OFFSET_CACHE.get(key);
        return offset != null ? offset.get() : 0;
    }

    /**
     * 更新队列最大偏移
     *
     * @param topic    主题
     * @param queueId  队列 ID
     * @param maxOffset 最大偏移
     */
    public static void updateQueueMaxOffset(String topic, int queueId, long maxOffset) {
        String key = buildKey(topic, queueId);
        QUEUE_MAX_OFFSET_CACHE.computeIfAbsent(key, k -> new AtomicLong())
                .updateAndGet(current -> Math.max(current, maxOffset));
    }

    /**
     * 获取队列最小偏移
     *
     * @param topic   主题
     * @param queueId 队列 ID
     * @return 最小偏移（通常为 0）
     */
    public static long getQueueMinOffset(String topic, int queueId) {
        // 假设消息不删除，最小偏移始终为 0
        return 0;
    }

    /**
     * 构建缓存 key
     *
     * @param topic   主题
     * @param queueId 队列 ID
     * @return key
     */
    private static String buildKey(String topic, int queueId) {
        return topic + "#" + queueId;
    }

    /**
     * 清除队列偏移缓存
     */
    public static void clearQueueOffsetCache() {
        QUEUE_MAX_OFFSET_CACHE.clear();
    }

    /**
     * 清除指定主题的队列偏移缓存
     *
     * @param topic 主题
     */
    public static void clearTopicQueueOffsetCache(String topic) {
        QUEUE_MAX_OFFSET_CACHE.entrySet().removeIf(entry -> entry.getKey().startsWith(topic + "#"));
    }

    /**
     * 获取所有队列偏移缓存
     *
     * @return 缓存映射
     */
    public static Map<String, Long> getAllQueueOffsets() {
        return QUEUE_MAX_OFFSET_CACHE.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().get()
                ));
    }
}
