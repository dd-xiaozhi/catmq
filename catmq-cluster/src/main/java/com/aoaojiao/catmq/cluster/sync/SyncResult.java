package com.aoaojiao.catmq.cluster.sync;

import lombok.Data;

/**
 * 同步结果
 * 描述同步操作的结果
 *
 * @author DD
 */
@Data
public class SyncResult {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 确认的从节点数量
     */
    private int ackCount;

    /**
     * 同步耗时（毫秒）
     */
    private long costMs;

    /**
     * 同步模式
     */
    private String syncMode;

    /**
     * 时间戳
     */
    private long timestamp;

    /**
     * 创建一个成功的同步结果
     *
     * @param ackCount 确认数量
     * @param costMs   耗时
     * @param syncMode 同步模式
     * @return 同步结果
     */
    public static SyncResult success(int ackCount, long costMs, String syncMode) {
        SyncResult result = new SyncResult();
        result.setSuccess(true);
        result.setAckCount(ackCount);
        result.setCostMs(costMs);
        result.setSyncMode(syncMode);
        result.setTimestamp(System.currentTimeMillis());
        return result;
    }

    /**
     * 创建一个失败的同步结果
     *
     * @param errorMessage 错误信息
     * @return 同步结果
     */
    public static SyncResult failure(String errorMessage) {
        SyncResult result = new SyncResult();
        result.setSuccess(false);
        result.setErrorMessage(errorMessage);
        result.setTimestamp(System.currentTimeMillis());
        return result;
    }

    /**
     * 创建一个成功的异步同步结果
     *
     * @return 同步结果
     */
    public static SyncResult asyncSuccess() {
        return success(0, 0, "ASYNC");
    }
}