package com.aoaojiao.catmq.store.config;

import lombok.Data;

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

}