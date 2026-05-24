package com.aoaojiao.catmq.cluster.discovery;

import com.aoaojiao.catmq.common.model.BrokerInfo;
import com.aoaojiao.catmq.cluster.model.ClusterConfig;
import com.aoaojiao.catmq.cluster.model.BrokerList;
import com.alibaba.fastjson2.JSON;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;
import org.apache.curator.framework.recipes.cache.TreeCache;
import org.apache.curator.framework.recipes.cache.TreeCacheListener;
import org.apache.curator.utils.ThreadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Broker 注册中心
 * 基于 ZooKeeper 实现 Broker 的注册与发现
 *
 * 主要功能：
 * 1. Broker 注册到 ZooKeeper
 * 2. 监听集群中 Broker 的变化
 * 3. 提供 Broker 列表查询
 *
 * @author DD
 */
public class BrokerRegistry {

    private static final Logger logger = LoggerFactory.getLogger(BrokerRegistry.class);

    /**
     * Curator 客户端
     */
    private final CuratorFramework curatorClient;

    /**
     * 集群配置
     */
    private final ClusterConfig clusterConfig;

    /**
     * Broker 注册路径
     */
    private final String registryPath;

    /**
     * Broker 列表（内存缓存）
     */
    private final BrokerList brokerList;

    /**
     * 发现监听器列表
     */
    private final List<BrokerDiscoveryListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * PathChildrenCache 用于监听子节点变化
     */
    private PathChildrenCache pathChildrenCache;

    /**
     * TreeCache 用于缓存整个树
     */
    private TreeCache treeCache;

    /**
     * 当前 Broker 信息
     */
    private BrokerInfo currentBroker;

    /**
     * 本节点注册路径
     */
    private String selfPath;

    /**
     * 构造函数
     *
     * @param curatorClient Curator 客户端
     * @param clusterConfig 集群配置
     */
    public BrokerRegistry(CuratorFramework curatorClient, ClusterConfig clusterConfig) {
        this.curatorClient = curatorClient;
        this.clusterConfig = clusterConfig;
        this.registryPath = clusterConfig.getZkRootPath() + clusterConfig.getBrokerRegistryPath();
        this.brokerList = new BrokerList();
    }

    /**
     * 初始化注册中心
     */
    public void initialize() {
        try {
            logger.info("初始化 BrokerRegistry，注册路径：{}", registryPath);

            // 确保注册路径存在
            if (curatorClient.checkExists().forPath(registryPath) == null) {
                curatorClient.create().creatingParentsIfNeeded().forPath(registryPath);
                logger.info("创建注册路径：{}", registryPath);
            }

            // 注册当前 Broker
            registerSelf();

            // 启动监听
            startListening();

            logger.info("BrokerRegistry 初始化完成");
        } catch (Exception e) {
            logger.error("BrokerRegistry 初始化失败", e);
            throw new RuntimeException("BrokerRegistry 初始化失败", e);
        }
    }

    /**
     * 注册当前 Broker
     */
    private void registerSelf() throws Exception {
        // 构建当前 Broker 信息
        currentBroker = new BrokerInfo(
                clusterConfig.getBrokerId(),
                clusterConfig.getBrokerName(),
                clusterConfig.getHost(),
                clusterConfig.getPort()
        );
        currentBroker.setRole(clusterConfig.isController() ? BrokerInfo.BrokerRole.MASTER : BrokerInfo.BrokerRole.SLAVE);
        currentBroker.setStatus(BrokerInfo.BrokerStatus.ACTIVE);
        currentBroker.setWeight(100);
        currentBroker.setStartTime(System.currentTimeMillis());
        currentBroker.setLastHeartbeat(System.currentTimeMillis());
        currentBroker.setAlive(true);

        // 构建节点路径
        selfPath = registryPath + "/" + currentBroker.getBrokerId();

        // 将 Broker 信息写入 ZooKeeper
        String brokerData = JSON.toJSONString(currentBroker);
        curatorClient.create().creatingParentsIfNeeded()
                .forPath(selfPath, brokerData.getBytes(StandardCharsets.UTF_8));

        logger.info("Broker 注册成功，路径：{}，数据：{}", selfPath, brokerData);
    }

