package com.aoaojiao.catmq.broker.config;

import lombok.Data;

/**
 * Broker 配置类
 *
 * @author DD
 */
@Data
public class BrokerConfig {

    /**
     * 心跳间隔（毫秒）
     * Broker 向 NameServer 发送心跳的间隔时间
     */
    private long heartbeatInterval = 30000;

}
