package com.aoaojiao.catmq.admin.notifier;

import com.aoaojiao.catmq.admin.model.AlertRecord;
import com.aoaojiao.catmq.admin.model.AlertRule;

/**
 * 告警通知器接口
 * 定义告警通知的发送方法
 *
 * @author DD
 */
public interface AlertNotifier {

    /**
     * 获取通知器类型
     */
    String getType();

    /**
     * 检查通知器是否启用
     */
    boolean isEnabled();

    /**
     * 发送告警通知
     *
     * @param record 告警记录
     * @param rule   告警规则
     * @return 是否发送成功
     */
    boolean sendAlert(AlertRecord record, AlertRule rule);

    /**
     * 获取通知器描述
     */
    default String getDescription() {
        return "告警通知器: " + getType();
    }
}