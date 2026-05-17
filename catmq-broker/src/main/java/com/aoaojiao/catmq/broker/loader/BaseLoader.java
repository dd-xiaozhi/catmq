package com.aoaojiao.catmq.broker.loader;

import com.aoaojiao.catmq.store.config.MessageStoreConfig;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.concurrent.locks.LockSupport;

/**
 * 文件加载器抽象基类
 * 提供通用的文件加载和定时刷新逻辑，子类只需实现抽象方法即可
 *
 * @param <T> 数据类型
 * @author DD
 */
@Slf4j
public abstract class BaseLoader<T> {

    protected final MessageStoreConfig messageStoreConfig;

    protected BaseLoader(MessageStoreConfig messageStoreConfig) {
        this.messageStoreConfig = messageStoreConfig;
    }

    /**
     * 从文件加载数据（读取并解析）
     *
     * @return 解析后的数据对象
     */
    protected abstract T loadData();

    /**
     * 获取数据用于刷盘（从缓存获取）
     *
     * @return 要刷盘的数据
     */
    protected abstract T getData();

    /**
     * 保存数据到缓存
     *
     * @param data 要缓存的数据
     */
    protected abstract void setData(T data);

    /**
     * 获取文件路径
     *
     * @return 文件绝对路径
     */
    protected abstract String getFilePath();

    /**
     * 获取刷新间隔时间（毫秒）
     *
     * @return 刷新间隔
     */
    protected abstract long getFlushIntervalMs();

    /**
     * 加载文件数据
     */
    public void load() {
        T data = loadData();
        setData(data);
        log.info("{} load data from: {}", getClass().getSimpleName(), getFilePath());
    }

    /**
     * 启动定时刷盘线程
     */
    public void startFlushThread() {
        new Thread(() -> {
            while (true) {
                // 刚从启动先让它 park
                LockSupport.parkNanos(getFlushIntervalMs() * 1_000_000L);
                log.info("refresh data, class: {}", getClass().getSimpleName());
                try {
                    T data = getData();
                    String jsonStr = com.alibaba.fastjson2.JSON.toJSONString(data,
                            com.alibaba.fastjson2.JSONWriter.Feature.PrettyFormat);
                    com.aoaojiao.catmq.broker.utils.FileContentUtil.writeStringToFile(getFilePath(), jsonStr);
                } catch (IOException e) {
                    throw new RuntimeException("refresh " + getFilePath() + " error", e);
                }
            }
        }, "refresh-" + getClass().getSimpleName() + "-thread").start();
    }
}