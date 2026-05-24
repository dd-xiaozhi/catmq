package com.aoaojiao.catmq.cluster;

import com.aoaojiao.catmq.cluster.discovery.BrokerDiscoveryListener;
import com.aoaojiao.catmq.cluster.discovery.BrokerRegistry;
import com.aoaojiao.catmq.cluster.election.LeaderElector;
import com.aoaojiao.catmq.cluster.election.LeaderElectorListener;
import com.aoaojiao.catmq.cluster.failover.MasterFailoverController;
import com.aoaojiao.catmq.cluster.failover.MasterFailoverListener;
import com.aoaojiao.catmq.cluster.loadbalance.LoadBalancer;
import com.aoaojiao.catmq.cluster.loadbalance.LoadBalancerFactory;
import com.aoaojiao.catmq.common.model.BrokerInfo;
import com.aoaojiao.catmq.cluster.model.BrokerList;
import com.aoaojiao.catmq.cluster.model.ClusterConfig;
import com.aoaojiao.catmq.cluster.model.ClusterState;
import com.aoaojiao.catmq.cluster.sync.SyncResult;
import com.aoaojiao.catmq.cluster.sync.SyncStrategy;
import com.aoaojiao.catmq.cluster.sync.SyncStrategyFactory;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 集群管理器
 * 整合所有集群相关功能，提供统一的集群管理接口
 *
 * 主要功能：
 * 1. 集群初始化和管理
 * 2. 主节点选举
 * 3. Broker 注册与发现
 * 4. 负载均衡
 * 5. 主从数据同步
 * 6. 故障检测与切换
 *
 * @author DD
 */
public class ClusterManager implements Closeable {

    private static final Logger logger = LoggerFactory.getLogger(ClusterManager.class);

    /**
     * Curator 客户端
     */
    private CuratorFramework curatorClient;

    /**
     * 主节点选举器
     */
    private LeaderElector leaderElector;

    /**
     * Broker 注册中心
     */
    private BrokerRegistry brokerRegistry;

    /**
     * 主从切换控制器
     */
    private MasterFailoverController masterFailoverController;

    /**
     * 集群配置
     */
    private final ClusterConfig clusterConfig;

    /**
     * 集群状态
     */
    private final ClusterState clusterState;

    /**
     * 负载均衡器
     */
    private LoadBalancer loadBalancer;

    /**
     * 同步策略
     */
    private SyncStrategy syncStrategy;

    /**
     * 是否已初始化
     */
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    /**
     * 初始化锁
     */
    private final ReentrantLock initLock = new ReentrantLock();

    /**
     * 心跳更新定时器
     */
    private java.util.concurrent.ScheduledExecutorService heartbeatScheduler;

    /**
     * 监听器列表
     */
    private final java.util.concurrent.CopyOnWriteArrayList<ClusterEventListener> eventListeners =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * 构造函数
     *
     * @param clusterConfig 集群配置
     */
    public ClusterManager(ClusterConfig clusterConfig) {
        this.clusterConfig = clusterConfig;
        this.clusterState = new ClusterState();
    }

