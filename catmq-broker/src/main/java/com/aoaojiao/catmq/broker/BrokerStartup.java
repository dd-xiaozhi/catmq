package com.aoaojiao.catmq.broker;

import com.aoaojiao.catmq.broker.loader.TopicConfigLoader;
import com.aoaojiao.catmq.store.config.MessageStoreConfig;

import java.io.IOException;

/**
 * @author DD
 * <p>
 * broker 启动类
 */
public class BrokerStartup {

    public static void main(String[] args) {
        // 初始化配置
        ConfigContext configContext = loadConfigFile();
        prepareLoading(configContext.getMessageStoreConfig());
    }

    private static ConfigContext loadConfigFile() {
        ConfigContext configContext = new ConfigContext();
        MessageStoreConfig messageStoreConfig = new MessageStoreConfig();
        configContext.setMessageStoreConfig(messageStoreConfig);
        return configContext;
    }

    private static void prepareLoading(MessageStoreConfig messageStoreConfig) {
        // 加载 queue 信息 (需要提前创建 topic)
        TopicConfigLoader topicConfigLoader = new TopicConfigLoader(messageStoreConfig);
        try {
            topicConfigLoader.loadTopicInfo();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
