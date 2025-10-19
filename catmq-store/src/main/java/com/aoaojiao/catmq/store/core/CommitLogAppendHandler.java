package com.aoaojiao.catmq.store.core;

import java.util.HashMap;

/**
 * @author DD
 * <p>
 * CommitLog 消息追加处理器
 * 负责将消息追加到 commitLog 文件中，同时对commitLog 文件进行分割处理
 */
public class CommitLogAppendHandler {

    /**
     * MMapFileModel 缓存
     */
    private final static HashMap<String, MMapFileModel> mMapFileModelMap = new HashMap<>();


}
