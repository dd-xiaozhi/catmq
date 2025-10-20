package com.aoaojiao.catmq.store.core;

/**
 * CommitLog 消息追加处理器
 * 负责对 CommitLog 追加消息与创建
 *
 * @author DD
 */
public class CommitLogAppendHandler {

    /**
     * MMapFileModel 缓存
     */
    private final static CommitLogFileModeManager commitLogFileModeManager = new CommitLogFileModeManager();


}
