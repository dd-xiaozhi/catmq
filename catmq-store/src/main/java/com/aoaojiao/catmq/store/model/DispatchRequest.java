package com.aoaojiao.catmq.store.model;

import lombok.Builder;
import lombok.Data;

/**
 * 分发请求
 * 用于将消息从 CommitLog 分发到 ConsumerQueue
 *
 * @author DD
 */
@Data
@Builder
public class DispatchRequest {

    /**
     * 主题
     */
    private String topic;

    /**
     * 队列 ID
     */
    private int queueId;

    /**
     * CommitLog 物理偏移量
     */
    private long physicalOffset;

    /**
     * 消息大小
     */
    private int size;

    /**
     * 标签哈希
     */
    private long tagCode;

    /**
     * 时间戳
     */
    private long timestamp;

    /**
     * 创建分发请求
     */
    public static DispatchRequest of(String topic, int queueId, long physicalOffset, int size, long tagCode) {
        return DispatchRequest.builder()
                .topic(topic)
                .queueId(queueId)
                .physicalOffset(physicalOffset)
                .size(size)
                .tagCode(tagCode)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
