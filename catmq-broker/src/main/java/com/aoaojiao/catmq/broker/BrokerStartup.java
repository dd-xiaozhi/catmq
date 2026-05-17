package com.aoaojiao.catmq.broker;

import com.aoaojiao.catmq.broker.loader.CatmqTopicLoader;
import com.aoaojiao.catmq.broker.loader.ConsumeQueueOffsetLoader;
import com.aoaojiao.catmq.common.cache.CommonCache;
import com.aoaojiao.catmq.common.model.CatmqTopicModel;
import com.aoaojiao.catmq.store.config.MessageStoreConfig;
import com.aoaojiao.catmq.store.core.CommitLogAppendHandler;
import com.aoaojiao.catmq.store.core.ConsumerQueueManager;
import com.aoaojiao.catmq.store.core.DispatchMessageService;
import com.aoaojiao.catmq.store.core.PullService;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * Broker 启动类
 *
 * @author DD
 */
@Data
public class BrokerStartup {

    private static final Logger log = LoggerFactory.getLogger(BrokerStartup.class);

    private ConfigContext configContext;
    private CatmqTopicLoader catmqTopicLoader;
    private CommitLogAppendHandler commitLogAppendHandler;
    private ConsumeQueueOffsetLoader consumeQueueOffsetLoader;

    // 新增组件
    private ConsumerQueueManager consumerQueueManager;
    private DispatchMessageService dispatchMessageService;
    private PullService pullService;

    public void start() {
        log.info("Broker starting...");

        initConfigContext();

        initProperties();

        startTaskThread();

        dataPrepareLoad();

        log.info("Broker started successfully");
    }

    /**
     * 初始化加载配置文件并创建 ConfigContext
     */
    private void initConfigContext() {
        ConfigContext configContext = new ConfigContext();
        MessageStoreConfig messageStoreConfig = new MessageStoreConfig();
        configContext.setMessageStoreConfig(messageStoreConfig);

        this.configContext = configContext;
    }

    /**
     * 数据预加载
     */
    private void dataPrepareLoad() {
        loadCommitLog();
        loadConsumerQueue();
        initDispatchService();
    }

    /**
     * 预加载 commitLog 文件到 内存中
     */
    private void loadCommitLog() {
        this.commitLogAppendHandler = new CommitLogAppendHandler(this.configContext.getMessageStoreConfig());
        List<CatmqTopicModel> catmqTopicModelList = CommonCache.getCatmqTopicModelList();
        for (CatmqTopicModel catmqTopicModel : catmqTopicModelList) {
            try {
                this.commitLogAppendHandler.prepareLoadingToMMap(catmqTopicModel.getTopic());
            } catch (IOException e) {
                throw new RuntimeException("prepare load topic commitLog file error", e);
            }
        }
        log.info("Loaded {} topics CommitLog", catmqTopicModelList.size());
    }

    /**
     * 加载消费队列
     */
    private void loadConsumerQueue() {
        // 初始化 ConsumerQueueManager
        this.consumerQueueManager = new ConsumerQueueManager(this.configContext.getMessageStoreConfig());

        // 加载所有 Topic 的 ConsumerQueue
        List<CatmqTopicModel> topicList = CommonCache.getCatmqTopicModelList();
        for (CatmqTopicModel topic : topicList) {
            if (topic.getQueueModelList() != null) {
                consumerQueueManager.loadTopic(topic.getTopic(), topic.getQueueModelList());
            }
        }
        log.info("Loaded ConsumerQueue: {}", consumerQueueManager.size());

        // 初始化 PullService
        this.pullService = new PullService(
                this.configContext.getMessageStoreConfig(),
                this.consumerQueueManager,
                CommitLogAppendHandler.getCommitLogManager()
        );
    }

    /**
     * 初始化分发服务
     */
    private void initDispatchService() {
        // 创建并启动分发服务
        this.dispatchMessageService = new DispatchMessageService(
                this.configContext.getMessageStoreConfig(),
                this.consumerQueueManager
        );
        this.dispatchMessageService.start();

        // 将分发服务注入到 CommitLogAppendHandler
        this.commitLogAppendHandler.setDispatchMessageService(this.dispatchMessageService);

        log.info("DispatchMessageService started");
    }

    /**
     * 初始化配置信息
     */
    private void initProperties() {
        this.catmqTopicLoader = new CatmqTopicLoader(this.configContext.getMessageStoreConfig());
        this.catmqTopicLoader.load();

        this.consumeQueueOffsetLoader = new ConsumeQueueOffsetLoader(this.configContext.getMessageStoreConfig());
        this.consumeQueueOffsetLoader.load();
    }

    /**
     * 启动任务线程
     */
    private void startTaskThread() {
        this.catmqTopicLoader.startFlushThread();
        this.consumeQueueOffsetLoader.startFlushThread();
    }

    private void prepareCommitLogFileInMMap() {

    }

    /**
     * 关闭 Broker
     */
    public void shutdown() {
        log.info("Broker shutting down...");

        // 关闭分发服务
        if (dispatchMessageService != null) {
            dispatchMessageService.shutdown();
        }

        // 关闭 ConsumerQueueManager
        if (consumerQueueManager != null) {
            consumerQueueManager.shutdown();
        }

        log.info("Broker shutdown completed");
    }

    public static void main(String[] args) {
        BrokerStartup broker = new BrokerStartup();
        try {
            broker.start();

            // 保持主线程运行
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("Broker interrupted");
        } finally {
            broker.shutdown();
        }
    }
}
