package com.aoaojiao.catmq.client.netty;

import com.aoaojiao.catmq.client.model.ClientRequest;
import com.alibaba.fastjson2.JSON;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 请求编码器
 * 将 ClientRequest 编码为字节数组
 *
 * @author DD
 */
public class RequestEncoder extends MessageToByteEncoder<ClientRequest> {

    private static final Logger log = LoggerFactory.getLogger(RequestEncoder.class);

    @Override
    protected void encode(ChannelHandlerContext ctx, ClientRequest request, ByteBuf out) throws Exception {
        try {
            // 协议格式：
            // +----------------+----------------+----------------+----------------+
            // | requestId(8)  | requestType(4)| payloadLen(4)  | payload        |
            // +----------------+----------------+----------------+----------------+

            byte[] payload = request.getPayload();

            // 计算总长度：requestId(8) + requestType(4) + payloadLen(4) + payload
            int totalLen = 8 + 4 + 4 + (payload != null ? payload.length : 0);

            out.ensureWritable(totalLen);

            // 写入 requestId
            out.writeLong(request.getRequestId());

            // 写入 requestType
            out.writeInt(request.getRequestType());

            // 写入 payloadLen
            out.writeInt(payload != null ? payload.length : 0);

            // 写入 payload
            if (payload != null && payload.length > 0) {
                out.writeBytes(payload);
            }

            log.debug("Encoded request: requestId={}, type={}", request.getRequestId(),
                    ClientRequest.getTypeName(request.getRequestType()));

        } catch (Exception e) {
            log.error("Failed to encode request: {}", request, e);
            throw e;
        }
    }
}