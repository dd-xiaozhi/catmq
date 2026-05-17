package com.aoaojiao.catmq.nameserver.service;

import com.aoaojiao.catmq.nameserver.model.BrokerInfo;
import com.aoaojiao.catmq.nameserver.model.TopicRouteInfo;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * 路由信息管理器测试
 *
 * @author DD
 */
public class RouteInfoManagerTest {

    private RouteInfoManager routeInfoManager;

    @Before
    public void setUp() {
        routeInfoManager = new RouteInfoManager();
        routeInfoManager.start();
    }

    @After
    public void tearDown() {
        routeInfoManager.stop();
    }

    @Test
    public void testRegisterBroker() {
        BrokerInfo broker = createBrokerInfo("broker_1", "127.0.0.1:9876");
        boolean registered = routeInfoManager.registerBroker(broker);

        assertTrue("应该注册成功", registered);

        BrokerInfo found = routeInfoManager.getBroker("broker_1");
        assertNotNull("应该找到Broker", found);
        assertEquals("地址应该匹配", "127.0.0.1:9876", found.getAddress());
    }

    @Test
    public void testRegisterDuplicateBroker() {
        BrokerInfo broker1 = createBrokerInfo("broker_1", "127.0.0.1:9876");
        BrokerInfo broker2 = createBrokerInfo("broker_1", "127.0.0.2:9876");

        routeInfoManager.registerBroker(broker1);
        boolean updated = routeInfoManager.registerBroker(broker2);

        assertFalse("重复注册应该返回false", updated);
    }

    @Test
    public void testUnregisterBroker() {
        BrokerInfo broker = createBrokerInfo("broker_del", "127.0.0.1:9876");
        routeInfoManager.registerBroker(broker);

        boolean unregistered = routeInfoManager.unregisterBroker("broker_del");
        assertTrue("应该注销成功", unregistered);

        BrokerInfo found = routeInfoManager.getBroker("broker_del");
        assertNull("不应该找到已注销的Broker", found);
    }

    @Test
    public void testUpdateHeartbeat() {
        BrokerInfo broker = createBrokerInfo("broker_heartbeat", "127.0.0.1:9876");
        routeInfoManager.registerBroker(broker);

        long beforeTime = routeInfoManager.getBroker("broker_heartbeat").getLastUpdateTime();

        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        boolean updated = routeInfoManager.updateHeartbeat("broker_heartbeat");
        assertTrue("应该更新心跳成功", updated);

        long afterTime = routeInfoManager.getBroker("broker_heartbeat").getLastUpdateTime();
        assertTrue("时间应该更新", afterTime > beforeTime);
    }

    @Test
    public void testGetAllBrokers() {
        for (int i = 1; i <= 3; i++) {
            BrokerInfo broker = createBrokerInfo("broker_" + i, "127.0.0." + i + ":9876");
            routeInfoManager.registerBroker(broker);
        }

        List<BrokerInfo> brokers = routeInfoManager.getAllBrokers();
        assertEquals("应该有3个Broker", 3, brokers.size());
    }

    @Test
    public void testGetAllAliveBrokers() {
        BrokerInfo alive = createBrokerInfo("broker_alive", "127.0.0.1:9876");
        BrokerInfo dead = createBrokerInfo("broker_dead", "127.0.0.2:9876");
        dead.setLastUpdateTime(System.currentTimeMillis() - 60000); // 60秒前

        routeInfoManager.registerBroker(alive);
        routeInfoManager.registerBroker(dead);

        List<BrokerInfo> aliveBrokers = routeInfoManager.getAllAliveBrokers();
        assertEquals("应该只有1个存活的Broker", 1, aliveBrokers.size());
        assertEquals("应该是alive的Broker", "broker_alive", aliveBrokers.get(0).getBrokerName());
    }

    @Test
    public void testRegisterTopicRoute() {
        BrokerInfo broker = createBrokerInfo("broker_topic", "127.0.0.1:9876");
        routeInfoManager.registerBroker(broker);

        TopicRouteInfo route = TopicRouteInfo.builder()
                .topic("test_topic")
                .brokerName("broker_topic")
                .queueCount(4)
                .build();

        boolean registered = routeInfoManager.registerTopicRoute(route);
        assertTrue("应该注册成功", registered);

        List<BrokerInfo> brokers = routeInfoManager.getTopicBrokers("test_topic");
        assertEquals("应该有1个Broker", 1, brokers.size());
    }

    @Test
    public void testGetTopicBrokersNotExist() {
        List<BrokerInfo> brokers = routeInfoManager.getTopicBrokers("not_exist_topic");
        assertTrue("不存在的Topic应该返回空列表", brokers.isEmpty());
    }

    @Test
    public void testIsBrokerAlive() {
        BrokerInfo broker = createBrokerInfo("broker_alive_check", "127.0.0.1:9876");
        routeInfoManager.registerBroker(broker);

        assertTrue("新注册的Broker应该存活", routeInfoManager.isBrokerAlive("broker_alive_check"));
        assertFalse("不存在的Broker应该不存活", routeInfoManager.isBrokerAlive("not_exist"));
    }

    private BrokerInfo createBrokerInfo(String name, String address) {
        return BrokerInfo.builder()
                .brokerName(name)
                .brokerId(0)
                .address(address)
                .weight(100)
                .clusterName("default")
                .haMasterAddress("")
                .lastUpdateTime(System.currentTimeMillis())
                .build();
    }
}