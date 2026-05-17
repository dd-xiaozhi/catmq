package com.aoaojiao.catmq.admin.model;

import lombok.Data;

/**
 * 告警记录
 *
 * @author DD
 */
@Data
public class AlertRecord {

    /**
     * 告警 ID
     */
    private String id;

    /**
     * 告警规则 ID
     */
    private String ruleId;

    /**
     * 告警规则名称
     */
    private String ruleName;

    /**
     * 告警级别
     */
    private AlertRule.AlertLevel alertLevel;

    /**
     * 告警消息
     */
    private String message;

    /**
     * 触发值
     */
    private Double triggerValue;

    /**
     * 告警时间
     */
    private Long timestamp;

    /**
     * 是否已确认
     */
    private Boolean acknowledged;

    /**
     * 确认时间
     */
    private Long acknowledgedTime;

    /**
     * 确认人
     */
    private String acknowledgedBy;

    /**
     * 是否解决
     */
    private Boolean resolved;

    /**
     * 解决时间
     */
    private Long resolvedTime;
}