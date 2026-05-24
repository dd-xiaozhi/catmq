package com.aoaojiao.catmq.nameserver.server;

import com.aoaojiao.catmq.nameserver.config.NameServerConfig;
import com.aoaojiao.catmq.common.model.BrokerInfo;
import com.aoaojiao.catmq.nameserver.model.TopicRouteInfo;
import com.aoaojiao.catmq.nameserver.protocol.*;
import com.aoaojiao.catmq.nameserver.service.RouteInfoManager;
import com.alibaba.fastjson2.JSON;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

/**
 * NameServer Netty 消息处理器
 * 统一处理 Broker 注册、心跳、客户端路由查询等请求
 *
 * @author DD
 */
public class NameServerHandler extends SimpleChannelInboundHandler<NettyMessage> {

    private static final Logger log = LoggerFactory.getLogger(NameServerHandler.class);

    private final RouteInfoManager routeInfoManager;
    private final NameServerConfig config;

    public NameServerHandler(RouteInfoManager routeInfoManager, NameServerConfig config) {
        this.routeInfoManager = routeInfoManager;
        this.config = config;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, NettyMessage msg) throws Exception {
        Channel channel = ctx.channel();
        String remoteAddress = channel.remoteAddress().toString();

        log.debug("Received message type: {} from {}", msg.getType(), remoteAddress);

        try {
            switch (msg.getType()) {
                case RequestType.BROKER_REGISTER:
                    handleBrokerRegister(ctx, msg);
                    break;

                case RequestType.BROKER_HEART_BEAT:
                    handleBrokerHeartBeat(ctx, msg);
                    break;

                case RequestType.BROKER_UN_REGISTER:
                    handleBrokerUnRegister(ctx, msg);
                    break;

                case RequestType.GET_TOPIC_ROUTE:
                    handleGetTopicRoute(ctx, msg);
                    break;

                case RequestType.GET_ALL_BROKER:
                    handleGetAllBroker(ctx, msg);
                    break;

                default:
                    log.warn("Unknown message type: {}", msg.getType());
                    sendErrorResponse(ctx, msg, "Unknown request type");
                    break;
            }
        } catch (Exception e) {
            log.error("Error processing message from {}: {}", remoteAddress, e.getMessage(), e);
            sendErrorResponse(ctx, msg, "Internal server error: " + e.getMessage());
        }
    }

    /**
     * 处理 Broker 注册请求
     */
    private void handleBrokerRegister(ChannelHandlerContext ctx, NettyMessage msg) {
        BrokerRegisterRequest request = JSON.parseObject(msg.getBody(), BrokerRegisterRequest.class);

        log.info("Broker register request: {}:{}:{}",
                request.getBrokerName(), request.getBrokerIp(), request.getBrokerPort());

        // 转换为 BrokerInfo 并注册
        BrokerInfo brokerInfo = request.toBrokerInfo();
        routeInfoManager.registerBroker(brokerInfo);

        // 发送响应
        BaseResponse response = BaseResponse.success(request.getRequestId(),
                "Broker registered successfully");
        sendMessage(ctx, RequestType.BROKER_REGISTER_RESPONSE, response);
    }

    /**
     * 处理 Broker 心跳请求
     */
    private void handleBrokerHeartBeat(ChannelHandlerContext ctx, NettyMessage msg) {
        BrokerHeartBeatRequest request = JSON.parseObject(msg.getBody(), BrokerHeartBeatRequest.class);

        boolean success = routeInfoManager.heartBeat(request.getBrokerName(), request.getTopicList());

        if (success) {
            log.debug("Broker heart beat: {}", request.getBrokerName());
            BaseResponse response = BaseResponse.success(request.getRequestId());
            sendMessage(ctx, RequestType.HEART_BEAT_RESPONSE, response);
        } else {
            log.warn("Broker heart beat failed, broker not found: {}", request.getBrokerName());
            BaseResponse response = BaseResponse.fail(request.getRequestId(), "Broker not found");
            sendMessage(ctx, RequestType.HEART_BEAT_RESPONSE, response);
        }
    }

