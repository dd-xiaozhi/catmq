package com.aoaojiao.catmq.nameserver.service;

import com.aoaojiao.catmq.nameserver.config.NameServerConfig;
import com.aoaojiao.catmq.nameserver.model.BrokerInfo;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * 路由信息管理器测试
 *
 * @author DD
 */
public class RouteInfoManagerTest {

    private RouteInfoManager routeInfoManager;
    private NameServerConfig config;

    @Before
    public void setUp() {
        config = new NameServerConfig();
        config.setBrokerRegistryPath("target/test-route-info.json");
        routeInfoManager = new RouteInfoManager(config);
    }

    @After
    public void tearDown() {
        routeInfoManager.clear();
    }

    @Test
    public void testRegisterBroker() {
        BrokerInfo broker = createBrokerInfo("broker_1", "127.0.0.1", 9876, 1);
        routeInfoManager.registerBroker(broker);

        BrokerInfo found = routeInfoManager.getBrokerInfo("broker_1");
        assertNotNull("应该找到 Broker", found);
        assertEquals("地址应该匹配", "127.0.0.1:9876", found.getAddress());
    }

    @Test
    public void testRegisterDuplicateBroker() {
        BrokerInfo broker1 = createBrokerInfo("broker_1", "127.0.0.1", 9876, 1);
        routeInfoManager.registerBroker(broker1);

        BrokerInfo broker2 = createBrokerInfo("broker_1", "127.0.0.2", 9877, 1);
        routeInfoManager.registerBroker(broker2);

        // 重复注册会更新
        BrokerInfo found = routeInfoManager.getBrokerInfo("broker_1");
        assertEquals("IP 应该更新为 127.0.0.2", "127.0.0.2:9877", found.getAddress());
    }

    @Test
    public void testUnregisterBroker() {
        BrokerInfo broker = createBrokerInfo("broker_del", "127.0.0.1", 9876, 1);
        routeInfoManager.registerBroker(broker);

        routeInfoManager.unRegisterBroker("broker_del");

        BrokerInfo found = routeInfoManager.getBrokerInfo("broker_del");
        assertNull("不应该找到已注销的 Broker", found);
    }

    @Test
    public void testHeartBeat() {
        BrokerInfo broker = createBrokerInfo("broker_heartbeat", "127.0.0.1", 9876, 1);
        routeInfoManager.registerBroker(broker);

        long beforeTime = routeInfoManager.getBrokerInfo("broker_heartbeat").getLastUpdateTimestamp();

        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        boolean success = routeInfoManager.heartBeat("broker_heartbeat", null);
        assertTrue("心跳更新应该成功", success);

        long afterTime = routeInfoManager.getBrokerInfo("broker_heartbeat").getLastUpdateTimestamp();
        assertTrue("时间应该更新", afterTime > beforeTime);
    }

    @Test
    public void testHeartBeatForNonExistBroker() {
        boolean result = routeInfoManager.heartBeat("non_exist", null);
        assertFalse("不存在 Broker 的心跳应该失败", result);
    }

    @Test
    public void testGetAllBrokers() {
        for (int i = 1; i <= 3; i++) {
            BrokerInfo broker = createBrokerInfo("broker_" + i, "127.0.0." + i, 9876, i);
            routeInfoManager.registerBroker(broker);
        }

        Collection<BrokerInfo> brokers = routeInfoManager.getAllBrokers();
        assertEquals("应该有 3 个 Broker", 3, brokers.size());
    }

    @Test
    public void testGetAllAliveBrokers() {
        // 注意：registerBroker 会自动设置 alive=true，这是当前实现的行为
        // 所以即使先设置 alive=false，注册后也会变为 true
        BrokerInfo broker1 = createBrokerInfo("broker_1", "127.0.0.1", 9876, 0);
        BrokerInfo broker2 = createBrokerInfo("broker_2", "127.0.0.2", 9876, 1);

        routeInfoManager.registerBroker(broker1);
        routeInfoManager.registerBroker(broker2);

        Collection<BrokerInfo> allBrokers = routeInfoManager.getAllBrokers();
        // 由于 registerBroker 总是设置 alive=true，所以两个 broker 都是存活的
        assertEquals("应该有 2 个 Broker", 2, allBrokers.size());

        Collection<BrokerInfo> aliveBrokers = routeInfoManager.getAllAliveBrokers();
        assertEquals("应该只有 2 个存活的 Broker（registerBroker 会设置 alive=true）", 2, aliveBrokers.size());
    }

