package com.aoaojiao.catmq.admin.controller;

import com.aoaojiao.catmq.admin.dto.response.ApiResponse;
import com.aoaojiao.catmq.admin.dto.response.HealthCheckResponse;
import com.aoaojiao.catmq.admin.service.HealthCheckService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查接口
 *
 * @author DD
 */
@Api(tags = "健康检查")
@RestController
@RequestMapping("/api/v1")
public class HealthController {

    private final HealthCheckService healthCheckService;

    public HealthController(HealthCheckService healthCheckService) {
        this.healthCheckService = healthCheckService;
    }

    /**
     * 统一健康检查接口
     */
    @GetMapping("/health")
    @ApiOperation(value = "健康检查", notes = "检查系统各组件的健康状态")
    public ApiResponse<HealthCheckResponse> health() {
        HealthCheckResponse health = healthCheckService.check();
        return ApiResponse.success(health);
    }

    /**
     * 简单健康检查（用于负载均衡探活）
     */
    @GetMapping("/health/simple")
    @ApiOperation(value = "简单健康检查", notes = "返回简单的 UP/DOWN 状态")
    public ApiResponse<String> simpleHealth() {
        return ApiResponse.success("UP");
    }
}