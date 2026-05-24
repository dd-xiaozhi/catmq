package com.aoaojiao.catmq.nameserver.model;

import com.aoaojiao.catmq.common.model.BrokerInfo;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * Topic 路由信息模型
 *
 * @author DD
 */
@Data
public class TopicRouteInfo implements Serializable {

    private static final long serialVersionUI = 1L;

    /**
     * Topic 名称
     */
    private String topic;

    /**
     * 拥有该 Topic 的 Broker 列表
     */
    private List<BrokerInfo> brokerInfoList;

    /**
     * 队列数量
     */
    private int queueCount;

    /**
     * 读队列数量
     */
    private int readQueueNums = 4;

    /**
     * 写队列数量
     */
    private int writeQueueNums = 4;

    /**
     * 路由信息更新时间戳
     */
    private long updateTimestamp;
}