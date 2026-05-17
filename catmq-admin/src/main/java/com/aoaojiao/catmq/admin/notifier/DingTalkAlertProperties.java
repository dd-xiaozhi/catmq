package com.aoaojiao.catmq.admin.notifier;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 钉钉告警配置属性
 *
 * @author DD
 */
@Component
@ConfigurationProperties(prefix = "catmq.admin.alert.notifier.dingtalk")
public class DingTalkAlertProperties {

    /**
     * 是否启用钉钉告警
     */
    private boolean enabled = false;

    /**
     * 钉钉 WebHook URL
     */
    private String webhookUrl;

    /**
     * 密签密钥（可选，用于加签模式）
     */
    private String secret;

    /**
     * @ 的手机号列表
     */
    private List<String> atMobiles;

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

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public List<String> getAtMobiles() {
        return atMobiles;
    }

    public void setAtMobiles(List<String> atMobiles) {
        this.atMobiles = atMobiles;
    }
}