package com.aoaojiao.catmq.nameserver.protocol;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 客户端获取 Topic 路由请求
 *
 * @author DD
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class TopicRouteRequest extends BaseRequest {

    /**
     * Topic 名称
     */
    private String topic;

    /**
     * 客户端组名（可选，用于区分不同消费者/生产者）
     */
    private String clientGroup;

    /**
     * 是否需要完整的 Broker 信息（包含权重等）
     */
    private boolean fullBrokerInfo = false;
}