package com.aoaojiao.catmq.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * CatMQ Admin 管理平台启动类
 *
 * @author DD
 */
@SpringBootApplication
@EnableScheduling
public class CatmqAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(CatmqAdminApplication.class, args);
    }
}