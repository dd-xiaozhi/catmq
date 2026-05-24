package com.aoaojiao.catmq.store.integration;

import com.aoaojiao.catmq.common.cache.CommonCache;
import com.aoaojiao.catmq.common.model.CatmqTopicModel;
import com.aoaojiao.catmq.common.model.CommitLogModel;
import com.aoaojiao.catmq.common.model.QueueModel;
import com.aoaojiao.catmq.store.config.MessageStoreConfig;
import com.aoaojiao.catmq.store.core.CommitLogAppendHandler;
import com.aoaojiao.catmq.store.core.ConsumerQueue;
import com.aoaojiao.catmq.store.core.ConsumerQueueManager;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * ConsumerQueue 集成测试
 * 测试消费队列的创建和基本操作
 *
 * @author DD
 */
public class ConsumerQueueIntegrationTest {

    private static final String TEST_TOPIC = "cq_test_topic";
    private static final String STORE_PATH = "D:/Work/project/catmq/catmq/store/test";
    private static MessageStoreConfig messageStoreConfig;
    private static ConsumerQueueManager consumerQueueManager;
    private static CommitLogAppendHandler commitLogAppendHandler;

    @BeforeClass
    public static void setUpClass() throws Exception {
        // 清理测试数据
        CommonCache.setCatmqTopicModelCache(new ArrayList<>());

        // 创建测试 Topic
        CatmqTopicModel topicModel = new CatmqTopicModel();
        topicModel.setTopic(TEST_TOPIC);
        topicModel.setCommitLogModel(new CommitLogModel());
        topicModel.getCommitLogModel().setFilename("00000000");
        topicModel.getCommitLogModel().setOffset(new AtomicInteger(0));
        topicModel.getCommitLogModel().setOffsetLimit(100);

        List<QueueModel> queueModels = new ArrayList<>();
        QueueModel queueModel = new QueueModel();
        queueModel.setId(0);
        queueModel.setMinOffset(0L);
        queueModel.setMaxOffset(0L);
        queueModel.setCurrentOffset(0L);
        queueModels.add(queueModel);
        topicModel.setQueueModelList(queueModels);

        CommonCache.getCatmqTopicModelList().add(topicModel);

        // 创建测试目录
        File testDir = new File(STORE_PATH + "/commitLog/" + TEST_TOPIC);
        File cqDir = new File(STORE_PATH + "/consumeQueue/" + TEST_TOPIC);
        if (!testDir.exists()) {
            testDir.mkdirs();
        }
        if (!cqDir.exists()) {
            cqDir.mkdirs();
        }

        // 初始化配置
        messageStoreConfig = new MessageStoreConfig();
        messageStoreConfig.setStorePathRootDir(STORE_PATH);

        // 初始化 CommitLogAppendHandler
        commitLogAppendHandler = new CommitLogAppendHandler(messageStoreConfig);
        commitLogAppendHandler.prepareLoadingToMMap(TEST_TOPIC);

        // 初始化 ConsumerQueueManager
        consumerQueueManager = new ConsumerQueueManager(messageStoreConfig);
        consumerQueueManager.loadTopic(TEST_TOPIC, queueModels);
    }

    @AfterClass
    public static void tearDownClass() {
        CommonCache.setCatmqTopicModelCache(new ArrayList<>());
    }

    @Test
    public void testConsumerQueueCreation() {
        // 验证 ConsumerQueue 已创建
        ConsumerQueue cq = consumerQueueManager.getOrCreate(TEST_TOPIC, 0);
        assertNotNull("ConsumerQueue 不应该为空", cq);
        System.out.println("ConsumerQueue 创建成功");
    }

    @Test
    public void testConsumerQueueFileExists() {
        // 验证 ConsumerQueue 目录已创建
        File cqDir = new File(STORE_PATH + "/consumeQueue/" + TEST_TOPIC);
        assertTrue("ConsumerQueue 目录应该存在", cqDir.exists());
        System.out.println("ConsumerQueue 目录已创建");
    }

    @Test
    public void testGetQueueInfo() {
        // 验证队列信息
        ConsumerQueue cq = consumerQueueManager.getOrCreate(TEST_TOPIC, 0);
        assertNotNull("ConsumerQueue 不应该为空", cq);

        // 获取队列信息
        int maxIndex = cq.getMaxIndexCount();
        System.out.println("队列最大索引: " + maxIndex);
        assertTrue("最大索引应该大于等于0", maxIndex >= 0);
    }

    @Test
    public void testTopicLoading() {
        // 验证 Topic 加载
        assertTrue("Topic 应该已加载", consumerQueueManager.contains(TEST_TOPIC, 0));

        Collection<ConsumerQueue> queues = consumerQueueManager.getByTopic(TEST_TOPIC);
        assertNotNull("ConsumerQueue 列表不应该为空", queues);
        assertFalse("ConsumerQueue 列表不应该为空", queues.isEmpty());
        System.out.println("Topic 加载成功，包含 " + queues.size() + " 个队列");
    }
}