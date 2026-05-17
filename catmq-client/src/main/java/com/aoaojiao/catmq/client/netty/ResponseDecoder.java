package com.aoaojiao.catmq.client.netty;

import com.aoaojiao.catmq.client.model.ClientResponse;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 响应解码器
 * 将字节数组解码为 ClientResponse
 *
 * @author DD
 */
public class ResponseDecoder extends ByteToMessageDecoder {

    private static final Logger log = LoggerFactory.getLogger(ResponseDecoder.class);

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // 协议格式：
        // +----------------+----------------+----------------+----------------+
        // | requestId(8)  | statusCode(4)  | payloadLen(4)  | payload        |
        // +----------------+----------------+----------------+----------------+

        // 需要至少读取 16 字节：requestId(8) + statusCode(4) + payloadLen(4)
        if (in.readableBytes() < 16) {
            return;
        }

        // 标记当前读取位置
        in.markReaderIndex();

        // 读取 requestId
        long requestId = in.readLong();

        // 读取 statusCode
        int statusCode = in.readInt();

        // 读取 payloadLen
        int payloadLen = in.readInt();

        // 检查是否有足够的字节读取 payload
        if (in.readableBytes() < payloadLen) {
            // 数据不完整，重置读取位置
            in.resetReaderIndex();
            return;
        }

        // 读取 payload
        byte[] payload = null;
        if (payloadLen > 0) {
            payload = new byte[payloadLen];
            in.readBytes(payload);
        }

        // 构建响应对象
        ClientResponse response = ClientResponse.builder()
                .requestId(requestId)
                .statusCode(statusCode)
                .payload(payload)
                .build();

        log.debug("Decoded response: requestId={}, statusCode={}", requestId, statusCode);

        out.add(response);
    }
}