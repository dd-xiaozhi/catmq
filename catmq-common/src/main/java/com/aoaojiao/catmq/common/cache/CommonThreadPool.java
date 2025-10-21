package com.aoaojiao.catmq.common.cache;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 通用线程池配置
 *
 * @author DD
 */
public class CommonThreadPool {

    /**
     * 负责刷新 catmq 的 topicInfo 文件刷盘工作
     */
    public static final ExecutorService refreshCatmqTopicInfoExecutor = Executors.newFixedThreadPool(1,
            r -> {
                Thread thread = new Thread(r);
                thread.setName("refresh-catmq-topic-info-thread");
                return thread;
            });
}
