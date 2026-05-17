package com.aoaojiao.catmq.admin.controller;

import com.aoaojiao.catmq.admin.dto.response.ApiResponse;
import com.aoaojiao.catmq.admin.dto.response.MessageResponse;
import com.aoaojiao.catmq.admin.service.MessageService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 消息查询接口
 *
 * @author DD
 */
@Api(tags = "消息查询")
@RestController
@RequestMapping("/api/v1/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    /**
     * 根据 Topic 查询消息
     */
    @GetMapping("/topic/{topic}")
    @ApiOperation(value = "根据 Topic 查询消息", notes = "分页查询指定 Topic 的消息")
    public ApiResponse<List<MessageResponse>> queryByTopic(
            @ApiParam(value = "Topic 名称", required = true)
            @PathVariable String topic,
            @ApiParam(value = "起始偏移量", example = "0")
            @RequestParam(defaultValue = "0") int offset,
            @ApiParam(value = "查询数量", example = "20")
            @RequestParam(defaultValue = "20") int limit) {
        List<MessageResponse> messages = messageService.queryByTopic(topic, offset, limit);
        return ApiResponse.success(messages);
    }

    /**
     * 根据消息 ID 查询
     */
    @GetMapping("/{messageId}")
    @ApiOperation(value = "根据消息 ID 查询", notes = "根据消息 ID 查询单条消息详情")
    public ApiResponse<MessageResponse> queryByMessageId(
            @ApiParam(value = "消息 ID", required = true)
            @PathVariable Long messageId) {
        MessageResponse message = messageService.queryByMessageId(messageId);
        return ApiResponse.success(message);
    }

    /**
     * 根据 Topic 和队列 ID 查询消息
     */
    @GetMapping("/topic/{topic}/queue/{queueId}")
    @ApiOperation(value = "根据 Topic 和队列查询", notes = "查询指定 Topic 和队列的消息")
    public ApiResponse<List<MessageResponse>> queryByTopicAndQueue(
            @ApiParam(value = "Topic 名称", required = true)
            @PathVariable String topic,
            @ApiParam(value = "队列 ID", required = true)
            @PathVariable int queueId,
            @ApiParam(value = "起始偏移量", example = "0")
            @RequestParam(defaultValue = "0") int offset,
            @ApiParam(value = "查询数量", example = "20")
            @RequestParam(defaultValue = "20") int limit) {
        List<MessageResponse> messages = messageService.queryByTopicAndQueue(topic, queueId, offset, limit);
        return ApiResponse.success(messages);
    }
}