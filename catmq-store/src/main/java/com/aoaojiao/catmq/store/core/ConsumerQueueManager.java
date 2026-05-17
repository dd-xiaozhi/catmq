package com.aoaojiao.catmq.store.core;

import com.aoaojiao.catmq.common.model.CatmqTopicModel;
import com.aoaojiao.catmq.common.model.QueueModel;
import com.aoaojiao.catmq.store.config.MessageStoreConfig;
import lombok.Getter;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ConsumerQueue 管理器
 * 管理所有 Topic 的 ConsumerQueue 实例
 *
 * @author DD
 */
public class ConsumerQueueManager {

    private final MessageStoreConfig config;

    /**
     * 缓存：topic#queueId -> ConsumerQueue
     */
    private final ConcurrentHashMap<String, ConsumerQueue> cqCache;

    /**
     * 存储路径
     */
    @Getter
    private String storePath;

    public ConsumerQueueManager(MessageStoreConfig config) {
        this.config = config;
        this.cqCache = new ConcurrentHashMap<>();
        this.storePath = initStorePath();
    }

    /**
     * 初始化存储路径
     */
    private String initStorePath() {
        String path = config.getStorePathRootDir() + File.separator + "consumeQueue";
        File folder = new File(path);
        if (!folder.exists()) {
            folder.mkdirs();
        }
        return path;
    }

    /**
     * 获取或创建 ConsumerQueue
     *
     * @param topic   主题
     * @param queueId 队列 ID
     * @return ConsumerQueue
     */
    public ConsumerQueue getOrCreate(String topic, int queueId) {
        String key = buildKey(topic, queueId);

        return cqCache.computeIfAbsent(key, k -> {
            try {
                return createConsumerQueue(topic, queueId);
            } catch (IOException e) {
                throw new RuntimeException("Create ConsumerQueue error: " + key, e);
            }
        });
    }

    /**
     * 获取 ConsumerQueue（不存在返回 null）
     *
     * @param topic   主题
     * @param queueId 队列 ID
     * @return ConsumerQueue
     */
    public ConsumerQueue get(String topic, int queueId) {
        return cqCache.get(buildKey(topic, queueId));
    }

    /**
     * 创建 ConsumerQueue 实例
     *
     * @param topic   主题
     * @param queueId 队列 ID
     * @return ConsumerQueue
     */
    private ConsumerQueue createConsumerQueue(String topic, int queueId) throws IOException {
        ConsumerQueue cq = new ConsumerQueue();
        cq.loadingFileInMMap(
                topic,
                queueId,
                this.storePath,
                config.getConsumeQueueCommitLogFileSize()
        );
        return cq;
    }

    /**
     * 加载指定 Topic 的所有 ConsumerQueue
     *
     * @param topic     主题
     * @param queueList 队列列表
     */
    public void loadTopic(String topic, List<QueueModel> queueList) {
        if (queueList == null || queueList.isEmpty()) {
            return;
        }

        for (QueueModel queue : queueList) {
            int queueId = queue.getId();
            ConsumerQueue cq = getOrCreate(topic, queueId);

            // 从 QueueModel 恢复消费进度
            if (queue.getMaxOffset() != null && queue.getMaxOffset() > 0) {
                // 跳转到最大偏移位置
                // 注意：这里只是更新内存状态，实际索引已经在文件中了
            }
        }
    }

    /**
     * 加载所有 Topic 的 ConsumerQueue
     *
     * @param topicList 主题列表
     */
    public void loadAllTopics(List<CatmqTopicModel> topicList) {
        if (topicList == null || topicList.isEmpty()) {
            return;
        }

        for (CatmqTopicModel topic : topicList) {
            List<QueueModel> queues = topic.getQueueModelList();
            if (queues != null && !queues.isEmpty()) {
                loadTopic(topic.getTopic(), queues);
            }
        }
    }

    /**
     * 构建缓存 key
     */
    private String buildKey(String topic, int queueId) {
        return topic + "#" + queueId;
    }

    /**
     * 获取所有 ConsumerQueue
     *
     * @return 所有 ConsumerQueue
     */
    public Collection<ConsumerQueue> getAll() {
        return cqCache.values();
    }

    /**
     * 获取 ConsumerQueue 数量
     *
     * @return 数量
     */
    public int size() {
        return cqCache.size();
    }

    /**
     * 检查是否包含指定的 ConsumerQueue
     *
     * @param topic   主题
     * @param queueId 队列 ID
     * @return 是否包含
     */
    public boolean contains(String topic, int queueId) {
        return cqCache.containsKey(buildKey(topic, queueId));
    }

    /**
     * 获取指定主题的所有 ConsumerQueue
     *
     * @param topic 主题
     * @return ConsumerQueue 列表
     */
    public Collection<ConsumerQueue> getByTopic(String topic) {
        return cqCache.entrySet().stream()
                .filter(e -> e.getKey().startsWith(topic + "#"))
                .map(java.util.Map.Entry::getValue)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 清理资源
     */
    public void shutdown() {
        cqCache.values().forEach(ConsumerQueue::shutdown);
        cqCache.clear();
    }

    @Override
    public String toString() {
        return "ConsumerQueueManager{" +
                "size=" + cqCache.size() +
                ", storePath='" + storePath + '\'' +
                '}';
    }
}
