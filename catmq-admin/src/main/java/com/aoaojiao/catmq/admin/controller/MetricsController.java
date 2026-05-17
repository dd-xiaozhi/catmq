package com.aoaojiao.catmq.admin.controller;

import com.aoaojiao.catmq.admin.dto.response.ApiResponse;
import com.aoaojiao.catmq.admin.dto.response.MetricsResponse;
import com.aoaojiao.catmq.admin.service.MetricsService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 指标接口
 *
 * @author DD
 */
@Api(tags = "指标监控")
@RestController
@RequestMapping("/api/v1/metrics")
public class MetricsController {

    private final MetricsService metricsService;

    public MetricsController(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    /**
     * 获取完整指标数据
     */
    @GetMapping
    @ApiOperation(value = "获取指标数据", notes = "获取系统的完整指标数据，包括吞吐量、队列深度、延迟、连接数等")
    public ApiResponse<MetricsResponse> getMetrics() {
        MetricsResponse metrics = metricsService.getMetrics();
        return ApiResponse.success(metrics);
    }
}