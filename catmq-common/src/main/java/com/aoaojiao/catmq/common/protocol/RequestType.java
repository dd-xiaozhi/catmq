package com.aoaojiao.catmq.common.protocol;

/**
 * 请求类型常量
 *
 * @author DD
 */
public interface RequestType {

    /**
     * Broker 注册
     */
    int BROKER_REGISTER = 1001;

    /**
     * Broker 心跳
     */
    int BROKER_HEART_BEAT = 1002;

    /**
     * Broker 注销
     */
    int BROKER_UN_REGISTER = 1003;

    /**
     * 查询 Topic 路由
     */
    int GET_TOPIC_ROUTE = 2001;

    /**
     * 查询所有 Broker
     */
    int GET_ALL_BROKER = 2002;

    /**
     * Broker 注册响应
     */
    int BROKER_REGISTER_RESPONSE = 3001;

    /**
     * 心跳响应
     */
    int HEART_BEAT_RESPONSE = 3002;

    /**
     * Topic 路由响应
     */
    int TOPIC_ROUTE_RESPONSE = 3003;

    /**
     * 查询所有 Broker 响应
     */
    int GET_ALL_BROKER_RESPONSE = 3004;

    /**
     * 通用错误响应
     */
    int ERROR_RESPONSE = 9999;
}
