package com.aoaojiao.catmq.nameserver.server;

import com.aoaojiao.catmq.nameserver.protocol.NettyMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NettyMessage 编码器
 * 格式: [魔数 4字节] + [类型 4字节] + [时间戳 8字节] + [消息ID长度 4字节] + [消息ID] + [消息体长度 4字节] + [消息体]
 *
 * @author DD
 */
public class NettyMessageEncoder extends MessageToByteEncoder<NettyMessage> {

    private static final Logger log = LoggerFactory.getLogger(NettyMessageEncoder.class);

    @Override
    protected void encode(ChannelHandlerContext ctx, NettyMessage msg, ByteBuf out) throws Exception {
        try {
            // 1. 计算消息体长度
            byte[] body = msg.getBody();
            int bodyLength = body != null ? body.length : 0;

            // 2. 计算消息 ID 长度
            String messageId = msg.getMessageId();
            byte[] messageIdBytes = messageId != null ? messageId.getBytes(java.nio.charset.StandardCharsets.UTF_8) : new byte[0];
            int messageIdLength = messageIdBytes.length;

            // 3. 写入数据（LengthFieldBasedFrameDecoder 会在前面加上总长度）
            out.writeInt(NettyMessage.MAGIC);
            out.writeInt(msg.getType());
            out.writeLong(msg.getTimestamp());

            // 消息 ID
            out.writeInt(messageIdLength);
            if (messageIdLength > 0) {
                out.writeBytes(messageIdBytes);
            }

            // 消息体
            out.writeInt(bodyLength);
            if (bodyLength > 0) {
                out.writeBytes(body);
            }

        } catch (Exception e) {
            log.error("Failed to encode NettyMessage", e);
            throw e;
        }
    }
}