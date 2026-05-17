package com.aoaojiao.catmq.nameserver.config;

import lombok.Data;

/**
 * NameServer 配置类
 *
 * @author DD
 */
@Data
public class NameServerConfig {

    /**
     * NameServer 监听端口
     */
    private int serverPort = 9876;

    /**
     * Broker 注册表文件路径
     */
    private String brokerRegistryPath = "./catmq/nameserver/brokerRegistry.json";

    /**
     * 路由信息文件路径
     */
    private String routeInfoPath = "./catmq/nameserver/routeInfo.json";

    /**
     * 心跳超时时间（毫秒）
     * 超过此时间未收到心跳的 Broker 将被移除
     */
    private long heartBeatTimeoutMs = 30000;

    /**
     * 心跳检测间隔（毫秒）
     */
    private long heartBeatCheckIntervalMs = 5000;

    /**
     * Netty 业务线程数
     */
    private int bossThreadCount = 1;
    private int workerThreadCount = 16;

    /**
     * Netty 洪泛攻击防护：单连接最大帧长度
     */
    private int maxFrameLength = 1048576; // 1MB

    /**
     * 路由信息持久化间隔（毫秒）
     */
    private long persistRouteInfoIntervalMs = 10000;
}