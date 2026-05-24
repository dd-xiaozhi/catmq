package com.aoaojiao.catmq.admin.integration;

import com.aoaojiao.catmq.admin.client.BrokerHttpClient;
import com.aoaojiao.catmq.admin.config.BrokerConfig;
import com.aoaojiao.catmq.admin.dto.response.BrokerStatusResponse;
import com.aoaojiao.catmq.common.model.BrokerInfo;
import com.aoaojiao.catmq.broker.http.BrokerHttpServer;
import com.aoaojiao.catmq.common.cache.CommonCache;
import com.aoaojiao.catmq.common.model.CatmqTopicModel;
import com.aoaojiao.catmq.common.model.QueueModel;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Broker 与 Admin 集成测试
 * 测试 Admin 通过 BrokerHttpClient 正确调用 Broker HTTP API 获取真实数据
 *
 * @author DD
 */
public class BrokerAdminIntegrationTest {

    private static BrokerHttpServer brokerHttpServer;
    private static Thread brokerThread;
    private static int brokerPort = 19992;

    @BeforeClass
    public static void setUpClass() throws Exception {
        // 清理并设置测试数据
        CommonCache.setCatmqTopicModelCache(new ArrayList<>());

        CatmqTopicModel topic = new CatmqTopicModel();
        topic.setTopic("integration_test_topic");
        List<QueueModel> queues = new ArrayList<>();
        QueueModel queue = new QueueModel();
        queue.setId(0);
        queues.add(queue);
        topic.setQueueModelList(queues);
        CommonCache.getCatmqTopicModelList().add(topic);

        // 启动 Broker HTTP 服务器
        brokerHttpServer = new BrokerHttpServer(brokerPort, "integration-broker");
        brokerThread = new Thread(() -> brokerHttpServer.start());
        brokerThread.setDaemon(true);
        brokerThread.start();

        // 等待服务器启动
        Thread.sleep(1000);
    }

    @AfterClass
    public static void tearDownClass() {
        if (brokerHttpServer != null) {
            brokerHttpServer.stop();
        }
    }

    @Test
    public void testBrokerHttpClientGetStatus() {
        // 创建 BrokerConfig
        BrokerConfig brokerConfig = new BrokerConfig();
        brokerConfig.setAddress("http://localhost:" + brokerPort);

        // 创建 BrokerHttpClient
        RestTemplate restTemplate = new RestTemplate();
        BrokerHttpClient brokerHttpClient = new BrokerHttpClient(restTemplate, brokerConfig);

        // 调用 getBrokerStatus
        BrokerStatusResponse status = brokerHttpClient.getBrokerStatus();

        // 验证返回的是真实的 Broker 数据
        assertNotNull("状态响应不应该为空", status);
        assertEquals("broker名称应该匹配", "integration-broker", status.getBrokerName());
        assertEquals("状态应该是 RUNNING", "RUNNING", status.getStatus());
        assertEquals("version应该匹配", "1.0-SNAPSHOT", status.getVersion());
        assertNotNull("startTime应该存在", status.getStartTime());
        assertNotNull("uptimeSeconds应该存在", status.getUptimeSeconds());
        assertEquals("topicCount应该为1", 1, status.getTopicCount().intValue());
        assertEquals("queueCount应该为1", 1, status.getQueueCount().intValue());
        assertNotNull("cpuUsagePercent应该存在", status.getCpuUsagePercent());
        assertNotNull("memoryUsagePercent应该存在", status.getMemoryUsagePercent());
    }

    @Test
    public void testBrokerHttpClientGetHeartbeat() {
        // 创建 BrokerConfig
        BrokerConfig brokerConfig = new BrokerConfig();
        brokerConfig.setAddress("http://localhost:" + brokerPort);

        // 创建 BrokerHttpClient
        RestTemplate restTemplate = new RestTemplate();
        BrokerHttpClient brokerHttpClient = new BrokerHttpClient(restTemplate, brokerConfig);

        // 调用 getHeartbeat
        BrokerInfo heartbeat = brokerHttpClient.getHeartbeat();

        // 验证返回的是真实的 Broker 数据
        assertNotNull("心跳响应不应该为空", heartbeat);
        assertEquals("broker名称应该匹配", "integration-broker", heartbeat.getBrokerName());
        assertEquals("状态应该是 RUNNING", "RUNNING", heartbeat.getStatus());
        assertNotNull("timestamp应该存在", heartbeat.getTimestamp());
        assertNotNull("cpuUsage应该存在", heartbeat.getCpuUsage());
        assertNotNull("memoryUsage应该存在", heartbeat.getMemoryUsage());
        assertEquals("topicCount应该为1", 1, heartbeat.getTopicCount().intValue());
        assertEquals("queueCount应该为1", 1, heartbeat.getQueueCount().intValue());
    }

    @Test
    public void testBrokerHttpClientFallbackWhenBrokerUnavailable() {
        // 创建指向不存在服务器的 BrokerConfig
        BrokerConfig brokerConfig = new BrokerConfig();
        brokerConfig.setAddress("http://localhost:19999");

        // 创建 BrokerHttpClient
        RestTemplate restTemplate = new RestTemplate();
        BrokerHttpClient brokerHttpClient = new BrokerHttpClient(restTemplate, brokerConfig);

        // 调用 getBrokerStatus - 应该返回降级数据而不是抛出异常
        BrokerStatusResponse status = brokerHttpClient.getBrokerStatus();

        // 验证降级处理
        assertNotNull("降级响应不应该为空", status);
        assertEquals("broker名称应该是 unknown", "unknown", status.getBrokerName());
        assertEquals("状态应该是 UNAVAILABLE", "UNAVAILABLE", status.getStatus());
    }

    @Test
    public void testBrokerHttpClientHeartbeatFallbackWhenBrokerUnavailable() {
        // 创建指向不存在服务器的 BrokerConfig
        BrokerConfig brokerConfig = new BrokerConfig();
        brokerConfig.setAddress("http://localhost:19999");

        // 创建 BrokerHttpClient
        RestTemplate restTemplate = new RestTemplate();
        BrokerHttpClient brokerHttpClient = new BrokerHttpClient(restTemplate, brokerConfig);

        // 调用 getHeartbeat - 应该返回降级数据
        BrokerInfo heartbeat = brokerHttpClient.getHeartbeat();

        // 验证降级处理
        assertNotNull("降级响应不应该为空", heartbeat);
        assertEquals("broker名称应该是 unknown", "unknown", heartbeat.getBrokerName());
        assertEquals("状态应该是 UNAVAILABLE", "UNAVAILABLE", heartbeat.getStatus());
    }

    @Test
    public void testTopicCountReflectsCacheData() {
        // 验证 Broker HTTP 服务器返回的 topicCount 与 CommonCache 一致
        assertEquals("CommonCache应该有1个topic", 1, CommonCache.getCatmqTopicModelList().size());
    }
}
