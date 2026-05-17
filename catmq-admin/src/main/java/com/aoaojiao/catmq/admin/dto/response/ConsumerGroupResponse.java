package com.aoaojiao.catmq.admin.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 消费组信息响应
 *
 * @author DD
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "消费组信息")
public class ConsumerGroupResponse {

    @ApiModelProperty(value = "消费组名称")
    private String groupName;

    @ApiModelProperty(value = "所属 Topic")
    private String topic;

    @ApiModelProperty(value = "消费组状态")
    private String status;

    @ApiModelProperty(value = "消费者数量")
    private Integer consumerCount;

    @ApiModelProperty(value = "消费进度列表")
    private List<ConsumeProgress> progressList;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConsumeProgress {
        @ApiModelProperty(value = "队列ID")
        private Integer queueId;

        @ApiModelProperty(value = "当前消费偏移量")
        private Long currentOffset;

        @ApiModelProperty(value = "最大偏移量")
        private Long maxOffset;

        @ApiModelProperty(value = "消费进度（百分比）")
        private Double progressPercent;

        @ApiModelProperty(value = "未消费消息数")
        private Long lag;
    }
}