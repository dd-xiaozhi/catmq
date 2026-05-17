package com.aoaojiao.catmq.store.delay.model;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 重试消息实体
 * 用于记录消费失败后的重试信息
 *
 * @author DD
 */
@Data
@Builder
public class RetryMessage {

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
     * 重试次数
     */
    private int retryCount;

    /**
     * 最大重试次数
     */
    private int maxRetryCount;

    /**
     * 下次重试时间戳
     */
    private long nextRetryTime;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 错误堆栈
     */
    private String errorStackTrace;

    /**
     * 创建时间
     */
    private long createTime;

    /**
     * 更新时间
     */
    private long updateTime;

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
     * 是否已完成重试（已成功或已死信）
     */
    @Builder.Default
    private boolean finished = false;

    /**
     * 是否为死信
     */
    @Builder.Default
    private boolean deadLetter = false;

    /**
     * 死信原因
     */
    private String deadLetterReason;

    /**
     * 创建重试消息
     */
    public static RetryMessage fromDelayMessage(DelayMessage delayMessage, long delayMs, String errorMsg) {
        long now = System.currentTimeMillis();
        return RetryMessage.builder()
                .messageId(delayMessage.getMessageId() + "_retry_" + (delayMessage.getRetryCount() + 1))
                .originalMessageId(delayMessage.getMessageId())
                .topic(delayMessage.getTopic())
                .queueId(delayMessage.getQueueId())
                .physicalOffset(delayMessage.getPhysicalOffset())
                .size(delayMessage.getSize())
                .tagCode(delayMessage.getTagCode())
                .retryCount(delayMessage.getRetryCount() + 1)
                .maxRetryCount(delayMessage.getMaxRetryCount())
                .nextRetryTime(now + delayMs)
                .errorMessage(errorMsg)
                .createTime(now)
                .updateTime(now)
                .properties(delayMessage.getProperties())
                .finished(false)
                .deadLetter(false)
                .build();
    }

    /**
     * 计算下次重试延迟（指数退避）
     *
     * @param baseDelayMs 基础延迟（毫秒）
     * @return 下次重试延迟（毫秒）
     */
    public long calculateNextDelay(long baseDelayMs) {
        // 指数退避: baseDelayMs * 2^retryCount
        long delay = baseDelayMs * (1L << retryCount);
        // 最大延迟 60 秒
        return Math.min(delay, 60000);
    }

    /**
     * 判断是否超过最大重试次数
     */
    public boolean isExceedMaxRetry() {
        return retryCount >= maxRetryCount;
    }

    /**
     * 标记为死信
     */
    public void markDeadLetter(String reason) {
        this.deadLetter = true;
        this.deadLetterReason = reason;
        this.finished = true;
    }

    /**
     * 标记已完成（消费成功）
     */
    public void markFinished() {
        this.finished = true;
    }

    /**
     * 更新时间
     */
    public void touch() {
        this.updateTime = System.currentTimeMillis();
    }

    /**
     * 获取重试状态描述
     */
    public String getStatusDesc() {
        if (deadLetter) {
            return "DEAD_LETTER";
        }
        if (finished) {
            return "FINISHED";
        }
        return "PENDING";
    }
}