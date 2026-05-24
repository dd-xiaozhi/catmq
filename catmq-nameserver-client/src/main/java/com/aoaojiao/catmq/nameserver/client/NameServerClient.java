package com.aoaojiao.catmq.nameserver.client;

import com.aoaojiao.catmq.common.protocol.BaseRequest;
import com.aoaojiao.catmq.common.protocol.BaseResponse;
import com.aoaojiao.catmq.common.protocol.BrokerHeartBeatRequest;
import com.aoaojiao.catmq.nameserver.model.BrokerInfo;
import com.aoaojiao.catmq.common.protocol.BrokerRegisterRequest;
import com.aoaojiao.catmq.common.protocol.NettyMessage;
import com.aoaojiao.catmq.common.protocol.RequestType;
import com.aoaojiao.catmq.common.protocol.TopicRouteRequest;
import com.alibaba.fastjson2.JSON;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * NameServer 连接异常
 */
class NameServerConnectionException extends Exception {
    public NameServerConnectionException(String message) {
        super(message);
    }

    public NameServerConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}

/**
 * NameServer 客户端
 * 用于 Broker 注册和 Client 查询路由
 *
 * @author DD
 */
public class NameServerClient {

    private static final Logger log = LoggerFactory.getLogger(NameServerClient.class);

    private final String nameServerHost;
    private final int nameServerPort;
    private final AtomicLong requestIdGenerator = new AtomicLong(0);

    private EventLoopGroup group;
    private Channel channel;
    private ConcurrentHashMap<Long, CompletableFuture<BaseResponse>> pendingRequests = new ConcurrentHashMap<>();

    public NameServerClient(String nameServerAddress) {
        String[] parts = nameServerAddress.split(":");
        this.nameServerHost = parts[0];
        this.nameServerPort = Integer.parseInt(parts[1]);
    }

