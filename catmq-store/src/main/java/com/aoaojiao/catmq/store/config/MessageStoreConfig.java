package com.aoaojiao.catmq.store.config;

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
    // private String storePathRootDir = "/Users/xiaozhi/work/java/project/catmq/catmq/store";
    private String storePathRootDir = "D:\\Work\\project\\catmq\\catmq\\store";
    private String commitLogDirPath = storePathRootDir + File.separator + "commitLog";
    private String topicInfoFilePath = storePathRootDir + File.separator + "catmq-topic.json";
    private String consumeQueueOffsetFilePath = storePathRootDir + File.separator + "consume-queue-offset.json";

    /**
     * 单个 commitLog 文件的大小
     */
    private Integer commitLogFileSize = 100;

}