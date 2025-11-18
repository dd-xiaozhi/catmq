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
public class Message {

    private int size;
    private byte[] content;

    /**
     * 读取成字节输错
     *
     * @return 字节数组
     */
    public byte[] convertToBytes() {
        
        return null;
    }
}
