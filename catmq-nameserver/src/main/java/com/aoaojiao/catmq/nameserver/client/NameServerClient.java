package com.aoaojiao.catmq.nameserver.client;

import com.aoaojiao.catmq.common.model.BrokerInfo;
import com.aoaojiao.catmq.nameserver.model.TopicRouteInfo;
import com.aoaojiao.catmq.nameserver.protocol.*;
import com.aoaojiao.catmq.nameserver.server.NettyMessageDecoder;
import com.aoaojiao.catmq.nameserver.server.NettyMessageEncoder;
import com.alibaba.fastjson2.JSON;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * NameServer 客户端
 * 用于 Broker 和 Client 连接 NameServer
 *
 * @author DD
 */
public class NameServerClient extends SimpleChannelInboundHandler<NettyMessage> {

    private static final Logger log = LoggerFactory.getLogger(NameServerClient.class);

    private final String host;
    private final int port;
    private final Map<Integer, ResponseFuture> responseMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, BrokerInfo> brokerCache = new ConcurrentHashMap<>();

    private EventLoopGroup group;
    private Channel channel;
    private String clientId;

    public NameServerClient(String address) {
        String[] parts = address.split(":");
        this.host = parts[0];
        this.port = Integer.parseInt(parts[1]);
        this.clientId = "client-" + System.currentTimeMillis();
    }

