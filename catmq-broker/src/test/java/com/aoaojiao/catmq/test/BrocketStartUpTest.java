package com.aoaojiao.catmq.test;

import com.aoaojiao.catmq.broker.BrokerStartup;
import com.aoaojiao.catmq.broker.ConfigContext;
import com.aoaojiao.catmq.store.config.MessageStoreConfig;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

/**
 * @author DD
 */
public class BrocketStartUpTest {

    private BrokerStartup brokerStartup;


    @Before
    public void before() {
        ConfigContext configContext = new ConfigContext();
        MessageStoreConfig messageStoreConfig = new MessageStoreConfig();
        configContext.setMessageStoreConfig(messageStoreConfig);
        this.brokerStartup = new BrokerStartup();
        brokerStartup.setConfigContext(configContext);
    }

    @Test
    public void brokerStartupStartTest() throws InterruptedException {
        brokerStartup.start();
        TimeUnit.SECONDS.sleep(60);
    }
}