    /**
     * 处理 Broker 注销请求
     */
    private void handleBrokerUnRegister(ChannelHandlerContext ctx, NettyMessage msg) {
        BrokerRegisterRequest request = JSON.parseObject(msg.getBody(), BrokerRegisterRequest.class);

        log.info("Broker unregister request: {}", request.getBrokerName());
        routeInfoManager.unRegisterBroker(request.getBrokerName());

        BaseResponse response = BaseResponse.success(request.getRequestId(),
                "Broker unregistered successfully");
        sendMessage(ctx, RequestType.BROKER_REGISTER_RESPONSE, response);
    }

    /**
     * 处理查询 Topic 路由请求
     */
    private void handleGetTopicRoute(ChannelHandlerContext ctx, NettyMessage msg) {
        TopicRouteRequest request = JSON.parseObject(msg.getBody(), TopicRouteRequest.class);

        log.debug("Topic route query: {}", request.getTopic());

        TopicRouteInfo routeInfo = routeInfoManager.getTopicRouteInfo(request.getTopic());

        if (routeInfo != null) {
            // 过滤掉不存活的 Broker
            List<BrokerInfo> aliveBrokers = routeInfo.getBrokerInfoList().stream()
                    .filter(BrokerInfo::isAlive)
                    .collect(Collectors.toList());

            if (aliveBrokers.isEmpty()) {
                // 所有 Broker 都不存活
                TopicRouteResponse response = TopicRouteResponse.fail(
                        request.getRequestId(), "No alive broker for topic: " + request.getTopic());
                sendMessage(ctx, RequestType.TOPIC_ROUTE_RESPONSE, response);
            } else {
                routeInfo.setBrokerInfoList(aliveBrokers);
                TopicRouteResponse response = TopicRouteResponse.success(request.getRequestId(),
                        java.util.Collections.singletonList(routeInfo));
                sendMessage(ctx, RequestType.TOPIC_ROUTE_RESPONSE, response);
            }
        } else {
            TopicRouteResponse response = TopicRouteResponse.fail(
                    request.getRequestId(), "Topic not found: " + request.getTopic());
            sendMessage(ctx, RequestType.TOPIC_ROUTE_RESPONSE, response);
        }
    }

    /**
     * 处理查询所有 Broker 请求
     */
    private void handleGetAllBroker(ChannelHandlerContext ctx, NettyMessage msg) {
        BaseRequest request = JSON.parseObject(msg.getBody(), BaseRequest.class);

        log.debug("Query all brokers");

        List<BrokerInfo> brokers = routeInfoManager.getAllBrokers().stream()
                .filter(BrokerInfo::isAlive)
                .collect(Collectors.toList());

        BaseResponse response = BaseResponse.success(request.getRequestId(), brokers);
        sendMessage(ctx, RequestType.GET_ALL_BROKER_RESPONSE, response);
    }

    /**
     * 发送错误响应
     */
    private void sendErrorResponse(ChannelHandlerContext ctx, NettyMessage msg, String errorMsg) {
        try {
            BaseResponse response = BaseResponse.fail(msg.getMessageId(), -1, errorMsg);
            sendMessage(ctx, RequestType.ERROR_RESPONSE, response);
        } catch (Exception e) {
            log.error("Failed to send error response", e);
        }
    }

    /**
     * 发送消息
     */
    private void sendMessage(ChannelHandlerContext ctx, int type, Object data) {
        String json = JSON.toJSONString(data);
        NettyMessage response = new NettyMessage(type, json);
        ctx.writeAndFlush(response);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("Channel connected: {}", ctx.channel().remoteAddress());
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.info("Channel disconnected: {}", ctx.channel().remoteAddress());
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("Channel exception: {}", ctx.channel().remoteAddress(), cause);
        super.exceptionCaught(ctx, cause);
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        log.debug("Channel registered: {}", ctx.channel());
        super.channelRegistered(ctx);
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        log.debug("Channel unregistered: {}", ctx.channel());
        super.channelUnregistered(ctx);
    }
}