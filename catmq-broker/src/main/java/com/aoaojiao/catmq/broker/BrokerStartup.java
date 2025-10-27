package com.aoaojiao.catmq.broker;

import com.aoaojiao.catmq.broker.loader.CatmqTopicLoader;
import com.aoaojiao.catmq.broker.loader.ConsumeQueueOffsetLoader;
import com.aoaojiao.catmq.common.cache.CommonCache;
import com.aoaojiao.catmq.common.model.CatmqTopicModel;
import com.aoaojiao.catmq.store.config.MessageStoreConfig;
import com.aoaojiao.catmq.store.core.CommitLogAppendHandler;
import lombok.Data;

import java.io.IOException;
import java.util.List;

/**
 * @author DD
 * <p>
 * broker 启动类
 */
@Data
public class BrokerStartup {

    private ConfigContext configContext;
    private CatmqTopicLoader catmqTopicLoader;
    private ConsumeQueueOffsetLoader consumeQueueOffsetLoader;
    private CommitLogAppendHandler commitLogAppendHandler;

    public void start() {
        initConfigContext();
        initProperties();
        startTaskThread();
        dataPrepareLoad();
    }

    /**
     * 初始化加载配置文件并创建 ConfigContext
     */
    private ConfigContext initConfigContext() {
        ConfigContext configContext = new ConfigContext();
        MessageStoreConfig messageStoreConfig = new MessageStoreConfig();
        configContext.setMessageStoreConfig(messageStoreConfig);

        this.configContext = configContext;
        return configContext;
    }

    /**
     * 数据预加载
     */
    private void dataPrepareLoad() {
        loadCommitLogFile();
        loadQueueCommitLogFile();
    }

    /**
     * 预加载 commitLog 文件到 内存中
     */
    private void loadCommitLogFile() {
        this.commitLogAppendHandler = new CommitLogAppendHandler(this.configContext.getMessageStoreConfig());
        List<CatmqTopicModel> catmqTopicModelList = CommonCache.getCatmqTopicModelList();
        for (CatmqTopicModel catmqTopicModel : catmqTopicModelList) {
            try {
                this.commitLogAppendHandler.prepareLoadingToMMap(catmqTopicModel.getTopic());
            } catch (IOException e) {
                throw new RuntimeException("prepare load topic commitLog file error", e);
            }
        }
    }

    /**
     * 加载队列索引
     */
    private void loadQueueCommitLogFile() {

    }

    /**
     * 初始化配置信息
     */
    private void initProperties() {
        this.catmqTopicLoader = new CatmqTopicLoader(this.configContext.getMessageStoreConfig());
        this.catmqTopicLoader.loadTopicInfo();

        this.consumeQueueOffsetLoader = new ConsumeQueueOffsetLoader(this.configContext.getMessageStoreConfig());
        this.consumeQueueOffsetLoader.loadConsumeQueueOffsetInfo();
    }

    /**
     * 启动任务线程
     */
    private void startTaskThread() {
        this.catmqTopicLoader.startFlushTopicInfoThread();
        this.consumeQueueOffsetLoader.startConsumeQueueOffsetInfoThread();
    }

    private void prepareCommitLogFileInMMap() {

    }

    public static void main(String[] args) {
        new BrokerStartup().start();
    }

}
