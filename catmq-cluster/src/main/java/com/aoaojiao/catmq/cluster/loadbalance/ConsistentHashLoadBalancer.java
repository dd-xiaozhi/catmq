package com.aoaojiao.catmq.cluster.loadbalance;

import com.aoaojiao.catmq.cluster.model.BrokerInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 一致性哈希负载均衡器
 * 相同 key 映射到相同节点，实现请求的粘性分布
 *
 * 实现原理：
 * 1. 将每个 Broker 映射到多个虚拟节点
 * 2. 对 key 进行哈希，映射到环上的位置
 * 3. 顺时针找到第一个虚拟节点所在的 Broker
 *
 * @author DD
 */
public class ConsistentHashLoadBalancer implements LoadBalancer {

    private static final Logger logger = LoggerFactory.getLogger(ConsistentHashLoadBalancer.class);

    /**
     * 虚拟节点数量
     */
    private final int virtualNodeCount;

    /**
     * 虚拟节点环
     */
    private final TreeMap<Long, BrokerInfo> virtualNodes = new TreeMap<>();

    /**
     * Broker 列表缓存
     */
    private volatile List<BrokerInfo> brokersCache = new ArrayList<>();

    /**
     * MD5 哈希算法
     */
    private MessageDigest md5;

    public ConsistentHashLoadBalancer() {
        this(100);
    }

    /**
     * 构造函数
     *
     * @param virtualNodeCount 虚拟节点数量
     */
    public ConsistentHashLoadBalancer(int virtualNodeCount) {
        this.virtualNodeCount = virtualNodeCount;
        try {
            this.md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 算法不可用", e);
        }
    }

    @Override
    public BrokerInfo select(List<BrokerInfo> brokers, String key) {
        if (brokers == null || brokers.isEmpty()) {
            logger.warn("一致性哈希负载均衡：无可用 Broker");
            return null;
        }

        // 检查是否需要更新虚拟节点环
        if (needUpdate(brokers)) {
            updateVirtualNodes(brokers);
        }

        if (virtualNodes.isEmpty()) {
            return brokers.get(0);
        }

        // 对 key 进行哈希
        long hash = hash(key);

        // 找到第一个大于等于 hash 的虚拟节点
        Long targetHash = virtualNodes.ceilingKey(hash);

        if (targetHash == null) {
            // 如果没有大于等于的，说明应该选择第一个（环回到起点）
            targetHash = virtualNodes.firstKey();
        }

        BrokerInfo selected = virtualNodes.get(targetHash);
        logger.debug("一致性哈希选择：key={}, hash={}, broker={}", key, hash, selected.getBrokerId());
        return selected;
    }

    /**
     * 计算哈希值
     *
     * @param key 键
     * @return 哈希值（使用 MD5 保证分布均匀）
     */
    private long hash(String key) {
        if (key == null) {
            key = "";
        }

        byte[] digest = md5.digest(key.getBytes());
        return ((long) (digest[3] & 0xFF) << 24)
                | ((long) (digest[2] & 0xFF) << 16)
                | ((long) (digest[1] & 0xFF) << 8)
                | (digest[0] & 0xFF);
    }

    /**
     * 检查是否需要更新虚拟节点环
     *
     * @param brokers Broker 列表
     * @return 是否需要更新
     */
    private boolean needUpdate(List<BrokerInfo> brokers) {
        if (brokersCache.size() != brokers.size()) {
            return true;
        }

        for (BrokerInfo broker : brokers) {
            if (!brokersCache.contains(broker)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 更新虚拟节点环
     *
     * @param brokers Broker 列表
     */
    private void updateVirtualNodes(List<BrokerInfo> brokers) {
        synchronized (this) {
            virtualNodes.clear();
            brokersCache = new ArrayList<>(brokers);

            for (BrokerInfo broker : brokers) {
                for (int i = 0; i < virtualNodeCount; i++) {
                    String virtualNodeKey = getVirtualNodeKey(broker, i);
                    long hash = hash(virtualNodeKey);
                    virtualNodes.put(hash, broker);
                }
            }

            logger.info("一致性哈希负载均衡：更新虚拟节点环，Broker 数量={}, 虚拟节点数量={}",
                    brokers.size(), virtualNodeCount);
        }
    }

    /**
     * 获取虚拟节点 key
     *
     * @param broker Broker 信息
     * @param index  虚拟节点索引
     * @return 虚拟节点 key
     */
    private String getVirtualNodeKey(BrokerInfo broker, int index) {
        return String.format("broker-%s-%d", broker.getBrokerId(), index);
    }

    @Override
    public String getName() {
        return "CONSISTENT_HASH";
    }

    @Override
    public void reset() {
        virtualNodes.clear();
    }

    /**
     * 获取虚拟节点数量
     *
     * @return 虚拟节点数量
     */
    public int getVirtualNodeCount() {
        return virtualNodeCount;
    }

    /**
     * 获取虚拟节点数量
     *
     * @return 虚拟节点数量
     */
    public int getVirtualNodeSize() {
        return virtualNodes.size();
    }

    /**
     * 简单的 TreeMap 实现，用于一致性哈希环
     */
    private static class TreeMap<K extends Comparable<K>, V> {

        private final java.util.TreeMap<K, V> map = new java.util.TreeMap<>();

        public V get(K key) {
            return map.get(key);
        }

        public void put(K key, V value) {
            map.put(key, value);
        }

        public void clear() {
            map.clear();
        }

        public K ceilingKey(K key) {
            return map.ceilingKey(key);
        }

        public K firstKey() {
            return map.firstKey();
        }

        public boolean isEmpty() {
            return map.isEmpty();
        }

        public int size() {
            return map.size();
        }
    }
}