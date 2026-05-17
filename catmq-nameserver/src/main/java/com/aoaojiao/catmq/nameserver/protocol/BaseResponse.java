package com.aoaojiao.catmq.nameserver.protocol;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 通用响应类
 *
 * @author DD
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class BaseResponse {

    /**
     * 响应状态码
     * 0: 成功
     * -1: 失败
     */
    private int code;

    /**
     * 响应消息
     */
    private String message;

    /**
     * 请求 ID
     */
    private String requestId;

    /**
     * 响应数据（可选）
     */
    private Object data;

    /**
     * 创建成功响应
     */
    public static BaseResponse success(String requestId) {
        BaseResponse response = new BaseResponse();
        response.setCode(0);
        response.setMessage("success");
        response.setRequestId(requestId);
        return response;
    }

    /**
     * 创建成功响应（带数据）
     */
    public static BaseResponse success(String requestId, Object data) {
        BaseResponse response = new BaseResponse();
        response.setCode(0);
        response.setMessage("success");
        response.setRequestId(requestId);
        response.setData(data);
        return response;
    }

    /**
     * 创建失败响应
     */
    public static BaseResponse fail(String requestId, String message) {
        BaseResponse response = new BaseResponse();
        response.setCode(-1);
        response.setMessage(message);
        response.setRequestId(requestId);
        return response;
    }

    /**
     * 创建失败响应
     */
    public static BaseResponse fail(String requestId, int code, String message) {
        BaseResponse response = new BaseResponse();
        response.setCode(code);
        response.setMessage(message);
        response.setRequestId(requestId);
        return response;
    }
}