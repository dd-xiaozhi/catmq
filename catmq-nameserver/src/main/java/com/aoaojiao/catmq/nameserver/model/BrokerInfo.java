package com.aoaojiao.catmq.nameserver.model;

import lombok.Data;

import java.io.Serializable;

/**
 * Broker 注册信息模型
 *
 * @author DD
 */
@Data
public class BrokerInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Broker 名称
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
     * Broker 序号（集群内唯一）
     */
    private int brokerId;

    /**
     * Broker 权重（用于负载均衡）
     */
    private int weight = 100;

    /**
     * 注册时间戳
     */
    private long lastUpdateTimestamp;

    /**
     * 是否存活
     */
    private volatile boolean alive = true;

    /**
     * 该 Broker 上管理的 Topic 列表
     */
    private String[] topicList;

    /**
     * Broker 集群名称
     */
    private String clusterName = "default-cluster";

    /**
     * 构造完整的地址: ip:port
     */
    public String getAddress() {
        return brokerIp + ":" + brokerPort;
    }
}