package com.aoaojiao.catmq.cluster.failover;

import com.aoaojiao.catmq.cluster.election.LeaderElector;
import com.aoaojiao.catmq.cluster.election.LeaderElectorListener;
import com.aoaojiao.catmq.cluster.model.BrokerInfo;
import com.aoaojiao.catmq.cluster.model.ClusterConfig;
import com.aoaojiao.catmq.cluster.model.BrokerList;
import org.apache.curator.framework.CuratorFramework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 主节点故障检测与切换控制器
 *
 * 主要功能：
 * 1. 检测主节点是否故障
 * 2. 自动提升从节点为主节点
 * 3. 通知客户端路由更新
 *
 * @author DD
 */
public class MasterFailoverController implements LeaderElectorListener {

    private static final Logger logger = LoggerFactory.getLogger(MasterFailoverController.class);

    /**
     * Curator 客户端
     */
    private final CuratorFramework curatorClient;

    /**
     * 集群配置
     */
    private final ClusterConfig clusterConfig;

    /**
     * Broker 注册中心
     */
    private final com.aoaojiao.catmq.cluster.discovery.BrokerRegistry brokerRegistry;

    /**
     * 主节点选举器
     */
    private final LeaderElector leaderElector;

    /**
     * 故障检测定时器
     */
    private ScheduledExecutorService heartbeatChecker;

    /**
     * 监听器列表
     */
    private final List<MasterFailoverListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * 是否正在执行故障切换
     */
    private final AtomicBoolean failoverInProgress = new AtomicBoolean(false);

    /**
     * 当前主节点
     */
    private final AtomicReference<BrokerInfo> currentMaster = new AtomicReference<>();

    /**
     * 上一次确认的主节点
     */
    private final AtomicReference<BrokerInfo> confirmedMaster = new AtomicReference<>();

    /**
     * 是否已初始化
     */
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    /**
     * 主节点超时时间（毫秒）
     */
    private static final long MASTER_TIMEOUT_MS = 30000;

    /**
     * 检测间隔（毫秒）
     */
    private static final long CHECK_INTERVAL_MS = 5000;

    /**
     * 确认失败需要的连续次数
     */
    private static final int FAILURE_THRESHOLD = 3;

    /**
     * 连续失败的次数
     */
    private int consecutiveFailures = 0;

    /**
     * 构造函数
     *
     * @param curatorClient  Curator 客户端
     * @param clusterConfig  集群配置
     * @param brokerRegistry Broker 注册中心
     * @param leaderElector  主节点选举器
     */
    public MasterFailoverController(CuratorFramework curatorClient,
                                      ClusterConfig clusterConfig,
                                      com.aoaojiao.catmq.cluster.discovery.BrokerRegistry brokerRegistry,
                                      LeaderElector leaderElector) {
        this.curatorClient = curatorClient;
        this.clusterConfig = clusterConfig;
        this.brokerRegistry = brokerRegistry;
        this.leaderElector = leaderElector;
    }

    /**
     * 初始化
     */
    public void initialize() {
        if (initialized.get()) {
            return;
        }

        logger.info("初始化 MasterFailoverController");

        // 添加自身为选举监听器
        leaderElector.addListener(this);

        // 启动心跳检测
        startHeartbeatChecker();

        initialized.set(true);
        logger.info("MasterFailoverController 初始化完成");
    }

