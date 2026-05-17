package com.aoaojiao.catmq.admin.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Broker 状态信息响应
 *
 * @author DD
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "Broker 状态信息")
public class BrokerStatusResponse {

    @ApiModelProperty(value = "Broker 名称")
    private String brokerName;

    @ApiModelProperty(value = "Broker 状态（RUNNING/STOPPED）")
    private String status;

    @ApiModelProperty(value = "启动时间（毫秒）")
    private Long startTime;

    @ApiModelProperty(value = "运行时长（秒）")
    private Long uptimeSeconds;

    @ApiModelProperty(value = "版本号")
    private String version;

    @ApiModelProperty(value = "CPU 使用率（百分比）")
    private Double cpuUsagePercent;

    @ApiModelProperty(value = "内存使用率（百分比）")
    private Double memoryUsagePercent;

    @ApiModelProperty(value = "磁盘使用率（百分比）")
    private Double diskUsagePercent;

    @ApiModelProperty(value = "JVM 内存信息")
    private JvmMemoryInfo jvmMemory;

    @ApiModelProperty(value = "线程信息")
    private ThreadInfo threadInfo;

    @ApiModelProperty(value = "文件描述符使用信息")
    private FileDescriptorInfo fileDescriptor;

    @ApiModelProperty(value = "Topic 数量")
    private Integer topicCount;

    @ApiModelProperty(value = "队列数量")
    private Integer queueCount;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JvmMemoryInfo {
        @ApiModelProperty(value = "堆内存总量（字节）")
        private Long heapTotal;

        @ApiModelProperty(value = "堆内存使用量（字节）")
        private Long heapUsed;

        @ApiModelProperty(value = "堆内存使用率（百分比）")
        private Double heapUsagePercent;

        @ApiModelProperty(value = "堆内存剩余量（字节）")
        private Long heapFree;

        @ApiModelProperty(value = "非堆内存总量（字节）")
        private Long nonHeapTotal;

        @ApiModelProperty(value = "非堆内存使用量（字节）")
        private Long nonHeapUsed;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ThreadInfo {
        @ApiModelProperty(value = "活跃线程数")
        private Integer activeCount;

        @ApiModelProperty(value = "峰值线程数")
        private Integer peakCount;

        @ApiModelProperty(value = "总线程数")
        private Long totalStartedCount;

        @ApiModelProperty(value = "守护线程数")
        private Integer daemonCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileDescriptorInfo {
        @ApiModelProperty(value = "已使用文件描述符数量")
        private Long openFdCount;

        @ApiModelProperty(value = "最大文件描述符数量")
        private Long maxFdCount;

        @ApiModelProperty(value = "使用率（百分比）")
        private Double usagePercent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GcInfo {
        @ApiModelProperty(value = "GC 次数")
        private Long gcCount;

        @ApiModelProperty(value = "GC 耗时（毫秒）")
        private Long gcTimeMillis;

        @ApiModelProperty(value = "GC 类型（Young/Old）")
        private String gcType;

        @ApiModelProperty(value = "内存池名称")
        private String memoryPoolName;
    }
}