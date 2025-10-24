package com.aoaojiao.catmq.test;


import com.aoaojiao.catmq.broker.loader.CatmqTopicLoader;
import com.aoaojiao.catmq.store.config.MessageStoreConfig;
import com.aoaojiao.catmq.store.core.CommitLogAppendHandler;
import com.aoaojiao.catmq.store.model.MessageModel;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

/**
 * @author DD
 */
public class CommitLogAppendHandlerTest {

    private MessageStoreConfig messageStoreConfig;

    @Before
    public void init() throws IOException {
        this.messageStoreConfig = new MessageStoreConfig();
        messageStoreConfig.setStorePathRootDir("D:\\Work\\project\\catmq\\catmq\\store");
        CatmqTopicLoader catmqTopicLoader = new CatmqTopicLoader(messageStoreConfig);
        catmqTopicLoader.loadTopicInfo();
    }

    @Test
    public void test() throws IOException, ClassNotFoundException {
        CommitLogAppendHandler commitLogAppendHandler = new CommitLogAppendHandler(messageStoreConfig);
        String topic = "order_pay_topic";
        commitLogAppendHandler.prepareLoadingToMMap(topic);
        byte[] content = "i am xiaozhi".getBytes();
        commitLogAppendHandler.appendMessage(topic, content);
        MessageModel messageModel = commitLogAppendHandler.readMessage(topic, 0, content.length);
        System.out.println("读取消息: " + new String(messageModel.getContent()));
    }
}