    /**
     * 初始化集群管理器
     *
     * @return 是否初始化成功
     */
    public boolean initialize() {
        if (initialized.get()) {
            logger.warn("ClusterManager 已经初始化，跳过");
            return true;
        }

        initLock.lock();
        try {
            if (initialized.get()) {
                return true;
            }

            logger.info("初始化 ClusterManager，配置：{}", clusterConfig);

            // 1. 初始化 Curator 客户端
            if (!initCuratorClient()) {
                return false;
            }

            // 2. 初始化主节点选举器
            leaderElector = new LeaderElector(clusterConfig);
            leaderElector.addListener(new LeaderElectorListener() {
                @Override
                public void onElectedAsLeader(BrokerInfo leaderInfo) {
                    notifyEvent(new ClusterEvent(ClusterEvent.Type.LEADER_ELECTED, leaderInfo));
                }

                @Override
                public void onLeaderRemoved(String previousLeaderId) {
                    notifyEvent(new ClusterEvent(ClusterEvent.Type.LEADER_REMOVED, previousLeaderId));
                }

                @Override
                public void onLeaderChanged(BrokerInfo oldLeader, BrokerInfo newLeader) {
                    notifyEvent(new ClusterEvent(ClusterEvent.Type.LEADER_CHANGED, new Object[]{oldLeader, newLeader}));
                }

                @Override
                public void onElectionError(Exception error) {
                    notifyEvent(new ClusterEvent(ClusterEvent.Type.ELECTION_ERROR, error));
                }

                @Override
                public void onStateChanged(boolean isLeader, ClusterConfig clusterConfig) {
                    clusterState.updateState(isLeader, isLeader ? leaderElector.getCurrentLeader() : null);
                    notifyEvent(new ClusterEvent(ClusterEvent.Type.STATE_CHANGED, isLeader));
                }
            });

            if (!leaderElector.initialize()) {
                logger.error("LeaderElector 初始化失败");
                return false;
            }

            // 3. 初始化 Broker 注册中心
            brokerRegistry = new BrokerRegistry(curatorClient, clusterConfig);
            brokerRegistry.initialize();

            // 4. 初始化主从切换控制器
            masterFailoverController = new MasterFailoverController(
                    curatorClient, clusterConfig, brokerRegistry, leaderElector);
            masterFailoverController.initialize();

            // 5. 初始化负载均衡器
            loadBalancer = LoadBalancerFactory.create(
                    clusterConfig.getLoadBalanceStrategy(),
                    clusterConfig.getVirtualNodeCount());

            // 6. 初始化同步策略
            syncStrategy = SyncStrategyFactory.create(clusterConfig.getSyncMode());

            // 7. 启动心跳更新
            startHeartbeatUpdater();

            // 8. 更新集群状态
            updateClusterState();

            clusterState.setAvailable(true);
            initialized.set(true);

            logger.info("ClusterManager 初始化完成");
            return true;

        } catch (Exception e) {
            logger.error("ClusterManager 初始化失败", e);
            return false;
        } finally {
            initLock.unlock();
        }
    }

    /**
     * 初始化 Curator 客户端
     */
    private boolean initCuratorClient() {
        try {
            logger.info("初始化 Curator 客户端，ZK 地址：{}", clusterConfig.getZkAddress());

            curatorClient = CuratorFrameworkFactory.newClient(
                    clusterConfig.getZkAddress(),
                    clusterConfig.getZkSessionTimeoutMs(),
                    clusterConfig.getZkConnectionTimeoutMs(),
                    new ExponentialBackoffRetry(1000, 3)
            );

            curatorClient.start();

            logger.info("Curator 客户端初始化完成");
            return true;
        } catch (Exception e) {
            logger.error("Curator 客户端初始化失败", e);
            return false;
        }
    }

