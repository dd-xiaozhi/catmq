package com.aoaojiao.catmq.cluster.model;

import lombok.Data;

/**
 * 集群状态信息
 * 描述整个集群的运行状态
 *
 * @author DD
 */
@Data
public class ClusterState {

    /**
     * 当前节点是否是主节点
     */
    private boolean isMaster;

    /**
     * 当前 Broker 信息
     */
    private BrokerInfo currentBroker;

    /**
     * 所有活跃的 Broker 列表
     */
    private volatile BrokerList brokerList;

    /**
     * 集群节点总数
     */
    private int totalBrokerCount;

    /**
     * 集群是否可用
     */
    private boolean available;

    /**
     * 最后更新时间
     */
    private long lastUpdateTime;

    /**
     * 构造函数
     */
    public ClusterState() {
        this.brokerList = new BrokerList();
        this.available = false;
        this.lastUpdateTime = System.currentTimeMillis();
    }

    /**
     * 更新状态
     *
     * @param isMaster 是否为主节点
     * @param currentBroker 当前 Broker 信息
     */
    public void updateState(boolean isMaster, BrokerInfo currentBroker) {
        this.isMaster = isMaster;
        this.currentBroker = currentBroker;
        this.lastUpdateTime = System.currentTimeMillis();
    }

    /**
     * 检查集群是否正常
     *
     * @return 是否正常
     */
    public boolean isHealthy() {
        return available && brokerList != null && !brokerList.isEmpty();
    }

    /**
     * 获取集群中主节点的数量
     *
     * @return 主节点数量
     */
    public int getMasterCount() {
        if (brokerList == null) {
            return 0;
        }
        return (int) brokerList.getAll().stream()
                .filter(b -> b.getRole() == BrokerInfo.BrokerRole.MASTER)
                .count();
    }

    /**
     * 获取可用的 Broker 数量
     *
     * @return 可用 Broker 数量
     */
    public int getAvailableBrokerCount() {
        if (brokerList == null) {
            return 0;
        }
        return (int) brokerList.getAll().stream()
                .filter(BrokerInfo::isAvailable)
                .count();
    }
}