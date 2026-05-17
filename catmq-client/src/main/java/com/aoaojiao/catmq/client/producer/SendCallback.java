package com.aoaojiao.catmq.client.producer;

import com.aoaojiao.catmq.client.model.SendMessageResponse;

/**
 * 发送回调接口
 *
 * @author DD
 */
public interface SendCallback {
    /**
     * 发送成功回调
     *
     * @param response 发送响应
     */
    void onSuccess(SendMessageResponse response);

    /**
     * 发送失败回调
     *
     * @param e 异常信息
     */
    void onFailure(Throwable e);
}