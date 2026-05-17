package com.aoaojiao.catmq.admin.notifier;

import com.aoaojiao.catmq.admin.model.AlertRecord;
import com.aoaojiao.catmq.admin.model.AlertRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 告警通知管理器
 * 统一管理所有告警通知器，提供异步发送能力
 *
 * @author DD
 */
@Component
public class AlertNotifierManager {

    private static final Logger log = LoggerFactory.getLogger(AlertNotifierManager.class);

    private final List<AlertNotifier> notifiers = new ArrayList<>();

    /**
     * 构造函数，自动注入所有 AlertNotifier 实现
     */
    public AlertNotifierManager(List<AlertNotifier> notifierList) {
        this.notifiers.addAll(notifierList);
    }

    /**
     * 初始化通知器
     */
    @PostConstruct
    public void init() {
        log.info("初始化告警通知器，共 {} 个", notifiers.size());
        for (AlertNotifier notifier : notifiers) {
            log.info("已注册通知器: {} - {}", notifier.getType(), notifier.getDescription());
        }
    }

    /**
     * 发送告警通知（同步）
     *
     * @param record 告警记录
     * @param rule   告警规则
     * @return 发送成功的通知器数量
     */
    public int sendAlert(AlertRecord record, AlertRule rule) {
        int successCount = 0;

        for (AlertNotifier notifier : notifiers) {
            if (notifier.isEnabled()) {
                try {
                    boolean success = notifier.sendAlert(record, rule);
                    if (success) {
                        successCount++;
                    }
                } catch (Exception e) {
                    log.error("通知器 {} 发送失败: {}", notifier.getType(), e.getMessage());
                }
            }
        }

        return successCount;
    }

    /**
     * 异步发送告警通知
     * 通知发送将在独立线程中执行，不会阻塞主流程
     *
     * @param record 告警记录
     * @param rule   告警规则
     */
    @Async("alertExecutor")
    public CompletableFuture<Integer> sendAlertAsync(AlertRecord record, AlertRule rule) {
        return CompletableFuture.completedFuture(sendAlert(record, rule));
    }

    /**
     * 获取所有已注册的通知器
     */
    public List<AlertNotifier> getNotifiers() {
        return new ArrayList<>(notifiers);
    }

    /**
     * 获取已启用的通知器
     */
    public List<AlertNotifier> getEnabledNotifiers() {
        List<AlertNotifier> enabledNotifiers = new ArrayList<>();
        for (AlertNotifier notifier : notifiers) {
            if (notifier.isEnabled()) {
                enabledNotifiers.add(notifier);
            }
        }
        return enabledNotifiers;
    }

    /**
     * 获取指定类型的通知器
     */
    public AlertNotifier getNotifier(String type) {
        for (AlertNotifier notifier : notifiers) {
            if (notifier.getType().equalsIgnoreCase(type)) {
                return notifier;
            }
        }
        return null;
    }
}