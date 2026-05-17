package com.aoaojiao.catmq.store.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ConsumerQueue 索引条目
 * 固定 20 字节：physicalOffset(8) + size(4) + tagCode(8)
 *
 * @author DD
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CQIndex {

    /**
     * CommitLog 物理偏移量
     */
    private long physicalOffset;

    /**
     * 消息大小
     */
    private int size;

    /**
     * 标签哈希（用于消息过滤）
     */
    private long tagCode;

    /**
     * 创建索引
     */
    public static CQIndex of(long physicalOffset, int size, long tagCode) {
        return new CQIndex(physicalOffset, size, tagCode);
    }

    /**
     * 检查是否有效
     */
    public boolean isValid() {
        return physicalOffset >= 0 && size > 0;
    }

    @Override
    public String toString() {
        return "CQIndex{" +
                "physicalOffset=" + physicalOffset +
                ", size=" + size +
                ", tagCode=" + tagCode +
                '}';
    }
}
