package com.aoaojiao.catmq.admin.controller;

import com.aoaojiao.catmq.admin.dto.response.ApiResponse;
import com.aoaojiao.catmq.admin.dto.response.BrokerStatusResponse;
import com.aoaojiao.catmq.admin.model.BrokerInfo;
import com.aoaojiao.catmq.admin.service.BrokerService;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Broker 控制器测试
 *
 * @author DD
 */
public class BrokerControllerTest {

    private BrokerController brokerController;
    private BrokerService brokerService;

    @Before
    public void setUp() {
        brokerService = new BrokerService();
        brokerController = new BrokerController(brokerService);
    }

    @Test
    public void testGetBrokerStatus() {
        ApiResponse<BrokerStatusResponse> response = brokerController.getBrokerStatus();

        assertNotNull("应该有响应", response);
        assertEquals("应该成功", 200, response.getCode());
    }

    @Test
    public void testGetBrokerConfig() {
        ApiResponse<Object> response = brokerController.getBrokerConfig();

        assertNotNull("应该有响应", response);
        assertEquals("应该成功", 200, response.getCode());
    }

    @Test
    public void testGetHeartbeat() {
        ApiResponse<BrokerInfo> response = brokerController.getHeartbeat();

        assertNotNull("应该有响应", response);
        assertEquals("应该成功", 200, response.getCode());
        assertNotNull("应该有数据", response.getData());
    }

    @Test
    public void testGetBrokerStatusNotNull() {
        ApiResponse<BrokerStatusResponse> response = brokerController.getBrokerStatus();
        assertNotNull("响应数据不应该为空", response);
        assertNotNull("响应数据内容不应该为空", response.getData());
    }
}