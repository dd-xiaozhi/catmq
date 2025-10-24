package com.aoaojiao.catmq.store.model;

import lombok.Builder;
import lombok.Data;

/**
 * 消息体
 * 最小的数据单元
 *
 * @author DD
 */
@Data
@Builder
public class MessageModel {

    /**
     * 消息内容
     */
    private byte[] content;

    /**
     * 消息大小，单位：字节
     */
    private int size;

    /**
     * 读取成字节输错
     *
     * @return
     */
    public byte[] convertToBytes() {
        
        return null;
    }
}
