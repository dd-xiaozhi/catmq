package com.aoaojiao.catmq.admin.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 健康检查响应
 *
 * @author DD
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "健康检查结果")
public class HealthCheckResponse {

    @ApiModelProperty(value = "总体健康状态（UP/DOWN/DEGRADED）")
    private String status;

    @ApiModelProperty(value = "检查时间（毫秒）")
    private Long timestamp;

    @ApiModelProperty(value = "总运行时长（秒）")
    private Long uptimeSeconds;

    @ApiModelProperty(value = "组件健康检查详情")
    private List<ComponentHealth> components;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComponentHealth {
        @ApiModelProperty(value = "组件名称")
        private String name;

        @ApiModelProperty(value = "组件状态")
        private String status;

        @ApiModelProperty(value = "详细信息")
        private String message;

        @ApiModelProperty(value = "响应时间（毫秒）")
        private Long responseTime;
    }
}