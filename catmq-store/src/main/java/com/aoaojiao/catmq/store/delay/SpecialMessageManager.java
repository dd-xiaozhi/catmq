package com.aoaojiao.catmq.store.delay;

import com.aoaojiao.catmq.store.config.MessageStoreConfig;
import com.aoaojiao.catmq.store.core.CommitLogAppendHandler;
import com.aoaojiao.catmq.store.core.ConsumerQueueManager;
import com.aoaojiao.catmq.store.core.DispatchMessageService;
import com.aoaojiao.catmq.store.delay.service.DeadLetterQueueService;
import com.aoaojiao.catmq.store.delay.service.DelayMessageService;
import com.aoaojiao.catmq.store.delay.service.RetryMessageService;
import com.aoaojiao.catmq.store.transaction.TransactionMessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 特殊消息服务管理器
 * 统一管理延迟消息、重试、死信队列和事务消息服务
 * 提供服务的生命周期管理和协调
 *
 * @author DD
 */
public class SpecialMessageManager {

    private static final Logger log = LoggerFactory.getLogger(SpecialMessageManager.class);

    /**
     * 死信主题前缀
     */
    private static final String DLQ_TOPIC_PREFIX = "%DLQ%.";

    /**
     * 延迟消息服务
     */
    private DelayMessageService delayMessageService;

    /**
     * 重试消息服务
     */
    private RetryMessageService retryMessageService;

    /**
     * 死信队列服务
     */
    private DeadLetterQueueService deadLetterQueueService;

    /**
     * 事务消息服务
     */
    private TransactionMessageService transactionMessageService;

    /**
     * 存储配置
     */
    private MessageStoreConfig config;

    /**
     * 分发服务
     */
    private DispatchMessageService dispatchMessageService;

    /**
     * 消费者队列管理器
     */
    private ConsumerQueueManager consumerQueueManager;

    /**
     * 是否已初始化
     */
    private volatile boolean initialized = false;

    /**
     * 初始化所有特殊消息服务
     *
     * @param config                 存储配置
     * @param dispatchMessageService  分发服务
     * @param consumerQueueManager   消费者队列管理器
     */
    public void init(MessageStoreConfig config,
                     DispatchMessageService dispatchMessageService,
                     ConsumerQueueManager consumerQueueManager) {
        if (initialized) {
            log.warn("SpecialMessageManager already initialized");
            return;
        }

        this.config = config;
        this.dispatchMessageService = dispatchMessageService;
        this.consumerQueueManager = consumerQueueManager;

        // 初始化死信队列服务（其他服务依赖它）
        this.deadLetterQueueService = new DeadLetterQueueService();
        this.deadLetterQueueService.init2(config.getStorePathRootDir(), DLQ_TOPIC_PREFIX + "default");
        this.deadLetterQueueService.start();

        // 初始化延迟消息服务
        this.delayMessageService = new DelayMessageService();
        this.delayMessageService.init(config, dispatchMessageService, consumerQueueManager);
        this.delayMessageService.start();

        // 初始化重试消息服务（依赖死信队列和延迟消息服务）
        this.retryMessageService = new RetryMessageService();
        this.retryMessageService.init(config.getStorePathRootDir());
        this.retryMessageService.setDlqService(deadLetterQueueService);
        this.retryMessageService.setDelayMessageService(delayMessageService);
        this.retryMessageService.start();

        // 初始化事务消息服务
        this.transactionMessageService = new TransactionMessageService();
        this.transactionMessageService.init(config.getStorePathRootDir());
        this.transactionMessageService.setDispatchMessageService(dispatchMessageService);
        this.transactionMessageService.start();

        initialized = true;

        log.info("SpecialMessageManager initialized successfully");
        log.info("  - DelayMessageService: enabled");
        log.info("  - RetryMessageService: enabled");
        log.info("  - DeadLetterQueueService: enabled");
        log.info("  - TransactionMessageService: enabled");
    }

    /**
     * 关闭所有特殊消息服务
     */
    public void shutdown() {
        if (!initialized) {
            return;
        }

        log.info("Shutting down SpecialMessageManager...");

        // 按依赖顺序关闭服务
        if (transactionMessageService != null) {
            transactionMessageService.shutdown();
        }

        if (retryMessageService != null) {
            retryMessageService.shutdown();
        }

        if (delayMessageService != null) {
            delayMessageService.shutdown();
        }

        if (deadLetterQueueService != null) {
            deadLetterQueueService.shutdown();
        }

        initialized = false;

        log.info("SpecialMessageManager shutdown completed");
    }

    /**
     * 获取延迟消息服务
     */
    public DelayMessageService getDelayMessageService() {
        return delayMessageService;
    }

    /**
     * 获取重试消息服务
     */
    public RetryMessageService getRetryMessageService() {
        return retryMessageService;
    }

    /**
     * 获取死信队列服务
     */
    public DeadLetterQueueService getDeadLetterQueueService() {
        return deadLetterQueueService;
    }

    /**
     * 获取事务消息服务
     */
    public TransactionMessageService getTransactionMessageService() {
        return transactionMessageService;
    }

    /**
     * 获取服务状态汇总
     */
    public String getStatusSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== SpecialMessageManager Status ===\n");
        sb.append("initialized: ").append(initialized).append("\n");

        if (delayMessageService != null) {
            sb.append("DelayMessageService: ").append(delayMessageService.getStatus()).append("\n");
        }

        if (retryMessageService != null) {
            sb.append("RetryMessageService: ").append(retryMessageService.getStatus()).append("\n");
        }

        if (deadLetterQueueService != null) {
            sb.append("DeadLetterQueueService: ").append(deadLetterQueueService.getStatus()).append("\n");
        }

        if (transactionMessageService != null) {
            sb.append("TransactionMessageService: ").append(transactionMessageService.getStatus()).append("\n");
        }

        return sb.toString();
    }

    /**
     * 检查是否已初始化
     */
    public boolean isInitialized() {
        return initialized;
    }
}