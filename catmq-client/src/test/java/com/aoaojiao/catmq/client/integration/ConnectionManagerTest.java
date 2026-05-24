package com.aoaojiao.catmq.client.integration;

import com.aoaojiao.catmq.client.config.ClientConfig;
import com.aoaojiao.catmq.client.netty.ConnectionManager;
import com.aoaojiao.catmq.client.netty.NettyClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * ConnectionManager 集成测试
 *
 * @author DD
 */
public class ConnectionManagerTest {

    private static final int TEST_BROKER_PORT = 19998;
    private static final String TEST_BROKER_ADDRESS = "localhost:" + TEST_BROKER_PORT;

    private EmbeddedBroker embeddedBroker;

    @Before
    public void setUp() throws Exception {
        // 启动嵌入式 Broker
        embeddedBroker = new EmbeddedBroker(TEST_BROKER_PORT, "connection-test-broker");
        embeddedBroker.start();

        // 等待 Broker 完全启动
        Thread.sleep(500);
    }

    @After
    public void tearDown() {
        if (embeddedBroker != null) {
            embeddedBroker.stop();
        }
    }

    /**
     * testConnectionEstablishment - 测试连接建立
     */
    @Test
    public void testConnectionEstablishment() {
        ClientConfig config = new ClientConfig();
        config.setBrokerAddress(TEST_BROKER_ADDRESS);
        config.setConnectTimeoutMs(5000);

        ConnectionManager connectionManager = new ConnectionManager(config);

        // 获取连接
        NettyClient client = connectionManager.getConnection();
        assertNotNull("应该能获取到连接", client);

        // 验证连接状态
        assertTrue("连接应该已建立", client.isConnected());

        // 清理
        connectionManager.shutdownAll();
    }

    /**
     * testConnectionReuse - 测试连接复用
     */
    @Test
    public void testConnectionReuse() {
        ClientConfig config = new ClientConfig();
        config.setBrokerAddress(TEST_BROKER_ADDRESS);
        config.setConnectTimeoutMs(5000);

        ConnectionManager connectionManager = new ConnectionManager(config);

        // 第一次获取连接
        NettyClient client1 = connectionManager.getConnection();
        assertNotNull("第一次获取连接应该成功", client1);
        assertTrue("第一次连接应该已建立", client1.isConnected());

        // 第二次获取相同地址的连接（应该复用）
        NettyClient client2 = connectionManager.getConnection();
        assertNotNull("第二次获取连接应该成功", client2);

        // 应该是同一个连接对象
        assertSame("相同地址应该复用连接", client1, client2);

        // 连接池大小应该为 1
        assertEquals("连接池大小应该为 1", 1, connectionManager.getConnectionPoolSize());

        // 清理
        connectionManager.shutdownAll();
    }

    /**
     * testConnectionFailureHandling - 测试连接失败处理
     */
    @Test
    public void testConnectionFailureHandling() {
        ClientConfig config = new ClientConfig();
        config.setBrokerAddress("localhost:19997"); // 不存在的端口
        config.setConnectTimeoutMs(2000);

        ConnectionManager connectionManager = new ConnectionManager(config);

        // 尝试获取连接应该失败
        try {
            connectionManager.getConnection();
            fail("连接不存在的 Broker 应该抛出异常");
        } catch (Exception e) {
            // 预期会抛出连接异常
            assertTrue("异常信息应该包含连接相关关键字",
                    e.getMessage().contains("connection") ||
                    e.getMessage().contains("Connection") ||
                    e.getMessage().contains("refused") ||
                    e.getMessage().contains("Failed"));
        }

        // 连接池应该为空
        assertEquals("连接池应该为空", 0, connectionManager.getConnectionPoolSize());
    }

    /**
     * testMultipleConnections - 测试多连接管理
     */
    @Test
    public void testMultipleConnections() {
        ClientConfig config1 = new ClientConfig();
        config1.setBrokerAddress(TEST_BROKER_ADDRESS);
        config1.setConnectTimeoutMs(5000);

        ClientConfig config2 = new ClientConfig();
        config2.setBrokerAddress(TEST_BROKER_ADDRESS);
        config2.setConnectTimeoutMs(5000);

        ConnectionManager connectionManager = new ConnectionManager(config1);

        // 获取两个连接（相同地址）
        NettyClient client1 = connectionManager.getConnection();
        NettyClient client2 = connectionManager.getConnection();

        // 应该是同一个连接
        assertSame("相同地址的连接应该复用", client1, client2);
        assertEquals("连接池大小应该为 1", 1, connectionManager.getConnectionPoolSize());

        // 清理
        connectionManager.shutdownAll();
    }

    /**
     * testShutdownAll - 测试关闭所有连接
     */
    @Test
    public void testShutdownAll() {
        ClientConfig config = new ClientConfig();
        config.setBrokerAddress(TEST_BROKER_ADDRESS);
        config.setConnectTimeoutMs(5000);

        ConnectionManager connectionManager = new ConnectionManager(config);

        // 获取连接
        NettyClient client = connectionManager.getConnection();
        assertNotNull("获取连接应该成功", client);
        assertTrue("连接应该已建立", client.isConnected());

        // 关闭所有连接
        connectionManager.shutdownAll();

        // 连接池应该为空
        assertEquals("连接池应该为空", 0, connectionManager.getConnectionPoolSize());
    }

    /**
     * testReconnectAfterShutdown - 测试关闭后重连
     */
    @Test
    public void testReconnectAfterShutdown() {
        ClientConfig config = new ClientConfig();
        config.setBrokerAddress(TEST_BROKER_ADDRESS);
        config.setConnectTimeoutMs(5000);

        ConnectionManager connectionManager = new ConnectionManager(config);

        // 第一次获取连接
        NettyClient client1 = connectionManager.getConnection();
        assertTrue("第一次连接应该成功", client1.isConnected());

        // 关闭所有连接
        connectionManager.shutdownAll();
        assertEquals("连接池应该为空", 0, connectionManager.getConnectionPoolSize());

        // 重新获取连接
        NettyClient client2 = connectionManager.getConnection();
        assertNotNull("重新获取连接应该成功", client2);
        assertTrue("重新连接应该成功", client2.isConnected());

        // 清理
        connectionManager.shutdownAll();
    }
}
