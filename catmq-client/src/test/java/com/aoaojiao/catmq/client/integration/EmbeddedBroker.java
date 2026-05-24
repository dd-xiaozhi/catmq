package com.aoaojiao.catmq.client.integration;

import com.aoaojiao.catmq.common.cache.CommonCache;
import com.aoaojiao.catmq.common.model.CatmqTopicModel;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 嵌入式 Broker 用于集成测试
 * 支持与客户端 Netty 客户端兼容的二进制协议
 *
 * @author DD
 */
public class EmbeddedBroker {

    private static final Logger log = LoggerFactory.getLogger(EmbeddedBroker.class);

    // 协议常量
    private static final int REQUEST_HEADER_SIZE = 16; // requestId(8) + requestType(4) + payloadLen(4)
    private static final int RESPONSE_HEADER_SIZE = 16; // requestId(8) + statusCode(4) + payloadLen(4)

    private final int port;
    private final String brokerName;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    private Thread serverThread;

    public EmbeddedBroker(int port, String brokerName) {
        this.port = port;
        this.brokerName = brokerName;
    }

    /**
     * 启动嵌入式 Broker
     */
    public void start() throws Exception {
        // 初始化 Topic 列表
        initTopics();

        // 创建并启动 Netty 服务器
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 128)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        // 请求解码器 - 解析客户端二进制请求
                        pipeline.addLast(new TestRequestDecoder());
                        // 响应编码器 - 发送二进制响应
                        pipeline.addLast(new TestResponseEncoder());
                        // 业务处理器
                        pipeline.addLast(new TestBrokerHandler());
                    }
                });

        InetSocketAddress address = new InetSocketAddress(port);
        ChannelFuture future = bootstrap.bind(address).sync();

        serverChannel = future.channel();

        log.info("EmbeddedBroker started on port {}", port);
    }

    /**
     * 初始化测试用 Topic
     */
    private void initTopics() {
        List<CatmqTopicModel> topics = new ArrayList<>();

        // 测试用 Topic
        CatmqTopicModel testTopic = new CatmqTopicModel();
        testTopic.setTopic("test_topic");
        topics.add(testTopic);

        CatmqTopicModel orderTopic = new CatmqTopicModel();
        orderTopic.setTopic("order_topic");
        topics.add(orderTopic);

        CommonCache.setCatmqTopicModelCache(topics);
    }

    /**
     * 异步启动 Broker
     */
    public void startAsync() {
        serverThread = new Thread(() -> {
            try {
                start();
            } catch (Exception e) {
                log.error("Failed to start EmbeddedBroker", e);
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();
    }

    /**
     * 停止嵌入式 Broker
     */
    public void stop() {
        log.info("Stopping EmbeddedBroker...");

        if (serverChannel != null) {
            serverChannel.close();
        }

        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }

        // 清理缓存
        CommonCache.clearQueueOffsetCache();
        CommonCache.setCatmqTopicModelCache(new ArrayList<>());

        log.info("EmbeddedBroker stopped");
    }

    public int getPort() {
        return port;
    }

    public String getAddress() {
        return "localhost:" + port;
    }

    // ==================== 测试用内部类 ====================

    /**
     * 测试用请求解码器 - 解析客户端二进制请求
     * 客户端协议: requestId(8) + requestType(4) + payloadLen(4) + payload
     */
    private static class TestRequestDecoder extends io.netty.handler.codec.ByteToMessageDecoder {
        // 需要的状态常量
        private static final int READ_REQUEST_ID = 0;
        private static final int READ_REQUEST_TYPE = 1;
        private static final int READ_PAYLOAD_LEN = 2;
        private static final int READ_PAYLOAD = 3;

        private int state = READ_REQUEST_ID;
        private long requestId;
        private int requestType;
        private int payloadLen;

        @Override
        protected void decode(ChannelHandlerContext ctx, io.netty.buffer.ByteBuf in, List<Object> out) throws Exception {
            // 读取 requestId (8 bytes)
            if (state == READ_REQUEST_ID) {
                if (in.readableBytes() < 8) {
                    return;
                }
                requestId = in.readLong();
                state = READ_REQUEST_TYPE;
            }

            // 读取 requestType (4 bytes)
            if (state == READ_REQUEST_TYPE) {
                if (in.readableBytes() < 4) {
                    return;
                }
                requestType = in.readInt();
                state = READ_PAYLOAD_LEN;
            }

            // 读取 payloadLen (4 bytes)
            if (state == READ_PAYLOAD_LEN) {
                if (in.readableBytes() < 4) {
                    return;
                }
                payloadLen = in.readInt();
                state = READ_PAYLOAD;
            }

            // 读取 payload
            if (state == READ_PAYLOAD) {
                if (in.readableBytes() < payloadLen) {
                    return;
                }
                byte[] payload = new byte[payloadLen];
                in.readBytes(payload);

                // 重置状态
                state = READ_REQUEST_ID;

                // 构建解析后的请求对象
                TestClientRequest request = new TestClientRequest(requestId, requestType, payload);
                out.add(request);
            }
        }

        @Override
        protected void handlerRemoved0(ChannelHandlerContext ctx) throws Exception {
            // 重置状态
            state = READ_REQUEST_ID;
        }
    }

    /**
     * 测试用响应编码器 - 发送二进制响应
     * 客户端期望协议: requestId(8) + statusCode(4) + payloadLen(4) + payload
     */
    private static class TestResponseEncoder extends io.netty.handler.codec.MessageToByteEncoder<byte[]> {
        @Override
        protected void encode(ChannelHandlerContext ctx, byte[] msg, io.netty.buffer.ByteBuf out) throws Exception {
            // 直接发送完整的响应字节数组，不添加长度前缀
            // 客户端 ResponseDecoder 直接解析: requestId(8) + statusCode(4) + payloadLen(4) + payload
            out.writeBytes(msg);
        }
    }

    /**
     * 测试用业务处理器
     */
    private static class TestBrokerHandler extends io.netty.channel.SimpleChannelInboundHandler<TestClientRequest> {

        private static final Map<String, AtomicLong> OFFSET_CACHE = new ConcurrentHashMap<>();

        // 请求类型常量（与 ClientRequest 一致）
        private static final int TYPE_SEND_MESSAGE = 1;
        private static final int TYPE_PULL_MESSAGE = 2;
        private static final int TYPE_COMMIT_OFFSET = 3;

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, TestClientRequest request) throws Exception {
            try {
                switch (request.requestType) {
                    case TYPE_SEND_MESSAGE:
                        handleSendMessage(ctx, request);
                        break;
                    case TYPE_PULL_MESSAGE:
                        handlePullMessage(ctx, request);
                        break;
                    case TYPE_COMMIT_OFFSET:
                        handleCommitOffset(ctx, request);
                        break;
                    default:
                        sendErrorResponse(ctx, request.requestId, "Unknown request type: " + request.requestType);
                }
            } catch (Exception e) {
                log.error("Error handling request", e);
                sendErrorResponse(ctx, request.requestId, e.getMessage());
            }
        }

        private void handleSendMessage(ChannelHandlerContext ctx, TestClientRequest request) {
            try {
                // 解析请求 payload
                String payloadStr = new String(request.payload, StandardCharsets.UTF_8);
                Map<String, Object> req = com.alibaba.fastjson2.JSON.parseObject(payloadStr);

                String topic = (String) req.get("topic");
                Object bodyObj = req.get("body");

                // body 在 JSON 中可能是 JSONArray (byte[]) 或 String
                String bodyStr;
                if (bodyObj instanceof String) {
                    bodyStr = (String) bodyObj;
                } else if (bodyObj instanceof List) {
                    // byte[] 被序列化为 JSONArray，需要转换回字节数组再转为字符串
                    List<?> bodyList = (List<?>) bodyObj;
                    byte[] bodyBytes = new byte[bodyList.size()];
                    for (int i = 0; i < bodyList.size(); i++) {
                        bodyBytes[i] = ((Number) bodyList.get(i)).byteValue();
                    }
                    bodyStr = new String(bodyBytes, StandardCharsets.UTF_8);
                } else {
                    sendErrorResponse(ctx, request.requestId, "body format error");
                    return;
                }

                if (topic == null || bodyStr == null) {
                    sendErrorResponse(ctx, request.requestId, "topic and body are required");
                    return;
                }

                // 验证 topic 是否存在
                boolean topicExists = CommonCache.getCatmqTopicModelList().stream()
                        .anyMatch(t -> t.getTopic().equals(topic));

                if (!topicExists) {
                    CatmqTopicModel newTopic = new CatmqTopicModel();
                    newTopic.setTopic(topic);
                    CommonCache.getCatmqTopicModelList().add(newTopic);
                }

                // 更新偏移量
                String offsetKey = topic + "#0";
                long newOffset = OFFSET_CACHE.computeIfAbsent(offsetKey, k -> new AtomicLong()).incrementAndGet();
                CommonCache.updateQueueMaxOffset(topic, 0, newOffset);

                // 构建响应
                Map<String, Object> response = new ConcurrentHashMap<>();
                response.put("success", true);
                response.put("messageId", "msg_" + System.currentTimeMillis() + "_" + newOffset);
                response.put("queueId", 0);
                response.put("physicalOffset", newOffset);

                sendSuccessResponse(ctx, request.requestId, response);

            } catch (Exception e) {
                log.error("Error handling send message", e);
                sendErrorResponse(ctx, request.requestId, e.getMessage());
            }
        }

        private void handlePullMessage(ChannelHandlerContext ctx, TestClientRequest request) {
            try {
                // 解析请求 payload
                String payloadStr = new String(request.payload, StandardCharsets.UTF_8);
                Map<String, Object> req = com.alibaba.fastjson2.JSON.parseObject(payloadStr);

                String topic = (String) req.get("topic");
                int queueId = (Integer) req.get("queueId");
                long offset = ((Number) req.get("offset")).longValue();

                if (topic == null) {
                    sendErrorResponse(ctx, request.requestId, "topic is required");
                    return;
                }

                long currentOffset = CommonCache.getQueueMaxOffset(topic, queueId);

                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("messages", new Object[]{});
                response.put("nextOffset", currentOffset);

                sendSuccessResponse(ctx, request.requestId, response);

            } catch (Exception e) {
                log.error("Error handling pull message", e);
                sendErrorResponse(ctx, request.requestId, e.getMessage());
            }
        }

        private void handleCommitOffset(ChannelHandlerContext ctx, TestClientRequest request) {
            try {
                // 解析请求 payload
                String payloadStr = new String(request.payload, StandardCharsets.UTF_8);
                Map<String, Object> req = com.alibaba.fastjson2.JSON.parseObject(payloadStr);

                String topic = (String) req.get("topic");
                int queueId = (Integer) req.get("queueId");
                long offset = ((Number) req.get("offset")).longValue();

                CommonCache.updateQueueMaxOffset(topic, queueId, offset);

                Map<String, Object> response = new HashMap<>();
                response.put("success", true);

                sendSuccessResponse(ctx, request.requestId, response);

            } catch (Exception e) {
                log.error("Error handling commit offset", e);
                sendErrorResponse(ctx, request.requestId, e.getMessage());
            }
        }

        private void sendSuccessResponse(ChannelHandlerContext ctx, long requestId, Map<String, Object> response) {
            try {
                String json = com.alibaba.fastjson2.JSON.toJSONString(response);
                byte[] payload = json.getBytes(StandardCharsets.UTF_8);

                // 构建响应: requestId(8) + statusCode(4) + payloadLen(4) + payload
                ByteBuffer buffer = ByteBuffer.allocate(RESPONSE_HEADER_SIZE + payload.length);
                buffer.order(java.nio.ByteOrder.BIG_ENDIAN);
                buffer.putLong(requestId);
                buffer.putInt(200); // SUCCESS
                buffer.putInt(payload.length);
                buffer.put(payload);

                ctx.writeAndFlush(buffer.array());
            } catch (Exception e) {
                log.error("Error sending success response", e);
            }
        }

        private void sendErrorResponse(ChannelHandlerContext ctx, long requestId, String error) {
            try {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("error", error);

                String json = com.alibaba.fastjson2.JSON.toJSONString(response);
                byte[] payload = json.getBytes(StandardCharsets.UTF_8);

                // 构建响应: requestId(8) + statusCode(4) + payloadLen(4) + payload
                ByteBuffer buffer = ByteBuffer.allocate(RESPONSE_HEADER_SIZE + payload.length);
                buffer.order(java.nio.ByteOrder.BIG_ENDIAN);
                buffer.putLong(requestId);
                buffer.putInt(500); // INTERNAL_ERROR
                buffer.putInt(payload.length);
                buffer.put(payload);

                ctx.writeAndFlush(buffer.array());
            } catch (Exception e) {
                log.error("Error sending error response", e);
            }
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            log.info("Client connected: {}", ctx.channel().remoteAddress());
            super.channelActive(ctx);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            log.info("Client disconnected: {}", ctx.channel().remoteAddress());
            super.channelInactive(ctx);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            log.error("Exception in channel", cause);
            super.exceptionCaught(ctx, cause);
        }
    }

    /**
     * 测试用客户端请求对象
     */
    private static class TestClientRequest {
        final long requestId;
        final int requestType;
        final byte[] payload;

        TestClientRequest(long requestId, int requestType, byte[] payload) {
            this.requestId = requestId;
            this.requestType = requestType;
            this.payload = payload;
        }
    }
}