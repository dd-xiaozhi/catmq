package com.aoaojiao.catmq.common.protocol;

import java.util.List;

/**
 * Topic 路由响应
 *
 * @author DD
 */
public class TopicRouteResponse {

    /**
     * 响应状态码
     * 0: 成功
     * >0: 失败
     */
    private int code;

    /**
     * 响应消息
     */
    private String message;

    /**
     * 请求 ID（用于追踪）
     */
    private long requestId;

    /**
     * 路由信息列表
     */
    private List<TopicRouteInfo> routeInfoList;

    public TopicRouteResponse() {
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public long getRequestId() {
        return requestId;
    }

    public void setRequestId(long requestId) {
        this.requestId = requestId;
    }

    public List<TopicRouteInfo> getRouteInfoList() {
        return routeInfoList;
    }

    public void setRouteInfoList(List<TopicRouteInfo> routeInfoList) {
        this.routeInfoList = routeInfoList;
    }

    /**
     * 创建成功响应
     */
    public static TopicRouteResponse success(long requestId, List<TopicRouteInfo> routeInfoList) {
        TopicRouteResponse response = new TopicRouteResponse();
        response.setCode(0);
        response.setMessage("success");
        response.setRequestId(requestId);
        response.setRouteInfoList(routeInfoList);
        return response;
    }

    /**
     * 创建失败响应
     */
    public static TopicRouteResponse fail(long requestId, String message) {
        TopicRouteResponse response = new TopicRouteResponse();
        response.setCode(-1);
        response.setMessage(message);
        response.setRequestId(requestId);
        return response;
    }
}
