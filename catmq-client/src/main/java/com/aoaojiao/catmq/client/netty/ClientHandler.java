package com.aoaojiao.catmq.client.netty;

import com.aoaojiao.catmq.client.model.ClientResponse;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 客户端处理器
 * 处理与 broker 的通信
 *
 * @author DD
 */
public class ClientHandler extends SimpleChannelInboundHandler<ClientResponse> {

    private static final Logger log = LoggerFactory.getLogger(ClientHandler.class);

    /**
     * 响应容器，用于存储等待响应的请求
     * Key: requestId
     * Value: ResponseFuture
     */
    private final ConcurrentHashMap<Long, ResponseFuture> responseMap = new ConcurrentHashMap<>();

    /**
     * 通道就绪
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("Channel active: {}", ctx.channel().remoteAddress());
        super.channelActive(ctx);
    }

    /**
     * 通道断开
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.warn("Channel inactive: {}", ctx.channel().remoteAddress());
        // 清理所有等待中的响应
        responseMap.values().forEach(future -> future.setFailure(new RuntimeException("Channel inactive")));
        responseMap.clear();
        super.channelInactive(ctx);
    }

    /**
     * 异常捕获
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("Exception caught: {}", cause.getMessage(), cause);
        ctx.close();
    }

    /**
     * 读取响应
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ClientResponse response) throws Exception {
        log.debug("Received response: requestId={}, statusCode={}",
                response.getRequestId(), response.getStatusCode());

        ResponseFuture future = responseMap.remove(response.getRequestId());
        if (future != null) {
            future.setResponse(response);
        } else {
            log.warn("No waiting request found for response: requestId={}", response.getRequestId());
        }
    }

    /**
     * 发送请求并等待响应
     *
     * @param requestId 请求 ID
     * @param timeoutMs 超时时间（毫秒）
     * @return 响应
     * @throws Exception 如果超时或发生错误
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
     * 响应Future
     */
    public static class ResponseFuture {
        private ClientResponse response;
        private Throwable failure;
        private final CountDownLatch latch = new CountDownLatch(1);

        public void setResponse(ClientResponse response) {
            this.response = response;
            latch.countDown();
        }

        public void setFailure(Throwable failure) {
            this.failure = failure;
            latch.countDown();
        }

        public ClientResponse get(long timeout, TimeUnit unit) throws Exception {
            if (!latch.await(timeout, unit)) {
                throw new RuntimeException("Request timeout");
            }
            if (failure != null) {
                throw new RuntimeException(failure);
            }
            return response;
        }
    }
}