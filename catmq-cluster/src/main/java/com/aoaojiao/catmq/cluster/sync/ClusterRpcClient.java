package com.aoaojiao.catmq.cluster.sync;

import com.aoaojiao.catmq.client.model.ClientRequest;
import com.aoaojiao.catmq.client.model.ClientResponse;
import com.aoaojiao.catmq.client.netty.RequestEncoder;
import com.aoaojiao.catmq.client.netty.ResponseDecoder;
import com.aoaojiao.catmq.common.model.BrokerInfo;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 集群内部 RPC 客户端
 * 用于主从节点之间的数据同步
 *
 * @author DD
 */
public class ClusterRpcClient {

    private static final Logger logger = LoggerFactory.getLogger(ClusterRpcClient.class);

    /**
     * 默认连接超时时间（毫秒）
     */
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 3000;

    /**
     * 默认请求超时时间（毫秒）
     */
    private static final int DEFAULT_REQUEST_TIMEOUT_MS = 5000;

    /**
     * 连接池：key 为 broker 地址 (host:port)
     */
    private final ConcurrentHashMap<String, BrokerConnection> connectionPool = new ConcurrentHashMap<>();

    /**
     * 获取 broker 连接
     *
     * @param broker broker 信息
     * @return 连接对象
     */
    public BrokerConnection getConnection(BrokerInfo broker) {
        String key = broker.getAddress();
        return connectionPool.computeIfAbsent(key, k -> new BrokerConnection(broker));
    }

    /**
     * 同步发送复制请求到从节点
     *
     * @param slave     从节点信息
     * @param data      要复制的数据
     * @param timeoutMs 超时时间
     * @return 是否成功
     */
    public boolean replicateToSlave(BrokerInfo slave, byte[] data, long timeoutMs) {
        try {
            BrokerConnection connection = getConnection(slave);
            if (!connection.connect()) {
                logger.warn("连接从节点失败：{}", slave.getAddress());
                return false;
            }

            ClientRequest request = ClientRequest.builder()
                    .requestId(generateRequestId())
                    .requestType(ClientRequest.REPLICATE_DATA)
                    .payload(data)
                    .build();

            ClientResponse response = connection.sendRequest(request, timeoutMs);
            if (response != null && response.isSuccess()) {
                logger.debug("数据复制到从节点成功：{}", slave.getBrokerId());
                return true;
            } else {
                logger.warn("数据复制到从节点失败，状态码={}：{}",
                        response != null ? response.getStatusCode() : -1, slave.getBrokerId());
                return false;
            }
        } catch (Exception e) {
            logger.error("数据复制到从节点异常：{}", slave.getBrokerId(), e);
            return false;
        }
    }

    /**
     * 异步发送复制请求到从节点
     *
     * @param slave 从节点信息
     * @param data  要复制的数据
     * @return 异步结果
     */
    public CompletableResult replicateToSlaveAsync(BrokerInfo slave, byte[] data) {
        CompletableResult result = new CompletableResult();
        try {
            BrokerConnection connection = getConnection(slave);
            if (!connection.connect()) {
                logger.warn("连接从节点失败：{}", slave.getAddress());
                result.setSuccess(false);
                return result;
            }

            ClientRequest request = ClientRequest.builder()
                    .requestId(generateRequestId())
                    .requestType(ClientRequest.REPLICATE_DATA)
                    .payload(data)
                    .build();

            connection.sendRequestAsync(request, DEFAULT_REQUEST_TIMEOUT_MS, result);
        } catch (Exception e) {
            logger.error("异步数据复制异常：{}", slave.getBrokerId(), e);
            result.setSuccess(false);
            result.setError(e.getMessage());
        }
        return result;
    }

    /**
     * 关闭所有连接
     */
    public void shutdown() {
        logger.info("关闭所有集群 RPC 连接...");
        connectionPool.values().forEach(BrokerConnection::shutdown);
        connectionPool.clear();
        logger.info("集群 RPC 连接已全部关闭");
    }

    /**
     * 生成请求 ID
     */
    private static long requestIdCounter = 0;

    private static long generateRequestId() {
        return System.currentTimeMillis() * 1000 + (++requestIdCounter % 1000);
    }

    /**
     * Broker 连接封装
     */
    public static class BrokerConnection {
        private static final Logger log = LoggerFactory.getLogger(BrokerConnection.class);

        private final BrokerInfo broker;
        private EventLoopGroup group;
        private Channel channel;
        private RpcHandler handler;
        private volatile boolean connected = false;

        public BrokerConnection(BrokerInfo broker) {
            this.broker = broker;
        }

