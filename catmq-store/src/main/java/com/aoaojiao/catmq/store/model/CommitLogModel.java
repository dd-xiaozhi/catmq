package com.aoaojiao.catmq.store.model;

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
     * commitLog文件最大的写入范围
     */
    private Long offsetLimit;
}
