package com.aoaojiao.catmq.nameserver.service;

import com.aoaojiao.catmq.nameserver.config.NameServerConfig;
import com.aoaojiao.catmq.nameserver.model.BrokerInfo;
import com.aoaojiao.catmq.nameserver.model.TopicRouteInfo;
import com.alibaba.fastjson2.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 路由信息管理器
 * 核心组件，负责管理 Broker 注册表和 Topic 与 Broker 的映射关系
 *
 * @author DD
 */
public class RouteInfoManager {

    private static final Logger log = LoggerFactory.getLogger(RouteInfoManager.class);

    private final NameServerConfig config;

    /**
     * Broker 注册表
     * Key: brokerName, Value: BrokerInfo
     */
    private final ConcurrentHashMap<String, BrokerInfo> brokerTable = new ConcurrentHashMap<>();

    /**
     * Topic 路由缓存
     * Key: topic, Value: 拥有该 Topic 的 Broker 列表（按权重排序）
     */
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<BrokerInfo>> topicRouteTable = new ConcurrentHashMap<>();

    /**
     * 集群内的所有 Broker 列表
     */
    private final CopyOnWriteArrayList<String> clusterBrokerList = new CopyOnWriteArrayList<>();

    public RouteInfoManager(NameServerConfig config) {
        this.config = config;
    }

    /**
     * 注册 Broker
     *
     * @param brokerInfo Broker 信息
     */
    public void registerBroker(BrokerInfo brokerInfo) {
        if (brokerInfo == null || brokerInfo.getBrokerName() == null) {
            log.warn("Broker info is invalid, skip registration");
            return;
        }

        String brokerName = brokerInfo.getBrokerName();
        brokerInfo.setLastUpdateTimestamp(System.currentTimeMillis());
        brokerInfo.setAlive(true);

        // 1. 更新 Broker 注册表
        BrokerInfo existBroker = brokerTable.put(brokerName, brokerInfo);
        if (existBroker != null) {
            log.info("Broker {} updated, IP: {}:{}", brokerName, brokerInfo.getBrokerIp(), brokerInfo.getBrokerPort());
        } else {
            log.info("Broker {} registered, IP: {}:{}", brokerName, brokerInfo.getBrokerIp(), brokerInfo.getBrokerPort());
            clusterBrokerList.add(brokerName);
        }

        // 2. 更新 Topic 路由表
        if (brokerInfo.getTopicList() != null) {
            for (String topic : brokerInfo.getTopicList()) {
                updateTopicRoute(topic, brokerInfo);
            }
        }

        // 3. 如果是首次注册或 Broker 信息发生变化，需要持久化
        if (existBroker == null) {
            persist();
        }
    }

    /**
     * 更新单个 Topic 的路由信息
     */
    private void updateTopicRoute(String topic, BrokerInfo brokerInfo) {
        topicRouteTable.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>());
        CopyOnWriteArrayList<BrokerInfo> brokers = topicRouteTable.get(topic);

        // 移除旧的相同 brokerId 的记录
        brokers.removeIf(b -> b.getBrokerId() == brokerInfo.getBrokerId());
        // 添加新的记录
        brokers.add(brokerInfo);

        // 按权重排序（权重高的在前）
        brokers.sort((a, b) -> b.getWeight() - a.getWeight());

