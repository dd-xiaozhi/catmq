package com.aoaojiao.catmq.cluster.sync;

import com.aoaojiao.catmq.cluster.model.BrokerInfo;
import com.aoaojiao.catmq.cluster.model.ClusterConfig;
import com.aoaojiao.catmq.cluster.model.BrokerList;

import java.util.List;

/**
 * 同步策略接口
 * 定义主从数据同步的方式
 *
 * @author DD
 */
public interface SyncStrategy {

    /**
     * 执行同步写入
     *
     * @param master       主节点
     * @param slaves       从节点列表
     * @param data         要同步的数据
     * @param clusterConfig 集群配置
     * @return 同步结果
     */
    SyncResult write(BrokerInfo master, List<BrokerInfo> slaves, byte[] data, ClusterConfig clusterConfig);

    /**
     * 获取同步模式名称
     *
     * @return 模式名称
     */
    String getName();

    /**
     * 获取同步模式类型
     *
     * @return 模式类型
     */
    ClusterConfig.SyncMode getMode();

    /**
     * 是否需要等待从节点确认
     *
     * @return 是否需要等待
     */
    default boolean needWaitAck() {
        return true;
    }

    /**
     * 获取最少需要确认的从节点数量
     *
     * @param totalSlaves 从节点总数
     * @param config      集群配置
     * @return 最少确认数量
     */
    default int getMinAckCount(int totalSlaves, ClusterConfig config) {
        switch (getMode()) {
            case SYNC:
                return totalSlaves; // 同步模式需要所有从节点确认
            case SEMI_SYNC:
                return Math.min(config.getReplicationFactor(), totalSlaves); // 半同步至少等待 N 个
            case ASYNC:
            default:
                return 0; // 异步模式不需要等待
        }
    }
}