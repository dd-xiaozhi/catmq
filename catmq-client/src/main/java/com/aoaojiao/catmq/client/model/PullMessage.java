package com.aoaojiao.catmq.client.model;

import lombok.Builder;
import lombok.Data;

/**
 * 拉取到的单个消息
 *
 * @author DD
 */
@Data
@Builder
public class PullMessage {

    /**
     * 消息 ID
     */
    private String messageId;

    /**
     * 主题
     */
    private String topic;

    /**
     * 队列 ID
     */
    private int queueId;

    /**
     * 物理偏移量
     */
    private long physicalOffset;

    /**
     * 消息体（字节数组）
     */
    private byte[] body;

    /**
     * 消息标签
     */
    private String tags;

    /**
     * 消息属性
     */
    private String properties;

    /**
     * 时间戳
     */
    private long timestamp;
}