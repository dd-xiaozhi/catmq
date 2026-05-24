package com.aoaojiao.catmq.cluster.model;

import com.aoaojiao.catmq.common.model.BrokerInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Broker 列表
 * 线程安全的 Broker 集合
 *
 * @author DD
 */
public class BrokerList {

    /**
     * Broker 列表
     */
    private final List<BrokerInfo> brokers;

    /**
     * 构造函数
     */
    public BrokerList() {
        this.brokers = new CopyOnWriteArrayList<>();
    }

    /**
     * 构造函数
     *
     * @param brokers 初始 Broker 列表
     */
    public BrokerList(List<BrokerInfo> brokers) {
        this.brokers = new CopyOnWriteArrayList<>(brokers);
    }

    /**
     * 添加 Broker
     *
     * @param broker Broker 信息
     */
    public void add(BrokerInfo broker) {
        brokers.add(broker);
    }

    /**
     * 移除 Broker
     *
     * @param brokerId Broker ID
     */
    public void remove(String brokerId) {
        brokers.removeIf(b -> b.getBrokerId().equals(brokerId));
    }

    /**
     * 获取 Broker 数量
     *
     * @return Broker 数量
     */
    public int size() {
        return brokers.size();
    }

    /**
     * 是否为空
     *
     * @return 是否为空
     */
    public boolean isEmpty() {
        return brokers.isEmpty();
    }

    /**
     * 获取所有 Broker
     *
     * @return Broker 列表
     */
    public List<BrokerInfo> getAll() {
        return new ArrayList<>(brokers);
    }

    /**
     * 获取所有活跃的 Broker
     *
     * @return 活跃 Broker 列表
     */
    public List<BrokerInfo> getActiveBrokers() {
        List<BrokerInfo> activeBrokers = new ArrayList<>();
        for (BrokerInfo broker : brokers) {
            if (broker.isAlive() && broker.getStatus() == BrokerInfo.BrokerStatus.ACTIVE) {
                activeBrokers.add(broker);
            }
        }
        return activeBrokers;
    }

    /**
     * 获取主节点
     *
     * @return 主节点，如果没有则返回 null
     */
    public BrokerInfo getMaster() {
        for (BrokerInfo broker : brokers) {
            if (broker.getRole() == BrokerInfo.BrokerRole.MASTER && broker.isAlive()) {
                return broker;
            }
        }
        return null;
    }

    /**
     * 获取所有从节点
     *
     * @return 从节点列表
     */
    public List<BrokerInfo> getSlaves() {
        List<BrokerInfo> slaves = new ArrayList<>();
        for (BrokerInfo broker : brokers) {
            if (broker.getRole() == BrokerInfo.BrokerRole.SLAVE && broker.isAlive()) {
                slaves.add(broker);
            }
        }
        return slaves;
    }

    /**
     * 根据 Broker ID 获取 Broker
     *
     * @param brokerId Broker ID
     * @return Broker 信息
     */
    public BrokerInfo getById(String brokerId) {
        for (BrokerInfo broker : brokers) {
            if (broker.getBrokerId().equals(brokerId)) {
                return broker;
            }
        }
        return null;
    }

    /**
     * 清空列表
     */
    public void clear() {
        brokers.clear();
    }

    /**
     * 替换整个列表
     *
     * @param newBrokers 新的 Broker 列表
     */
    public void replaceAll(List<BrokerInfo> newBrokers) {
        brokers.clear();
        brokers.addAll(newBrokers);
    }

    @Override
    public String toString() {
        return "BrokerList{" +
                "brokers=" + brokers +
                ", count=" + brokers.size() +
                '}';
    }
}