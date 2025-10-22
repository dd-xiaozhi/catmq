package com.aoaojiao.catmq.common.model;

import lombok.Data;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author DD
 */
@Data
public class CommitLogModel {

    /**
     * 最新的 commitLog 文件名
     */
    private String filename;

    /**
     * 最新的commitLog文件写入地址
     */
    private AtomicInteger offset;

    /**
     * commitLog文件最大的写入范围, MMap 可以申请的最大内存是 Integer 的最大值
     */
    private Integer offsetLimit;

    public int addOffset(int offset) {
        return this.offset.addAndGet(offset);
    }

    public int offsetDiff() {
        return this.offsetLimit - this.offset.get();
    }
}
