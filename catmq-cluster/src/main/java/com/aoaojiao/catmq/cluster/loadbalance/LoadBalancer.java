package com.aoaojiao.catmq.cluster.loadbalance;

import com.aoaojiao.catmq.cluster.model.BrokerInfo;

import java.util.List;

/**
 * 负载均衡器接口
 * 定义选择 Broker 的策略
 *
 * @author DD
 */
public interface LoadBalancer {

    /**
     * 选择一个 Broker
     *
     * @param brokers 可用的 Broker 列表
     * @param key     用于计算的 key（如 topic、message key 等）
     * @return 选中的 Broker，如果列表为空则返回 null
     */
    BrokerInfo select(List<BrokerInfo> brokers, String key);

    /**
     * 获取负载均衡策略名称
     *
     * @return 策略名称
     */
    String getName();

    /**
     * 更新路由信息（用于某些需要学习的策略）
     *
     * @param brokerId Broker ID
     * @param success  请求是否成功
     */
    default void updateRoute(String brokerId, boolean success) {
        // 默认实现为空，某些策略可能需要
    }

    /**
     * 重置路由状态
     */
    default void reset() {
        // 默认实现为空
    }
}