package com.aoaojiao.catmq.admin.notifier;

import com.aoaojiao.catmq.admin.model.AlertRecord;
import com.aoaojiao.catmq.admin.model.AlertRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 邮件告警通知器
 * 通过 SMTP 发送邮件告警通知
 *
 * @author DD
 */
@Component
@ConditionalOnProperty(name = "catmq.admin.alert.notifier.email.enabled", havingValue = "true", matchIfMissing = false)
public class EmailAlertNotifier implements AlertNotifier {

    private static final Logger log = LoggerFactory.getLogger(EmailAlertNotifier.class);

    private final EmailAlertProperties properties;
    private final org.springframework.mail.javamail.JavaMailSender mailSender;

    public EmailAlertNotifier(EmailAlertProperties properties,
                               org.springframework.mail.javamail.JavaMailSender mailSender) {
        this.properties = properties;
        this.mailSender = mailSender;
    }

    @Override
    public String getType() {
        return "EMAIL";
    }

    @Override
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    @Override
    public boolean sendAlert(AlertRecord record, AlertRule rule) {
        if (!isEnabled()) {
            log.debug("邮件通知器未启用，跳过发送");
            return false;
        }

        try {
            org.springframework.mail.javamail.MimeMessageHelper helper = new org.springframework.mail.javamail.MimeMessageHelper(
                    mailSender.createMimeMessage(), true, "UTF-8");

            helper.setFrom(properties.getFromAddress());
            helper.setTo(properties.getToAddresses().toArray(new String[0]));
            helper.setSubject(buildSubject(record, rule));
            helper.setText(buildContent(record, rule), true);

            mailSender.send(helper.getMimeMessage());
            log.info("邮件告警发送成功: {} -> {}", properties.getToAddresses(), rule.getName());
            return true;
        } catch (Exception e) {
            log.error("邮件告警发送失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 构建邮件主题
     */
    private String buildSubject(AlertRecord record, AlertRule rule) {
        String levelPrefix = "[" + record.getAlertLevel() + "]";
        return String.format("%s CatMQ 告警 - %s", levelPrefix, rule.getName());
    }

    /**
     * 构建 HTML 邮件内容
     */
    private String buildContent(AlertRecord record, AlertRule rule) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body>");
        sb.append("<h2>CatMQ 系统告警通知</h2>");
        sb.append("<table style='border-collapse: collapse; width: 100%;'>");

        sb.append("<tr style='background-color: ").append(getLevelColor(record.getAlertLevel())).append(";'>");
        sb.append("<td style='padding: 10px; font-weight: bold; color: white;'>告警级别</td>");
        sb.append("<td style='padding: 10px;'>").append(record.getAlertLevel()).append("</td></tr>");

        sb.append("<tr><td style='padding: 10px; font-weight: bold;'>告警名称</td>");
        sb.append("<td style='padding: 10px;'>").append(rule.getName()).append("</td></tr>");

        sb.append("<tr><td style='padding: 10px; font-weight: bold;'>告警消息</td>");
        sb.append("<td style='padding: 10px;'>").append(record.getMessage()).append("</td></tr>");

        sb.append("<tr><td style='padding: 10px; font-weight: bold;'>触发值</td>");
        sb.append("<td style='padding: 10px;'>").append(record.getTriggerValue()).append("</td></tr>");

        sb.append("<tr><td style='padding: 10px; font-weight: bold;'>阈值</td>");
        sb.append("<td style='padding: 10px;'>").append(rule.getThreshold()).append("</td></tr>");

        sb.append("<tr><td style='padding: 10px; font-weight: bold;'>触发时间</td>");
        sb.append("<td style='padding: 10px;'>").append(formatTimestamp(record.getTimestamp())).append("</td></tr>");

        sb.append("<tr><td style='padding: 10px; font-weight: bold;'>告警描述</td>");
        sb.append("<td style='padding: 10px;'>").append(rule.getDescription()).append("</td></tr>");

        sb.append("</table>");
        sb.append("<p style='color: #666; margin-top: 20px;'>此邮件由 CatMQ 告警系统自动发送，请及时处理。</p>");
        sb.append("</body></html>");

        return sb.toString();
    }

    /**
     * 根据告警级别获取背景颜色
     */
    private String getLevelColor(AlertRule.AlertLevel level) {
        switch (level) {
            case CRITICAL: return "#dc3545";
            case ERROR:     return "#fd7e14";
            case WARN:      return "#ffc107";
            case INFO:
            default:        return "#17a2b8";
        }
    }

    /**
     * 格式化时间戳
     */
    private String formatTimestamp(Long timestamp) {
        if (timestamp == null) {
            return "N/A";
        }
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(new java.util.Date(timestamp));
    }

    @Override
    public String getDescription() {
        return "邮件通知器 (收件人: " + properties.getToAddresses() + ")";
    }
}