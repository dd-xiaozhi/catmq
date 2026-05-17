package com.aoaojiao.catmq.client.netty;

import com.aoaojiao.catmq.client.config.ClientConfig;
import com.aoaojiao.catmq.client.model.ClientRequest;
import com.aoaojiao.catmq.client.model.ClientResponse;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Netty 客户端
 * 管理与 Broker 的网络连接
 *
 * @author DD
 */
public class NettyClient {

    private static final Logger log = LoggerFactory.getLogger(NettyClient.class);

    private final ClientConfig config;
    private final String host;
    private final int port;

    private EventLoopGroup group;
    private Channel channel;
    private ClientHandler handler;

    /**
     * 连接状态
     */
    private volatile boolean connected = false;

    public NettyClient(ClientConfig config) {
        this.config = config;
        String[] address = config.getBrokerAddress().split(":");
        this.host = address[0];
        this.port = Integer.parseInt(address[1]);
    }

    /**
     * 连接 Broker
     */
    public void connect() throws Exception {
        if (connected) {
            return;
        }

        group = new NioEventLoopGroup();
        handler = new ClientHandler();

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.getConnectTimeoutMs())
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
            ChannelFuture future = bootstrap.connect(host, port).sync();
            if (future.isSuccess()) {
                this.channel = future.channel();
                this.connected = true;
                log.info("Connected to broker: {}:{}", host, port);
            } else {
                throw new RuntimeException("Failed to connect to broker");
            }
        } catch (Exception e) {
            group.shutdownGracefully();
            throw e;
        }
    }

    /**
     * 发送请求并等待响应
     *
     * @param request   请求
     * @param timeoutMs 超时时间
     * @return 响应
     */
    public ClientResponse sendRequest(ClientRequest request, long timeoutMs) throws Exception {
        if (!connected || channel == null || !channel.isActive()) {
            // 尝试重连
            connect();
        }

        return handler.sendRequest(request.getRequestId(), () -> {
            channel.writeAndFlush(request);
        }, timeoutMs);
    }

    /**
     * 同步发送请求（带重试）
     *
     * @param request 请求
     * @return 响应
     */
    public ClientResponse sendRequestWithRetry(ClientRequest request) {
        int retryTimes = 0;
        Exception lastException = null;

        while (retryTimes < config.getMaxRetryTimes()) {
            try {
                return sendRequest(request, config.getRequestTimeoutMs());
            } catch (Exception e) {
                lastException = e;
                retryTimes++;
                log.warn("Send request failed, retry {} times: {}", retryTimes, e.getMessage());

                if (retryTimes < config.getMaxRetryTimes()) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(config.getRetryIntervalMs());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Retry interrupted");
                    }

                    // 重新连接
                    connected = false;
                    try {
                        connect();
                    } catch (Exception re) {
                        log.error("Reconnect failed: {}", re.getMessage());
                    }
                }
            }
        }

        throw new RuntimeException("Send request failed after " + retryTimes + " retries", lastException);
    }

    /**
     * 关闭连接
     */
    public void shutdown() {
        log.info("Shutting down Netty client...");
        connected = false;

        if (channel != null) {
            channel.close();
            channel = null;
        }

        if (group != null) {
            group.shutdownGracefully();
            group = null;
        }

        log.info("Netty client shutdown completed");
    }

    /**
     * 判断是否已连接
     */
    public boolean isConnected() {
        return connected && channel != null && channel.isActive();
    }
}