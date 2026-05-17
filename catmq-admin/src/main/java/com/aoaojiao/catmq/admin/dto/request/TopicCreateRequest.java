package com.aoaojiao.catmq.admin.dto.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

/**
 * 创建 Topic 请求参数
 *
 * @author DD
 */
@Data
@ApiModel(description = "创建 Topic 请求参数")
public class TopicCreateRequest {

    @NotBlank(message = "topic 名称不能为空")
    @ApiModelProperty(value = "Topic 名称", required = true, example = "order_pay_topic")
    private String topic;

    @NotNull(message = "queueCount 不能为空")
    @Positive(message = "queueCount 必须为正数")
    @ApiModelProperty(value = "队列数量", required = true, example = "4")
    private Integer queueCount;

    @ApiModelProperty(value = "描述信息", example = "订单支付消息主题")
    private String description;
}