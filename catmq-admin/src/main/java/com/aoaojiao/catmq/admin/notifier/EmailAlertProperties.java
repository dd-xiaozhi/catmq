package com.aoaojiao.catmq.admin.notifier;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 邮件告警配置属性
 *
 * @author DD
 */
@Component
@ConfigurationProperties(prefix = "catmq.admin.alert.notifier.email")
public class EmailAlertProperties {

    /**
     * 是否启用邮件告警
     */
    private boolean enabled = false;

    /**
     * 发件人邮箱地址
     */
    private String fromAddress;

    /**
     * 收件人邮箱地址列表
     */
    private List<String> toAddresses;

    /**
     * SMTP 服务器地址
     */
    private String smtpHost;

    /**
     * SMTP 端口
     */
    private int smtpPort = 587;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public void setFromAddress(String fromAddress) {
        this.fromAddress = fromAddress;
    }

    public List<String> getToAddresses() {
        return toAddresses;
    }

    public void setToAddresses(List<String> toAddresses) {
        this.toAddresses = toAddresses;
    }

    public String getSmtpHost() {
        return smtpHost;
    }

    public void setSmtpHost(String smtpHost) {
        this.smtpHost = smtpHost;
    }

    public int getSmtpPort() {
        return smtpPort;
    }

    public void setSmtpPort(int smtpPort) {
        this.smtpPort = smtpPort;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}