package com.aoaojiao.catmq.admin.controller;

import com.aoaojiao.catmq.admin.dto.response.ApiResponse;
import com.aoaojiao.catmq.admin.model.AlertRecord;
import com.aoaojiao.catmq.admin.model.AlertRule;
import com.aoaojiao.catmq.admin.service.AlertService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 告警管理接口
 *
 * @author DD
 */
@Api(tags = "告警管理")
@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    /**
     * 获取所有告警规则
     */
    @GetMapping("/rules")
    @ApiOperation(value = "获取告警规则列表", notes = "获取系统中所有配置的告警规则")
    public ApiResponse<List<AlertRule>> getRules() {
        List<AlertRule> rules = alertService.getRules();
        return ApiResponse.success(rules);
    }

    /**
     * 添加告警规则
     */
    @PostMapping("/rules")
    @ApiOperation(value = "添加告警规则", notes = "添加新的告警规则")
    public ApiResponse<Void> addRule(
            @ApiParam(value = "告警规则", required = true)
            @RequestBody AlertRule rule) {
        alertService.addRule(rule);
        return ApiResponse.success("告警规则添加成功", null);
    }

    /**
     * 删除告警规则
     */
    @DeleteMapping("/rules/{ruleId}")
    @ApiOperation(value = "删除告警规则", notes = "删除指定的告警规则")
    public ApiResponse<Void> deleteRule(
            @ApiParam(value = "规则 ID", required = true)
            @PathVariable String ruleId) {
        alertService.removeRule(ruleId);
        return ApiResponse.success("告警规则删除成功", null);
    }

    /**
     * 启用/禁用告警规则
     */
    @PutMapping("/rules/{ruleId}")
    @ApiOperation(value = "更新告警规则状态", notes = "启用或禁用指定的告警规则")
    public ApiResponse<Void> updateRuleStatus(
            @ApiParam(value = "规则 ID", required = true)
            @PathVariable String ruleId,
            @ApiParam(value = "是否启用", required = true)
            @RequestParam boolean enabled) {
        alertService.setRuleEnabled(ruleId, enabled);
        return ApiResponse.success("告警规则更新成功", null);
    }

    /**
     * 获取告警记录
     */
    @GetMapping("/records")
    @ApiOperation(value = "获取告警记录", notes = "获取最近的告警记录")
    public ApiResponse<List<AlertRecord>> getAlertRecords(
            @ApiParam(value = "记录数量", example = "100")
            @RequestParam(defaultValue = "100") int limit) {
        List<AlertRecord> records = alertService.getAlertRecords(limit);
        return ApiResponse.success(records);
    }

    /**
     * 获取活跃告警
     */
    @GetMapping("/active")
    @ApiOperation(value = "获取活跃告警", notes = "获取所有未解决的告警")
    public ApiResponse<List<AlertRecord>> getActiveAlerts() {
        List<AlertRecord> alerts = alertService.getActiveAlerts();
        return ApiResponse.success(alerts);
    }

    /**
     * 确认告警
     */
    @PutMapping("/records/{alertId}/acknowledge")
    @ApiOperation(value = "确认告警", notes = "确认指定告警已处理")
    public ApiResponse<Void> acknowledgeAlert(
            @ApiParam(value = "告警 ID", required = true)
            @PathVariable String alertId,
            @ApiParam(value = "确认人")
            @RequestParam(required = false, defaultValue = "admin") String acknowledgedBy) {
        alertService.acknowledgeAlert(alertId, acknowledgedBy);
        return ApiResponse.success("告警已确认", null);
    }

    /**
     * 获取未确认告警数量
     */
    @GetMapping("/unacknowledged/count")
    @ApiOperation(value = "获取未确认告警数量", notes = "获取未确认的告警数量")
    public ApiResponse<Long> getUnacknowledgedCount() {
        long count = alertService.getUnacknowledgedCount();
        return ApiResponse.success(count);
    }
}