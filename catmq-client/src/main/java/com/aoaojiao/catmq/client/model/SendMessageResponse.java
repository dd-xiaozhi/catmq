package com.aoaojiao.catmq.client.model;

import lombok.Builder;
import lombok.Data;

/**
 * 发送消息响应
 *
 * @author DD
 */
@Data
@Builder
public class SendMessageResponse {

    /**
     * 发送状态
     */
    private boolean success;

    /**
     * 消息 ID
     */
    private String messageId;

    /**
     * 队列 ID
     */
    private int queueId;

    /**
     * 物理偏移量
     */
    private long physicalOffset;

    /**
     * 错误信息
     */
    private String errorMessage;
}