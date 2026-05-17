package com.aoaojiao.catmq.admin.notifier;

import com.aoaojiao.catmq.admin.model.AlertRecord;
import com.aoaojiao.catmq.admin.model.AlertRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * 企业微信告警通知器
 * 通过企业微信 WebHook 发送告警通知
 *
 * @author DD
 */
@Component
@ConditionalOnProperty(name = "catmq.admin.alert.notifier.wechat.enabled", havingValue = "true", matchIfMissing = false)
public class WeChatWorkAlertNotifier implements AlertNotifier {

    private static final Logger log = LoggerFactory.getLogger(WeChatWorkAlertNotifier.class);

    private final WeChatWorkAlertProperties properties;
    private final RestTemplate restTemplate;

    public WeChatWorkAlertNotifier(WeChatWorkAlertProperties properties) {
        this.properties = properties;
        this.restTemplate = new RestTemplate();
    }

    @Override
    public String getType() {
        return "WECHAT_WORK";
    }

    @Override
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    @Override
    public boolean sendAlert(AlertRecord record, AlertRule rule) {
        if (!isEnabled()) {
            log.debug("企业微信通知器未启用，跳过发送");
            return false;
        }

        try {
            String webhookUrl = properties.getWebhookUrl();
            if (webhookUrl == null || webhookUrl.isEmpty()) {
                log.warn("企业微信 WebHook URL 未配置");
                return false;
            }

            // 构建企业微信消息
            Map<String, Object> message = buildMessage(record, rule);

            // 发送请求
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(message, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(webhookUrl, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("企业微信告警发送成功: {}", rule.getName());
                return true;
            } else {
                log.error("企业微信告警发送失败，状态码: {}", response.getStatusCode());
                return false;
            }
        } catch (Exception e) {
            log.error("企业微信告警发送异常: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 构建企业微信 Markdown 格式消息
     */
    private Map<String, Object> buildMessage(AlertRecord record, AlertRule rule) {
        Map<String, Object> message = new HashMap<>();
        message.put("msgtype", "markdown");

        // 构建内容
        StringBuilder content = new StringBuilder();
        content.append("<div class=\"highlight\">CatMQ 系统告警通知</div>\n\n");
        content.append("### <font color=\"warning\">").append(record.getAlertLevel())
                .append("</font> ").append(rule.getName()).append("\n\n");
        content.append("> **告警消息**: ").append(record.getMessage()).append("\n\n");
        content.append("> **触发值**: ").append(record.getTriggerValue()).append("\n\n");
        content.append("> **阈值**: ").append(rule.getThreshold()).append("\n\n");
        content.append("> **触发时间**: ").append(formatTimestamp(record.getTimestamp())).append("\n\n");
        content.append("> **告警描述**: ").append(rule.getDescription()).append("\n\n");

        Map<String, Object> markdown = new HashMap<>();
        markdown.put("content", content.toString());
        message.put("markdown", markdown);

        return message;
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
        return "企业微信通知器 (WebHook: " + maskSecret(properties.getWebhookUrl()) + ")";
    }

    /**
     * 隐藏 WebHook URL 中的敏感信息
     */
    private String maskSecret(String url) {
        if (url == null || url.isEmpty()) {
            return "未配置";
        }
        if (url.length() > 20) {
            return url.substring(0, 20) + "...";
        }
        return url;
    }
}