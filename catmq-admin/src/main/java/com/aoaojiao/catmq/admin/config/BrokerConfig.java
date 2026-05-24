package com.aoaojiao.catmq.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Broker 配置
 *
 * @author DD
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "catmq.broker")
public class BrokerConfig {

    private String address = "http://localhost:9090";
}
