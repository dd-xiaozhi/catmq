package com.aoaojiao.catmq.cluster.sync;

import com.aoaojiao.catmq.cluster.model.BrokerInfo;
import com.aoaojiao.catmq.cluster.model.ClusterConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 异步刷新策略
 * 主节点写入后立即返回，不等待从节点确认
 *
 * 特点：
 * - 延迟最低
 * - 吞吐量最高
 * - 数据可能丢失（主节点故障时）
 *
 * @author DD
 */
public class AsyncWriteStrategy implements SyncStrategy {

    private static final Logger logger = LoggerFactory.getLogger(AsyncWriteStrategy.class);

    /**
     * 异步执行器
     */
    private final ExecutorService asyncExecutor = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors() * 2
    );

    @Override
    public SyncResult write(BrokerInfo master, List<BrokerInfo> slaves, byte[] data, ClusterConfig clusterConfig) {
        long startTime = System.currentTimeMillis();

        logger.info("异步刷新策略：开始异步写入，主节点={}, 从节点数量={}",
                master.getBrokerId(), slaves != null ? slaves.size() : 0);

        // 主节点先写入
        boolean masterWriteSuccess = writeToMaster(master, data);
        if (!masterWriteSuccess) {
            return SyncResult.failure("主节点写入失败");
        }

        // 异步同步到从节点
        if (slaves != null && !slaves.isEmpty()) {
            for (BrokerInfo slave : slaves) {
                // 异步执行，不阻塞
                CompletableFuture.runAsync(() -> {
                    try {
                        writeToSlaveAsync(slave, data);
                    } catch (Exception e) {
                        logger.error("异步写入从节点失败：{}", slave.getBrokerId(), e);
                    }
                }, asyncExecutor);
            }
        }

        long costMs = System.currentTimeMillis() - startTime;
        logger.info("异步刷新策略：主节点写入完成，耗时={}ms", costMs);

        return SyncResult.asyncSuccess();
    }

    /**
     * 写入主节点
     *
     * @param master 主节点
     * @param data   数据
     * @return 是否成功
     */
    private boolean writeToMaster(BrokerInfo master, byte[] data) {
        // TODO: 实现实际的主节点写入逻辑
        logger.debug("写入主节点：{}", master.getBrokerId());
        return true;
    }

    /**
     * 异步写入从节点
     *
     * @param slave 从节点
     * @param data  数据
     */
    private void writeToSlaveAsync(BrokerInfo slave, byte[] data) {
        // TODO: 实现实际的从节点写入逻辑
        logger.debug("异步写入从节点：{}", slave.getBrokerId());
    }

    @Override
    public String getName() {
        return "ASYNC_WRITE";
    }

    @Override
    public ClusterConfig.SyncMode getMode() {
        return ClusterConfig.SyncMode.ASYNC;
    }

    @Override
    public boolean needWaitAck() {
        return false;
    }

    @Override
    public int getMinAckCount(int totalSlaves, ClusterConfig config) {
        return 0;
    }

    /**
     * 关闭异步执行器
     */
    public void shutdown() {
        asyncExecutor.shutdown();
    }
}