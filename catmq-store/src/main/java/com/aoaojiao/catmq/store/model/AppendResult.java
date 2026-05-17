package com.aoaojiao.catmq.store.model;

import lombok.Builder;
import lombok.Data;

/**
 * 消息追加结果
 *
 * @author DD
 */
@Data
@Builder
public class AppendResult {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * CommitLog 物理偏移量
     */
    private long physicalOffset;

    /**
     * 消息大小
     */
    private int size;

    /**
     * 错误信息
     */
    private String errorMsg;

    /**
     * 创建成功结果
     */
    public static AppendResult success(long physicalOffset, int size) {
        return AppendResult.builder()
                .success(true)
                .physicalOffset(physicalOffset)
                .size(size)
                .build();
    }

    /**
     * 创建失败结果
     */
    public static AppendResult fail(String errorMsg) {
        return AppendResult.builder()
                .success(false)
                .errorMsg(errorMsg)
                .build();
    }
}
