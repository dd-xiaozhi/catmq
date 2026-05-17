package com.aoaojiao.catmq.admin.controller;

import com.aoaojiao.catmq.admin.dto.response.ApiResponse;
import com.aoaojiao.catmq.admin.dto.response.ConsumerGroupResponse;
import com.aoaojiao.catmq.admin.service.ConsumerGroupService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 消费组管理接口
 *
 * @author DD
 */
@Api(tags = "消费组管理")
@RestController
@RequestMapping("/api/v1/consumer-groups")
public class ConsumerGroupController {

    private final ConsumerGroupService consumerGroupService;

    public ConsumerGroupController(ConsumerGroupService consumerGroupService) {
        this.consumerGroupService = consumerGroupService;
    }

    /**
     * 获取所有消费组
     */
    @GetMapping
    @ApiOperation(value = "获取所有消费组", notes = "返回系统中所有消费组及其消费进度")
    public ApiResponse<List<ConsumerGroupResponse>> listConsumerGroups() {
        List<ConsumerGroupResponse> groups = consumerGroupService.listConsumerGroups();
        return ApiResponse.success(groups);
    }

    /**
     * 获取指定消费组
     */
    @GetMapping("/{groupName}")
    @ApiOperation(value = "获取指定消费组", notes = "根据消费组名称查询详细信息")
    public ApiResponse<ConsumerGroupResponse> getConsumerGroup(
            @ApiParam(value = "消费组名称", required = true)
            @PathVariable String groupName) {
        ConsumerGroupResponse group = consumerGroupService.getConsumerGroup(groupName);
        if (group == null) {
            return ApiResponse.error(404, "消费组 [" + groupName + "] 不存在");
        }
        return ApiResponse.success(group);
    }

    /**
     * 获取指定 Topic 的所有消费组
     */
    @GetMapping("/topic/{topic}")
    @ApiOperation(value = "获取 Topic 的消费组", notes = "查询指定 Topic 的所有消费组")
    public ApiResponse<List<ConsumerGroupResponse>> getConsumerGroupsByTopic(
            @ApiParam(value = "Topic 名称", required = true)
            @PathVariable String topic) {
        List<ConsumerGroupResponse> groups = consumerGroupService.getConsumerGroupsByTopic(topic);
        return ApiResponse.success(groups);
    }
}