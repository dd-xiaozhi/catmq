package com.aoaojiao.catmq.store.core;

import com.aoaojiao.catmq.common.cache.CommonCache;
import com.aoaojiao.catmq.store.config.MessageStoreConfig;
import com.aoaojiao.catmq.store.model.DispatchRequest;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 消息分发服务
 * 将 CommitLog 写入的消息分发到各个 ConsumerQueue
 *
 * @author DD
 */
public class DispatchMessageService {

    private static final Logger log = LoggerFactory.getLogger(DispatchMessageService.class);

    private final MessageStoreConfig config;
    private final ConsumerQueueManager consumerQueueManager;

    /**
     * 分发队列
     */
    private final LinkedBlockingQueue<DispatchRequest> dispatchQueue;

    /**
     * 分发线程
     */
    private final Thread dispatchThread;

    /**
     * 是否运行中
     */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 批量大小
     */
    private static final int BATCH_SIZE = 100;

    /**
     * 队列容量
     */
    private static final int QUEUE_CAPACITY = 100000;

    public DispatchMessageService(MessageStoreConfig config, ConsumerQueueManager consumerQueueManager) {
        this.config = config;
        this.consumerQueueManager = consumerQueueManager;
        this.dispatchQueue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
        this.dispatchThread = new Thread(this::dispatchLoop, "DispatchMessageService-Thread");
    }

    /**
     * 启动分发服务
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            dispatchThread.start();
            log.info("DispatchMessageService started");
        }
    }

    /**
     * 添加分发请求（异步）
     *
     * @param request 分发请求
     * @return 是否添加成功
     */
    public boolean putDispatchRequest(DispatchRequest request) {
        if (!running.get()) {
            log.warn("DispatchMessageService is not running, dispatch sync");
            dispatchSync(request);
            return true;
        }

        if (!dispatchQueue.offer(request)) {
            // 队列满，降级为同步分发
            log.warn("Dispatch queue is full, dispatch sync");
            dispatchSync(request);
            return false;
        }
        return true;
    }

    /**
     * 分发循环
     */
    private void dispatchLoop() {
        List<DispatchRequest> batch = new ArrayList<>(BATCH_SIZE);

        while (running.get()) {
            try {
                // 批量获取
                dispatchQueue.drainTo(batch, BATCH_SIZE);

                if (batch.isEmpty()) {
                    // 没有数据，阻塞等待
                    DispatchRequest request = dispatchQueue.take();
                    batch.add(request);
                }

                // 批量写入 ConsumerQueue
                for (DispatchRequest request : batch) {
                    try {
                        doDispatch(request);
                    } catch (Exception e) {
                        log.error("Dispatch error: {}", request, e);
                    }
                }
                batch.clear();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("DispatchMessageService interrupted");
                break;
            } catch (Exception e) {
                log.error("Dispatch loop error", e);
            }
        }

        // 处理剩余数据
        drainRemaining();
    }

    /**
     * 处理剩余数据
     */
    private void drainRemaining() {
        List<DispatchRequest> remaining = new ArrayList<>();
        dispatchQueue.drainTo(remaining);

        for (DispatchRequest request : remaining) {
            try {
                doDispatch(request);
            } catch (Exception e) {
                log.error("Dispatch remaining error: {}", request, e);
            }
        }
    }

    /**
     * 执行分发
     *
     * @param request 分发请求
     */
    private void doDispatch(DispatchRequest request) {
        String topic = request.getTopic();
        int queueId = request.getQueueId();

        // 获取 ConsumerQueue
        ConsumerQueue cq = consumerQueueManager.getOrCreate(topic, queueId);

        // 写入索引
        cq.writeIndex(
                request.getPhysicalOffset(),
                request.getSize(),
                request.getTagCode()
        );

        // 更新 CommonCache 中的 maxOffset
        CommonCache.updateQueueMaxOffset(topic, queueId, cq.getMaxLogicOffset());

        if (log.isDebugEnabled()) {
            log.debug("Dispatch success: topic={}, queueId={}, offset={}, size={}",
                    topic, queueId, request.getPhysicalOffset(), request.getSize());
        }
    }

    /**
     * 同步分发（降级方案）
     *
     * @param request 分发请求
     */
    private void dispatchSync(DispatchRequest request) {
        doDispatch(request);
    }

    /**
     * 停止分发服务
     */
    public void shutdown() {
        if (running.compareAndSet(true, false)) {
            // 中断分发线程
            dispatchThread.interrupt();

            try {
                dispatchThread.join(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            log.info("DispatchMessageService shutdown");
        }
    }

    /**
     * 是否运行中
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * 获取队列大小
     */
    public int getQueueSize() {
        return dispatchQueue.size();
    }
}
