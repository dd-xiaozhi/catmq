package com.aoaojiao.catmq.nameserver.server;

import com.aoaojiao.catmq.nameserver.config.NameServerConfig;
import com.aoaojiao.catmq.nameserver.service.HeartBeatService;
import com.aoaojiao.catmq.nameserver.service.RouteInfoManager;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

/**
 * NameServer Netty 服务端
 * 负责启动和管理 Netty 服务器，接受 Broker 和 Client 的连接请求
 *
 * @author DD
 */
public class NameServer {

    private static final Logger log = LoggerFactory.getLogger(NameServer.class);

    private final NameServerConfig config;
    private final RouteInfoManager routeInfoManager;
    private final HeartBeatService heartBeatService;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    public NameServer(NameServerConfig config, RouteInfoManager routeInfoManager, HeartBeatService heartBeatService) {
        this.config = config;
        this.routeInfoManager = routeInfoManager;
        this.heartBeatService = heartBeatService;
    }

    /**
     * 启动 NameServer
     */
    public void start() {
        // 1. 初始化 Boss 和 Worker 线程组
        bossGroup = new NioEventLoopGroup(config.getBossThreadCount());
        workerGroup = new NioEventLoopGroup(config.getWorkerThreadCount());

        try {
            // 2. 配置 ServerBootstrap
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline pipeline = ch.pipeline();

                            // 添加日志处理器
                            pipeline.addLast(new LoggingHandler(LogLevel.DEBUG));

                            // 帧解码器 - 基于长度字段的解码
                            // 参数：maxFrameLength, lengthFieldOffset, lengthFieldLength, lengthAdjustment, initialBytesToStrip
                            pipeline.addLast(new LengthFieldBasedFrameDecoder(
                                    config.getMaxFrameLength(), 0, 4, 0, 4));

                            // 自定义解码器和编码器
                            pipeline.addLast(new NettyMessageDecoder());
                            pipeline.addLast(new NettyMessageEncoder());

                            // 业务处理器
                            pipeline.addLast(new NameServerHandler(routeInfoManager, config));
                        }
                    });

            // 3. 绑定端口并启动
            InetSocketAddress address = new InetSocketAddress(config.getServerPort());
            ChannelFuture future = bootstrap.bind(address).sync();

            serverChannel = future.channel();

            log.info("=================================================");
            log.info("  NameServer started successfully!");
            log.info("  Listen on: {}", address);
            log.info("  HeartBeat timeout: {}ms", config.getHeartBeatTimeoutMs());
            log.info("  HeartBeat check interval: {}ms", config.getHeartBeatCheckIntervalMs());
            log.info("=================================================");

            // 4. 启动心跳检测服务
            heartBeatService.start();

            // 5. 从文件加载路由信息
            routeInfoManager.load();

            // 6. 等待服务器关闭
            serverChannel.closeFuture().sync();

        } catch (Exception e) {
            log.error("Failed to start NameServer", e);
            throw new RuntimeException("NameServer start failed", e);
        } finally {
            shutdown();
        }
    }

    /**
     * 关闭 NameServer
     */
    public void shutdown() {
        log.info("Shutting down NameServer...");

        // 1. 关闭心跳检测服务
        if (heartBeatService != null) {
            heartBeatService.shutdown();
        }

        // 2. 持久化路由信息
        if (routeInfoManager != null) {
            routeInfoManager.persist();
        }

        // 3. 关闭 Netty 线程组
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }

        // 4. 关闭服务器 Channel
        if (serverChannel != null) {
            serverChannel.close();
        }

        log.info("NameServer shutdown completed");
    }

    /**
     * 关闭服务器 Channel
     */
    public void close() {
        if (serverChannel != null) {
            serverChannel.close();
        }
    }

    /**
     * 获取当前在线的 Broker 数量
     */
    public int getOnlineBrokerCount() {
        return heartBeatService.getAliveBrokerCount();
    }

    /**
     * 获取已注册的 Broker 总数
     */
    public int getTotalBrokerCount() {
        return heartBeatService.getTotalBrokerCount();
    }
}