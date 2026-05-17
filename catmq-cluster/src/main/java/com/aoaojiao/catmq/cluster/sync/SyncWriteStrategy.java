package com.aoaojiao.catmq.cluster.sync;

import com.aoaojiao.catmq.cluster.model.BrokerInfo;
import com.aoaojiao.catmq.cluster.model.ClusterConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 同步刷新策略
 * 主节点等待所有从节点写入成功才返回
 *
 * 特点：
 * - 数据安全性最高
 * - 延迟较高
 * - 适用于对数据可靠性要求极高的场景
 *
 * @author DD
 */
public class SyncWriteStrategy implements SyncStrategy {

    private static final Logger logger = LoggerFactory.getLogger(SyncWriteStrategy.class);

    /**
     * 默认同步超时时间（毫秒）
     */
    private static final long DEFAULT_SYNC_TIMEOUT_MS = 5000;

    @Override
    public SyncResult write(BrokerInfo master, List<BrokerInfo> slaves, byte[] data, ClusterConfig clusterConfig) {
        long startTime = System.currentTimeMillis();

        if (slaves == null || slaves.isEmpty()) {
            logger.warn("同步刷新策略：没有可用的从节点，仅在主节点写入");
            return SyncResult.failure("没有可用的从节点");
        }

        logger.info("同步刷新策略：开始同步写入，主节点={}, 从节点数量={}",
                master.getBrokerId(), slaves.size());

        int successCount = 0;
        int totalSlaves = slaves.size();

        // 同步写入每个从节点
        for (BrokerInfo slave : slaves) {
            try {
                // 模拟同步写入操作
                boolean success = writeToSlave(slave, data, DEFAULT_SYNC_TIMEOUT_MS);
                if (success) {
                    successCount++;
                }
            } catch (Exception e) {
                logger.error("同步写入从节点失败：{}", slave.getBrokerId(), e);
            }
        }

        long costMs = System.currentTimeMillis() - startTime;

        // 所有从节点都写入成功才算成功
        if (successCount == totalSlaves) {
            logger.info("同步刷新策略：写入成功，所有从节点确认，数量={}, 耗时={}ms",
                    successCount, costMs);
            return SyncResult.success(successCount, costMs, "SYNC");
        } else {
            logger.warn("同步刷新策略：写入部分失败，成功={}, 总数={}",
                    successCount, totalSlaves);
            // 同步模式要求全部成功，所以返回失败
            return SyncResult.failure("部分从节点写入失败，成功=" + successCount + ", 总数=" + totalSlaves);
        }
    }

    /**
     * 写入从节点
     *
     * @param slave      从节点
     * @param data       数据
     * @param timeoutMs  超时时间
     * @return 是否成功
     */
    private boolean writeToSlave(BrokerInfo slave, byte[] data, long timeoutMs) {
        // TODO: 实现实际的从节点写入逻辑
        // 这里应该通过 RPC 调用从节点的写入接口
        logger.debug("同步写入从节点：{}", slave.getBrokerId());

        // 模拟同步写入
        try {
            // 实际实现应该通过 Netty RPC 调用从节点
            // 这里简化处理
            return true;
        } catch (Exception e) {
            logger.error("写入从节点异常：{}", slave.getBrokerId(), e);
            return false;
        }
    }

    @Override
    public String getName() {
        return "SYNC_WRITE";
    }

    @Override
    public ClusterConfig.SyncMode getMode() {
        return ClusterConfig.SyncMode.SYNC;
    }

    @Override
    public boolean needWaitAck() {
        return true;
    }
}