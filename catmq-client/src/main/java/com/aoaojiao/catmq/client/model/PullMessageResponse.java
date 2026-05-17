package com.aoaojiao.catmq.client.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 拉取消息响应
 *
 * @author DD
 */
@Data
@Builder
public class PullMessageResponse {

    /**
     * 拉取状态
     */
    private PullStatus status;

    /**
     * 下次拉取起始偏移
     */
    private long nextBeginOffset;

    /**
     * 最小偏移
     */
    private long minOffset;

    /**
     * 最大偏移
     */
    private long maxOffset;

    /**
     * 消息列表
     */
    private List<PullMessage> messages;

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
     * 判断是否成功
     */
    public boolean isSuccess() {
        return status == PullStatus.FOUND;
    }
}