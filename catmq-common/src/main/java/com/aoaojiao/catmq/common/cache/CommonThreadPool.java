package com.aoaojiao.catmq.common.cache;

import lombok.AllArgsConstructor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 通用线程池配置
 *
 * @author DD
 */
public class CommonThreadPool {

    /**
     * 负责刷新 catmq-topic.json 文件刷盘工作
     */
    public static final ExecutorService refreshCatmqTopicFileExecutor = Executors.newFixedThreadPool(1,
            r -> new Worker("refresh-catmq-topic-file-thread", r));
    
    /**
     * 负责刷新 consume-queue-offset.json 文件刷盘工作
     */
    public static final ExecutorService refreshConsumeQueueOffsetFileExecutor = Executors.newFixedThreadPool(1,
            r -> new Worker("refresh-consume-queue-offset-file-thread", r));
    
    @AllArgsConstructor
    public static class Worker extends Thread {
        
        public String threadName;
        public Runnable runnable;

        @Override
        public void run() {
            runnable.run();
        }
    }
}
