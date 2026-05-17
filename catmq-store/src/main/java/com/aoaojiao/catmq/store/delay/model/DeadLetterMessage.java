package com.aoaojiao.catmq.store.delay.model;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 死信消息实体
 * 记录超过最大重试次数的消息
 *
 * @author DD
 */
@Data
@Builder
public class DeadLetterMessage {

    /**
     * 消息唯一标识
     */
    private String messageId;

    /**
     * 原始消息 ID
     */
    private String originalMessageId;

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
     * 消息内容（JSON 序列化）
     */
    private String messageBody;

    /**
     * 扩展属性
     */
    private Map<String, String> properties;

    /**
     * 消费者组
     */
    private String consumerGroup;

    /**
     * 死信进入时间
     */
    private long deadLetterTime;

    /**
     * 原始错误信息
     */
    private String errorMessage;

    /**
     * 错误堆栈
     */
    private String errorStackTrace;

    /**
     * 最终死信原因
     */
    private String deadLetterReason;

    /**
     * 重试次数
     */
    private int retryCount;

    /**
     * 最大重试次数
     */
    private int maxRetryCount;

    /**
     * 创建时间
     */
    private long createTime;

    /**
     * 处理状态: 0-未处理, 1-处理中, 2-已处理
     */
    @Builder.Default
    private int status = 0;

    /**
     * 处理时间
     */
    private long processTime;

    /**
     * 处理结果
     */
    private String processResult;

    /**
     * 从 RetryMessage 创建死信消息
     */
    public static DeadLetterMessage fromRetryMessage(RetryMessage retryMessage, String reason) {
        long now = System.currentTimeMillis();
        return DeadLetterMessage.builder()
                .messageId(retryMessage.getMessageId() + "_dlq")
                .originalMessageId(retryMessage.getOriginalMessageId())
                .topic(retryMessage.getTopic())
                .queueId(retryMessage.getQueueId())
                .physicalOffset(retryMessage.getPhysicalOffset())
                .size(retryMessage.getSize())
                .tagCode(retryMessage.getTagCode())
                .messageBody(retryMessage.getMessageBody())
                .properties(retryMessage.getProperties())
                .consumerGroup(retryMessage.getConsumerGroup())
                .deadLetterTime(now)
                .errorMessage(retryMessage.getErrorMessage())
                .errorStackTrace(retryMessage.getErrorStackTrace())
                .deadLetterReason(reason)
                .retryCount(retryMessage.getRetryCount())
                .maxRetryCount(retryMessage.getMaxRetryCount())
                .createTime(retryMessage.getCreateTime())
                .status(0)
                .build();
    }

    /**
     * 标记为处理中
     */
    public void markProcessing() {
        this.status = 1;
    }

    /**
     * 标记为已处理
     */
    public void markProcessed(String result) {
        this.status = 2;
        this.processTime = System.currentTimeMillis();
        this.processResult = result;
    }

    /**
     * 获取状态描述
     */
    public String getStatusDesc() {
        switch (status) {
            case 0:
                return "PENDING";
            case 1:
                return "PROCESSING";
            case 2:
                return "PROCESSED";
            default:
                return "UNKNOWN";
        }
    }

    /**
     * 判断是否已处理
     */
    public boolean isProcessed() {
        return status == 2;
    }

    /**
     * 判断是否未处理
     */
    public boolean isPending() {
        return status == 0;
    }

    /**
     * 标记为死信
     */
    public void markDeadLetter(String reason) {
        this.deadLetterReason = reason;
    }
}