    /**
     * 连接到 NameServer
     */
    public void connect() throws InterruptedException {
        group = new NioEventLoopGroup(1);

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        // 帧解码器 - 基于长度字段的解码
                        pipeline.addLast(new LengthFieldBasedFrameDecoder(1048576, 0, 4, 0, 4));
                        // 自定义解码器和编码器
                        pipeline.addLast(new NettyMessageEncoder());
                        pipeline.addLast(new NettyMessageDecoder());
                        // 业务处理器
                        pipeline.addLast(NameServerClient.this);
                    }
                });

        ChannelFuture future = bootstrap.connect(host, port).sync();
        this.channel = future.channel();
        log.info("Connected to NameServer at {}:{}", host, port);
    }

    /**
     * 关闭连接
     */
    public void close() {
        if (channel != null) {
            channel.close();
        }
        if (group != null) {
            group.shutdownGracefully();
        }
        log.info("NameServerClient closed");
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, NettyMessage msg) throws Exception {
        // 根据消息类型处理响应
        switch (msg.getType()) {
            case RequestType.BROKER_REGISTER_RESPONSE:
                handleBrokerRegisterResponse(msg);
                break;
            case RequestType.HEART_BEAT_RESPONSE:
                handleHeartBeatResponse(msg);
                break;
            case RequestType.TOPIC_ROUTE_RESPONSE:
                handleTopicRouteResponse(msg);
                break;
            case RequestType.GET_ALL_BROKER_RESPONSE:
                handleGetAllBrokerResponse(msg);
                break;
            case RequestType.ERROR_RESPONSE:
                handleErrorResponse(msg);
                break;
            default:
                log.warn("Unknown response type: {}", msg.getType());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("Channel exception", cause);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.info("Channel disconnected");
    }

    // ==================== 业务方法 ====================

    /**
     * 注册 Broker 到 NameServer
     */
    public boolean registerBroker(BrokerInfo brokerInfo) throws InterruptedException, TimeoutException {
        BrokerRegisterRequest request = BrokerRegisterRequest.fromBrokerInfo(brokerInfo);
        request.setRequestId(generateRequestId());

        ResponseFuture future = new ResponseFuture(request.getRequestId());
        responseMap.put(request.getRequestId().hashCode(), future);

        NettyMessage message = new NettyMessage(RequestType.BROKER_REGISTER, JSON.toJSONString(request));
        channel.writeAndFlush(message);

        BaseResponse response = future.getResponse(5, TimeUnit.SECONDS);
        responseMap.remove(request.getRequestId().hashCode());

        if (response != null && response.getCode() == 0) {
            log.info("Broker {} registered successfully", brokerInfo.getBrokerName());
            return true;
        }
        log.warn("Broker registration failed: {}", response != null ? response.getMessage() : "timeout");
        return false;
    }

    /**
     * 发送心跳
     */
    public boolean sendHeartBeat(String brokerName, String[] topicList) throws InterruptedException, TimeoutException {
        BrokerHeartBeatRequest request = new BrokerHeartBeatRequest();
        request.setRequestId(generateRequestId());
        request.setBrokerName(brokerName);
        request.setBrokerIp("127.0.0.1");
        request.setBrokerPort(10911);
        request.setBrokerId("1");
        request.setTopicList(Arrays.asList(topicList));

        ResponseFuture future = new ResponseFuture(request.getRequestId());
        responseMap.put(request.getRequestId().hashCode(), future);

        NettyMessage message = new NettyMessage(RequestType.BROKER_HEART_BEAT, JSON.toJSONString(request));
        channel.writeAndFlush(message);

        BaseResponse response = future.getResponse(5, TimeUnit.SECONDS);
        responseMap.remove(request.getRequestId().hashCode());

        if (response != null && response.getCode() == 0) {
            log.debug("Heartbeat sent successfully for {}", brokerName);
            return true;
        }
        log.debug("Heartbeat failed: {}", response != null ? response.getMessage() : "timeout");
        return false;
    }

    /**
     * 查询 Topic 路由
     */
    public List<BrokerInfo> getTopicRoute(String topic) throws InterruptedException, TimeoutException {
        TopicRouteRequest request = new TopicRouteRequest();
        request.setRequestId(generateRequestId());
        request.setTopic(topic);

        ResponseFuture future = new ResponseFuture(request.getRequestId());
        responseMap.put(request.getRequestId().hashCode(), future);

        NettyMessage message = new NettyMessage(RequestType.GET_TOPIC_ROUTE, JSON.toJSONString(request));
        channel.writeAndFlush(message);

        BaseResponse response = future.getResponse(5, TimeUnit.SECONDS);
        responseMap.remove(request.getRequestId().hashCode());

        if (response != null && response.getCode() == 0 && response.getData() != null) {
            // 解析返回的 TopicRouteResponse
            TopicRouteResponse routeResponse = JSON.parseObject(
                    JSON.toJSONString(response.getData()), TopicRouteResponse.class);
            if (routeResponse != null && routeResponse.getRouteInfoList() != null) {
                List<BrokerInfo> brokerInfos = new ArrayList<>();
                for (TopicRouteInfo routeInfo : routeResponse.getRouteInfoList()) {
                    if (routeInfo.getBrokerInfoList() != null) {
                        brokerInfos.addAll(routeInfo.getBrokerInfoList());
                    }
                }
                if (!brokerInfos.isEmpty()) {
                    brokerCache.put(topic, brokerInfos.get(0));
                    return brokerInfos;
                }
            }
        }
        log.debug("Topic route query failed or no route found for topic: {}", topic);
        return null;
    }

    // ==================== 响应处理 ====================

    private void handleBrokerRegisterResponse(NettyMessage msg) {
        BaseResponse response = JSON.parseObject(msg.getBody(), BaseResponse.class);
        completeResponse(response);
    }

    private void handleHeartBeatResponse(NettyMessage msg) {
        BaseResponse response = JSON.parseObject(msg.getBody(), BaseResponse.class);
        completeResponse(response);
    }

    private void handleTopicRouteResponse(NettyMessage msg) {
        TopicRouteResponse response = JSON.parseObject(msg.getBody(), TopicRouteResponse.class);
        // 将 TopicRouteResponse 包装在 BaseResponse 中以保持一致性
        BaseResponse baseResponse = new BaseResponse();
        baseResponse.setCode(response.getCode());
        baseResponse.setMessage(response.getMessage());
        baseResponse.setRequestId(response.getRequestId());
        baseResponse.setData(response);  // 将整个 TopicRouteResponse 对象设置到 data
        completeResponse(baseResponse);
    }

    private void handleGetAllBrokerResponse(NettyMessage msg) {
        BaseResponse response = JSON.parseObject(msg.getBody(), BaseResponse.class);
        completeResponse(response);
    }

    private void handleErrorResponse(NettyMessage msg) {
        BaseResponse response = JSON.parseObject(msg.getBody(), BaseResponse.class);
        completeResponse(response);
        log.error("Error response: {}", response.getMessage());
    }

    private void completeResponse(BaseResponse response) {
        ResponseFuture future = responseMap.get(response.getRequestId().hashCode());
        if (future != null) {
            future.putResponse(response);
        }
    }

    // ==================== 工具方法 ====================

    private String generateRequestId() {
        return clientId + "-" + System.currentTimeMillis() + "-" + (int) (Math.random() * 10000);
    }

    /**
     * 响应future，用于同步等待响应
     */
    private static class ResponseFuture {
        private final String requestId;
        private final CountDownLatch latch = new CountDownLatch(1);
        private BaseResponse response;

        ResponseFuture(String requestId) {
            this.requestId = requestId;
        }

        public void putResponse(BaseResponse response) {
            this.response = response;
            latch.countDown();
        }

        public BaseResponse getResponse(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
            if (!latch.await(timeout, unit)) {
                throw new TimeoutException("Response timeout for request: " + requestId);
            }
            return response;
        }
    }

    // ==================== Getter ====================

    public boolean isConnected() {
        return channel != null && channel.isActive();
    }

    public ConcurrentHashMap<String, BrokerInfo> getBrokerCache() {
        return brokerCache;
    }
}
