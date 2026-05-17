package com.aoaojiao.catmq.cluster.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Broker 节点信息
 * 描述集群中每个 broker 节点的基本信息
 *
 * @author DD
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BrokerInfo {

    /**
     * Broker 唯一标识
     */
    private String brokerId;

    /**
     * Broker 名称（用于逻辑分组）
     */
    private String brokerName;

    /**
     * Broker 主机地址
     */
    private String host;

    /**
     * Broker 端口
     */
    private int port;

    /**
     * 节点角色：MASTER / SLAVE / FOLLOWER
     */
    private BrokerRole role;

    /**
     * Broker 状态：ACTIVE / INACTIVE / READONLY
     */
    private BrokerStatus status;

    /**
     * 权重（用于负载均衡）
     */
    private int weight;

    /**
     * 启动时间戳
     */
    private long startTime;

    /**
     * 最后心跳时间戳
     */
    private long lastHeartbeat;

    /**
     * Broker 是否可用
     */
    private boolean available;

    /**
     * 构造函数
     *
     * @param brokerId Broker ID
     * @param brokerName Broker 名称
     * @param host 主机地址
     * @param port 端口
     */
    public BrokerInfo(String brokerId, String brokerName, String host, int port) {
        this.brokerId = brokerId;
        this.brokerName = brokerName;
        this.host = host;
        this.port = port;
        this.role = BrokerRole.SLAVE;
        this.status = BrokerStatus.ACTIVE;
        this.weight = 100;
        this.startTime = System.currentTimeMillis();
        this.lastHeartbeat = System.currentTimeMillis();
        this.available = true;
    }

    /**
     * 获取 Broker 地址
     *
     * @return 地址字符串 (host:port)
     */
    public String getAddress() {
        return host + ":" + port;
    }

    /**
     * 更新心跳时间
     */
    public void updateHeartbeat() {
        this.lastHeartbeat = System.currentTimeMillis();
    }

    /**
     * 检查 Broker 是否超时（超过指定时间未心跳）
     *
     * @param timeoutMillis 超时时间（毫秒）
     * @return 是否超时
     */
    public boolean isTimeout(long timeoutMillis) {
        return System.currentTimeMillis() - lastHeartbeat > timeoutMillis;
    }

    /**
     * Broker 角色枚举
     */
    public enum BrokerRole {
        /**
         * 主节点：处理所有写请求，是集群中唯一可以写入的节点
         */
        MASTER,

        /**
         * 从节点：同步主节点数据，可处理读请求
         */
        SLAVE,

        /**
         * 跟随节点：在某些实现中与 SLAVE 类似
         */
        FOLLOWER
    }

    /**
     * Broker 状态枚举
     */
    public enum BrokerStatus {
        /**
         * 活跃：正常运行，可接受请求
         */
        ACTIVE,

        /**
         * 非活跃：节点不可用
         */
        INACTIVE,

        /**
         * 只读：可以处理读请求，但不能写入
         */
        READONLY
    }
}