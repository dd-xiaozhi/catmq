package com.aoaojiao.catmq.admin.service;

import com.aoaojiao.catmq.admin.model.AlertRecord;
import com.aoaojiao.catmq.admin.model.AlertRule;
import com.aoaojiao.catmq.admin.notifier.AlertNotifierManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 告警服务
 *
 * @author DD
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    /**
     * 告警规则列表
     */
    private final Map<String, AlertRule> alertRules = new ConcurrentHashMap<>();

    /**
     * 告警记录（最近 1000 条）
     */
    private final Deque<AlertRecord> alertRecords = new ConcurrentLinkedDeque<>();

    /**
     * 告警触发状态（用于判断持续告警）
     * key: ruleId
     * value: 首次触发时间
     */
    private final Map<String, Long> alertTriggerTimes = new ConcurrentHashMap<>();

    /**
     * 告警通知管理器
     */
    private final AlertNotifierManager alertNotifierManager;

    /**
     * 默认告警规则
     */
    public AlertService(AlertNotifierManager alertNotifierManager) {
        this.alertNotifierManager = alertNotifierManager;
        initDefaultRules();
    }

    /**
     * 初始化默认告警规则
     */
    private void initDefaultRules() {
        // CPU 使用率 > 80% 持续 60 秒告警
        AlertRule cpuRule = new AlertRule();
        cpuRule.setId("cpu_usage_high");
        cpuRule.setName("CPU 使用率过高");
        cpuRule.setMetricType(AlertRule.MetricType.CPU_USAGE);
        cpuRule.setThreshold(80.0);
        cpuRule.setOperator(AlertRule.Operator.GT);
        cpuRule.setAlertLevel(AlertRule.AlertLevel.WARN);
        cpuRule.setEnabled(true);
        cpuRule.setDurationSeconds(60);
        cpuRule.setDescription("CPU 使用率超过 80%");
        addRule(cpuRule);

        // 内存使用率 > 85% 持续 60 秒告警
        AlertRule memoryRule = new AlertRule();
        memoryRule.setId("memory_usage_high");
        memoryRule.setName("内存使用率过高");
        memoryRule.setMetricType(AlertRule.MetricType.MEMORY_USAGE);
        memoryRule.setThreshold(85.0);
        memoryRule.setOperator(AlertRule.Operator.GT);
        memoryRule.setAlertLevel(AlertRule.AlertLevel.ERROR);
        memoryRule.setEnabled(true);
        memoryRule.setDurationSeconds(60);
        memoryRule.setDescription("内存使用率超过 85%");
        addRule(memoryRule);

        // 磁盘使用率 > 90% 告警
        AlertRule diskRule = new AlertRule();
        diskRule.setId("disk_usage_high");
        diskRule.setName("磁盘使用率过高");
        diskRule.setMetricType(AlertRule.MetricType.DISK_USAGE);
        diskRule.setThreshold(90.0);
        diskRule.setOperator(AlertRule.Operator.GT);
        diskRule.setAlertLevel(AlertRule.AlertLevel.CRITICAL);
        diskRule.setEnabled(true);
        diskRule.setDurationSeconds(0);
        diskRule.setDescription("磁盘使用率超过 90%");
        addRule(diskRule);

        // 队列深度 > 10000 告警
        AlertRule queueRule = new AlertRule();
        queueRule.setId("queue_depth_high");
        queueRule.setName("队列堆积严重");
        queueRule.setMetricType(AlertRule.MetricType.QUEUE_DEPTH);
        queueRule.setThreshold(10000.0);
        queueRule.setOperator(AlertRule.Operator.GT);
        queueRule.setAlertLevel(AlertRule.AlertLevel.WARN);
        queueRule.setEnabled(true);
        queueRule.setDurationSeconds(120);
        queueRule.setDescription("队列消息堆积超过 10000 条");
        addRule(queueRule);

        // 消息写入延迟 > 100ms 告警
        AlertRule latencyRule = new AlertRule();
        latencyRule.setId("write_latency_high");
        latencyRule.setName("写入延迟过高");
        latencyRule.setMetricType(AlertRule.MetricType.LATENCY);
        latencyRule.setThreshold(100.0);
        latencyRule.setOperator(AlertRule.Operator.GT);
        latencyRule.setAlertLevel(AlertRule.AlertLevel.WARN);
        latencyRule.setEnabled(true);
        latencyRule.setDurationSeconds(30);
        latencyRule.setDescription("消息写入延迟超过 100ms");
        addRule(latencyRule);
    }

    /**
     * 添加告警规则
     */
    public void addRule(AlertRule rule) {
        alertRules.put(rule.getId(), rule);
        log.info("添加告警规则: {}", rule.getName());
    }

    /**
     * 删除告警规则
     */
    public void removeRule(String ruleId) {
        alertRules.remove(ruleId);
        alertTriggerTimes.remove(ruleId);
        log.info("删除告警规则: {}", ruleId);
    }

    /**
     * 获取所有告警规则
     */
    public List<AlertRule> getRules() {
        return new ArrayList<>(alertRules.values());
    }

    /**
     * 启用/禁用告警规则
     */
    public void setRuleEnabled(String ruleId, boolean enabled) {
        AlertRule rule = alertRules.get(ruleId);
        if (rule != null) {
            rule.setEnabled(enabled);
            if (!enabled) {
                alertTriggerTimes.remove(ruleId);
            }
        }
    }

    /**
     * 检查指标是否触发告警
     */
    public void checkAndAlert(AlertRule.MetricType metricType, double value) {
        for (AlertRule rule : alertRules.values()) {
            if (!rule.getEnabled()) {
                continue;
            }

            if (rule.getMetricType() != metricType) {
                continue;
            }

            boolean triggered = compareValue(value, rule.getThreshold(), rule.getOperator());

            if (triggered) {
                // 检查持续时间
                if (rule.getDurationSeconds() > 0) {
                    Long firstTriggerTime = alertTriggerTimes.get(rule.getId());
                    if (firstTriggerTime == null) {
                        alertTriggerTimes.put(rule.getId(), System.currentTimeMillis());
                    } else {
                        long duration = (System.currentTimeMillis() - firstTriggerTime) / 1000;
                        if (duration >= rule.getDurationSeconds()) {
                            fireAlert(rule, value);
                            alertTriggerTimes.remove(rule.getId());
                        }
                    }
                } else {
                    fireAlert(rule, value);
                }
            } else {
                // 恢复正常，清除触发时间
                alertTriggerTimes.remove(rule.getId());
            }
        }
    }

    /**
     * 比较值和阈值
     */
    private boolean compareValue(double value, double threshold, AlertRule.Operator operator) {
        switch (operator) {
            case GT:
                return value > threshold;
            case LT:
                return value < threshold;
            case GTE:
                return value >= threshold;
            case LTE:
                return value <= threshold;
            case EQ:
                return Math.abs(value - threshold) < 0.001;
            default:
                return false;
        }
    }

    /**
     * 触发告警
     */
    private void fireAlert(AlertRule rule, double triggerValue) {
        AlertRecord record = new AlertRecord();
        record.setId(UUID.randomUUID().toString());
        record.setRuleId(rule.getId());
        record.setRuleName(rule.getName());
        record.setAlertLevel(rule.getAlertLevel());
        record.setMessage(String.format("%s: 当前值 %.2f, 阈值 %.2f",
                rule.getName(), triggerValue, rule.getThreshold()));
        record.setTriggerValue(triggerValue);
        record.setTimestamp(System.currentTimeMillis());
        record.setAcknowledged(false);
        record.setResolved(false);

        alertRecords.addFirst(record);

        // 保留最近 1000 条记录
        while (alertRecords.size() > 1000) {
            alertRecords.removeLast();
        }

        // 输出告警日志
        log.warn("告警触发: {} [{}] - {} (当前值: {}, 阈值: {})",
                rule.getAlertLevel(), rule.getName(), rule.getDescription(),
                triggerValue, rule.getThreshold());

        // 发送告警通知（异步，不阻塞主流程）
        alertNotifierManager.sendAlertAsync(record, rule);
    }

    /**
     * 获取告警记录
     */
    public List<AlertRecord> getAlertRecords(int limit) {
        int count = 0;
        List<AlertRecord> result = new ArrayList<>();
        for (AlertRecord record : alertRecords) {
            if (count++ >= limit) {
                break;
            }
            result.add(record);
        }
        return result;
    }

    /**
     * 确认告警
     */
    public void acknowledgeAlert(String alertId, String acknowledgedBy) {
        for (AlertRecord record : alertRecords) {
            if (record.getId().equals(alertId)) {
                record.setAcknowledged(true);
                record.setAcknowledgedTime(System.currentTimeMillis());
                record.setAcknowledgedBy(acknowledgedBy);
                break;
            }
        }
    }

    /**
     * 获取未确认的告警数量
     */
    public long getUnacknowledgedCount() {
        return alertRecords.stream()
                .filter(r -> !r.getAcknowledged())
                .count();
    }

    /**
     * 获取活跃告警（未解决的告警）
     */
    public List<AlertRecord> getActiveAlerts() {
        List<AlertRecord> activeAlerts = new ArrayList<>();
        for (AlertRecord record : alertRecords) {
            if (!record.getResolved()) {
                activeAlerts.add(record);
            }
        }
        return activeAlerts;
    }

    /**
     * 定时检查指标并触发告警
     * 每 10 秒执行一次
     */
    @Scheduled(fixedRate = 10000)
    public void scheduledAlertCheck() {
        try {
            // 获取当前指标值进行检查
            // 这里简化实现，实际应该从 MetricsService 获取真实数据

            // CPU 使用率检查（模拟数据）
            checkAndAlert(AlertRule.MetricType.CPU_USAGE, getCurrentCpuUsage());

            // 内存使用率检查（模拟数据）
            checkAndAlert(AlertRule.MetricType.MEMORY_USAGE, getCurrentMemoryUsage());

        } catch (Exception e) {
            log.error("告警检查异常", e);
        }
    }

    private double getCurrentCpuUsage() {
        // 简化实现
        return Math.random() * 30 + 10; // 10% - 40%
    }

    private double getCurrentMemoryUsage() {
        // 简化实现
        return Math.random() * 20 + 40; // 40% - 60%
    }
}