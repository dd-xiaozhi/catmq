package com.aoaojiao.catmq.cluster.discovery;

import com.aoaojiao.catmq.cluster.model.BrokerInfo;
import com.aoaojiao.catmq.cluster.model.BrokerList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Broker 发现监听器
 * 监听集群中 Broker 的变化
 *
 * @author DD
 */
public interface BrokerDiscoveryListener {

    /**
     * 当新的 Broker 加入集群时调用
     *
     * @param broker 新加入的 Broker
     */
    void onBrokerAdded(BrokerInfo broker);

    /**
     * 当 Broker 从集群移除时调用
     *
     * @param brokerId 被移除的 Broker ID
     */
    void onBrokerRemoved(String brokerId);

    /**
     * 当 Broker 状态变化时调用
     *
     * @param broker 更新后的 Broker
     */
    void onBrokerUpdated(BrokerInfo broker);

    /**
     * 当 Broker 列表变化时调用
     *
     * @param brokers 最新的 Broker 列表
     */
    void onBrokerListChanged(List<BrokerInfo> brokers);

    /**
     * 当发现服务不可用时调用
     *
     * @param error 错误信息
     */
    void onDiscoveryError(Exception error);

    /**
     * 当主节点变更时调用
     *
     * @param oldMaster 旧主节点
     * @param newMaster 新主节点
     */
    void onMasterChanged(BrokerInfo oldMaster, BrokerInfo newMaster);
}