        log.debug("Topic {} route updated, broker count: {}", topic, brokers.size());
    }

    /**
     * Broker 心跳续约
     *
     * @param brokerName Broker 名称
     * @param topicList   Broker 当前管理的 Topic 列表
     * @return true: Broker 存活且已更新, false: Broker 不存在
     */
    public boolean heartBeat(String brokerName, String[] topicList) {
        BrokerInfo brokerInfo = brokerTable.get(brokerName);
        if (brokerInfo == null) {
            log.warn("Broker {} not found during heart beat", brokerName);
            return false;
        }

        // 更新心跳时间
        brokerInfo.setLastUpdateTimestamp(System.currentTimeMillis());
        brokerInfo.setAlive(true);

        // 更新 Topic 列表（可能发生变化）
        if (topicList != null) {
            brokerInfo.setTopicList(topicList);

            // 重新构建该 Broker 的 Topic 路由
            for (String topic : topicList) {
                updateTopicRoute(topic, brokerInfo);
            }
        }

        log.debug("Broker {} heart beat updated at {}", brokerName, brokerInfo.getLastUpdateTimestamp());
        return true;
    }

    /**
     * 注销 Broker
     *
     * @param brokerName Broker 名称
     */
    public void unRegisterBroker(String brokerName) {
        BrokerInfo removed = brokerTable.remove(brokerName);
        if (removed != null) {
            clusterBrokerList.remove(brokerName);

            // 清除该 Broker 相关的路由信息
            String[] topics = removed.getTopicList();
            if (topics != null) {
                for (String topic : topics) {
                    CopyOnWriteArrayList<BrokerInfo> brokers = topicRouteTable.get(topic);
                    if (brokers != null) {
                        brokers.removeIf(b -> b.getBrokerName().equals(brokerName));
                        // 如果该 Topic 没有 Broker 了，从路由表移除
                        if (brokers.isEmpty()) {
                            topicRouteTable.remove(topic);
                        }
                    }
                }
            }

            log.info("Broker {} unregistered", brokerName);
            persist();
        }
    }

    /**
     * 获取 Topic 路由信息
     *
     * @param topic Topic 名称
     * @return Topic 路由信息，不存在返回 null
     */
    public TopicRouteInfo getTopicRouteInfo(String topic) {
        CopyOnWriteArrayList<BrokerInfo> brokers = topicRouteTable.get(topic);
        if (brokers == null || brokers.isEmpty()) {
            return null;
        }

        TopicRouteInfo routeInfo = new TopicRouteInfo();
        routeInfo.setTopic(topic);
        // 转换为普通列表（避免序列化 CopyOnWriteArrayList）
        routeInfo.setBrokerInfoList(new ArrayList<>(brokers));
        routeInfo.setQueueCount(brokers.get(0).getTopicList() != null ?
            brokers.get(0).getTopicList().length : 4);
        routeInfo.setUpdateTimestamp(System.currentTimeMillis());
        return routeInfo;
    }

    /**
     * 获取 Topic 路由列表
     *
     * @param topic Topic 名称
     * @return Broker 列表
     */
    public List<BrokerInfo> getBrokerListByTopic(String topic) {
        CopyOnWriteArrayList<BrokerInfo> brokers = topicRouteTable.get(topic);
        if (brokers == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(brokers);
    }

    /**
     * 获取所有存活的 Broker
     */
    public Collection<BrokerInfo> getAllAliveBrokers() {
        return brokerTable.values().stream()
                .filter(BrokerInfo::isAlive)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有 Broker
     */
    public Collection<BrokerInfo> getAllBrokers() {
        return brokerTable.values();
    }

    /**
     * 获取集群内 Broker 数量
     */
    public int getBrokerCount() {
        return brokerTable.size();
    }

    /**
     * 获取集群内存活的 Broker 数量
     */
    public int getAliveBrokerCount() {
        return (int) brokerTable.values().stream().filter(BrokerInfo::isAlive).count();
    }

    /**
     * 根据 Broker 名称获取 Broker 信息
     */
    public BrokerInfo getBrokerInfo(String brokerName) {
        return brokerTable.get(brokerName);
    }

    /**
     * 检查 Broker 是否存活
     */
    public boolean isBrokerAlive(String brokerName) {
        BrokerInfo brokerInfo = brokerTable.get(brokerName);
        return brokerInfo != null && brokerInfo.isAlive();
    }

    /**
     * 获取所有 Topic 列表
     */
    public Set<String> getAllTopics() {
        return new HashSet<>(topicRouteTable.keySet());
    }

    /**
     * 持久化路由信息到文件
     */
    public synchronized void persist() {
        // 确保目录存在
        File dir = new File(config.getBrokerRegistryPath()).getParentFile();
        if (dir != null && !dir.exists()) {
            dir.mkdirs();
        }

        RouteInfoSnapshot snapshot = new RouteInfoSnapshot();
        snapshot.setBrokerTable(new HashMap<>(brokerTable));
        snapshot.setTopicRouteTable(new HashMap<>());

        // 只序列化关键信息
        for (Map.Entry<String, CopyOnWriteArrayList<BrokerInfo>> entry : topicRouteTable.entrySet()) {
            snapshot.getTopicRouteTable().put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }

        try (FileOutputStream fos = new FileOutputStream(config.getBrokerRegistryPath());
             OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            writer.write(JSON.toJSONString(snapshot));
            log.debug("Route info persisted to {}", config.getBrokerRegistryPath());
        } catch (IOException e) {
            log.error("Failed to persist route info", e);
        }
    }

    /**
     * 从文件加载路由信息
     */
    public synchronized void load() {
        File file = new File(config.getBrokerRegistryPath());
        if (!file.exists()) {
            log.info("No route info file found, skip loading");
            return;
        }

        try (FileInputStream fis = new FileInputStream(file);
             InputStreamReader reader = new InputStreamReader(fis, StandardCharsets.UTF_8);
             BufferedReader br = new BufferedReader(reader)) {

            StringBuilder content = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                content.append(line);
            }

            RouteInfoSnapshot snapshot = JSON.parseObject(content.toString(), RouteInfoSnapshot.class);

            if (snapshot != null && snapshot.getBrokerTable() != null) {
                for (Map.Entry<String, BrokerInfo> entry : snapshot.getBrokerTable().entrySet()) {
                    entry.getValue().setAlive(false); // 加载时默认不存活，等待心跳激活
                    registerBroker(entry.getValue());
                }
                log.info("Loaded {} brokers from file", snapshot.getBrokerTable().size());
            }
        } catch (IOException e) {
            log.error("Failed to load route info", e);
        }
    }

    /**
     * 清除所有数据（用于测试或重启）
     */
    public void clear() {
        brokerTable.clear();
        topicRouteTable.clear();
        clusterBrokerList.clear();
        log.info("Route info cleared");
    }

    /**
     * 快照类，用于持久化
     */
    private static class RouteInfoSnapshot {
        private Map<String, BrokerInfo> brokerTable;
        private Map<String, List<BrokerInfo>> topicRouteTable;

        public Map<String, BrokerInfo> getBrokerTable() {
            return brokerTable;
        }

        public void setBrokerTable(Map<String, BrokerInfo> brokerTable) {
            this.brokerTable = brokerTable;
        }

        public Map<String, List<BrokerInfo>> getTopicRouteTable() {
            return topicRouteTable;
        }

        public void setTopicRouteTable(Map<String, List<BrokerInfo>> topicRouteTable) {
            this.topicRouteTable = topicRouteTable;
        }
    }
}