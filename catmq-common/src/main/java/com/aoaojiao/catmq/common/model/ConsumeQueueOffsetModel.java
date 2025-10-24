package com.aoaojiao.catmq.common.model;

import lombok.Data;

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
        
        private Map<String, ConsumerGroupDetail> consumerGroupDetail; 
    }
    
    @Data
    public static class ConsumerGroupDetail {
        
        private Map<String, Map<String, String>> ConsumerGroupDetailMap;
    }
    
}
