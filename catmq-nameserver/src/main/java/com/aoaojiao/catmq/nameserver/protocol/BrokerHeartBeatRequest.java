package com.aoaojiao.catmq.nameserver.protocol;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Broker 心跳请求
 *
 * @author DD
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class BrokerHeartBeatRequest extends BaseRequest {

    /**
     * Broker 名称
     */
    private String brokerName;

    /**
     * Broker IP
     */
    private String brokerIp;

    /**
     * Broker 端口
     */
    private int brokerPort;

    /**
     * Broker 序号
     */
    private String brokerId;

    /**
     * 当前该 Broker 管理的 Topic 列表（可能发生变化）
     */
    private List<String> topicList;
}