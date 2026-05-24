package com.aoaojiao.catmq.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Broker 信息
 * 统一所有模块的 BrokerInfo 定义
 *
 * @author DD
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BrokerInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Broker 唯一标识
     */
    private String brokerId;

    /**
     * Broker 名称（用于逻辑分组）
     */
    private String brokerName;

    /**
     * Broker IP 地址
     */
    private String brokerIp;

    /**
     * Broker 端口
     */
    private int brokerPort;

    /**
     * 节点角色：MASTER / SLAVE / FOLLOWER
     */
    private BrokerRole role;

    /**
     * Broker 状态：ACTIVE / INACTIVE / READONLY / RUNNING
     */
    private BrokerStatus status;

    /**
     * Broker 集群名称
     */
    private String clusterName;

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
     * Broker 是否存活
     */
    private boolean alive;

    /**
     * CPU 使用率
     */
    private Double cpuUsage;

    /**
     * 内存使用率
     */
    private Double memoryUsage;

    /**
     * 磁盘使用率
     */
    private Double diskUsage;

    /**
     * 活跃连接数
     */
    private Integer activeConnections;

    /**
     * Topic 数量
     */
    private Integer topicCount;

    /**
     * 该 Broker 上管理的 Topic 列表
     */
    private List<String> topicList;

    /**
     * 消息发送速率
     */
    private Double sendRate;

    /**
     * 消息消费速率
     */
    private Double consumeRate;

    /**
     * 时间戳（lastHeartbeat 的别名，用于兼容旧代码）
     */
    private Long timestamp;

    /**
     * 构造函数
     *
     * @param brokerId Broker ID
     * @param brokerName Broker 名称
     * @param brokerIp Broker IP
     * @param brokerPort Broker 端口
     */
    public BrokerInfo(String brokerId, String brokerName, String brokerIp, int brokerPort) {
        this.brokerId = brokerId;
        this.brokerName = brokerName;
        this.brokerIp = brokerIp;
        this.brokerPort = brokerPort;
        this.role = BrokerRole.SLAVE;
        this.status = BrokerStatus.ACTIVE;
        this.weight = 100;
        this.startTime = System.currentTimeMillis();
        this.lastHeartbeat = System.currentTimeMillis();
        this.alive = true;
        this.topicList = new ArrayList<>();
    }

    /**
     * 构造完整的地址: ip:port
     */
    public String getAddress() {
        return brokerIp + ":" + brokerPort;
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
        READONLY,

        /**
         * 运行中
         */
        RUNNING
    }
}
