package com.aoaojiao.catmq.store.core;

import java.nio.MappedByteBuffer;

/**
 * MMap 文件模型
 *
 * @author DD
 */
public class MMapFileModel {

    private String topicName;
    private String currentCommitLogFileName;
    private String commitLogPath;
    private MappedByteBuffer mappedByteBuffer;

    public void loadFileInMMap(String topicName, int startOffset, int mappedSize) {
        this.topicName = topicName;

    }
}