        /**
         * 连接 Broker
         */
        public synchronized boolean connect() {
            if (connected && channel != null && channel.isActive()) {
                return true;
            }

            shutdown();

            group = new NioEventLoopGroup(1);
            handler = new RpcHandler();

            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, DEFAULT_CONNECT_TIMEOUT_MS)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(new ResponseDecoder());
                            pipeline.addLast(new RequestEncoder());
                            pipeline.addLast(handler);
                        }
                    });

            try {
                String[] address = broker.getAddress().split(":");
                String host = address[0];
                int port = Integer.parseInt(address[1]);

                ChannelFuture future = bootstrap.connect(host, port).sync();
                if (future.isSuccess()) {
                    this.channel = future.channel();
                    this.connected = true;
                    log.info("已连接到 Broker：{}", broker.getAddress());
                    return true;
                } else {
                    log.error("连接 Broker 失败：{}", broker.getAddress());
                    return false;
                }
            } catch (Exception e) {
                log.error("连接 Broker 异常：{}", broker.getAddress(), e);
                shutdown();
                return false;
            }
        }

        /**
         * 同步发送请求
         */
        public ClientResponse sendRequest(ClientRequest request, long timeoutMs) throws Exception {
            if (!connected || channel == null || !channel.isActive()) {
                if (!connect()) {
                    throw new RuntimeException("无法连接到 Broker：" + broker.getAddress());
                }
            }
            return handler.sendRequest(request.getRequestId(), () -> {
                channel.writeAndFlush(request);
            }, timeoutMs);
        }

        /**
         * 异步发送请求
         */
        public void sendRequestAsync(ClientRequest request, long timeoutMs, CompletableResult result) {
            try {
                if (!connected || channel == null || !channel.isActive()) {
                    if (!connect()) {
                        result.setSuccess(false);
                        result.setError("无法连接到 Broker：" + broker.getAddress());
                        return;
                    }
                }
                handler.sendRequestAsync(request.getRequestId(), () -> {
                    channel.writeAndFlush(request);
                }, timeoutMs, result);
            } catch (Exception e) {
                result.setSuccess(false);
                result.setError(e.getMessage());
            }
        }

        /**
         * 关闭连接
         */
        public synchronized void shutdown() {
            connected = false;
            if (channel != null) {
                try {
                    channel.close();
                } catch (Exception e) {
                    log.debug("关闭通道异常：{}", e.getMessage());
                }
                channel = null;
            }
            if (group != null) {
                group.shutdownGracefully();
                group = null;
            }
        }

        /**
         * 检查是否已连接
         */
        public boolean isConnected() {
            return connected && channel != null && channel.isActive();
        }
    }

    /**
     * RPC 处理器
     */
    private static class RpcHandler extends SimpleChannelInboundHandler<ClientResponse> {

        private static final Logger log = LoggerFactory.getLogger(RpcHandler.class);

        private final ConcurrentHashMap<Long, ResponseFuture> responseMap = new ConcurrentHashMap<>();

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ClientResponse response) throws Exception {
            log.debug("收到响应：requestId={}, statusCode={}",
                    response.getRequestId(), response.getStatusCode());

            ResponseFuture future = responseMap.remove(response.getRequestId());
            if (future != null) {
                future.setResponse(response);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            log.error("RPC 通道异常：{}", cause.getMessage(), cause);
            ctx.close();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            log.warn("RPC 通道断开");
            responseMap.values().forEach(future -> future.setFailure(new RuntimeException("通道断开")));
            responseMap.clear();
            super.channelInactive(ctx);
        }

        /**
         * 同步发送请求并等待响应
         */
        public ClientResponse sendRequest(long requestId, Runnable sender, long timeoutMs) throws Exception {
            ResponseFuture future = new ResponseFuture();
            responseMap.put(requestId, future);

            try {
                sender.run();
                ClientResponse response = future.get(timeoutMs, TimeUnit.MILLISECONDS);
                return response;
            } finally {
                responseMap.remove(requestId);
            }
        }

        /**
         * 异步发送请求
         */
        public void sendRequestAsync(long requestId, Runnable sender, long timeoutMs, CompletableResult result) {
            ResponseFuture future = new ResponseFuture(result);
            responseMap.put(requestId, future);

            try {
                sender.run();
                // 异步等待，超时由 CompletableResult 自己处理
            } catch (Exception e) {
                responseMap.remove(requestId);
                result.setSuccess(false);
                result.setError(e.getMessage());
            }
        }
    }

    /**
     * 响应 Future
     */
    private static class ResponseFuture {
        private final CountDownLatch latch = new CountDownLatch(1);
        private ClientResponse response;
        private Throwable failure;
        private final CompletableResult asyncResult;

        public ResponseFuture() {
            this.asyncResult = null;
        }

        public ResponseFuture(CompletableResult asyncResult) {
            this.asyncResult = asyncResult;
        }

        public void setResponse(ClientResponse response) {
            this.response = response;
            latch.countDown();
            if (asyncResult != null) {
                asyncResult.setResponse(response);
            }
        }

        public void setFailure(Throwable failure) {
            this.failure = failure;
            latch.countDown();
            if (asyncResult != null) {
                asyncResult.setSuccess(false);
                asyncResult.setError(failure.getMessage());
            }
        }

        public ClientResponse get(long timeout, TimeUnit unit) throws Exception {
            if (!latch.await(timeout, unit)) {
                throw new RuntimeException("请求超时");
            }
            if (failure != null) {
                throw new RuntimeException(failure);
            }
            return response;
        }
    }

    /**
     * 异步操作结果
     */
    public static class CompletableResult {
        private volatile boolean success;
        private volatile String error;
        private volatile ClientResponse response;
        private final AtomicInteger ackCounter = new AtomicInteger(0);

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public void setError(String error) {
            this.error = error;
        }

        public void setResponse(ClientResponse response) {
            this.response = response;
            this.success = response.isSuccess();
        }

        public boolean isSuccess() {
            return success;
        }

        public String getError() {
            return error;
        }

        public int incrementAck() {
            return ackCounter.incrementAndGet();
        }

        public int getAckCount() {
            return ackCounter.get();
        }
    }
}