package com.aoaojiao.catmq.admin.controller;

import com.aoaojiao.catmq.admin.service.BrokerService;
import com.aoaojiao.catmq.admin.service.MetricsService;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

/**
 * Broker 控制器测试
 *
 * @author DD
 */
public class BrokerControllerTest {

    private BrokerController brokerController;
    private BrokerService brokerService;
    private MetricsService metricsService;

    @Before
    public void setUp() {
        brokerService = new BrokerService();
        metricsService = new MetricsService();
        brokerController = new BrokerController(brokerService, metricsService);
    }

    @Test
    public void testGetBrokerStatus() {
        var response = brokerController.getBrokerStatus();

        assertNotNull("应该有响应", response);
        assertEquals("应该成功", 200, response.getCode().intValue());
        assertNotNull("应该有数据", response.getData());
    }

    @Test
    public void testGetBrokerConfig() {
        var response = brokerController.getBrokerConfig();

        assertNotNull("应该有响应", response);
        assertEquals("应该成功", 200, response.getCode().intValue());
        assertNotNull("应该有配置数据", response.getData());
    }

    @Test
    public void testGetBrokerHeartbeat() {
        var response = brokerController.getBrokerHeartbeat();

        assertNotNull("应该有响应", response);
        assertEquals("应该成功", 200, response.getCode().intValue());
        assertNotNull("应该有心跳数据", response.getData());
    }

    @Test
    public void testGetJvmMemory() {
        var response = brokerController.getJvmMemory();

        assertNotNull("应该有响应", response);
        assertEquals("应该成功", 200, response.getCode().intValue());

        Map<String, Object> data = (Map<String, Object>) response.getData();
        assertNotNull("应该有堆内存信息", data.get("heap"));
        assertNotNull("应该有非堆内存信息", data.get("nonHeap"));
    }

    @Test
    public void testGetThreadInfo() {
        var response = brokerController.getThreadInfo();

        assertNotNull("应该有响应", response);
        assertEquals("应该成功", 200, response.getCode().intValue());

        Map<String, Object> data = (Map<String, Object>) response.getData();
        assertNotNull("应该有活跃线程数", data.get("activeCount"));
        assertNotNull("应该有峰值线程数", data.get("peakCount"));
    }

    @Test
    public void testGetGcInfo() {
        var response = brokerController.getGcInfo();

        assertNotNull("应该有响应", response);
        assertEquals("应该成功", 200, response.getCode().intValue());
        assertNotNull("应该有GC信息", response.getData());
    }

    @Test
    public void testGetFileDescriptorInfo() {
        var response = brokerController.getFileDescriptorInfo();

        assertNotNull("应该有响应", response);
        assertEquals("应该成功", 200, response.getCode().intValue());

        Map<String, Object> data = (Map<String, Object>) response.getData();
        assertNotNull("应该有打开文件描述符数", data.get("openCount"));
        assertNotNull("应该有最大文件描述符数", data.get("maxCount"));
    }
}