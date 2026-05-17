package com.aoaojiao.catmq.admin.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 指标数据响应
 *
 * @author DD
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "系统指标数据")
public class MetricsResponse {

    @ApiModelProperty(value = "采集时间（毫秒）")
    private Long timestamp;

    @ApiModelProperty(value = "消息吞吐量指标")
    private ThroughputMetrics throughput;

    @ApiModelProperty(value = "队列深度指标")
    private QueueDepthMetrics queueDepth;

    @ApiModelProperty(value = "延迟指标")
    private LatencyMetrics latency;

    @ApiModelProperty(value = "连接数指标")
    private ConnectionMetrics connection;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ThroughputMetrics {
        @ApiModelProperty(value = "消息发送速率（条/秒）")
        private Double sendRate;

        @ApiModelProperty(value = "消息消费速率（条/秒）")
        private Double consumeRate;

        @ApiModelProperty(value = "总发送消息数")
        private Long totalSendCount;

        @ApiModelProperty(value = "总消费消息数")
        private Long totalConsumeCount;

        @ApiModelProperty(value = "各 Topic 发送速率")
        private Map<String, Double> topicSendRates;

        @ApiModelProperty(value = "各 Topic 消费速率")
        private Map<String, Double> topicConsumeRates;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QueueDepthMetrics {
        @ApiModelProperty(value = "各 Topic 队列消息堆积数")
        private Map<String, Long> topicQueueDepth;

        @ApiModelProperty(value = "最大堆积数 Topic")
        private String maxDepthTopic;

        @ApiModelProperty(value = "最大堆积数")
        private Long maxDepth;

        @ApiModelProperty(value = "总堆积消息数")
        private Long totalDepth;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LatencyMetrics {
        @ApiModelProperty(value = "消息写入延迟（毫秒）- 平均")
        private Double writeLatencyAvg;

        @ApiModelProperty(value = "消息写入延迟（毫秒）- 最大")
        private Double writeLatencyMax;

        @ApiModelProperty(value = "消息写入延迟（毫秒）- 最小")
        private Double writeLatencyMin;

        @ApiModelProperty(value = "消息消费延迟（毫秒）- 平均")
        private Double consumeLatencyAvg;

        @ApiModelProperty(value = "消息消费延迟（毫秒）- 最大")
        private Double consumeLatencyMax;

        @ApiModelProperty(value = "消息消费延迟（毫秒）- 最小")
        private Double consumeLatencyMin;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConnectionMetrics {
        @ApiModelProperty(value = "当前活跃连接数")
        private Integer activeConnectionCount;

        @ApiModelProperty(value = "总连接数")
        private Integer totalConnectionCount;

        @ApiModelProperty(value = "Producer 连接数")
        private Integer producerCount;

        @ApiModelProperty(value = "Consumer 连接数")
        private Integer consumerCount;
    }
}