package com.aoaojiao.catmq.admin.controller;

import com.aoaojiao.catmq.admin.dto.request.TopicCreateRequest;
import com.aoaojiao.catmq.admin.dto.response.ApiResponse;
import com.aoaojiao.catmq.admin.dto.response.TopicResponse;
import com.aoaojiao.catmq.admin.service.TopicService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Topic 管理接口
 *
 * @author DD
 */
@Api(tags = "Topic 管理")
@RestController
@RequestMapping("/api/v1/topics")
public class TopicController {

    private final TopicService topicService;

    public TopicController(TopicService topicService) {
        this.topicService = topicService;
    }

    /**
     * 获取所有 Topic 列表
     */
    @GetMapping
    @ApiOperation(value = "获取所有 Topic 列表", notes = "返回系统中所有已注册的 Topic")
    public ApiResponse<List<TopicResponse>> listTopics() {
        List<TopicResponse> topics = topicService.listTopics();
        return ApiResponse.success(topics);
    }

    /**
     * 获取指定 Topic 信息
     */
    @GetMapping("/{topicName}")
    @ApiOperation(value = "获取指定 Topic 信息", notes = "根据 Topic 名称查询详细信息")
    public ApiResponse<TopicResponse> getTopic(
            @ApiParam(value = "Topic 名称", required = true)
            @PathVariable String topicName) {
        TopicResponse topic = topicService.getTopic(topicName);
        if (topic == null) {
            return ApiResponse.error(404, "Topic [" + topicName + "] 不存在");
        }
        return ApiResponse.success(topic);
    }

    /**
     * 创建 Topic
     */
    @PostMapping
    @ApiOperation(value = "创建 Topic", notes = "创建一个新的消息 Topic")
    public ApiResponse<TopicResponse> createTopic(
            @ApiParam(value = "创建 Topic 请求参数", required = true)
            @Validated @RequestBody TopicCreateRequest request) {
        try {
            TopicResponse topic = topicService.createTopic(request);
            return ApiResponse.success("Topic 创建成功", topic);
        } catch (IllegalStateException e) {
            return ApiResponse.error(409, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(500, "创建 Topic 失败: " + e.getMessage());
        }
    }

    /**
     * 删除 Topic
     */
    @DeleteMapping("/{topicName}")
    @ApiOperation(value = "删除 Topic", notes = "删除指定的 Topic")
    public ApiResponse<Void> deleteTopic(
            @ApiParam(value = "Topic 名称", required = true)
            @PathVariable String topicName) {
        try {
            topicService.deleteTopic(topicName);
            return ApiResponse.success("Topic 删除成功", null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(404, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(500, "删除 Topic 失败: " + e.getMessage());
        }
    }

    /**
     * 获取各 Topic 的队列深度（消息堆积数）
     */
    @GetMapping("/queue-depth")
    @ApiOperation(value = "获取队列深度", notes = "获取所有 Topic 的队列消息堆积数")
    public ApiResponse<Map<String, Long>> getQueueDepth() {
        Map<String, Long> depthMap = topicService.getTopicQueueDepth();
        return ApiResponse.success(depthMap);
    }
}