    /**
     * 连接 NameServer
     */
    public void connect() throws Exception {
        group = new NioEventLoopGroup();

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new LengthFieldBasedFrameDecoder(1048576, 0, 4, 0, 4));
                        pipeline.addLast(new ClientMessageEncoder());
                        pipeline.addLast(new ClientMessageDecoder());
                        pipeline.addLast(new ClientHandler());
                    }
                });

        ChannelFuture future = bootstrap.connect(nameServerHost, nameServerPort).sync();
        if (future.isSuccess()) {
            this.channel = future.channel();
            log.info("Connected to NameServer: {}:{}", nameServerHost, nameServerPort);
        } else {
            throw new NameServerConnectionException("Failed to connect to NameServer");
        }
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
        log.info("NameServer client closed");
    }

    /**
     * 注册 Broker
     */
    public boolean registerBroker(BrokerInfo brokerInfo) {
        try {
            BrokerRegisterRequest request = new BrokerRegisterRequest();
            request.setRequestId(generateRequestId());
            request.setBrokerName(brokerInfo.getBrokerName());
            request.setBrokerIp(brokerInfo.getBrokerIp());
            request.setBrokerPort(brokerInfo.getBrokerPort());
            request.setBrokerId(brokerInfo.getBrokerId());
            request.setWeight(brokerInfo.getWeight());
            request.setClusterName(brokerInfo.getClusterName());
            request.setTopicList(brokerInfo.getTopicList());

            BaseResponse response = sendRequest(RequestType.BROKER_REGISTER, request);
            return response != null && response.isSuccess();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Failed to register broker: {}", brokerInfo.getBrokerName(), e);
            return false;
        } catch (ExecutionException e) {
            log.error("Failed to register broker: {}", brokerInfo.getBrokerName(), e.getCause());
            return false;
        }
    }

    /**
     * 发送心跳
     */
    public boolean sendHeartBeat(String brokerName, String[] topicList) {
        try {
            BrokerHeartBeatRequest request = new BrokerHeartBeatRequest();
            request.setRequestId(generateRequestId());
            request.setBrokerName(brokerName);
            request.setTopicList(topicList);

            BaseResponse response = sendRequest(RequestType.BROKER_HEART_BEAT, request);
            return response != null && response.isSuccess();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Failed to send heart beat for broker: {}", brokerName, e);
            return false;
        } catch (ExecutionException e) {
            log.error("Failed to send heart beat for broker: {}", brokerName, e.getCause());
            return false;
        }
    }

    /**
     * 查询 Topic 路由
     */
    public List<BrokerInfo> getTopicRoute(String topic) {
        try {
            TopicRouteRequest request = new TopicRouteRequest();
            request.setRequestId(generateRequestId());
            request.setTopic(topic);

            BaseResponse response = sendRequest(RequestType.GET_TOPIC_ROUTE, request);
            if (response != null && response.isSuccess() && response.getData() != null) {
                return JSON.parseArray(response.getData().toString(), BrokerInfo.class);
            }
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Failed to get topic route: {}", topic, e);
            return null;
        } catch (ExecutionException e) {
            log.error("Failed to get topic route: {}", topic, e.getCause());
            return null;
        }
    }

    private long generateRequestId() {
        return requestIdGenerator.incrementAndGet();
    }

    private BaseResponse sendRequest(int type, Object request) throws Exception {
        long requestId = generateRequestId();
        String json = JSON.toJSONString(request);
        NettyMessage msg = new NettyMessage(type, json);
        msg.setMessageId(String.valueOf(requestId));

        CompletableFuture<BaseResponse> future = new CompletableFuture<>();
        pendingRequests.put(requestId, future);

        channel.writeAndFlush(msg);

        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.warn("Request timeout, type: {}, requestId: {}", type, requestId);
            return null;
        } finally {
            pendingRequests.remove(requestId);
        }
    }

    /**
     * 客户端消息编码器 - 与 NameServer 协议兼容
     */
    private class ClientMessageEncoder extends MessageToByteEncoder<NettyMessage> {
        @Override
        protected void encode(ChannelHandlerContext ctx, NettyMessage msg, ByteBuf out) throws Exception {
            byte[] body = msg.getBody();
            int bodyLength = body != null ? body.length : 0;

            String messageId = msg.getMessageId();
            byte[] messageIdBytes = messageId != null ? messageId.getBytes(StandardCharsets.UTF_8) : new byte[0];
            int messageIdLength = messageIdBytes.length;

            out.writeInt(NettyMessage.MAGIC);
            out.writeInt(msg.getType());
            out.writeLong(msg.getTimestamp());
            out.writeInt(messageIdLength);
            if (messageIdLength > 0) {
                out.writeBytes(messageIdBytes);
            }
            out.writeInt(bodyLength);
            if (bodyLength > 0) {
                out.writeBytes(body);
            }
        }
    }

    /**
     * 客户端消息解码器 - 与 NameServer 协议兼容
     */
    private class ClientMessageDecoder extends ByteToMessageDecoder {
        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
            if (in.readableBytes() < 8) {
                return;
            }

            in.markReaderIndex();

            int magic = in.readInt();
            if (magic != NettyMessage.MAGIC) {
                in.resetReaderIndex();
                if (in.readableBytes() > 0) {
                    in.readByte();
                }
                return;
            }

            if (in.readableBytes() < 16) {
                in.resetReaderIndex();
                return;
            }

            int type = in.readInt();
            long timestamp = in.readLong();

            int messageIdLength = in.readInt();
            String messageId = null;
            if (messageIdLength > 0) {
                if (in.readableBytes() < messageIdLength) {
                    in.resetReaderIndex();
                    return;
                }
                byte[] messageIdBytes = new byte[messageIdLength];
                in.readBytes(messageIdBytes);
                messageId = new String(messageIdBytes, StandardCharsets.UTF_8);
            }

            int bodyLength = in.readInt();
            byte[] body = null;
            if (bodyLength > 0) {
                if (in.readableBytes() < bodyLength) {
                    in.resetReaderIndex();
                    return;
                }
                body = new byte[bodyLength];
                in.readBytes(body);
            }

            NettyMessage nettyMessage = new NettyMessage();
            nettyMessage.setType(type);
            nettyMessage.setBody(body);
            nettyMessage.setTimestamp(timestamp);
            nettyMessage.setMessageId(messageId);

            out.add(nettyMessage);
        }
    }

    private class ClientHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            NettyMessage nettyMessage = (NettyMessage) msg;
            String bodyStr = new String(nettyMessage.getBody(), StandardCharsets.UTF_8);
            BaseResponse baseResponse = JSON.parseObject(bodyStr, BaseResponse.class);

            if (nettyMessage.getMessageId() != null) {
                try {
                    long requestId = Long.parseLong(nettyMessage.getMessageId());
                    CompletableFuture<BaseResponse> future = pendingRequests.get(requestId);
                    if (future != null) {
                        future.complete(baseResponse);
                    }
                } catch (NumberFormatException e) {
                    log.warn("Invalid requestId: {}", nettyMessage.getMessageId());
                }
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            log.error("Exception in NameServer client", cause);
        }
    }
}
