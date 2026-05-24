package com.aoaojiao.catmq.common.protocol;

import java.io.Serializable;

/**
 * Netty 消息包装
 *
 * @author DD
 */
public class NettyMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 魔数，用于消息验证
     */
    public static final int MAGIC = 0xCAFEBABE;

    /**
     * 消息 ID
     */
    private long requestId;

    /**
     * 消息类型
     */
    private int type;

    /**
     * 消息体（网络传输使用 byte[]）
     */
    private byte[] body;

    /**
     * 消息创建时间戳
     */
    private long timestamp;

    /**
     * 消息 ID 字符串（用于兼容性）
     */
    private String messageId;

    public NettyMessage() {
    }

    public NettyMessage(int type, byte[] body) {
        this.type = type;
        this.body = body;
        this.timestamp = System.currentTimeMillis();
    }

    public NettyMessage(int type, String bodyStr) {
        this.type = type;
        this.body = bodyStr.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        this.timestamp = System.currentTimeMillis();
    }

    public long getRequestId() {
        return requestId;
    }

    public void setRequestId(long requestId) {
        this.requestId = requestId;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public byte[] getBody() {
        return body;
    }

    public void setBody(byte[] body) {
        this.body = body;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }
}
