package com.aoaojiao.catmq.broker.http;

import com.aoaojiao.catmq.common.cache.CommonCache;
import com.aoaojiao.catmq.common.model.CatmqTopicModel;
import com.aoaojiao.catmq.common.model.QueueModel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Broker HTTP 服务器集成测试
 *
 * @author DD
 */
public class BrokerHttpServerTest {

    private BrokerHttpServer httpServer;
    private int port = 19990;
    private Thread serverThread;

    @Before
    public void setUp() throws Exception {
        // 清理缓存并添加测试数据
        CommonCache.setCatmqTopicModelCache(new ArrayList<>());

        CatmqTopicModel topic = new CatmqTopicModel();
        topic.setTopic("test_topic");
        List<QueueModel> queues = new ArrayList<>();
        QueueModel queue = new QueueModel();
        queue.setId(0);
        queues.add(queue);
        topic.setQueueModelList(queues);
        CommonCache.getCatmqTopicModelList().add(topic);

        // 启动 HTTP 服务器
        httpServer = new BrokerHttpServer(port, "test-broker");
        serverThread = new Thread(() -> httpServer.start());
        serverThread.setDaemon(true);
        serverThread.start();

        // 等待服务器启动
        Thread.sleep(500);
    }

    @After
    public void tearDown() {
        if (httpServer != null) {
            httpServer.stop();
        }
    }

    @Test
    public void testGetBrokerStatus() throws Exception {
        URL url = new URL("http://localhost:" + port + "/broker/status");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        try {
            assertEquals(HttpURLConnection.HTTP_OK, conn.getResponseCode());

            String response = readResponse(conn);
            assertNotNull(response);
            assertTrue("响应应该是 JSON 格式", response.startsWith("{"));

            // 验证响应包含必要字段
            assertTrue(response.contains("\"brokerName\""));
            assertTrue(response.contains("\"status\""));
            assertTrue(response.contains("\"uptimeSeconds\""));
            assertTrue(response.contains("\"topicCount\""));
            assertTrue(response.contains("\"queueCount\""));
            assertTrue(response.contains("\"cpuUsagePercent\""));
            assertTrue(response.contains("\"memoryUsagePercent\""));
        } finally {
            conn.disconnect();
        }
    }

    @Test
    public void testGetBrokerHeartbeat() throws Exception {
        URL url = new URL("http://localhost:" + port + "/broker/heartbeat");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        try {
            assertEquals(HttpURLConnection.HTTP_OK, conn.getResponseCode());

            String response = readResponse(conn);
            assertNotNull(response);
            assertTrue("响应应该是 JSON 格式", response.startsWith("{"));

            // 验证响应包含必要字段
            assertTrue(response.contains("\"brokerName\""));
            assertTrue(response.contains("\"status\""));
            assertTrue(response.contains("\"timestamp\""));
            assertTrue(response.contains("\"cpuUsage\""));
            assertTrue(response.contains("\"memoryUsage\""));
            assertTrue(response.contains("\"topicCount\""));
        } finally {
            conn.disconnect();
        }
    }

    @Test
    public void testTopicCountFromCache() throws Exception {
        URL url = new URL("http://localhost:" + port + "/broker/status");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        try {
            String response = readResponse(conn);
            // 我们添加了一个测试 topic
            assertTrue("响应应包含 topicCount", response.contains("\"topicCount\":1"));
        } finally {
            conn.disconnect();
        }
    }

    @Test
    public void testWrongMethod() throws Exception {
        URL url = new URL("http://localhost:" + port + "/broker/status");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");

        try {
            assertEquals(HttpURLConnection.HTTP_BAD_METHOD, conn.getResponseCode());
        } finally {
            conn.disconnect();
        }
    }

    private String readResponse(HttpURLConnection conn) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }
}
