package com.aoaojiao.catmq.admin.model;

import lombok.Data;

/**
 * Broker 信息
 *
 * @author DD
 */
@Data
public class BrokerInfo {

    /**
     * Broker 名称
     */
    private String brokerName;

    /**
     * Broker 状态
     */
    private String status;

    /**
     * 心跳时间戳
     */
    private Long timestamp;

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
     * 消息发送速率
     */
    private Double sendRate;

    /**
     * 消息消费速率
     */
    private Double consumeRate;
}