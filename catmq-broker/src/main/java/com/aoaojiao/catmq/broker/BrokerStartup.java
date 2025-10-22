package com.aoaojiao.catmq.broker;

import com.aoaojiao.catmq.broker.loader.TopicConfigLoader;
import com.aoaojiao.catmq.store.config.MessageStoreConfig;
import lombok.Data;

/**
 * @author DD
 * <p>
 * broker 启动类
 */
@Data
public class BrokerStartup {

    public ConfigContext configContext;
    private TopicConfigLoader topicConfigLoader;

    public void start() {
        // 加载配置文件
        ConfigContext configContext = loadConfigFile();
        // 启动预加载
        prepareLoading(configContext.getMessageStoreConfig());
    }

    private ConfigContext loadConfigFile() {
        ConfigContext configContext = new ConfigContext();
        MessageStoreConfig messageStoreConfig = new MessageStoreConfig();
        configContext.setMessageStoreConfig(messageStoreConfig);

        this.configContext = configContext;
        return configContext;
    }

    private void prepareLoading(MessageStoreConfig messageStoreConfig) {
        // 加载 queue 信息 (需要提前创建 topic)
        this.topicConfigLoader = new TopicConfigLoader(messageStoreConfig);
        this.topicConfigLoader.loadTopicInfo();
        topicConfigLoader.startFlushTopicInfoThread();
    }

    public static void main(String[] args) {
        new BrokerStartup().start();
    }

}
