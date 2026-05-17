package com.aoaojiao.catmq.nameserver.protocol;

import com.aoaojiao.catmq.nameserver.model.TopicRouteInfo;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Topic 路由响应
 *
 * @author DD
 */
@Data
@EqualsAndHashCode(callSuper = false)
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
    private String requestId;

    /**
     * 路由信息列表
     */
    private List<TopicRouteInfo> routeInfoList;

    /**
     * 创建成功响应
     */
    public static TopicRouteResponse success(String requestId, List<TopicRouteInfo> routeInfoList) {
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
    public static TopicRouteResponse fail(String requestId, String message) {
        TopicRouteResponse response = new TopicRouteResponse();
        response.setCode(-1);
        response.setMessage(message);
        response.setRequestId(requestId);
        return response;
    }
}