package com.aoaojiao.catmq.cluster.election;

import com.aoaojiao.catmq.common.model.BrokerInfo;
import com.aoaojiao.catmq.cluster.model.ClusterConfig;
import com.aoaojiao.catmq.cluster.model.ClusterState;
import lombok.Getter;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.leader.LeaderLatch;
import org.apache.curator.framework.recipes.leader.Participant;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Leader 选举器
 * 使用 Curator LeaderLatch 实现主节点选举
 *
 * 主要功能：
 * 1. 参与主节点选举
 * 2. 监听选举结果变化
 * 3. 提供当前主节点查询
 *
 * @author DD
 */
public class LeaderElector implements Closeable {

    private static final Logger logger = LoggerFactory.getLogger(LeaderElector.class);

    /**
     * Curator 客户端
     */
    private CuratorFramework curatorClient;

    /**
     * LeaderLatch 实例
     */
    private LeaderLatch leaderLatch;

    /**
     * 集群配置
     */
    private ClusterConfig clusterConfig;

    /**
     * 选举结果监听器列表
     */
    private final List<LeaderElectorListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * 是否已经获得主节点身份
     */
    private final AtomicBoolean isLeader = new AtomicBoolean(false);

    /**
     * 当前主节点信息
     * -- GETTER --
     *  获取当前主节点信息
     *
     * @return 主节点信息

     */
    @Getter
    private volatile BrokerInfo currentLeader;

    /**
     * 是否已初始化
     */
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    /**
     * 初始化锁
     */
    private final ReentrantLock initLock = new ReentrantLock();

    /**
     * 集群状态
     */
    private ClusterState clusterState;

    /**
     * 构造函数
     *
     * @param clusterConfig 集群配置
     */
    public LeaderElector(ClusterConfig clusterConfig) {
        this.clusterConfig = clusterConfig;
        this.clusterState = new ClusterState();
    }

    /**
     * 初始化选举器
     *
     * @return 是否初始化成功
     */
    public boolean initialize() {
        if (initialized.get()) {
            logger.warn("LeaderElector 已经初始化，跳过");
            return true;
        }

        initLock.lock();
        try {
            if (initialized.get()) {
                return true;
            }

            logger.info("初始化 LeaderElector，ZK 地址：{}", clusterConfig.getZkAddress());

            // 创建 Curator 客户端
            curatorClient = CuratorFrameworkFactory.newClient(
                    clusterConfig.getZkAddress(),
                    new ExponentialBackoffRetry(1000, 3)
            );

            // 添加连接状态监听器
            curatorClient.getConnectionStateListenable().addListener(new ConnectionStateListener() {
                @Override
                public void stateChanged(CuratorFramework client, ConnectionState newState) {
                    handleConnectionStateChanged(newState);
                }
            });

            // 启动客户端
            curatorClient.start();

            // 创建 LeaderLatch
            String electionPath = clusterConfig.getZkRootPath() + clusterConfig.getControllerElectionPath();
            String participantId = buildParticipantId();

            leaderLatch = new LeaderLatch(curatorClient, electionPath, participantId);

            // 添加主节点变更监听器
            leaderLatch.addListener(new org.apache.curator.framework.recipes.leader.LeaderLatchListener() {
                @Override
                public void isLeader() {
                    handleLeadershipAcquired();
                }

                @Override
                public void notLeader() {
                    handleLeadershipLost();
                }
            });

            // 启动 LeaderLatch
            leaderLatch.start();

            initialized.set(true);
            logger.info("LeaderElector 初始化完成，本节点ID：{}", participantId);

            return true;
        } catch (Exception e) {
            logger.error("LeaderElector 初始化失败", e);
            return false;
        } finally {
            initLock.unlock();
        }
    }

    /**
     * 构建参与者 ID
     *
     * @return 参与者 ID
     */
    private String buildParticipantId() {
        return String.format("%s:%s:%d",
                clusterConfig.getBrokerId(),
                clusterConfig.getHost(),
                clusterConfig.getPort());
    }

    /**
     * 处理连接状态变化
     *
     * @param newState 新状态
     */
    private void handleConnectionStateChanged(ConnectionState newState) {
        logger.info("ZK 连接状态变化：{}", newState);

        switch (newState) {
            case CONNECTED:
            case RECONNECTED:
                logger.info("ZK 连接已恢复");
                break;
            case LOST:
            case SUSPENDED:
                logger.warn("ZK 连接丢失或断开");
                // 重置主节点状态
                if (isLeader.compareAndSet(true, false)) {
                    notifyLeadershipLost(null);
                }
                break;
            case READ_ONLY:
                logger.warn("ZK 连接处于只读模式");
                break;
        }
    }

