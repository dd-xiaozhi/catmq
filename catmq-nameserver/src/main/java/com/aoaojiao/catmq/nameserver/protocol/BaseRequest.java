package com.aoaojiao.catmq.nameserver.protocol;

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

    private String requestId;
    private long timestamp;

    public BaseRequest() {
        this.timestamp = System.currentTimeMillis();
    }
}