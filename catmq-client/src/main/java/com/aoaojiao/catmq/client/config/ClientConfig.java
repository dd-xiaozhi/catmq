package com.aoaojiao.catmq.client.config;

import lombok.Data;

/**
 * 客户端配置
 *
 * @author DD
 */
@Data
public class ClientConfig {

    /**
     * Broker 地址（暂时写死本地地址）
     */
    private String brokerAddress = "localhost:8080";

    /**
     * 连接超时时间（毫秒）
     */
    private int connectTimeoutMs = 5000;

    /**
     * 请求超时时间（毫秒）
     */
    private int requestTimeoutMs = 3000;

    /**
     * 最大重试次数
     */
    private int maxRetryTimes = 3;

    /**
     * 重试间隔时间（毫秒）
     */
    private int retryIntervalMs = 1000;

    /**
     * 消费者拉取间隔（毫秒）
     */
    private long pullIntervalMs = 1000;

    /**
     * 单次拉取最大消息数
     */
    private int maxPullMessageCount = 10;

    /**
     * 最大连接数
     */
    private int maxConnectionPoolSize = 5;

    /**
     * 发送消息时是否等待 broker 响应
     */
    private boolean waitForSendResponse = true;

    /**
     * 构建默认配置
     */
    public static ClientConfig defaultConfig() {
        ClientConfig config = new ClientConfig();
        config.setBrokerAddress("localhost:8080");
        config.setConnectTimeoutMs(5000);
        config.setRequestTimeoutMs(3000);
        config.setMaxRetryTimes(3);
        config.setRetryIntervalMs(1000);
        config.setPullIntervalMs(1000);
        config.setMaxPullMessageCount(10);
        config.setMaxConnectionPoolSize(5);
        config.setWaitForSendResponse(true);
        return config;
    }
}