    /**
     * 处理获得主节点身份
     */
    private void handleLeadershipAcquired() {
        logger.info("本节点已获得主节点身份");

        if (isLeader.compareAndSet(false, true)) {
            currentLeader = buildCurrentBrokerInfo(BrokerInfo.BrokerRole.MASTER);
            clusterState.updateState(true, currentLeader);
            notifyLeadershipAcquired(currentLeader);
        }
    }

    /**
     * 处理失去主节点身份
     */
    private void handleLeadershipLost() {
        logger.info("本节点已失去主节点身份");

        if (isLeader.compareAndSet(true, false)) {
            String previousLeaderId = currentLeader != null ? currentLeader.getBrokerId() : null;
            clusterState.updateState(false, null);
            notifyLeadershipLost(previousLeaderId);
        }
    }

    /**
     * 构建当前 Broker 信息
     *
     * @param role 角色
     * @return Broker 信息
     */
    private BrokerInfo buildCurrentBrokerInfo(BrokerInfo.BrokerRole role) {
        return new BrokerInfo(
                clusterConfig.getBrokerId(),
                clusterConfig.getBrokerName(),
                clusterConfig.getHost(),
                clusterConfig.getPort()
        );
    }

    /**
     * 通知监听器获得主节点身份
     *
     * @param leaderInfo 主节点信息
     */
    private void notifyLeadershipAcquired(BrokerInfo leaderInfo) {
        for (LeaderElectorListener listener : listeners) {
            try {
                listener.onElectedAsLeader(leaderInfo);
                listener.onStateChanged(true, clusterConfig);
            } catch (Exception e) {
                logger.error("通知监听器失败", e);
            }
        }
    }

    /**
     * 通知监听器失去主节点身份
     *
     * @param previousLeaderId 之前的主节点 ID
     */
    private void notifyLeadershipLost(String previousLeaderId) {
        for (LeaderElectorListener listener : listeners) {
            try {
                listener.onLeaderRemoved(previousLeaderId);
                listener.onStateChanged(false, clusterConfig);
            } catch (Exception e) {
                logger.error("通知监听器失败", e);
            }
        }
    }

    /**
     * 检查当前是否是主节点
     *
     * @return 是否是主节点
     */
    public boolean isLeader() {
        return isLeader.get();
    }

    /**
     * 获取当前集群中所有参与者
     *
     * @return 参与者列表
     */
    public List<Participant> getParticipants() {
        try {
            if (leaderLatch != null) {
                return new java.util.ArrayList<>(leaderLatch.getParticipants());
            }
        } catch (Exception e) {
            logger.error("获取参与者列表失败", e);
        }
        return null;
    }

    /**
     * 获取当前主节点
     *
     * @return 主节点参与者
     */
    public Participant getLeader() {
        try {
            if (leaderLatch != null) {
                return leaderLatch.getLeader();
            }
        } catch (Exception e) {
            logger.error("获取主节点失败", e);
        }
        return null;
    }

    /**
     * 添加选举结果监听器
     *
     * @param listener 监听器
     */
    public void addListener(LeaderElectorListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    /**
     * 移除选举结果监听器
     *
     * @param listener 监听器
     */
    public void removeListener(LeaderElectorListener listener) {
        listeners.remove(listener);
    }

    /**
     * 等待成为主节点
     *
     * @param timeout 超时时间
     * @param unit 时间单位
     * @return 是否成为主节点
     * @throws InterruptedException 如果线程被中断
     */
    public boolean awaitLeadership(long timeout, TimeUnit unit) throws InterruptedException {
        if (leaderLatch != null) {
            return leaderLatch.await(timeout, unit);
        }
        return false;
    }

    @Override
    public void close() throws IOException {
        logger.info("关闭 LeaderElector");

        isLeader.set(false);

        if (leaderLatch != null) {
            try {
                leaderLatch.close();
            } catch (IOException e) {
                logger.error("关闭 LeaderLatch 失败", e);
            }
            leaderLatch = null;
        }

        if (curatorClient != null) {
            curatorClient.close();
            curatorClient = null;
        }

        initialized.set(false);
        logger.info("LeaderElector 已关闭");
    }
}