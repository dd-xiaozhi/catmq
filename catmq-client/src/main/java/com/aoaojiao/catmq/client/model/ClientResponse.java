package com.aoaojiao.catmq.client.model;

import lombok.Builder;
import lombok.Data;

/**
 * 客户端响应消息
 *
 * @author DD
 */
@Data
@Builder
public class ClientResponse {

    /**
     * 响应 ID，匹配请求的 requestId
     */
    private long requestId;

    /**
     * 响应状态码
     */
    private int statusCode;

    /**
     * 状态消息
     */
    private String statusMessage;

    /**
     * 响应数据（JSON 序列化后的字节数组）
     */
    private byte[] payload;

    // ==================== 状态码常量 ====================

    /**
     * 成功
     */
    public static final int SUCCESS = 200;

    /**
     * 请求错误
     */
    public static final int BAD_REQUEST = 400;

    /**
     * 服务器内部错误
     */
    public static final int INTERNAL_ERROR = 500;

    /**
     * 请求超时
     */
    public static final int TIMEOUT = 504;

    /**
     * 消息不存在
     */
    public static final int MSG_NOT_FOUND = 404;

    /**
     * 获取状态码名称
     */
    public static String getStatusName(int code) {
        switch (code) {
            case SUCCESS:
                return "SUCCESS";
            case BAD_REQUEST:
                return "BAD_REQUEST";
            case INTERNAL_ERROR:
                return "INTERNAL_ERROR";
            case TIMEOUT:
                return "TIMEOUT";
            case MSG_NOT_FOUND:
                return "MSG_NOT_FOUND";
            default:
                return "UNKNOWN";
        }
    }

    /**
     * 判断是否成功
     */
    public boolean isSuccess() {
        return statusCode == SUCCESS;
    }
}