package com.aoaojiao.catmq.store.transaction;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 事务消息实体
 * 用于记录事务消息的元数据和状态
 *
 * @author DD
 */
@Data
@Builder
public class TransactionMessage {

    /**
     * 事务消息 ID（用于唯一标识事务消息）
     */
    private String transactionId;

    /**
     * 原始消息 ID
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
     * 事务状态
     */
    private TransactionState transactionState;

    /**
     * 事务类型
     */
    private TransactionType transactionType;

    /**
     * 创建时间
     */
    private long createTime;

    /**
     * 最后更新时间
     */
    private long updateTime;

    /**
     * 过期时间（用于回查）
     */
    private long expireTime;

    /**
     * 回查次数
     */
    @Builder.Default
    private int checkCount = 0;

    /**
     * 最大回查次数
     */
    @Builder.Default
    private int maxCheckCount = 5;

    /**
     * 扩展属性
     */
    private Map<String, String> properties;

    /**
     * 消息内容（JSON 序列化）
     */
    private String messageBody;

    /**
     * 消费者组列表（需要提交后才能投递）
     */
    private String consumerGroups;

    /**
     * 事务结果（COMMIT 或 ROLLBACK）
     */
    private String transactionResult;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 事务状态枚举
     */
    public enum TransactionState {
        /**
         * 预处理阶段，消息不可见
         */
        PREPARED,

        /**
         * 已提交，消息可见
         */
        COMMIT,

        /**
         * 已回滚，消息删除
         */
        ROLLBACK,

        /**
         * 未知状态，需要回查
         */
        UNKNOWN,

        /**
         * 已结束（提交或回滚完成）
         */
        END
    }

    /**
     * 事务类型枚举
     */
    public enum TransactionType {
        /**
         * 两阶段提交
         */
        TWO_PHASE_COMMIT,

        /**
         * 单阶段消息（普通消息，无事务）
         */
        NORMAL
    }

    /**
     * 创建事务消息
     */
    public static TransactionMessage createPrepared(String transactionId, String topic, int queueId,
                                                    long physicalOffset, int size, long tagCode,
                                                    String messageBody, Map<String, String> properties) {
        long now = System.currentTimeMillis();
        return TransactionMessage.builder()
                .transactionId(transactionId)
                .messageId(transactionId) // 事务 ID 作为消息 ID
                .topic(topic)
                .queueId(queueId)
                .physicalOffset(physicalOffset)
                .size(size)
                .tagCode(tagCode)
                .transactionState(TransactionState.PREPARED)
                .transactionType(TransactionType.TWO_PHASE_COMMIT)
                .createTime(now)
                .updateTime(now)
                .expireTime(now + 60000) // 默认 60 秒超时
                .checkCount(0)
                .maxCheckCount(5)
                .messageBody(messageBody)
                .properties(properties)
                .build();
    }

    /**
     * 提交事务
     */
    public void commit() {
        this.transactionState = TransactionState.COMMIT;
        this.updateTime = System.currentTimeMillis();
        this.transactionResult = "COMMIT";
    }

    /**
     * 回滚事务
     */
    public void rollback() {
        this.transactionState = TransactionState.ROLLBACK;
        this.updateTime = System.currentTimeMillis();
        this.transactionResult = "ROLLBACK";
    }

    /**
     * 标记为结束
     */
    public void markEnd() {
        this.transactionState = TransactionState.END;
        this.updateTime = System.currentTimeMillis();
    }

    /**
     * 增加回查次数
     */
    public void incrementCheckCount() {
        this.checkCount++;
        this.updateTime = System.currentTimeMillis();
    }

    /**
     * 判断是否需要回查
     */
    public boolean needCheck() {
        return transactionState == TransactionState.PREPARED
                || transactionState == TransactionState.UNKNOWN;
    }

    /**
     * 判断是否超过最大回查次数
     */
    public boolean isExceedMaxCheck() {
        return checkCount >= maxCheckCount;
    }

    /**
     * 判断是否超时
     */
    public boolean isTimeout() {
        return System.currentTimeMillis() > expireTime;
    }

    /**
     * 是否已提交
     */
    public boolean isCommitted() {
        return transactionState == TransactionState.COMMIT || transactionState == TransactionState.END;
    }

    /**
     * 是否已回滚
     */
    public boolean isRolledBack() {
        return transactionState == TransactionState.ROLLBACK || transactionState == TransactionState.END;
    }

    /**
     * 获取状态描述
     */
    public String getStatusDesc() {
        return transactionState.name();
    }

    /**
     * 设置消费者组列表
     */
    public void setConsumerGroups(String groups) {
        this.consumerGroups = groups;
    }

    /**
     * 获取消费者组列表
     */
    public String[] getConsumerGroupList() {
        if (consumerGroups == null || consumerGroups.isEmpty()) {
            return new String[0];
        }
        return consumerGroups.split(",");
    }
}