package com.aoaojiao.catmq.broker;

import com.aoaojiao.catmq.broker.loader.TopicConfigLoader;
import com.aoaojiao.catmq.common.cache.CommonCache;
import com.aoaojiao.catmq.common.model.CatmqTopicModel;
import com.aoaojiao.catmq.store.config.MessageStoreConfig;
import com.aoaojiao.catmq.store.core.CommitLogAppendHandler;
import lombok.Data;

import java.io.IOException;

/**
 * @author DD
 * <p>
 * broker 启动类
 */
@Data
public class BrokerStartup {

    public ConfigContext configContext;
    private TopicConfigLoader topicConfigLoader;
    private CommitLogAppendHandler commitLogAppendHandler;

    public void start() {
        // 加载配置文件
        ConfigContext configContext = loadConfigFile();
        // 预加载 commitLog
        prepareLoadFile(configContext.getMessageStoreConfig());
    }

    private ConfigContext loadConfigFile() {
        ConfigContext configContext = new ConfigContext();
        MessageStoreConfig messageStoreConfig = new MessageStoreConfig();
        configContext.setMessageStoreConfig(messageStoreConfig);

        this.configContext = configContext;
        return configContext;
    }

    private void prepareLoadFile(MessageStoreConfig messageStoreConfig) {
        this.commitLogAppendHandler = new CommitLogAppendHandler(messageStoreConfig);
        // 加载 queue 信息 (需要提前创建 topic)
        loadTopicConfig(messageStoreConfig);
        // 预加载 commitLog 文件到内存中
        prepareCommitLogFileInMMap();
    }

    private void prepareCommitLogFileInMMap() {
        for (CatmqTopicModel catmqTopicModel : CommonCache.CATMQ_TOPIC_MODEL_CACHE) {
            try {
                this.commitLogAppendHandler.prepareLoadingToMMap(catmqTopicModel.getTopic());
            } catch (IOException e) {
                throw new RuntimeException("prepare load topic commitLog file error", e);
            }
        }
    }

    private void loadTopicConfig(MessageStoreConfig messageStoreConfig) {
        this.topicConfigLoader = new TopicConfigLoader(messageStoreConfig);
        this.topicConfigLoader.loadTopicInfo();
        topicConfigLoader.startFlushTopicInfoThread();
    }

    public static void main(String[] args) {
        new BrokerStartup().start();
        
    }

}
