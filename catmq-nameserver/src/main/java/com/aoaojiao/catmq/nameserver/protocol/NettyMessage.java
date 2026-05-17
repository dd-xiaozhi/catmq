package com.aoaojiao.catmq.nameserver.protocol;

import java.io.Serializable;

/**
 * Netty message protocol header
 * Universal message format for all request/response messages
 *
 * @author DD
 */
public class NettyMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Magic number for message validation */
    public static final int MAGIC = 0xCAFEBABE;

    /** Message type */
    private int type;

    /** Message body serialized to byte array */
    private byte[] body;

    /** Message creation timestamp */
    private long timestamp;

    /** Message ID (for idempotency, deduplication, etc.) */
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