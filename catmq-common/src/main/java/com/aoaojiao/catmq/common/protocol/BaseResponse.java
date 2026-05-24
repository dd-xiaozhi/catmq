package com.aoaojiao.catmq.common.protocol;

/**
 * 基础响应类
 *
 * @author DD
 */
public class BaseResponse {

    /**
     * 响应状态码
     * 0: 成功
     * -1: 失败
     */
    private int code;

    /**
     * 请求 ID
     */
    private long requestId;

    /**
     * 响应消息
     */
    private String message;

    /**
     * 响应数据（可选）
     */
    private Object data;

    public BaseResponse() {
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public long getRequestId() {
        return requestId;
    }

    public void setRequestId(long requestId) {
        this.requestId = requestId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    /**
     * 判断请求是否成功
     */
    public boolean isSuccess() {
        return code == 0;
    }

    /**
     * 创建成功响应
     */
    public static BaseResponse success(long requestId) {
        BaseResponse response = new BaseResponse();
        response.setCode(0);
        response.setMessage("success");
        response.setRequestId(requestId);
        return response;
    }

    /**
     * 创建成功响应（带数据）
     */
    public static BaseResponse success(long requestId, Object data) {
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
    public static BaseResponse fail(long requestId, String message) {
        BaseResponse response = new BaseResponse();
        response.setCode(-1);
        response.setMessage(message);
        response.setRequestId(requestId);
        return response;
    }

    /**
     * 创建失败响应
     */
    public static BaseResponse fail(long requestId, int code, String message) {
        BaseResponse response = new BaseResponse();
        response.setCode(code);
        response.setMessage(message);
        response.setRequestId(requestId);
        return response;
    }
}
