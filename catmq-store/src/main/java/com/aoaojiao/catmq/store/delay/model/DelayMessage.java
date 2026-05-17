package com.aoaojiao.catmq.store.delay.model;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 延迟消息实体
 * 包含消息本身和延迟相关元数据
 *
 * @author DD
 */
@Data
@Builder
public class DelayMessage {

    /**
     * 消息唯一标识
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
     * 延迟开始时间戳
     */
    private long startDelayTime;

    /**
     * 延迟结束时间戳（即到期时间）
     */
    private long expireTime;

    /**
     * 延迟级别（用于分类管理）
     */
    private int delayLevel;

    /**
     * 扩展属性
     */
    private Map<String, String> properties;

    /**
     * 创建时间
     */
    private long createTime;

    /**
     * 重试次数
     */
    @Builder.Default
    private int retryCount = 0;

    /**
     * 最大重试次数
     */
    @Builder.Default
    private int maxRetryCount = 16;

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
     * 原始消息 ID（用于重试消息关联原消息）
     */
    private String originalMessageId;

    /**
     * 创建延迟消息
     */
    public static DelayMessage create(String messageId, String topic, int queueId,
                                      long physicalOffset, int size, long tagCode,
                                      long delayMs) {
        long now = System.currentTimeMillis();
        return DelayMessage.builder()
                .messageId(messageId)
                .topic(topic)
                .queueId(queueId)
                .physicalOffset(physicalOffset)
                .size(size)
                .tagCode(tagCode)
                .startDelayTime(now)
                .expireTime(now + delayMs)
                .createTime(now)
                .retryCount(0)
                .maxRetryCount(16)
                .deadLetter(false)
                .build();
    }

    /**
     * 创建重试消息
     */
    public static DelayMessage createRetryMessage(DelayMessage original, int retryCount) {
        return DelayMessage.builder()
                .messageId(original.getMessageId() + "_retry_" + retryCount)
                .topic(original.getTopic())
                .queueId(original.getQueueId())
                .physicalOffset(original.getPhysicalOffset())
                .size(original.getSize())
                .tagCode(original.getTagCode())
                .startDelayTime(System.currentTimeMillis())
                .expireTime(0) // 需要根据重试策略计算
                .createTime(original.getCreateTime())
                .retryCount(retryCount)
                .maxRetryCount(original.getMaxRetryCount())
                .deadLetter(false)
                .originalMessageId(original.getMessageId())
                .properties(original.getProperties())
                .build();
    }

    /**
     * 计算重试延迟时间（指数退避）
     *
     * @param baseDelayMs 基础延迟（默认 1000ms）
     * @return 延迟时间
     */
    public long calculateRetryDelay(long baseDelayMs) {
        // 指数退避: 1s, 2s, 4s, 8s, 16s, 32s, 60s(上限)
        long delay = baseDelayMs * (1L << retryCount);
        return Math.min(delay, 60000); // 最大 60 秒
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
    public void markAsDeadLetter(String reason) {
        this.deadLetter = true;
        this.deadLetterReason = reason;
    }

    /**
     * 推进到下一个重试
     */
    public void incrementRetry() {
        this.retryCount++;
        // 更新消息 ID
        this.messageId = this.messageId + "_retry_" + retryCount;
    }
}