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

    // ConsumerQueue 每条索引大小：physicalOffset(8字节) + size(4字节) + tagCode(8字节) = 20字节
    public static final int CQ_INDEX_SIZE = 20;

    // 消息头魔数
    public static final int MESSAGE_MAGIC_CODE = 0x43414D51;  // "CAMQ" 的 ASCII

    // 最大单条消息大小 (4MB)
    public static final int MAX_MESSAGE_SIZE = 4 * StorageUnits.MEGABYTE;
}
