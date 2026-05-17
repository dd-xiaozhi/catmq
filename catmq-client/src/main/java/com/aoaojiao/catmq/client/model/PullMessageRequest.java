package com.aoaojiao.catmq.client.model;

import lombok.Builder;
import lombok.Data;

/**
 * 拉取消息请求
 *
 * @author DD
 */
@Data
@Builder
public class PullMessageRequest {

    /**
     * 主题
     */
    private String topic;

    /**
     * 队列 ID
     */
    private int queueId;

    /**
     * 拉取起始偏移量
     */
    private long offset;

    /**
     * 最大拉取消息数量
     */
    private int maxMsgCount;

    /**
     * 最大拉取字节数
     */
    private int maxMsgSize;
}