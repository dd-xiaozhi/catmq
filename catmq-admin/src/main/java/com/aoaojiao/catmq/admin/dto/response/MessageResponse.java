package com.aoaojiao.catmq.admin.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 消息信息响应
 *
 * @author DD
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "消息信息")
public class MessageResponse {

    @ApiModelProperty(value = "消息ID（物理偏移量）")
    private Long messageId;

    @ApiModelProperty(value = "Topic 名称")
    private String topic;

    @ApiModelProperty(value = "队列ID")
    private Integer queueId;

    @ApiModelProperty(value = "消息键")
    private String keys;

    @ApiModelProperty(value = "消息标签")
    private String tags;

    @ApiModelProperty(value = "消息体")
    private String body;

    @ApiModelProperty(value = "消息体大小（字节）")
    private Integer bodySize;

    @ApiModelProperty(value = "生产者编码")
    private String producerCode;

    @ApiModelProperty(value = "发送时间（毫秒）")
    private Long storeTimestamp;

    @ApiModelProperty(value = "消费次数")
    private Integer consumeCount;
}