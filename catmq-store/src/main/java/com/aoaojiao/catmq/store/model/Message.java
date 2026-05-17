package com.aoaojiao.catmq.store.model;

import com.aoaojiao.catmq.store.constants.StoreConstant;
import com.aoaojiao.catmq.store.util.CRCUtil;
import com.aoaojiao.catmq.store.util.MMapUtil;
import lombok.Builder;
import lombok.Data;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 消息体
 * 最小数据单元
 *
 * @author DD
 */
@Data
@Builder
public class Message {

    // ========== 消息头（36 字节固定）==========
    /**
     * 魔数，用于校验
     */
    private int magicCode;

    /**
     * 消息体 CRC 校验
     */
    private int bodyCRC;

    /**
     * 队列 ID
     */
    private int queueId;

    /**
     * 消息标志
     */
    private int flag;

    /**
     * 属性长度
     */
    private int propertiesLength;

    /**
     * 系统标志
     */
    private int sysFlag;

    /**
     * 消息体长度
     */
    private int bodyLength;

    /**
     * 时间戳
     */
    private long timestamp;

    // ========== 消息体 ==========
    /**
     * 消息体内容
     */
    private byte[] body;

    // ========== 属性 ==========
    /**
     * 消息属性（KV 结构）
     */
    private Map<String, String> properties;

    // ========== 运行时字段（不序列化）==========
    /**
     * CommitLog 物理偏移量
     */
    @Builder.Default
    private long physicalOffset = -1;

    /**
     * 消息总大小（包含消息头）
     */
    @Builder.Default
    private int totalSize = 0;

    /**
     * 序列化消息为字节数组
     *
     * @return 字节数组
     */
    public byte[] convertToBytes() {
        // 计算属性 JSON 长度
        byte[] propertiesBytes = serializeProperties();

        // 计算总大小
        int totalSize = 36 + (this.body != null ? this.body.length : 0) + propertiesBytes.length;

        // 分配 ByteBuffer
        ByteBuffer buffer = ByteBuffer.allocate(totalSize);

        // 写入消息头（36 字节）
        buffer.putInt(this.magicCode);
        buffer.putInt(this.bodyCRC);
        buffer.putInt(this.queueId);
        buffer.putInt(this.flag);
        buffer.putInt(this.propertiesLength);
        buffer.putInt(this.sysFlag);
        buffer.putInt(this.bodyLength);
        buffer.putLong(this.timestamp);

        // 写入消息体
        if (this.body != null && this.body.length > 0) {
            buffer.put(this.body);
        }

        // 写入属性
        if (propertiesBytes.length > 0) {
            buffer.put(propertiesBytes);
        }

        this.totalSize = totalSize;
        return buffer.array();
    }

    /**
     * 从字节数组反序列化
     *
     * @param data 字节数组
     * @return Message 对象
     */
    public static Message parseFrom(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);

        // 读取消息头
        int magicCode = buffer.getInt();
        int bodyCRC = buffer.getInt();
        int queueId = buffer.getInt();
        int flag = buffer.getInt();
        int propertiesLength = buffer.getInt();
        int sysFlag = buffer.getInt();
        int bodyLength = buffer.getInt();
        long timestamp = buffer.getLong();

        // 读取消息体
        byte[] body = null;
        if (bodyLength > 0) {
            body = new byte[bodyLength];
            buffer.get(body);
        }

        // 读取属性
        Map<String, String> properties = null;
        if (propertiesLength > 0) {
            byte[] propsData = new byte[propertiesLength];
            buffer.get(propsData);
            properties = deserializeProperties(propsData);
        }

        Message message = Message.builder()
                .magicCode(magicCode)
                .bodyCRC(bodyCRC)
                .queueId(queueId)
                .flag(flag)
                .propertiesLength(propertiesLength)
                .sysFlag(sysFlag)
                .bodyLength(bodyLength)
                .timestamp(timestamp)
                .body(body)
                .properties(properties)
                .totalSize(data.length)
                .build();

        return message;
    }

    /**
     * 计算消息总大小（包含消息头）
     *
     * @return 消息总大小
     */
    public int calculateBodySize() {
        byte[] propertiesBytes = serializeProperties();
        return 36 + (this.body != null ? this.body.length : 0) + propertiesBytes.length;
    }

    /**
     * 计算消息体的 CRC
     *
     * @return CRC 值
     */
    public int calculateBodyCRC() {
        if (this.body == null || this.body.length == 0) {
            return 0;
        }
        return CRCUtil.crc32(this.body);
    }

    /**
     * 设置默认值
     */
    public void initDefaultValues() {
        if (this.magicCode == 0) {
            this.magicCode = StoreConstant.MESSAGE_MAGIC_CODE;
        }
        if (this.bodyCRC == 0 && this.body != null) {
            this.bodyCRC = calculateBodyCRC();
        }
        if (this.body != null) {
            this.bodyLength = this.body.length;
        }
        if (this.propertiesLength == 0 && this.properties != null && !this.properties.isEmpty()) {
            this.propertiesLength = serializeProperties().length;
        }
    }

    /**
     * 序列化属性为字节数组
     */
    private byte[] serializeProperties() {
        if (this.properties == null || this.properties.isEmpty()) {
            return new byte[0];
        }
        String propsJson = com.alibaba.fastjson2.JSON.toJSONString(this.properties);
        return propsJson.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 反序列化属性
     */
    private static Map<String, String> deserializeProperties(byte[] data) {
        if (data == null || data.length == 0) {
            return new HashMap<>();
        }
        String propsJson = new String(data, StandardCharsets.UTF_8);
        return com.alibaba.fastjson2.JSON.parseObject(propsJson,
                new com.alibaba.fastjson2.TypeReference<Map<String, String>>() {});
    }

    /**
     * 获取标签哈希
     *
     * @return 标签哈希
     */
    public long getTagCode() {
        if (this.properties == null) {
            return 0;
        }
        String tags = this.properties.get("TAGS");
        if (tags == null || tags.isEmpty()) {
            return 0;
        }
        return CRCUtil.crc64(tags);
    }

    /**
     * 创建简单消息
     *
     * @param body 消息体
     * @return Message 对象
     */
    public static Message createSimpleMessage(byte[] body) {
        return Message.builder()
                .magicCode(StoreConstant.MESSAGE_MAGIC_CODE)
                .body(body)
                .bodyLength(body != null ? body.length : 0)
                .bodyCRC(CRCUtil.crc32(body))
                .queueId(0)
                .flag(0)
                .propertiesLength(0)
                .sysFlag(0)
                .timestamp(System.currentTimeMillis())
                .properties(new HashMap<>())
                .build();
    }
}
