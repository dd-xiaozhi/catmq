package com.aoaojiao.catmq.cluster.loadbalance;

import com.aoaojiao.catmq.cluster.model.BrokerInfo;
import com.aoaojiao.catmq.cluster.model.ClusterConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 负载均衡器工厂
 * 根据配置创建和管理负载均衡器实例
 *
 * @author DD
 */
public class LoadBalancerFactory {

    private static final Logger logger = LoggerFactory.getLogger(LoadBalancerFactory.class);

    /**
     * 负载均衡器缓存
     */
    private static final Map<String, LoadBalancer> loadBalancers = new ConcurrentHashMap<>();

    /**
     * 根据策略类型创建负载均衡器
     *
     * @param strategy 策略类型
     * @return 负载均衡器实例
     */
    public static LoadBalancer create(ClusterConfig.LoadBalanceStrategy strategy) {
        return create(strategy, 0);
    }

    /**
     * 根据策略类型和配置创建负载均衡器
     *
     * @param strategy         策略类型
     * @param virtualNodeCount 虚拟节点数量（用于一致性哈希）
     * @return 负载均衡器实例
     */
    public static LoadBalancer create(ClusterConfig.LoadBalanceStrategy strategy, int virtualNodeCount) {
        String key = buildKey(strategy, virtualNodeCount);

        return loadBalancers.computeIfAbsent(key, k -> {
            logger.info("创建负载均衡器：策略={}, 虚拟节点数={}", strategy, virtualNodeCount);
            switch (strategy) {
                case ROUND_ROBIN:
                    return new RoundRobinLoadBalancer();
                case RANDOM:
                    return new RandomLoadBalancer();
                case CONSISTENT_HASH:
                    return new ConsistentHashLoadBalancer(virtualNodeCount > 0 ? virtualNodeCount : 100);
                default:
                    logger.warn("未知的负载均衡策略：{}，使用默认轮询", strategy);
                    return new RoundRobinLoadBalancer();
            }
        });
    }

    /**
     * 根据 Broker 列表和策略选择 Broker
     *
     * @param brokers  Broker 列表
     * @param strategy 策略类型
     * @param key      用于计算的 key
     * @return 选中的 Broker
     */
    public static BrokerInfo select(List<BrokerInfo> brokers, ClusterConfig.LoadBalanceStrategy strategy, String key) {
        LoadBalancer balancer = create(strategy);
        return balancer.select(brokers, key);
    }

    /**
     * 根据 Broker 列表和策略选择 Broker
     *
     * @param brokers  Broker 列表
     * @param strategy 策略类型
     * @param key      用于计算的 key
     * @param virtualNodeCount 虚拟节点数量
     * @return 选中的 Broker
     */
    public static BrokerInfo select(List<BrokerInfo> brokers, ClusterConfig.LoadBalanceStrategy strategy,
                                    String key, int virtualNodeCount) {
        LoadBalancer balancer = create(strategy, virtualNodeCount);
        return balancer.select(brokers, key);
    }

    /**
     * 构建缓存 key
     *
     * @param strategy         策略类型
     * @param virtualNodeCount 虚拟节点数量
     * @return 缓存 key
     */
    private static String buildKey(ClusterConfig.LoadBalanceStrategy strategy, int virtualNodeCount) {
        return strategy.name() + "-" + virtualNodeCount;
    }

    /**
     * 清空负载均衡器缓存
     */
    public static void clear() {
        loadBalancers.clear();
        logger.info("清空负载均衡器缓存");
    }

    /**
     * 重置指定策略的负载均衡器
     *
     * @param strategy 策略类型
     */
    public static void reset(ClusterConfig.LoadBalanceStrategy strategy) {
        String key = buildKey(strategy, 0);
        LoadBalancer balancer = loadBalancers.get(key);
        if (balancer != null) {
            balancer.reset();
            logger.info("重置负载均衡器：{}", strategy);
        }
    }
}