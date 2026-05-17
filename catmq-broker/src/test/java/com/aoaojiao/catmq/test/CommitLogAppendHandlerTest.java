package com.aoaojiao.catmq.test;


import com.aoaojiao.catmq.broker.loader.CatmqTopicLoader;
import com.aoaojiao.catmq.store.config.MessageStoreConfig;
import com.aoaojiao.catmq.store.core.CommitLogAppendHandler;
import com.aoaojiao.catmq.store.model.Message;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * @author DD
 */
public class CommitLogAppendHandlerTest {

    private MessageStoreConfig messageStoreConfig;

    @Before
    public void init() {
        this.messageStoreConfig = new MessageStoreConfig();
        CatmqTopicLoader catmqTopicLoader = new CatmqTopicLoader(messageStoreConfig);
        catmqTopicLoader.loadTopicInfo();
    }

    @Test
    @Ignore("需要真实文件系统环境，运行集成测试时启用")
    public void test() throws IOException, ClassNotFoundException, InterruptedException {
        CommitLogAppendHandler commitLogAppendHandler = new CommitLogAppendHandler(messageStoreConfig);
        String topic = "order_pay_topic";
        commitLogAppendHandler.prepareLoadingToMMap(topic);
        byte[] content = "i am xiaozhi".getBytes();
        commitLogAppendHandler.appendMessage(topic, content);
        Message message = commitLogAppendHandler.readMessage(topic, 0, content.length);
        System.out.println("读取消息: " + new String(message.getBody()));
        TimeUnit.SECONDS.sleep(6);
    }
}