package com.aoaojiao.catmq.admin.service;

import com.aoaojiao.catmq.admin.dto.response.ConsumerGroupResponse;
import com.aoaojiao.catmq.common.cache.CommonCache;
import com.aoaojiao.catmq.common.model.CatmqTopicModel;
import com.aoaojiao.catmq.common.model.ConsumeQueueOffsetModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 消费组管理服务
 *
 * @author DD
 */
@Service
public class ConsumerGroupService {

    private static final Logger log = LoggerFactory.getLogger(ConsumerGroupService.class);

    /**
     * 获取所有消费组
     */
    public List<ConsumerGroupResponse> listConsumerGroups() {
        List<ConsumerGroupResponse> groups = new ArrayList<>();

        // 获取消费进度
        ConsumeQueueOffsetModel offsetModel = CommonCache.getConsumeQueueOffsetModelCache();
        if (offsetModel == null || offsetModel.getOffsetTable() == null) {
            return groups;
        }

        Map<String, ConsumeQueueOffsetModel.TopicDetail> detailMap =
                offsetModel.getOffsetTable().getConsumerGroupDetail();

        if (detailMap == null) {
            return groups;
        }

        for (Map.Entry<String, ConsumeQueueOffsetModel.TopicDetail> entry : detailMap.entrySet()) {
            String groupName = entry.getKey();
            ConsumeQueueOffsetModel.TopicDetail topicDetail = entry.getValue();

            ConsumerGroupResponse group = new ConsumerGroupResponse();
            group.setGroupName(groupName);
            group.setTopic(extractTopicFromGroupName(groupName));
            group.setStatus("ACTIVE");
            group.setConsumerCount(1);

            // 解析消费进度
            List<ConsumerGroupResponse.ConsumeProgress> progressList = new ArrayList<>();
            for (Map.Entry<String, ConsumeQueueOffsetModel.PartitionOffset> queueEntry :
                    topicDetail.entrySet()) {
                Integer queueId = Integer.parseInt(queueEntry.getKey());

                long currentOffset = 0;
                long maxOffset = CommonCache.getQueueMaxOffset(group.getTopic(), queueId);

                Map<String, String> offsetMap = queueEntry.getValue();
                if (offsetMap != null && !offsetMap.isEmpty()) {
                    String offsetStr = offsetMap.get("offset");
                    if (offsetStr != null) {
                        currentOffset = Long.parseLong(offsetStr);
                    }
                }

                long lag = Math.max(0, maxOffset - currentOffset);
                double progressPercent = maxOffset > 0 ? (double) currentOffset / maxOffset * 100 : 0;

                progressList.add(ConsumerGroupResponse.ConsumeProgress.builder()
                        .queueId(queueId)
                        .currentOffset(currentOffset)
                        .maxOffset(maxOffset)
                        .lag(lag)
                        .progressPercent(progressPercent)
                        .build());
            }

            group.setProgressList(progressList);
            groups.add(group);
        }

        return groups;
    }

    /**
     * 获取指定消费组
     */
    public ConsumerGroupResponse getConsumerGroup(String groupName) {
        List<ConsumerGroupResponse> groups = listConsumerGroups();
        return groups.stream()
                .filter(g -> groupName.equals(g.getGroupName()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取指定 Topic 的消费组
     */
    public List<ConsumerGroupResponse> getConsumerGroupsByTopic(String topic) {
        return listConsumerGroups().stream()
                .filter(g -> topic.equals(g.getTopic()))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 从消费组名称提取 Topic
     * 格式: groupName#topic
     */
    private String extractTopicFromGroupName(String groupName) {
        int hashIndex = groupName.indexOf('#');
        if (hashIndex > 0) {
            return groupName.substring(hashIndex + 1);
        }
        return groupName;
    }
}