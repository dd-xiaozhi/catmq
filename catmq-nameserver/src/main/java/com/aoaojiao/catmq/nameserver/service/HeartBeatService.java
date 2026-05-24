package com.aoaojiao.catmq.nameserver.service;

import com.aoaojiao.catmq.nameserver.config.NameServerConfig;
import com.aoaojiao.catmq.common.model.BrokerInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 心跳检测服务
 * 定期扫描存活的 Broker，将超时的 Broker 标记为不存活
 *
 * @author DD
 */
public class HeartBeatService {

    private static final Logger log = LoggerFactory.getLogger(HeartBeatService.class);

    private final RouteInfoManager routeInfoManager;
    private final NameServerConfig config;

    private final ScheduledExecutorService scheduledExecutor;
    private ScheduledFuture<?> heartBeatCheckTask;

    private final AtomicBoolean running = new AtomicBoolean(false);

    public HeartBeatService(RouteInfoManager routeInfoManager, NameServerConfig config) {
        this.routeInfoManager = routeInfoManager;
        this.config = config;
        this.scheduledExecutor = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "HeartBeatService-Scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 启动心跳检测服务
     */
    public void start() {
        if (!running.compareAndSet(false, true)) {
            log.warn("HeartBeatService already started");
            return;
        }

        log.info("Starting HeartBeatService, timeout: {}ms, check interval: {}ms",
                config.getHeartBeatTimeoutMs(), config.getHeartBeatCheckIntervalMs());

        // 启动定时任务
        heartBeatCheckTask = scheduledExecutor.scheduleAtFixedRate(
                this::checkBrokerHeartBeat,
                config.getHeartBeatCheckIntervalMs(),
                config.getHeartBeatCheckIntervalMs(),
                TimeUnit.MILLISECONDS
        );

        log.info("HeartBeatService started successfully");
    }

    /**
     * 检测所有 Broker 的心跳状态
     */
    private void checkBrokerHeartBeat() {
        try {
            long currentTime = System.currentTimeMillis();
            long timeout = config.getHeartBeatTimeoutMs();

            Collection<BrokerInfo> allBrokers = routeInfoManager.getAllBrokers();
            if (allBrokers.isEmpty()) {
                return;
            }

            int expiredCount = 0;
            for (BrokerInfo brokerInfo : allBrokers) {
                // 只检查存活的 Broker
                if (!brokerInfo.isAlive()) {
                    continue;
                }

                long timeDiff = currentTime - brokerInfo.getLastHeartbeat();
                if (timeDiff > timeout) {
                    // Broker 心跳超时，标记为不存活
                    brokerInfo.setAlive(false);
                    expiredCount++;

                    log.warn("Broker {} heartbeat expired, last update: {}ms ago, timeout: {}ms",
                            brokerInfo.getBrokerName(), timeDiff, timeout);
                }
            }

            if (expiredCount > 0) {
                log.info("Marked {} brokers as dead", expiredCount);
            }

        } catch (Exception e) {
            log.error("Error checking broker heart beat", e);
        }
    }

    /**
     * 停止心跳检测服务
     */
    public void shutdown() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        log.info("Shutting down HeartBeatService...");

        if (heartBeatCheckTask != null) {
            heartBeatCheckTask.cancel(false);
        }

        scheduledExecutor.shutdown();
        try {
            if (!scheduledExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduledExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        log.info("HeartBeatService shutdown completed");
    }

    /**
     * 获取当前存活的 Broker 数量
     */
    public int getAliveBrokerCount() {
        return routeInfoManager.getAliveBrokerCount();
    }

    /**
     * 获取已注册的 Broker 总数
     */
    public int getTotalBrokerCount() {
        return routeInfoManager.getBrokerCount();
    }
}