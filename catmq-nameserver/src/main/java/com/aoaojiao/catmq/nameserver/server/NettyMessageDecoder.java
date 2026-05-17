package com.aoaojiao.catmq.nameserver.server;

import com.aoaojiao.catmq.nameserver.protocol.NettyMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * NettyMessage 解码器
 * 注意：配合 LengthFieldBasedFrameDecoder 使用，已剥离总长度字段
 * 格式: [魔数 4字节] + [类型 4字节] + [时间戳 8字节] + [消息ID长度 4字节] + [消息ID] + [消息体长度 4字节] + [消息体]
 *
 * @author DD
 */
public class NettyMessageDecoder extends ByteToMessageDecoder {

    private static final Logger log = LoggerFactory.getLogger(NettyMessageDecoder.class);

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // 1. 检查是否可读（至少需要魔数 + 类型 = 8 字节）
        if (in.readableBytes() < 8) {
            return;
        }

        // 2. 标记当前读取位置（用于回退）
        in.markReaderIndex();

        // 3. 读取魔数并校验
        int magic = in.readInt();
        if (magic != NettyMessage.MAGIC) {
            log.warn("Invalid magic number: 0x{}, expected: 0x{}", Integer.toHexString(magic), Integer.toHexString(NettyMessage.MAGIC));
            // 回退并跳过此字节，尝试重新同步
            in.resetReaderIndex();
            if (in.readableBytes() > 0) {
                in.readByte();
            }
            return;
        }

        // 4. 检查剩余数据是否足够（至少：类型4 + 时间戳8 + 消息ID长度4 = 16 字节头部）
        if (in.readableBytes() < 16) {
            in.resetReaderIndex();
            return;
        }

        try {
            // 5. 读取类型
            int type = in.readInt();

            // 6. 读取时间戳
            long timestamp = in.readLong();

            // 7. 读取消息 ID
            int messageIdLength = in.readInt();
            String messageId = null;
            if (messageIdLength > 0) {
                if (in.readableBytes() < messageIdLength) {
                    in.resetReaderIndex();
                    return;
                }
                byte[] messageIdBytes = new byte[messageIdLength];
                in.readBytes(messageIdBytes);
                messageId = new String(messageIdBytes, java.nio.charset.StandardCharsets.UTF_8);
            }

            // 8. 读取消息体
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

            // 9. 构建 NettyMessage
            NettyMessage nettyMessage = new NettyMessage();
            nettyMessage.setType(type);
            nettyMessage.setBody(body);
            nettyMessage.setTimestamp(timestamp);
            nettyMessage.setMessageId(messageId);

            out.add(nettyMessage);
            log.debug("Decoded message: type={}, bodyLength={}, messageId={}", type, bodyLength, messageId);

        } catch (Exception e) {
            log.error("Failed to decode NettyMessage", e);
            in.resetReaderIndex();
            // 跳过当前帧，继续处理
            if (in.readableBytes() > 0) {
                int skipBytes = Math.min(in.readableBytes(), 128);
                in.readBytes(skipBytes);
            }
        }
    }
}