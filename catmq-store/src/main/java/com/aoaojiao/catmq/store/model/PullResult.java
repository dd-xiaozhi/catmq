package com.aoaojiao.catmq.store.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 消息拉取结果
 *
 * @author DD
 */
@Data
@Builder
public class PullResult {

    /**
     * 拉取状态
     */
    private PullStatus status;

    /**
     * 下次拉取起始偏移
     */
    private long nextBeginOffset;

    /**
     * 队列最小偏移
     */
    private long minOffset;

    /**
     * 队列最大偏移
     */
    private long maxOffset;

    /**
     * 拉取到的消息列表
     */
    private List<Message> messages;

    /**
     * 拉取状态枚举
     */
    public enum PullStatus {
        /**
         * 找到消息
         */
        FOUND,
        /**
         * 没有新消息
         */
        NO_NEW_MSG,
        /**
         * 队列为空
         */
        NO_MSG_IN_QUEUE,
        /**
         * 偏移量非法
         */
        OFFSET_ILLEGAL
    }

    /**
     * 创建 FOUND 结果
     */
    public static PullResult found(long nextBeginOffset, long minOffset, long maxOffset, List<Message> messages) {
        return PullResult.builder()
                .status(PullStatus.FOUND)
                .nextBeginOffset(nextBeginOffset)
                .minOffset(minOffset)
                .maxOffset(maxOffset)
                .messages(messages)
                .build();
    }

    /**
     * 创建没有新消息结果
     */
    public static PullResult noNewMsg(long offset, long minOffset, long maxOffset) {
        return PullResult.builder()
                .status(PullStatus.NO_NEW_MSG)
                .nextBeginOffset(offset)
                .minOffset(minOffset)
                .maxOffset(maxOffset)
                .messages(java.util.Collections.emptyList())
                .build();
    }

    /**
     * 创建队列为空结果
     */
    public static PullResult noMsgInQueue() {
        return PullResult.builder()
                .status(PullStatus.NO_MSG_IN_QUEUE)
                .messages(java.util.Collections.emptyList())
                .build();
    }

    /**
     * 创建偏移量非法结果
     */
    public static PullResult offsetIllegal(long minOffset, long maxOffset) {
        return PullResult.builder()
                .status(PullStatus.OFFSET_ILLEGAL)
                .nextBeginOffset(minOffset)
                .minOffset(minOffset)
                .maxOffset(maxOffset)
                .messages(java.util.Collections.emptyList())
                .build();
    }
}