    /**
     * 启动心跳检测
     */
    private void startHeartbeatChecker() {
        heartbeatChecker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "MasterFailover-HeartbeatChecker");
            t.setDaemon(true);
            return t;
        });

        heartbeatChecker.scheduleAtFixedRate(() -> {
            try {
                checkMasterHealth();
            } catch (Exception e) {
                logger.error("心跳检测异常", e);
            }
        }, CHECK_INTERVAL_MS, CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS);

        logger.info("启动心跳检测，间隔={}ms", CHECK_INTERVAL_MS);
    }

    /**
     * 检查主节点健康状态
     */
    private void checkMasterHealth() {
        if (failoverInProgress.get()) {
            return;
        }

        BrokerInfo master = brokerRegistry.getMaster();

        if (master == null) {
            logger.debug("当前没有主节点");
            if (currentMaster.get() != null) {
                // 之前有主节点，现在没了，可能发生了故障
                handleMasterDisappeared();
            }
            return;
        }

        // 更新当前主节点
        currentMaster.set(master);

        // 检查是否是本地主节点
        if (isLocalBroker(master)) {
            // 本节点是主节点，说明当前选举正常
            consecutiveFailures = 0;
            if (confirmedMaster.get() == null) {
                confirmedMaster.set(master);
                logger.info("确认主节点：{}", master.getBrokerId());
            }
            return;
        }

        // 检查主节点是否超时
        if (master.isTimeout(MASTER_TIMEOUT_MS)) {
            consecutiveFailures++;
            logger.warn("主节点心跳超时检测：{}，连续失败次数={}",
                    master.getBrokerId(), consecutiveFailures);

            notifyFailureDetectionStarted(master);

            if (consecutiveFailures >= FAILURE_THRESHOLD) {
                // 确认主节点故障，执行切换
                handleMasterFailure(master);
            } else {
                notifyFailureDetectionCompleted(master, false);
            }
        } else {
            // 主节点正常
            consecutiveFailures = 0;
            if (confirmedMaster.get() == null) {
                confirmedMaster.set(master);
                logger.info("确认主节点：{}", master.getBrokerId());
            }
        }
    }

    /**
     * 处理主节点消失
     */
    private void handleMasterDisappeared() {
        logger.warn("主节点消失，尝试重新选举");

        if (!leaderElector.isLeader()) {
            // 触发重新选举
            try {
                // LeaderElector 会自动处理重新选举
                // 这里可以添加额外的处理逻辑
            } catch (Exception e) {
                logger.error("处理主节点消失失败", e);
            }
        }

        currentMaster.set(null);
        confirmedMaster.set(null);
    }

    /**
     * 处理主节点故障
     *
     * @param failedMaster 故障的主节点
     */
    private void handleMasterFailure(BrokerInfo failedMaster) {
        if (failoverInProgress.compareAndSet(false, true)) {
            logger.warn("主节点故障确认：{}", failedMaster.getBrokerId());

            notifyMasterFailed(failedMaster);

            try {
                // 主节点故障时，不影响本节点状态
                // 等待 LeaderElector 自动重新选举
                // 这里可以添加一些额外的处理，比如数据补偿等

                logger.info("等待重新选举新主节点");

            } catch (Exception e) {
                logger.error("处理主节点故障失败", e);
                failoverInProgress.set(false);
                notifyFailoverFailed("处理主节点故障失败：" + e.getMessage());
            }
        }
    }

    /**
     * 检查是否是本地 Broker
     *
     * @param broker Broker 信息
     * @return 是否是本地
     */
    private boolean isLocalBroker(BrokerInfo broker) {
        return broker != null
                && broker.getBrokerId().equals(clusterConfig.getBrokerId())
                && broker.getHost().equals(clusterConfig.getHost())
                && broker.getPort() == clusterConfig.getPort();
    }

    /**
     * 添加监听器
     *
     * @param listener 监听器
     */
    public void addListener(MasterFailoverListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    /**
     * 移除监听器
     *
     * @param listener 监听器
     */
    public void removeListener(MasterFailoverListener listener) {
        listeners.remove(listener);
    }

    /**
     * 通知主节点失败
     */
    private void notifyMasterFailed(BrokerInfo failedMaster) {
        for (MasterFailoverListener listener : listeners) {
            try {
                listener.onMasterFailed(failedMaster);
            } catch (Exception e) {
                logger.error("通知主节点失败失败", e);
            }
        }
    }

    /**
     * 通知新主节点被选举
     */
    private void notifyNewMasterElected(BrokerInfo newMaster) {
        for (MasterFailoverListener listener : listeners) {
            try {
                listener.onNewMasterElected(newMaster);
            } catch (Exception e) {
                logger.error("通知新主节点选举失败", e);
            }
        }
    }

    /**
     * 通知故障切换完成
     */
    private void notifyFailoverComplete(BrokerInfo oldMaster, BrokerInfo newMaster) {
        failoverInProgress.set(false);
        consecutiveFailures = 0;

        for (MasterFailoverListener listener : listeners) {
            try {
                listener.onFailoverComplete(oldMaster, newMaster);
            } catch (Exception e) {
                logger.error("通知故障切换完成失败", e);
            }
        }
    }

    /**
     * 通知故障切换失败
     */
    private void notifyFailoverFailed(String error) {
        failoverInProgress.set(false);

        for (MasterFailoverListener listener : listeners) {
            try {
                listener.onFailoverFailed(error);
            } catch (Exception e) {
                logger.error("通知故障切换失败失败", e);
            }
        }
    }

    /**
     * 通知故障检测开始
     */
    private void notifyFailureDetectionStarted(BrokerInfo suspectedMaster) {
        for (MasterFailoverListener listener : listeners) {
            try {
                listener.onFailureDetectionStarted(suspectedMaster);
            } catch (Exception e) {
                logger.error("通知故障检测开始失败", e);
            }
        }
    }

    /**
     * 通知故障检测完成
     */
    private void notifyFailureDetectionCompleted(BrokerInfo suspectedMaster, boolean confirmed) {
        for (MasterFailoverListener listener : listeners) {
            try {
                listener.onFailureDetectionCompleted(suspectedMaster, confirmed);
            } catch (Exception e) {
                logger.error("通知故障检测完成失败", e);
            }
        }
    }

    /**
     * 获取当前主节点
     *
     * @return 当前主节点
     */
    public BrokerInfo getCurrentMaster() {
        return currentMaster.get();
    }

    /**
     * 检查是否正在执行故障切换
     *
     * @return 是否正在切换
     */
    public boolean isFailoverInProgress() {
        return failoverInProgress.get();
    }

    /**
     * 获取确认的主节点
     *
     * @return 确认的主节点
     */
    public BrokerInfo getConfirmedMaster() {
        return confirmedMaster.get();
    }

    // ============ LeaderElectorListener 实现 ============

    @Override
    public void onElectedAsLeader(BrokerInfo leaderInfo) {
        logger.info("本节点被选举为新的主节点：{}", leaderInfo.getBrokerId());

        BrokerInfo oldMaster = confirmedMaster.get();
        confirmedMaster.set(leaderInfo);
        currentMaster.set(leaderInfo);

        // 更新注册中心中的角色
        brokerRegistry.updateRole(BrokerInfo.BrokerRole.MASTER);

        notifyNewMasterElected(leaderInfo);

        if (oldMaster != null && !oldMaster.getBrokerId().equals(leaderInfo.getBrokerId())) {
            notifyFailoverComplete(oldMaster, leaderInfo);
        }
    }

    @Override
    public void onLeaderRemoved(String previousLeaderId) {
        logger.info("主节点被移除：{}", previousLeaderId);

        if (previousLeaderId != null && previousLeaderId.equals(confirmedMaster.get().getBrokerId())) {
            confirmedMaster.set(null);
        }
    }

    @Override
    public void onLeaderChanged(BrokerInfo oldLeader, BrokerInfo newLeader) {
        logger.info("主节点变更：{} -> {}", oldLeader, newLeader);
    }

    @Override
    public void onElectionError(Exception error) {
        logger.error("选举错误", error);
        notifyFailoverFailed("选举错误：" + error.getMessage());
    }

    @Override
    public void onStateChanged(boolean isLeader, ClusterConfig clusterConfig) {
        if (isLeader) {
            logger.info("状态变更：本节点成为主节点");
            // 更新角色
            brokerRegistry.updateRole(BrokerInfo.BrokerRole.MASTER);
        } else {
            logger.info("状态变更：本节点成为从节点");
            brokerRegistry.updateRole(BrokerInfo.BrokerRole.SLAVE);
        }
    }

    /**
     * 关闭
     */
    public void shutdown() {
        logger.info("关闭 MasterFailoverController");

        if (heartbeatChecker != null) {
            heartbeatChecker.shutdown();
            try {
                if (!heartbeatChecker.awaitTermination(5, TimeUnit.SECONDS)) {
                    heartbeatChecker.shutdownNow();
                }
            } catch (InterruptedException e) {
                heartbeatChecker.shutdownNow();
            }
        }

        if (leaderElector != null) {
            leaderElector.removeListener(this);
        }
    }
}