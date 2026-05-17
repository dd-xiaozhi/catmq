package com.aoaojiao.catmq.admin.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Topic 信息响应
 *
 * @author DD
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "Topic 信息")
public class TopicResponse {

    @ApiModelProperty(value = "Topic 名称")
    private String topic;

    @ApiModelProperty(value = "队列数量")
    private Integer queueCount;

    @ApiModelProperty(value = "描述信息")
    private String description;

    @ApiModelProperty(value = "创建时间（毫秒）")
    private Long createAt;

    @ApiModelProperty(value = "更新时间（毫秒）")
    private Long updateAt;

    @ApiModelProperty(value = "队列信息列表")
    private List<QueueInfo> queueList;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QueueInfo {
        @ApiModelProperty(value = "队列ID")
        private Integer queueId;

        @ApiModelProperty(value = "最大偏移量")
        private Long maxOffset;

        @ApiModelProperty(value = "最小偏移量")
        private Long minOffset;
    }
}