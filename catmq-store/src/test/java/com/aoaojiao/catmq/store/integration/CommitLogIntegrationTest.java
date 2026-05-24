package com.aoaojiao.catmq.store.integration;

import com.aoaojiao.catmq.common.cache.CommonCache;
import com.aoaojiao.catmq.common.model.CatmqTopicModel;
import com.aoaojiao.catmq.common.model.CommitLogModel;
import com.aoaojiao.catmq.common.model.QueueModel;
import com.aoaojiao.catmq.store.config.MessageStoreConfig;
import com.aoaojiao.catmq.store.core.CommitLogAppendHandler;
import com.aoaojiao.catmq.store.model.AppendResult;
import com.aoaojiao.catmq.store.model.Message;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * CommitLog 集成测试
 * 测试消息写入 CommitLog 并读取的完整流程
 *
 * @author DD
 */
public class CommitLogIntegrationTest {

    private static final String TEST_TOPIC = "commitlog_test_topic";
    private static final String STORE_PATH = "D:/Work/project/catmq/catmq/store/test";
    private static MessageStoreConfig messageStoreConfig;
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
        if (!testDir.exists()) {
            testDir.mkdirs();
        }

        // 初始化配置
        messageStoreConfig = new MessageStoreConfig();
        messageStoreConfig.setStorePathRootDir(STORE_PATH);

        // 初始化 CommitLogAppendHandler
        commitLogAppendHandler = new CommitLogAppendHandler(messageStoreConfig);
        commitLogAppendHandler.prepareLoadingToMMap(TEST_TOPIC);
    }

    @AfterClass
    public static void tearDownClass() {
        CommonCache.setCatmqTopicModelCache(new ArrayList<>());
    }

    @Test
    public void testAppendMessage() throws IOException {
        // 写入消息
        String testMessage = "Hello CommitLog Test Message";
        byte[] messageBody = testMessage.getBytes();

        AppendResult result = commitLogAppendHandler.appendMessage(TEST_TOPIC, 0, Message.createSimpleMessage(messageBody));

        assertTrue("写入应该成功", result.isSuccess());
        assertTrue("写入偏移量应该大于等于0", result.getPhysicalOffset() >= 0);
        System.out.println("消息写入成功，偏移量: " + result.getPhysicalOffset());
    }

    @Test
    public void testAppendMultipleMessages() throws InterruptedException {
        int messageCount = 10;
        CountDownLatch latch = new CountDownLatch(messageCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < messageCount; i++) {
            final int index = i;
            new Thread(() -> {
                try {
                    String message = "TestMessage-" + index;
                    AppendResult result = commitLogAppendHandler.appendMessage(TEST_TOPIC, 0, Message.createSimpleMessage(message.getBytes()));
                    if (result.isSuccess()) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        latch.await(10, TimeUnit.SECONDS);
        assertEquals("所有消息应该写入成功", messageCount, successCount.get());
        System.out.println("成功写入 " + successCount.get() + " 条消息");
    }

    @Test
    public void testMessagePersistence() throws IOException {
        // 先写入一条消息
        String testMessage = "Persistence Test Message";
        byte[] messageBody = testMessage.getBytes();

        AppendResult result1 = commitLogAppendHandler.appendMessage(TEST_TOPIC, 0, Message.createSimpleMessage(messageBody));

        // 再写入一条
        String testMessage2 = "Second Persistence Test Message";
        byte[] messageBody2 = testMessage2.getBytes();
        AppendResult result2 = commitLogAppendHandler.appendMessage(TEST_TOPIC, 0, Message.createSimpleMessage(messageBody2));

        // 验证偏移量递增
        assertTrue("第二条消息的偏移量应该大于第一条", result2.getPhysicalOffset() > result1.getPhysicalOffset());
        System.out.println("消息偏移量: offset1=" + result1.getPhysicalOffset() + ", offset2=" + result2.getPhysicalOffset());
    }

    @Test
    public void testTopicCountInCache() {
        // 验证 CommonCache 中的 Topic 数量
        List<CatmqTopicModel> topics = CommonCache.getCatmqTopicModelList();
        assertNotNull("Topic 列表不应该为空", topics);
        assertTrue("至少应该有测试 Topic", topics.size() >= 1);

        // 查找测试 Topic
        CatmqTopicModel found = null;
        for (CatmqTopicModel topic : topics) {
            if (TEST_TOPIC.equals(topic.getTopic())) {
                found = topic;
                break;
            }
        }
        assertNotNull("测试 Topic 应该存在", found);
        assertNotNull("CommitLogModel 不应该为空", found.getCommitLogModel());
        System.out.println("找到测试 Topic: " + found.getTopic());
    }

    @Test
    public void testCommitLogFileCreation() {
        // 验证 CommitLog 文件已创建
        File commitLogFile = new File(STORE_PATH + "/commitLog/" + TEST_TOPIC + "/00000000");
        assertTrue("CommitLog 文件应该存在", commitLogFile.exists());
        assertTrue("CommitLog 文件大小应该大于0", commitLogFile.length() > 0);
        System.out.println("CommitLog 文件大小: " + commitLogFile.length() + " bytes");
    }
}