package com.aoaojiao.catmq.admin.notifier;

import com.aoaojiao.catmq.admin.model.AlertRecord;
import com.aoaojiao.catmq.admin.model.AlertRule;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * 钉钉告警通知器
 * 通过钉钉 WebHook 发送告警通知
 *
 * @author DD
 */
@Component
@ConditionalOnProperty(name = "catmq.admin.alert.notifier.dingtalk.enabled", havingValue = "true", matchIfMissing = false)
public class DingTalkAlertNotifier implements AlertNotifier {

    private static final Logger log = LoggerFactory.getLogger(DingTalkAlertNotifier.class);

    private final DingTalkAlertProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public DingTalkAlertNotifier(DingTalkAlertProperties properties) {
        this.properties = properties;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getType() {
        return "DINGTALK";
    }

    @Override
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    @Override
    public boolean sendAlert(AlertRecord record, AlertRule rule) {
        if (!isEnabled()) {
            log.debug("钉钉通知器未启用，跳过发送");
            return false;
        }

        try {
            String webhookUrl = properties.getWebhookUrl();
            if (webhookUrl == null || webhookUrl.isEmpty()) {
                log.warn("钉钉 WebHook URL 未配置");
                return false;
            }

            // 构建钉钉消息
            Map<String, Object> message = buildMessage(record, rule);

            // 发送请求
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(message, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(webhookUrl, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("钉钉告警发送成功: {}", rule.getName());
                return true;
            } else {
                log.error("钉钉告警发送失败，状态码: {}", response.getStatusCode());
                return false;
            }
        } catch (Exception e) {
            log.error("钉钉告警发送异常: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 构建钉钉 Markdown 格式消息
     */
    private Map<String, Object> buildMessage(AlertRecord record, AlertRule rule) {
        Map<String, Object> message = new HashMap<>();
        message.put("msgtype", "markdown");

        // 标题
        String title = String.format("%s [%s]", record.getAlertLevel(), rule.getName());

        // 内容
        StringBuilder content = new StringBuilder();
        content.append("### ").append(title).append("\n\n");
        content.append("> **告警消息**: ").append(record.getMessage()).append("\n\n");
        content.append("> **触发值**: ").append(record.getTriggerValue()).append("\n\n");
        content.append("> **阈值**: ").append(rule.getThreshold()).append("\n\n");
        content.append("> **触发时间**: ").append(formatTimestamp(record.getTimestamp())).append("\n\n");
        content.append("> **告警描述**: ").append(rule.getDescription()).append("\n\n");

        // 判断是否需要 @ 某人
        if (properties.getAtMobiles() != null && !properties.getAtMobiles().isEmpty()) {
            content.append("---\n\n");
            content.append("**注意**: ").append(properties.getAtMobiles());
        }

        Map<String, Object> markdown = new HashMap<>();
        markdown.put("title", title);
        markdown.put("text", content.toString());
        message.put("markdown", markdown);

        // 添加 at 配置
        if (properties.getAtMobiles() != null && !properties.getAtMobiles().isEmpty()) {
            Map<String, Object> at = new HashMap<>();
            at.put("atMobiles", properties.getAtMobiles());
            at.put("isAtAll", false);
            message.put("at", at);
        }

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
        return "钉钉通知器 (WebHook: " + maskSecret(properties.getWebhookUrl()) + ")";
    }

    /**
     * 隐藏 WebHook URL 中的敏感信息
     */
    private String maskSecret(String url) {
        if (url == null || url.isEmpty()) {
            return "未配置";
        }
        // 简单隐藏，保留最后几个字符
        if (url.length() > 20) {
            return url.substring(0, 20) + "...";
        }
        return url;
    }
}