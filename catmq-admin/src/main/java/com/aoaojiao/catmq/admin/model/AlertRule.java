package com.aoaojiao.catmq.admin.model;

import lombok.Data;

/**
 * 告警规则
 *
 * @author DD
 */
@Data
public class AlertRule {

    /**
     * 规则 ID
     */
    private String id;

    /**
     * 规则名称
     */
    private String name;

    /**
     * 指标类型
     */
    private MetricType metricType;

    /**
     * 告警阈值
     */
    private Double threshold;

    /**
     * 比较操作符（GT, LT, GTE, LTE, EQ）
     */
    private Operator operator;

    /**
     * 告警级别（INFO, WARN, ERROR, CRITICAL）
     */
    private AlertLevel alertLevel;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 持续时间（秒），超过该时间持续触发才告警
     */
    private Integer durationSeconds;

    /**
     * 描述
     */
    private String description;

    public enum MetricType {
        CPU_USAGE,           // CPU 使用率
        MEMORY_USAGE,        // 内存使用率
        DISK_USAGE,          // 磁盘使用率
        QUEUE_DEPTH,         // 队列深度
        SEND_RATE,           // 发送速率
        CONSUME_RATE,        // 消费速率
        LATENCY,             // 延迟
        CONNECTION_COUNT,    // 连接数
        THREAD_COUNT,        // 线程数
        FD_USAGE             // 文件描述符使用率
    }

    public enum Operator {
        GT,     // 大于
        LT,     // 小于
        GTE,    // 大于等于
        LTE,    // 小于等于
        EQ      // 等于
    }

    public enum AlertLevel {
        INFO,       // 信息
        WARN,       // 警告
        ERROR,      // 错误
        CRITICAL    // 严重
    }
}