package com.aoaojiao.catmq.client.model;

import lombok.Builder;
import lombok.Data;

/**
 * 客户端请求消息
 *
 * @author DD
 */
@Data
@Builder
public class ClientRequest {

    /**
     * 请求 ID，用于匹配响应
     */
    private long requestId;

    /**
     * 请求类型
     */
    private int requestType;

    /**
     * 请求数据（JSON 序列化后的字节数组）
     */
    private byte[] payload;

    // ==================== 请求类型常量 ====================

    /**
     * 发送消息
     */
    public static final int SEND_MESSAGE = 1;

    /**
     * 拉取消息
     */
    public static final int PULL_MESSAGE = 2;

    /**
     * 提交消费进度
     */
    public static final int COMMIT_OFFSET = 3;

    /**
     * 获取请求类型名称
     */
    public static String getTypeName(int type) {
        switch (type) {
            case SEND_MESSAGE:
                return "SEND_MESSAGE";
            case PULL_MESSAGE:
                return "PULL_MESSAGE";
            case COMMIT_OFFSET:
                return "COMMIT_OFFSET";
            default:
                return "UNKNOWN";
        }
    }
}