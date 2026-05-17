package com.aoaojiao.catmq.admin.notifier;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 企业微信告警配置属性
 *
 * @author DD
 */
@Component
@ConfigurationProperties(prefix = "catmq.admin.alert.notifier.wechat")
public class WeChatWorkAlertProperties {

    /**
     * 是否启用企业微信告警
     */
    private boolean enabled = false;

    /**
     * 企业微信 WebHook URL
     */
    private String webhookUrl;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }
}