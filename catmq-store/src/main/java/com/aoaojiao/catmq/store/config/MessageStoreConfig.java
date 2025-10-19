package com.aoaojiao.catmq.store.config;

/**
 * @author DD
 *
 * 消息存储配置
 */
public class MessageStoreConfig {

    // 保存 commitLog 文件数据的根目录
    // TODO 先写死，后面修改从环境变量或者系统属性中获取
    private String storePathRootDir = "D:\\Work\\project\\catmq\\catmq\\store";
}
