package com.aoaojiao.catmq.admin.service;

import com.aoaojiao.catmq.admin.dto.response.MessageResponse;
import com.aoaojiao.catmq.common.cache.CommonCache;
import com.aoaojiao.catmq.common.model.CatmqTopicModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 消息查询服务
 *
 * @author DD
 */
@Service
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);

    /**
     * 根据 Topic 查询消息
     */
    public List<MessageResponse> queryByTopic(String topic, int offset, int limit) {
        Map<String, CatmqTopicModel> topicMap = CommonCache.getCatmqTopicModelMap();
        CatmqTopicModel topicModel = topicMap.get(topic);

        if (topicModel == null) {
            return Collections.emptyList();
        }

        List<MessageResponse> messages = new ArrayList<>();

        // 简化实现：基于偏移量模拟消息查询
        // 实际实现应该从 CommitLog 中读取
        long maxOffset = CommonCache.getQueueMaxOffset(topic, 0);
        long start = Math.max(0, offset);
        long end = Math.min(maxOffset, start + limit);

        for (long i = start; i < end; i++) {
            messages.add(MessageResponse.builder()
                    .messageId(i)
                    .topic(topic)
                    .queueId(0)
                    .body("消息内容-" + i)
                    .bodySize(64)
                    .storeTimestamp(System.currentTimeMillis() - (maxOffset - i) * 1000)
                    .consumeCount(0)
                    .build());
        }

        return messages;
    }

    /**
     * 根据消息 ID 查询
     */
    public MessageResponse queryByMessageId(Long messageId) {
        // 简化实现：模拟返回消息
        // 实际实现应该从 CommitLog 中读取
        return MessageResponse.builder()
                .messageId(messageId)
                .topic("unknown")
                .queueId(0)
                .body("消息内容-" + messageId)
                .bodySize(64)
                .storeTimestamp(System.currentTimeMillis())
                .consumeCount(0)
                .build();
    }

    /**
     * 根据 Topic 和队列 ID 查询消息
     */
    public List<MessageResponse> queryByTopicAndQueue(String topic, int queueId, int offset, int limit) {
        Map<String, CatmqTopicModel> topicMap = CommonCache.getCatmqTopicModelMap();
        CatmqTopicModel topicModel = topicMap.get(topic);

        if (topicModel == null) {
            return Collections.emptyList();
        }

        List<MessageResponse> messages = new ArrayList<>();

        long maxOffset = CommonCache.getQueueMaxOffset(topic, queueId);
        long start = Math.max(0, offset);
        long end = Math.min(maxOffset, start + limit);

        for (long i = start; i < end; i++) {
            messages.add(MessageResponse.builder()
                    .messageId(i)
                    .topic(topic)
                    .queueId(queueId)
                    .body("消息内容-" + i)
                    .bodySize(64)
                    .storeTimestamp(System.currentTimeMillis() - (maxOffset - i) * 1000)
                    .consumeCount(0)
                    .build());
        }

        return messages;
    }
}