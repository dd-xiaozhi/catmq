package com.aoaojiao.catmq.cluster.loadbalance;

import com.aoaojiao.catmq.cluster.model.BrokerInfo;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * 负载均衡测试
 *
 * @author DD
 */
public class LoadBalancerTest {

    private List<BrokerInfo> brokers;

    @Before
    public void setUp() {
        brokers = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            BrokerInfo broker = BrokerInfo.builder()
                    .brokerName("broker_" + i)
                    .address("127.0.0." + i + ":9876")
                    .weight(100)
                    .build();
            brokers.add(broker);
        }
    }

    @Test
    public void testRoundRobinLoadBalancer() {
        LoadBalancer lb = new RoundRobinLoadBalancer();

        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < 100; i++) {
            BrokerInfo selected = lb.select(brokers, "key_" + i);
            counts.merge(selected.getBrokerName(), 1, Integer::sum);
        }

        // 每个 broker 应该被选中 20 次
        for (int i = 1; i <= 5; i++) {
            int count = counts.getOrDefault("broker_" + i, 0);
            assertEquals("broker_" + i + " 应该被选中20次", 20, count);
        }
    }

    @Test
    public void testRoundRobinOrder() {
        LoadBalancer lb = new RoundRobinLoadBalancer();

        for (int i = 0; i < 5; i++) {
            BrokerInfo selected = lb.select(brokers, "key");
            assertEquals("顺序应该是 broker_" + (i + 1), "broker_" + (i + 1), selected.getBrokerName());
        }
    }

    @Test
    public void testRandomLoadBalancer() {
        LoadBalancer lb = new RandomLoadBalancer();

        Map<String, Integer> counts = new HashMap<>();
        int total = 1000;
        for (int i = 0; i < total; i++) {
            BrokerInfo selected = lb.select(brokers, "key_" + i);
            counts.merge(selected.getBrokerName(), 1, Integer::sum);
        }

        // 随机应该比较均匀，每个大约 200 次
        for (int i = 1; i <= 5; i++) {
            int count = counts.getOrDefault("broker_" + i, 0);
            assertTrue("broker_" + i + " 应该在 150-250 范围内，实际: " + count,
                    count >= 150 && count <= 250);
        }
    }

    @Test
    public void testConsistentHashLoadBalancer() {
        LoadBalancer lb = new ConsistentHashLoadBalancer(100); // 100个虚拟节点

        // 相同的 key 应该路由到相同的 broker
        String key = "test_key";
        BrokerInfo first = lb.select(brokers, key);
        BrokerInfo second = lb.select(brokers, key);
        assertSame("相同的key应该路由到相同的broker", first, second);

        // 不同的 key 应该分布均匀
        Map<String, Integer> counts = new HashMap<>();
        int total = 1000;
        for (int i = 0; i < total; i++) {
            BrokerInfo selected = lb.select(brokers, "key_" + i);
            counts.merge(selected.getBrokerName(), 1, Integer::sum);
        }

        // 一致性哈希应该比较均匀
        for (int i = 1; i <= 5; i++) {
            int count = counts.getOrDefault("broker_" + i, 0);
            assertTrue("broker_" + i + " 应该在 150-250 范围内，实际: " + count,
                    count >= 150 && count <= 250);
        }
    }

    @Test
    public void testConsistentHashNoKey() {
        LoadBalancer lb = new ConsistentHashLoadBalancer(100);

        // 没有 key 时使用随机
        BrokerInfo selected = lb.select(brokers, null);
        assertNotNull("应该选择一个broker", selected);
    }

    @Test
    public void testEmptyBrokers() {
        List<BrokerInfo> emptyBrokers = new ArrayList<>();

        LoadBalancer roundRobin = new RoundRobinLoadBalancer();
        assertNull("空列表应该返回null", roundRobin.select(emptyBrokers, "key"));

        LoadBalancer random = new RandomLoadBalancer();
        assertNull("空列表应该返回null", random.select(emptyBrokers, "key"));

        LoadBalancer consistentHash = new ConsistentHashLoadBalancer(100);
        assertNull("空列表应该返回null", consistentHash.select(emptyBrokers, "key"));
    }

    @Test
    public void testSingleBroker() {
        List<BrokerInfo> singleBroker = new ArrayList<>();
        singleBroker.add(brokers.get(0));

        LoadBalancer lb = new RoundRobinLoadBalancer();
        for (int i = 0; i < 10; i++) {
            BrokerInfo selected = lb.select(singleBroker, "key_" + i);
            assertEquals("应该总是选择唯一的broker", "broker_1", selected.getBrokerName());
        }
    }

    @Test
    public void testLoadBalancerFactory() {
        LoadBalancer roundRobin = LoadBalancerFactory.getLoadBalancer(LoadBalancer.Strategy.ROUND_ROBIN);
        assertTrue("应该是RoundRobin实例", roundRobin instanceof RoundRobinLoadBalancer);

        LoadBalancer random = LoadBalancerFactory.getLoadBalancer(LoadBalancer.Strategy.RANDOM);
        assertTrue("应该是Random实例", random instanceof RandomLoadBalancer);

        LoadBalancer consistentHash = LoadBalancerFactory.getLoadBalancer(LoadBalancer.Strategy.CONSISTENT_HASH);
        assertTrue("应该是ConsistentHash实例", consistentHash instanceof ConsistentHashLoadBalancer);
    }
}