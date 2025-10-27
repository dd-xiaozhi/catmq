package com.aoaojiao.catmq.store.constants;

/**
 * @author DD
 */
public class StoreConstant {

    public static final String CATMQ_HOME = "CATMQ_HOME";

    // 消息 commitLog file 默认存储大小 (1GB)
    public static final int DEFAULT_MESSAGE_COMMIT_LOG_FILE_SIZE = StorageUnits.GIGABYTE;

    // consumeQueue commitLog file 默认存储大小 (12MB)
    public static final int DEFAULT_CONSUME_QUEUE_COMMIT_LOG_FILE_SIZE = 12 * StorageUnits.MEGABYTE;
}
