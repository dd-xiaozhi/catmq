package com.aoaojiao.catmq.store.config;

import com.aoaojiao.catmq.store.constants.StoreConstant;
import lombok.Data;

import java.io.File;

/**
 * @author DD
 *
 * 消息存储配置
 */
@Data
public class MessageStoreConfig {

    // 保存 commitLog 文件数据的根目录
    // TODO 先写死，后面修改从环境变量或者系统属性中获取
     private String storePathRootDir = "/Users/xiaozhi/work/java/project/catmq/catmq/store";
//    private String storePathRootDir = "D:\\Work\\project\\catmq\\catmq\\store";
    private String commitLogDirPath = storePathRootDir + File.separator + "commitLog";
    private String topicInfoFilePath = storePathRootDir + File.separator + "catmq-topic.json";
    private String consumeQueueOffsetFilePath = storePathRootDir + File.separator + "consume-queue-offset.json";

    // 消息 commitLog 文件大小，默认 1GB TODO 可配置
    private Integer messageCommitLogFileSize = StoreConstant.DEFAULT_MESSAGE_COMMIT_LOG_FILE_SIZE;

    // consumeQueue commitLog 文件大小，默认 12MB TODO 可配置
    private Integer consumeQueueCommitLogFileSize = StoreConstant.DEFAULT_CONSUME_QUEUE_COMMIT_LOG_FILE_SIZE;

}