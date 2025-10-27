package com.aoaojiao.catmq.common.model;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * consume-queue-offset 文件模型
 *
 * @author DD
 */
@Data
public class ConsumeQueueOffsetModel {

    private OffsetTable offsetTable;

    @Data
    public static class OffsetTable {

        private Map<String, TopicDetail> consumerGroupDetail = new HashMap<>();
    }

    @Data
    public static class TopicDetail extends HashMap<String, PartitionOffset> {

    }

    @Data
    public static class PartitionOffset extends HashMap<String, String> {

    }
}
