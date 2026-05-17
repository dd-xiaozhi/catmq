package com.aoaojiao.catmq.cluster.sync;

import com.aoaojiao.catmq.cluster.model.BrokerInfo;
import com.aoaojiao.catmq.cluster.model.ClusterConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 半同步刷新策略
 * 主节点等待至少一个从节点写入成功才返回
 *
 * 特点：
 * - 在数据可靠性和性能之间取得平衡
 * - 至少保证一个从节点同步成功
 * - 适用于大多数生产环境
 *
 * @author DD
 */
public class SemiSyncWriteStrategy implements SyncStrategy {

    private static final Logger logger = LoggerFactory.getLogger(SemiSyncWriteStrategy.class);

    /**
     * 默认同步超时时间（毫秒）
     */
    private static final long DEFAULT_SYNC_TIMEOUT_MS = 3000;

    /**
     * 确认结果缓存（用于跟踪每个写入请求的确认状态）
     */
    private final ConcurrentHashMap<String, AtomicInteger> ackCache = new ConcurrentHashMap<>();

    @Override
    public SyncResult write(BrokerInfo master, List<BrokerInfo> slaves, byte[] data, ClusterConfig clusterConfig) {
        long startTime = System.currentTimeMillis();

        if (slaves == null || slaves.isEmpty()) {
            logger.warn("半同步刷新策略：没有可用的从节点，降级为异步写入");
            return SyncResult.asyncSuccess();
        }

        int minAckCount = getMinAckCount(slaves.size(), clusterConfig);
        if (minAckCount <= 0) {
            minAckCount = 1; // 至少等待一个
        }

        logger.info("半同步刷新策略：开始写入，主节点={}, 从节点数量={}, 最少确认数={}",
                master.getBrokerId(), slaves.size(), minAckCount);

        // 主节点先写入
        boolean masterWriteSuccess = writeToMaster(master, data);
        if (!masterWriteSuccess) {
            return SyncResult.failure("主节点写入失败");
        }

        // 并行写入从节点
        String writeId = String.valueOf(System.currentTimeMillis());
        AtomicInteger ackCounter = new AtomicInteger(0);
        ackCache.put(writeId, ackCounter);

        try {
            // 并发写入所有从节点
            CompletableFuture<Boolean>[] futures = new CompletableFuture[slaves.size()];
            for (int i = 0; i < slaves.size(); i++) {
                final BrokerInfo slave = slaves.get(i);
                futures[i] = CompletableFuture.supplyAsync(() ->
                        writeToSlaveWithAck(slave, data, writeId, clusterConfig)
                );
            }

            // 等待至少 minAckCount 个从节点确认
            int successCount = 0;
            for (int i = 0; i < futures.length; i++) {
                try {
                    Boolean success = futures[i].get(DEFAULT_SYNC_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    if (success) {
                        successCount++;
                        if (successCount >= minAckCount) {
                            // 已达到最少确认数，可以返回了
                            break;
                        }
                    }
                } catch (Exception e) {
                    logger.warn("等待从节点确认超时或失败：{}", slaves.get(i).getBrokerId());
                }
            }

            long costMs = System.currentTimeMillis() - startTime;

            if (successCount >= minAckCount) {
                logger.info("半同步刷新策略：写入成功，确认数={}, 耗时={}ms",
                        successCount, costMs);
                return SyncResult.success(successCount, costMs, "SEMI_SYNC");
            } else {
                logger.warn("半同步刷新策略：确认数不足，成功={}, 最少需要={}",
                        successCount, minAckCount);
                // 半同步模式下，即使没有足够确认也允许写入（降级）
                return SyncResult.success(successCount, costMs, "SEMI_SYNC_DEGRADED");
            }
        } finally {
            ackCache.remove(writeId);
        }
    }

    /**
     * 写入主节点
     *
     * @param master 主节点
     * @param data    数据
     * @return 是否成功
     */
    private boolean writeToMaster(BrokerInfo master, byte[] data) {
        // TODO: 实现实际的主节点写入逻辑
        logger.debug("写入主节点：{}", master.getBrokerId());
        return true;
    }

    /**
     * 写入从节点并确认
     *
     * @param slave      从节点
     * @param data       数据
     * @param writeId    写入 ID
     * @param config     配置
     * @return 是否成功
     */
    private boolean writeToSlaveWithAck(BrokerInfo slave, byte[] data, String writeId, ClusterConfig config) {
        try {
            // TODO: 实现实际的从节点写入逻辑
            logger.debug("半同步写入从节点：{}", slave.getBrokerId());

            // 模拟写入和确认
            AtomicInteger counter = ackCache.get(writeId);
            if (counter != null) {
                counter.incrementAndGet();
            }

            return true;
        } catch (Exception e) {
            logger.error("半同步写入从节点失败：{}", slave.getBrokerId(), e);
            return false;
        }
    }

    @Override
    public String getName() {
        return "SEMI_SYNC_WRITE";
    }

    @Override
    public ClusterConfig.SyncMode getMode() {
        return ClusterConfig.SyncMode.SEMI_SYNC;
    }

    @Override
    public boolean needWaitAck() {
        return true;
    }

    @Override
    public int getMinAckCount(int totalSlaves, ClusterConfig config) {
        return Math.min(config.getReplicationFactor(), totalSlaves);
    }
}