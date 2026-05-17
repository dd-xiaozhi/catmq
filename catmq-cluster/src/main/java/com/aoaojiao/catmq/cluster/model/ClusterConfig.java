package com.aoaojiao.catmq.cluster.model;

import lombok.Data;

/**
 * 集群配置信息
 * 用于配置集群相关的参数
 *
 * @author DD
 */
@Data
public class ClusterConfig {

    /**
     * ZooKeeper 连接地址，多个地址用逗号分隔
     * 例如：192.168.1.1:2181,192.168.1.2:2181,192.168.1.3:2181
     */
    private String zkAddress = "localhost:2181";

    /**
     * ZooKeeper 连接超时时间（毫秒）
     */
    private int zkConnectionTimeoutMs = 15000;

    /**
     * ZooKeeper 会话超时时间（毫秒）
     */
    private int zkSessionTimeoutMs = 60000;

    /**
     * ZooKeeper 根节点路径
     */
    private String zkRootPath = "/catmq";

    /**
     * Broker 注册节点路径
     */
    private String brokerRegistryPath = "/catmq/brokers";

    /**
     * Controller 选举路径
     */
    private String controllerElectionPath = "/catmq/controller";

    /**
     * 本 Broker ID
     */
    private String brokerId;

    /**
     * 本 Broker 名称
     */
    private String brokerName;

    /**
     * 本 Broker 主机地址
     */
    private String host;

    /**
     * 本 Broker 端口
     */
    private int port;

    /**
     * 负载均衡策略：ROUND_ROBIN / RANDOM / CONSISTENT_HASH
     */
    private LoadBalanceStrategy loadBalanceStrategy = LoadBalanceStrategy.ROUND_ROBIN;

    /**
     * 主从同步模式：SYNC / ASYNC / SEMI_SYNC
     */
    private SyncMode syncMode = SyncMode.ASYNC;

    /**
     * 是否为主节点
     */
    private boolean isController = false;

    /**
     * 心跳检测间隔（毫秒）
     */
    private long heartbeatIntervalMs = 30000;

    /**
     * 节点超时时间（毫秒）
     */
    private long nodeTimeoutMs = 120000;

    /**
     * 重新选举间隔（毫秒）
     */
    private long rebalanceIntervalMs = 60000;

    /**
     * 一致性哈希虚拟节点数量
     */
    private int virtualNodeCount = 100;

    /**
     * 复制因子（半同步模式下需要等待的从节点数量）
     */
    private int replicationFactor = 1;

    /**
     * 是否启用高可用
     */
    private boolean haEnabled = true;

    /**
     * 同步复制超时时间（毫秒）
     */
    private long syncTimeoutMs = 5000;

    /**
     * 负载均衡策略枚举
     */
    public enum LoadBalanceStrategy {
        /**
         * 轮询策略：依次选择每个节点
         */
        ROUND_ROBIN,

        /**
         * 随机策略：随机选择节点
         */
        RANDOM,

        /**
         * 一致性哈希策略：相同 key 映射到相同节点
         */
        CONSISTENT_HASH
    }

    /**
     * 主从同步模式枚举
     */
    public enum SyncMode {
        /**
         * 同步模式：主节点等待所有从节点写入成功才返回
         */
        SYNC,

        /**
         * 异步模式：主节点写入后立即返回
         */
        ASYNC,

        /**
         * 半同步模式：主节点等待至少一个从节点写入成功
         */
        SEMI_SYNC
    }
}