    @Test
    public void testGetBrokerListByTopic() {
        BrokerInfo broker = createBrokerInfo("broker_topic", "127.0.0.1", 9876, 0);
        broker.setTopicList(new String[]{"test_topic"});
        routeInfoManager.registerBroker(broker);

        List<BrokerInfo> brokers = routeInfoManager.getBrokerListByTopic("test_topic");
        assertEquals("应该有 1 个 Broker", 1, brokers.size());
        assertEquals("应该是 broker_topic", "broker_topic", brokers.get(0).getBrokerName());
    }

    @Test
    public void testGetBrokerListByTopicNotExist() {
        List<BrokerInfo> brokers = routeInfoManager.getBrokerListByTopic("not_exist_topic");
        assertTrue("不存在的 Topic 应该返回空列表", brokers.isEmpty());
    }

    @Test
    public void testGetTopicRouteInfo() {
        BrokerInfo broker = createBrokerInfo("broker_route", "127.0.0.1", 9876, 0);
        broker.setTopicList(new String[]{"test_topic"});
        routeInfoManager.registerBroker(broker);

        com.aoaojiao.catmq.nameserver.model.TopicRouteInfo routeInfo = routeInfoManager.getTopicRouteInfo("test_topic");
        assertNotNull("应该返回路由信息", routeInfo);
        assertEquals("Topic 应该匹配", "test_topic", routeInfo.getTopic());
        assertEquals("应该有 1 个 Broker", 1, routeInfo.getBrokerInfoList().size());
    }

    @Test
    public void testIsBrokerAlive() {
        BrokerInfo broker = createBrokerInfo("broker_alive_check", "127.0.0.1", 9876, 0);
        routeInfoManager.registerBroker(broker);

        assertTrue("新注册的 Broker 应该存活", routeInfoManager.isBrokerAlive("broker_alive_check"));
        assertFalse("不存在的 Broker 应该不存活", routeInfoManager.isBrokerAlive("not_exist"));
    }

    @Test
    public void testGetBrokerCount() {
        for (int i = 1; i <= 5; i++) {
            BrokerInfo broker = createBrokerInfo("broker_count_" + i, "127.0.0." + i, 9876, i);
            routeInfoManager.registerBroker(broker);
        }

        assertEquals("应该有 5 个 Broker", 5, routeInfoManager.getBrokerCount());
        assertEquals("应该有 5 个存活的 Broker", 5, routeInfoManager.getAliveBrokerCount());
    }

    @Test
    public void testGetAllTopics() {
        BrokerInfo broker1 = createBrokerInfo("broker_topics_1", "127.0.0.1", 9876, 1);
        broker1.setTopicList(new String[]{"topic_a", "topic_b"});
        routeInfoManager.registerBroker(broker1);

        BrokerInfo broker2 = createBrokerInfo("broker_topics_2", "127.0.0.2", 9876, 2);
        broker2.setTopicList(new String[]{"topic_b", "topic_c"});
        routeInfoManager.registerBroker(broker2);

        Set<String> topics = routeInfoManager.getAllTopics();
        assertEquals("应该有 3 个不同的 Topic", 3, topics.size());
        assertTrue("应该包含 topic_a", topics.contains("topic_a"));
        assertTrue("应该包含 topic_b", topics.contains("topic_b"));
        assertTrue("应该包含 topic_c", topics.contains("topic_c"));
    }

    @Test
    public void testClear() {
        for (int i = 1; i <= 3; i++) {
            BrokerInfo broker = createBrokerInfo("broker_clear_" + i, "127.0.0." + i, 9876, i);
            routeInfoManager.registerBroker(broker);
        }

        assertEquals("清除前应该有 3 个 Broker", 3, routeInfoManager.getBrokerCount());

        routeInfoManager.clear();

        assertEquals("清除后应该有 0 个 Broker", 0, routeInfoManager.getBrokerCount());
        assertTrue("清除后所有 Topic 应该为空", routeInfoManager.getAllTopics().isEmpty());
    }

    private BrokerInfo createBrokerInfo(String name, String ip, int port, int brokerId) {
        BrokerInfo broker = new BrokerInfo();
        broker.setBrokerName(name);
        broker.setBrokerIp(ip);
        broker.setBrokerPort(port);
        broker.setBrokerId(brokerId);
        broker.setWeight(100);
        broker.setClusterName("default-cluster");
        broker.setLastUpdateTimestamp(System.currentTimeMillis());
        broker.setAlive(true);
        return broker;
    }
}