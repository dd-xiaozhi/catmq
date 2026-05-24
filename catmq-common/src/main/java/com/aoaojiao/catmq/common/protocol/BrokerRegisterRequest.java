package com.aoaojiao.catmq.common.protocol;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Broker 注册请求
 *
 * @author DD
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class BrokerRegisterRequest extends BaseRequest {

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
     * Broker 序号
     */
    private int brokerId;

    /**
     * Broker 权重
     */
    private int weight;

    /**
     * 集群名称
     */
    private String clusterName;

    /**
     * 该 Broker 管理的 Topic 列表
     */
    private String[] topicList;

    /**
     * 从 BrokerInfo 构造注册请求
     */
    public static BrokerRegisterRequest fromBrokerInfo(BrokerInfo brokerInfo) {
        BrokerRegisterRequest request = new BrokerRegisterRequest();
        request.setBrokerName(brokerInfo.getBrokerName());
        request.setBrokerIp(brokerInfo.getBrokerIp());
        request.setBrokerPort(brokerInfo.getBrokerPort());
        request.setBrokerId(brokerInfo.getBrokerId());
        request.setWeight(brokerInfo.getWeight());
        request.setClusterName(brokerInfo.getClusterName());
        request.setTopicList(brokerInfo.getTopicList());
        return request;
    }

    /**
     * 转换为 BrokerInfo
     */
    public BrokerInfo toBrokerInfo() {
        BrokerInfo brokerInfo = new BrokerInfo();
        brokerInfo.setBrokerName(this.brokerName);
        brokerInfo.setBrokerIp(this.brokerIp);
        brokerInfo.setBrokerPort(this.brokerPort);
        brokerInfo.setBrokerId(this.brokerId);
        brokerInfo.setWeight(this.weight);
        brokerInfo.setClusterName(this.clusterName);
        brokerInfo.setTopicList(this.topicList);
        return brokerInfo;
    }
}
