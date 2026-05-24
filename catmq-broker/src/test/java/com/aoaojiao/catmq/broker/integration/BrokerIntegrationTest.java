package com.aoaojiao.catmq.broker.integration;

import com.aoaojiao.catmq.broker.http.BrokerHttpServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import static org.junit.Assert.*;

/**
 * Broker 完整集成测试
 * 测试 Broker HTTP 服务启动和基本功能
 *
 * @author DD
 */
public class BrokerIntegrationTest {

    private static BrokerHttpServer brokerHttpServer;
    private static Thread brokerThread;
    private static int brokerHttpPort = 19991;

    @BeforeClass
    public static void setUpClass() throws Exception {
        // 启动 Broker HTTP 服务器
        brokerHttpServer = new BrokerHttpServer(brokerHttpPort, "integration-broker");
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
    public void testBrokerHttpServerStart() {
        assertNotNull("Broker HTTP 服务器应该已启动", brokerHttpServer);
        System.out.println("Broker HTTP 服务器状态: RUNNING");
    }

    @Test
    public void testBrokerHttpStatusEndpoint() throws Exception {
        // 调用 /broker/status 端点
        String url = "http://localhost:" + brokerHttpPort + "/broker/status";
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");

        int responseCode = connection.getResponseCode();
        assertEquals("HTTP 状态码应该是 200", 200, responseCode);

        BufferedReader reader = new BufferedReader(
            new InputStreamReader(connection.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();

        String responseBody = response.toString();
        assertTrue("响应应该包含 brokerName", responseBody.contains("integration-broker"));
        assertTrue("响应应该包含 RUNNING 状态", responseBody.contains("RUNNING"));
        System.out.println("Broker Status 响应: " + responseBody.substring(0, Math.min(200, responseBody.length())));
    }

    @Test
    public void testBrokerHttpHeartbeatEndpoint() throws Exception {
        // 调用 /broker/heartbeat 端点
        String url = "http://localhost:" + brokerHttpPort + "/broker/heartbeat";
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");

        int responseCode = connection.getResponseCode();
        assertEquals("HTTP 状态码应该是 200", 200, responseCode);

        BufferedReader reader = new BufferedReader(
            new InputStreamReader(connection.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();

        String responseBody = response.toString();
        assertTrue("响应应该包含 brokerName", responseBody.contains("integration-broker"));
        System.out.println("Broker Heartbeat 响应: " + responseBody.substring(0, Math.min(200, responseBody.length())));
    }
}