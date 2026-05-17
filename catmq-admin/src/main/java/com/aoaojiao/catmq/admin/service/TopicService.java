package com.aoaojiao.catmq.admin.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.aoaojiao.catmq.admin.dto.request.TopicCreateRequest;
import com.aoaojiao.catmq.admin.dto.response.TopicResponse;
import com.aoaojiao.catmq.admin.model.AlertRule;
import com.aoaojiao.catmq.admin.util.FileContentUtil;
import com.aoaojiao.catmq.broker.BrokerStartup;
import com.aoaojiao.catmq.common.cache.CommonCache;
import com.aoaojiao.catmq.common.model.CatmqTopicModel;
import com.aoaojiao.catmq.common.model.CommitLogModel;
import com.aoaojiao.catmq.common.model.QueueModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Topic 管理服务
 *
 * @author DD
 */
@Service
public class TopicService {

    private static final Logger log = LoggerFactory.getLogger(TopicService.class);

    private static final String TOPIC_CONFIG_FILE = "D:\\Work\\project\\catmq\\catmq\\store\\catmq-topic.json";

    /**
     * 查询所有 Topic
     */
    public List<TopicResponse> listTopics() {
        List<CatmqTopicModel> topicModels = CommonCache.getCatmqTopicModelList();
        if (topicModels == null || topicModels.isEmpty()) {
            return Collections.emptyList();
        }

        return topicModels.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * 根据名称查询 Topic
     */
    public TopicResponse getTopic(String topicName) {
        Map<String, CatmqTopicModel> topicMap = CommonCache.getCatmqTopicModelMap();
        CatmqTopicModel model = topicMap.get(topicName);
        if (model == null) {
            return null;
        }
        return convertToResponse(model);
    }

    /**
     * 创建 Topic
     */
    public TopicResponse createTopic(TopicCreateRequest request) {
        String topic = request.getTopic();

        // 检查是否已存在
        Map<String, CatmqTopicModel> topicMap = CommonCache.getCatmqTopicModelMap();
        if (topicMap.containsKey(topic)) {
            throw new IllegalStateException("Topic [" + topic + "] 已存在");
        }

        // 构建新 Topic 模型
        CatmqTopicModel newTopic = new CatmqTopicModel();
        newTopic.setTopic(topic);
        newTopic.setCreateAt(System.currentTimeMillis());
        newTopic.setUpdateAt(System.currentTimeMillis());

        // 创建队列列表
        List<QueueModel> queueModels = new ArrayList<>();
        for (int i = 0; i < request.getQueueCount(); i++) {
            QueueModel queueModel = new QueueModel();
            queueModel.setId(i);
            queueModel.setMaxOffset(0L);
            queueModels.add(queueModel);
        }
        newTopic.setQueueModelList(queueModels);

        // 添加到缓存
        CommonCache.getCatmqTopicModelList().add(newTopic);

        // 持久化到文件
        persistTopicInfo();

        log.info("创建 Topic 成功: {}", topic);
        return convertToResponse(newTopic);
    }

    /**
     * 删除 Topic
     */
    public void deleteTopic(String topicName) {
        List<CatmqTopicModel> topicList = CommonCache.getCatmqTopicModelList();
        boolean removed = topicList.removeIf(t -> topicName.equals(t.getTopic()));

        if (!removed) {
            throw new IllegalArgumentException("Topic [" + topicName + "] 不存在");
        }

        // 清除队列偏移缓存
        CommonCache.clearTopicQueueOffsetCache(topicName);

        // 持久化
        persistTopicInfo();

        log.info("删除 Topic 成功: {}", topicName);
    }

    /**
     * 获取 Topic 队列深度（消息堆积数）
     */
    public Map<String, Long> getTopicQueueDepth() {
        Map<String, Long> depthMap = new ConcurrentHashMap<>();

        List<CatmqTopicModel> topics = CommonCache.getCatmqTopicModelList();
        for (CatmqTopicModel topic : topics) {
            List<QueueModel> queues = topic.getQueueModelList();
            if (queues != null) {
                for (QueueModel queue : queues) {
                    long maxOffset = CommonCache.getQueueMaxOffset(topic.getTopic(), queue.getId());
                    long minOffset = 0L;
                    depthMap.put(topic.getTopic() + "#" + queue.getId(), maxOffset - minOffset);
                }
            }
        }

        return depthMap;
    }

    /**
     * 持久化 Topic 信息到文件
     */
    private void persistTopicInfo() {
        try {
            List<CatmqTopicModel> topics = CommonCache.getCatmqTopicModelList();
            String json = JSON.toJSONString(topics, JSONWriter.Feature.PrettyFormat);
            FileContentUtil.writeStringToFile(TOPIC_CONFIG_FILE, json);
        } catch (IOException e) {
            log.error("持久化 Topic 信息失败", e);
            throw new RuntimeException("持久化 Topic 信息失败", e);
        }
    }

    /**
     * 转换为响应对象
     */
    private TopicResponse convertToResponse(CatmqTopicModel model) {
        List<TopicResponse.QueueInfo> queueInfos = new ArrayList<>();

        if (model.getQueueModelList() != null) {
            for (QueueModel queue : model.getQueueModelList()) {
                long maxOffset = CommonCache.getQueueMaxOffset(model.getTopic(), queue.getId());
                queueInfos.add(TopicResponse.QueueInfo.builder()
                        .queueId(queue.getId())
                        .maxOffset(maxOffset)
                        .minOffset(0L)
                        .build());
            }
        }

        return TopicResponse.builder()
                .topic(model.getTopic())
                .queueCount(model.getQueueModelList() != null ? model.getQueueModelList().size() : 0)
                .createAt(model.getCreateAt())
                .updateAt(model.getUpdateAt())
                .queueList(queueInfos)
                .build();
    }
}