    /**
     * 启动监听
     */
    private void startListening() throws Exception {
        // 使用 PathChildrenCache 监听子节点变化
        pathChildrenCache = new PathChildrenCache(curatorClient, registryPath, true);

        pathChildrenCache.getListenable().addListener(new PathChildrenCacheListener() {
            @Override
            public void childEvent(CuratorFramework client, PathChildrenCacheEvent event) throws Exception {
                handleChildEvent(event);
            }
        });

        // 使用自定义线程池
        ThreadFactory threadFactory = ThreadUtils.newThreadFactory("BrokerRegistry-Listener-%d");
        pathChildrenCache.start(PathChildrenCache.StartMode.BUILD_INITIAL_CACHE);

        logger.info("启动 Broker 变化监听");
    }

    /**
     * 处理子节点事件
     *
     * @param event 事件
     */
    private void handleChildEvent(PathChildrenCacheEvent event) {
        try {
            switch (event.getType()) {
                case CHILD_ADDED:
                    logger.info("Broker 加入集群：{}", event.getData().getPath());
                    handleBrokerAdded(event);
                    break;
                case CHILD_REMOVED:
                    logger.info("Broker 离开集群：{}", event.getData().getPath());
                    handleBrokerRemoved(event);
                    break;
                case CHILD_UPDATED:
                    logger.info("Broker 信息更新：{}", event.getData().getPath());
                    handleBrokerUpdated(event);
                    break;
                default:
                    logger.debug("处理事件：{}", event.getType());
                    break;
            }
        } catch (Exception e) {
            logger.error("处理子节点事件失败", e);
        }
    }

    /**
     * 处理 Broker 添加
     */
    private void handleBrokerAdded(PathChildrenCacheEvent event) {
        try {
            String path = event.getData().getPath();
            if (path.equals(selfPath)) {
                // 是自身节点，不处理
                return;
            }

            BrokerInfo broker = parseBrokerInfo(event.getData().getData());
            if (broker != null) {
                brokerList.add(broker);
                notifyBrokerAdded(broker);
                notifyBrokerListChanged();
            }
        } catch (Exception e) {
            logger.error("处理 Broker 添加失败", e);
        }
    }

    /**
     * 处理 Broker 移除
     */
    private void handleBrokerRemoved(PathChildrenCacheEvent event) {
        try {
            String path = event.getData().getPath();
            String brokerId = extractBrokerId(path);

            brokerList.remove(brokerId);
            notifyBrokerRemoved(brokerId);
            notifyBrokerListChanged();
        } catch (Exception e) {
            logger.error("处理 Broker 移除失败", e);
        }
    }

    /**
     * 处理 Broker 更新
     */
    private void handleBrokerUpdated(PathChildrenCacheEvent event) {
        try {
            BrokerInfo broker = parseBrokerInfo(event.getData().getData());
            if (broker != null) {
                // 更新内存中的 Broker 信息
                BrokerInfo existing = brokerList.getById(broker.getBrokerId());
                if (existing != null) {
                    brokerList.remove(broker.getBrokerId());
                }
                brokerList.add(broker);
                notifyBrokerUpdated(broker);
            }
        } catch (Exception e) {
            logger.error("处理 Broker 更新失败", e);
        }
    }

    /**
     * 解析 Broker 信息
     *
     * @param data 数据
     * @return Broker 信息
     */
    private BrokerInfo parseBrokerInfo(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        try {
            String json = new String(data, StandardCharsets.UTF_8);
            return JSON.parseObject(json, BrokerInfo.class);
        } catch (Exception e) {
            logger.error("解析 Broker 信息失败", e);
            return null;
        }
    }

