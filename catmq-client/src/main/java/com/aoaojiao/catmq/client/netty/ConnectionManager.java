package com.aoaojiao.catmq.client.netty;

import com.aoaojiao.catmq.client.config.ClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 连接管理器
 * 管理 Netty 客户端连接池
 *
 * @author DD
 */
public class ConnectionManager {

    private static final Logger log = LoggerFactory.getLogger(ConnectionManager.class);

    private final ClientConfig config;
    private final ConcurrentHashMap<String, NettyClient> clientPool = new ConcurrentHashMap<>();
    private final AtomicInteger roundRobin = new AtomicInteger(0);

    public ConnectionManager(ClientConfig config) {
        this.config = config;
    }

    /**
     * 获取连接
     *
     * @return Netty 客户端
     */
    public NettyClient getConnection() {
        String key = config.getBrokerAddress();

        NettyClient client = clientPool.get(key);
        if (client == null) {
            client = createConnection(key);
        }

        if (!client.isConnected()) {
            try {
                client.connect();
            } catch (Exception e) {
                log.error("Failed to connect to broker: {}", config.getBrokerAddress(), e);
                // 连接失败时从池中移除无效的客户端
                clientPool.remove(key);
                throw new RuntimeException("Connection to broker failed: " + e.getMessage(), e);
            }
        }

        return client;
    }

    /**
     * 创建连接
     */
    private synchronized NettyClient createConnection(String key) {
        NettyClient existing = clientPool.get(key);
        if (existing != null) {
            return existing;
        }

        NettyClient client = new NettyClient(config);
        clientPool.put(key, client);
        log.info("Created new connection: {}", key);

        return client;
    }

    /**
     * 获取下一个连接（轮询负载均衡）
     *
     * @return Netty 客户端
     */
    public NettyClient getNextConnection() {
        return getConnection();
    }

    /**
     * 关闭所有连接
     */
    public void shutdownAll() {
        log.info("Shutting down all connections...");
        clientPool.values().forEach(NettyClient::shutdown);
        clientPool.clear();
        log.info("All connections shutdown completed");
    }

    /**
     * 获取连接池大小
     */
    public int getConnectionPoolSize() {
        return clientPool.size();
    }
}