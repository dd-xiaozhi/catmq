package com.aoaojiao.catmq.client.consumer;

/**
 * 消费结果枚举
 *
 * @author DD
 */
public enum ConsumeResult {
    /**
     * 消费成功
     */
    SUCCESS,

    /**
     * 消费失败，需要重试
     */
    RETRY,

    /**
     * 消费失败，直接进入死信队列
     */
    DLQ
}