    /**
     * 从路径中提取 Broker ID
     *
     * @param path 路径
     * @return Broker ID
     */
    private String extractBrokerId(String path) {
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash >= 0) {
            return path.substring(lastSlash + 1);
        }
        return path;
    }

    /**
     * 更新当前 Broker 的心跳
     */
    public void updateHeartbeat() {
        if (currentBroker != null) {
            currentBroker.updateHeartbeat();
            try {
                String brokerData = JSON.toJSONString(currentBroker);
                curatorClient.setData().forPath(selfPath, brokerData.getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                logger.error("更新心跳失败", e);
            }
        }
    }

    /**
     * 更新当前 Broker 的状态
     *
     * @param status 新状态
     */
    public void updateStatus(BrokerInfo.BrokerStatus status) {
        if (currentBroker != null) {
            currentBroker.setStatus(status);
            try {
                String brokerData = JSON.toJSONString(currentBroker);
                curatorClient.setData().forPath(selfPath, brokerData.getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                logger.error("更新状态失败", e);
            }
        }
    }

    /**
     * 更新当前 Broker 的角色
     *
     * @param role 新角色
     */
    public void updateRole(BrokerInfo.BrokerRole role) {
        if (currentBroker != null) {
            currentBroker.setRole(role);
            try {
                String brokerData = JSON.toJSONString(currentBroker);
                curatorClient.setData().forPath(selfPath, brokerData.getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                logger.error("更新角色失败", e);
            }
        }
    }

    /**
     * 获取所有 Broker
     *
     * @return Broker 列表
     */
    public BrokerList getAllBrokers() {
        return brokerList;
    }

    /**
     * 获取活跃的 Broker
     *
     * @return 活跃 Broker 列表
     */
    public List<BrokerInfo> getActiveBrokers() {
        return brokerList.getActiveBrokers();
    }

    /**
     * 获取主节点
     *
     * @return 主节点
     */
    public BrokerInfo getMaster() {
        return brokerList.getMaster();
    }

    /**
     * 获取所有从节点
     *
     * @return 从节点列表
     */
    public List<BrokerInfo> getSlaves() {
        return brokerList.getSlaves();
    }

    /**
     * 根据 ID 获取 Broker
     *
     * @param brokerId Broker ID
     * @return Broker 信息
     */
    public BrokerInfo getBrokerById(String brokerId) {
        return brokerList.getById(brokerId);
    }

    /**
     * 获取当前 Broker
     *
     * @return 当前 Broker 信息
     */
    public BrokerInfo getCurrentBroker() {
        return currentBroker;
    }

    /**
     * 检查当前是否是主节点
     *
     * @return 是否是主节点
     */
    public boolean isMaster() {
        return currentBroker != null && currentBroker.getRole() == BrokerInfo.BrokerRole.MASTER;
    }

    /**
     * 添加发现监听器
     *
     * @param listener 监听器
     */
    public void addListener(BrokerDiscoveryListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    /**
     * 移除发现监听器
     *
     * @param listener 监听器
     */
    public void removeListener(BrokerDiscoveryListener listener) {
        listeners.remove(listener);
    }

    /**
     * 通知 Broker 添加
     */
    private void notifyBrokerAdded(BrokerInfo broker) {
        for (BrokerDiscoveryListener listener : listeners) {
            try {
                listener.onBrokerAdded(broker);
            } catch (Exception e) {
                logger.error("通知 Broker 添加失败", e);
            }
        }
    }

    /**
     * 通知 Broker 移除
     */
    private void notifyBrokerRemoved(String brokerId) {
        for (BrokerDiscoveryListener listener : listeners) {
            try {
                listener.onBrokerRemoved(brokerId);
            } catch (Exception e) {
                logger.error("通知 Broker 移除失败", e);
            }
        }
    }

    /**
     * 通知 Broker 更新
     */
    private void notifyBrokerUpdated(BrokerInfo broker) {
        for (BrokerDiscoveryListener listener : listeners) {
            try {
                listener.onBrokerUpdated(broker);
            } catch (Exception e) {
                logger.error("通知 Broker 更新失败", e);
            }
        }
    }

    /**
     * 通知 Broker 列表变化
     */
    private void notifyBrokerListChanged() {
        for (BrokerDiscoveryListener listener : listeners) {
            try {
                listener.onBrokerListChanged(brokerList.getAll());
            } catch (Exception e) {
                logger.error("通知 Broker 列表变化失败", e);
            }
        }
    }

    /**
     * 通知主节点变更
     */
    private void notifyMasterChanged(BrokerInfo oldMaster, BrokerInfo newMaster) {
        for (BrokerDiscoveryListener listener : listeners) {
            try {
                listener.onMasterChanged(oldMaster, newMaster);
            } catch (Exception e) {
                logger.error("通知主节点变更失败", e);
            }
        }
    }

    /**
     * 关闭注册中心
     */
    public void close() {
        logger.info("关闭 BrokerRegistry");

        try {
            // 注销当前 Broker
            if (selfPath != null && curatorClient.checkExists().forPath(selfPath) != null) {
                curatorClient.delete().forPath(selfPath);
                logger.info("注销 Broker：{}", selfPath);
            }

            // 关闭 PathChildrenCache
            if (pathChildrenCache != null) {
                pathChildrenCache.close();
            }

            // 关闭 TreeCache
            if (treeCache != null) {
                treeCache.close();
            }
        } catch (Exception e) {
            logger.error("关闭 BrokerRegistry 失败", e);
        }
    }
}