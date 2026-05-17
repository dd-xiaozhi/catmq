package com.aoaojiao.catmq.test;

import com.aoaojiao.catmq.broker.BrokerStartup;
import com.aoaojiao.catmq.broker.ConfigContext;
import com.aoaojiao.catmq.store.config.MessageStoreConfig;
import com.aoaojiao.catmq.store.core.CommitLogAppendHandler;
import org.junit.Before;
import org.junit.Test;
import org.junit.Ignore;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * @author DD
 */
public class BrocketStartUpTest {

    private BrokerStartup brokerStartup;
    private ConfigContext configContext;

    @Before
    public void before() {
        ConfigContext configContext = new ConfigContext();
        MessageStoreConfig messageStoreConfig = new MessageStoreConfig();
        configContext.setMessageStoreConfig(messageStoreConfig);
        this.configContext = configContext;
        this.brokerStartup = new BrokerStartup();
        brokerStartup.setConfigContext(configContext);
    }

    @Test
    @Ignore("需要真实 broker 环境，运行集成测试时启用")
    public void brokerStartupStartTest() throws InterruptedException, IOException, ClassNotFoundException {
        brokerStartup.start();
        CommitLogAppendHandler commitLogAppendHandler = new CommitLogAppendHandler(this.configContext.getMessageStoreConfig());
        for (int i = 0; i < 60; i++) {
            commitLogAppendHandler.appendMessage("order_pay_topic", ("i am xiaozhi" + i).getBytes());
            TimeUnit.SECONDS.sleep(1);
        }
    }
}
