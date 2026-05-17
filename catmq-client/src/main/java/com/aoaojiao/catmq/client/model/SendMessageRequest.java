package com.aoaojiao.catmq.client.model;

import lombok.Builder;
import lombok.Data;

/**
 * 发送消息请求
 *
 * @author DD
 */
@Data
@Builder
public class SendMessageRequest {

    /**
     * 主题
     */
    private String topic;

    /**
     * 消息体（JSON 序列化后的字节数组）
     */
    private byte[] body;

    /**
     * 标签（可选）
     */
    private String tags;

    /**
     * 消息属性（可选）
     */
    private String properties;
}