    /**
     * 启动心跳更新
     */
    private void startHeartbeatUpdater() {
        heartbeatScheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ClusterManager-HeartbeatUpdater");
            t.setDaemon(true);
            return t;
        });

        long interval = clusterConfig.getHeartbeatIntervalMs();
        heartbeatScheduler.scheduleAtFixedRate(() -> {
            try {
                brokerRegistry.updateHeartbeat();
            } catch (Exception e) {
                logger.error("更新心跳失败", e);
            }
        }, interval, interval, TimeUnit.MILLISECONDS);

        logger.info("启动心跳更新，间隔={}ms", interval);
    }

    /**
     * 更新集群状态
     */
    private void updateClusterState() {
        BrokerInfo currentBroker = brokerRegistry.getCurrentBroker();
        boolean isLeader = leaderElector.isLeader();

        clusterState.updateState(isLeader, currentBroker);
        clusterState.setBrokerList(brokerRegistry.getAllBrokers());
        clusterState.setTotalBrokerCount(brokerRegistry.getAllBrokers().size());
        clusterState.setAvailable(true);
        clusterState.setLastUpdateTime(System.currentTimeMillis());
    }

    /**
     * 选择一个 Broker 用于写入
     * 写入操作只能发送到主节点
     *
     * @param topic 主题
     * @return 主节点 Broker
     */
    public BrokerInfo selectForWrite(String topic) {
        BrokerInfo master = brokerRegistry.getMaster();
        if (master != null) {
            logger.debug("选择主节点写入：{}", master.getBrokerId());
            return master;
        }

        // 如果没有主节点，检查本地是否是主节点
        if (leaderElector.isLeader()) {
            return brokerRegistry.getCurrentBroker();
        }

        logger.warn("没有可用的主节点");
        return null;
    }

    /**
     * 选择一个 Broker 用于读取
     * 读取操作可以使用负载均衡策略选择任意 Broker
     *
     * @param topic 主题
     * @param key   用于负载均衡的 key
     * @return 选中的 Broker
     */
    public BrokerInfo selectForRead(String topic, String key) {
        List<BrokerInfo> activeBrokers = brokerRegistry.getActiveBrokers();

        if (activeBrokers.isEmpty()) {
            logger.warn("没有可用的 Broker 用于读取");
            return null;
        }

        BrokerInfo selected = loadBalancer.select(activeBrokers, key != null ? key : topic);
        logger.debug("负载均衡选择 Broker：{}", selected != null ? selected.getBrokerId() : "null");
        return selected;
    }

    /**
     * 执行同步写入
     *
     * @param topic 主题
     * @param data  数据
     * @return 同步结果
     */
    public SyncResult syncWrite(String topic, byte[] data) {
        BrokerInfo master = selectForWrite(topic);
        if (master == null) {
            return SyncResult.failure("没有可用的主节点");
        }

        List<BrokerInfo> slaves = brokerRegistry.getSlaves();
        return syncStrategy.write(master, slaves, data, clusterConfig);
    }

    /**
     * 检查当前是否是主节点
     *
     * @return 是否是主节点
     */
    public boolean isLeader() {
        return leaderElector.isLeader();
    }

    /**
     * 获取当前 Broker 信息
     *
     * @return 当前 Broker
     */
    public BrokerInfo getCurrentBroker() {
        return brokerRegistry.getCurrentBroker();
    }

    /**
     * 获取当前主节点
     *
     * @return 主节点
     */
    public BrokerInfo getMaster() {
        return brokerRegistry.getMaster();
    }

    /**
     * 获取所有 Broker
     *
     * @return Broker 列表
     */
    public BrokerList getAllBrokers() {
        return brokerRegistry.getAllBrokers();
    }

    /**
     * 获取活跃的 Broker
     *
     * @return 活跃 Broker 列表
     */
    public List<BrokerInfo> getActiveBrokers() {
        return brokerRegistry.getActiveBrokers();
    }

    /**
     * 获取集群状态
     *
     * @return 集群状态
     */
    public ClusterState getClusterState() {
        updateClusterState();
        return clusterState;
    }

    /**
     * 获取集群配置
     *
     * @return 集群配置
     */
    public ClusterConfig getClusterConfig() {
        return clusterConfig;
    }

    /**
     * 获取负载均衡器
     *
     * @return 负载均衡器
     */
    public LoadBalancer getLoadBalancer() {
        return loadBalancer;
    }

    /**
     * 更新负载均衡策略
     *
     * @param strategy 新策略
     */
    public void updateLoadBalanceStrategy(ClusterConfig.LoadBalanceStrategy strategy) {
        clusterConfig.setLoadBalanceStrategy(strategy);
        loadBalancer = LoadBalancerFactory.create(strategy, clusterConfig.getVirtualNodeCount());
        logger.info("更新负载均衡策略：{}", strategy);
    }

    /**
     * 更新同步模式
     *
     * @param syncMode 新同步模式
     */
    public void updateSyncMode(ClusterConfig.SyncMode syncMode) {
        clusterConfig.setSyncMode(syncMode);
        syncStrategy = SyncStrategyFactory.create(syncMode);
        logger.info("更新同步模式：{}", syncMode);
    }

    /**
     * 添加集群事件监听器
     *
     * @param listener 监听器
     */
    public void addEventListener(ClusterEventListener listener) {
        if (listener != null) {
            eventListeners.add(listener);
        }
    }

    /**
     * 移除集群事件监听器
     *
     * @param listener 监听器
     */
    public void removeEventListener(ClusterEventListener listener) {
        eventListeners.remove(listener);
    }

    /**
     * 通知事件
     *
     * @param event 事件
     */
    private void notifyEvent(ClusterEvent event) {
        for (ClusterEventListener listener : eventListeners) {
            try {
                listener.onClusterEvent(event);
            } catch (Exception e) {
                logger.error("通知集群事件失败", e);
            }
        }
    }

    /**
     * 添加 Broker 发现监听器
     *
     * @param listener 监听器
     */
    public void addBrokerDiscoveryListener(BrokerDiscoveryListener listener) {
        brokerRegistry.addListener(listener);
    }

    /**
     * 添加主从切换监听器
     *
     * @param listener 监听器
     */
    public void addMasterFailoverListener(MasterFailoverListener listener) {
        masterFailoverController.addListener(listener);
    }

    /**
     * 等待成为主节点
     *
     * @param timeout 超时时间
     * @param unit    时间单位
     * @return 是否成为主节点
     * @throws InterruptedException 如果线程被中断
     */
    public boolean awaitLeadership(long timeout, TimeUnit unit) throws InterruptedException {
        return leaderElector.awaitLeadership(timeout, unit);
    }

    @Override
    public void close() throws IOException {
        logger.info("关闭 ClusterManager");

        if (heartbeatScheduler != null) {
            heartbeatScheduler.shutdown();
            try {
                if (!heartbeatScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    heartbeatScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                heartbeatScheduler.shutdownNow();
            }
        }

        if (masterFailoverController != null) {
            masterFailoverController.shutdown();
        }

        if (brokerRegistry != null) {
            brokerRegistry.close();
        }

        if (leaderElector != null) {
            leaderElector.close();
        }

        if (curatorClient != null) {
            curatorClient.close();
        }

        initialized.set(false);
        logger.info("ClusterManager 已关闭");
    }

    /**
     * 集群事件类型
     */
    public enum EventType {
        LEADER_ELECTED,      // 主节点被选举
        LEADER_REMOVED,      // 主节点被移除
        LEADER_CHANGED,      // 主节点变更
        BROKER_JOINED,       // Broker 加入
        BROKER_LEFT,         // Broker 离开
        BROKER_UPDATED,      // Broker 更新
        STATE_CHANGED,       // 状态变更
        ELECTION_ERROR       // 选举错误
    }

    /**
     * 集群事件
     */
    public static class ClusterEvent {
        public enum Type {
            LEADER_ELECTED,
            LEADER_REMOVED,
            LEADER_CHANGED,
            BROKER_JOINED,
            BROKER_LEFT,
            BROKER_UPDATED,
            STATE_CHANGED,
            ELECTION_ERROR
        }

        private final Type type;
        private final Object data;
        private final long timestamp;

        public ClusterEvent(Type type, Object data) {
            this.type = type;
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }

        public Type getType() {
            return type;
        }

        public Object getData() {
            return data;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }

    /**
     * 集群事件监听器
     */
    public interface ClusterEventListener {
        /**
         * 处理集群事件
         *
         * @param event 事件
         */
        void onClusterEvent(ClusterEvent event);
    }
}