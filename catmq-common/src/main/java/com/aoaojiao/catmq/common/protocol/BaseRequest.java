package com.aoaojiao.catmq.common.protocol;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 基础请求类
 *
 * @author DD
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class BaseRequest {

    /**
     * 请求 ID
     */
    private long requestId;

    /**
     * 请求时间戳
     */
    private long timestamp;

    public BaseRequest() {
        this.timestamp = System.currentTimeMillis();
    }
}
