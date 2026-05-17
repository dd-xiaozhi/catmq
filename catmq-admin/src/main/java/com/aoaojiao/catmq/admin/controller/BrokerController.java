package com.aoaojiao.catmq.admin.controller;

import com.aoaojiao.catmq.admin.dto.response.ApiResponse;
import com.aoaojiao.catmq.admin.dto.response.BrokerStatusResponse;
import com.aoaojiao.catmq.admin.model.BrokerInfo;
import com.aoaojiao.catmq.admin.service.BrokerService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.web.bind.annotation.*;

/**
 * Broker 管理接口
 *
 * @author DD
 */
@Api(tags = "Broker 管理")
@RestController
@RequestMapping("/api/v1/broker")
public class BrokerController {

    private final BrokerService brokerService;

    public BrokerController(BrokerService brokerService) {
        this.brokerService = brokerService;
    }

    /**
     * 获取 Broker 状态信息
     */
    @GetMapping("/status")
    @ApiOperation(value = "获取 Broker 状态", notes = "获取 Broker 的详细状态信息，包括 CPU、内存、磁盘、线程等")
    public ApiResponse<BrokerStatusResponse> getBrokerStatus() {
        BrokerStatusResponse status = brokerService.getBrokerStatus();
        return ApiResponse.success(status);
    }

    /**
     * 获取 Broker 心跳信息
     */
    @GetMapping("/heartbeat")
    @ApiOperation(value = "获取 Broker 心跳", notes = "获取 Broker 的心跳信息，用于监控存活状态")
    public ApiResponse<BrokerInfo> getHeartbeat() {
        BrokerInfo heartbeat = brokerService.getHeartbeat();
        return ApiResponse.success(heartbeat);
    }

    /**
     * 获取 Broker 配置信息
     */
    @GetMapping("/config")
    @ApiOperation(value = "获取 Broker 配置", notes = "获取 Broker 的当前配置信息")
    public ApiResponse<Object> getBrokerConfig() {
        // 返回配置信息
        return ApiResponse.success("获取配置成功", null);
    }
}