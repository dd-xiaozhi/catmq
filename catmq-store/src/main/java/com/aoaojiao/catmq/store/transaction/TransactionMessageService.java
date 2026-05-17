package com.aoaojiao.catmq.store.transaction;

import com.aoaojiao.catmq.store.core.DispatchMessageService;
import com.aoaojiao.catmq.store.model.DispatchRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 事务消息服务
 * 负责管理事务消息的完整生命周期
 * 实现两阶段提交协议：Half 消息预处理 -> 本地事务执行 -> 提交/回滚
 * 支持事务状态回查机制
 *
 * @author DD
 */
public class TransactionMessageService {

    private static final Logger log = LoggerFactory.getLogger(TransactionMessageService.class);

    /**
     * 服务运行状态
     */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 事务消息索引（transactionId -> TransactionMessage）
     */
    private final ConcurrentHashMap<String, TransactionMessage> transactionIndex = new ConcurrentHashMap<>();

    /**
     * 按状态分组的事务消息
     */
    private final ConcurrentHashMap<TransactionMessage.TransactionState, Set<String>> stateIndex =
            new ConcurrentHashMap<>();

    /**
     * 待回查的事务消息（按过期时间排序）
     */
    private final ConcurrentHashMap<String, TransactionMessage> pendingCheckIndex = new ConcurrentHashMap<>();

    /**
     * 事务消息存储目录
     */
    private String transactionDir;

    /**
     * 回查服务
     */
    private TransactionCheckService checkService;

    /**
     * 调度线程池
     */
    private ScheduledExecutorService scheduler;

    /**
     * 分发服务（用于提交时分发到 ConsumerQueue）
     */
    private DispatchMessageService dispatchMessageService;

    /**
     * 回查间隔（毫秒）
     */
    private long checkIntervalMs = 3000;

    /**
     * 默认事务超时时间（毫秒）
     */
    private long defaultTimeoutMs = 60000;

    /**
     * 日期格式化器
     */
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");

    /**
     * 初始化事务消息服务
     */
    public void init(String storePathRootDir) {
        this.transactionDir = storePathRootDir + File.separator + "transaction";

        File dir = new File(transactionDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // 初始化调度线程
        this.scheduler = Executors.newScheduledThreadPool(2,
                r -> new Thread(r, "TransactionMessageService-Scheduler"));

        // 初始化状态索引
        for (TransactionMessage.TransactionState state : TransactionMessage.TransactionState.values()) {
            stateIndex.put(state, Collections.newSetFromMap(new ConcurrentHashMap<>()));
        }

        // 加载已存在的事务消息
        loadTransactionMessages();

        // 初始化回查服务
        this.checkService = new TransactionCheckService(this);

        log.info("TransactionMessageService initialized: transactionDir={}", transactionDir);
    }

    /**
     * 设置分发服务
     */
    public void setDispatchMessageService(DispatchMessageService dispatchMessageService) {
        this.dispatchMessageService = dispatchMessageService;
    }

    /**
     * 启动服务
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            // 启动事务状态扫描任务
            scheduler.scheduleAtFixedRate(this::scanTransactionMessages,
                    checkIntervalMs, checkIntervalMs, TimeUnit.MILLISECONDS);

            // 启动回查服务
            if (checkService != null) {
                checkService.start();
            }

            log.info("TransactionMessageService started");
        }
    }

    /**
     * 停止服务
     */
    public void shutdown() {
        if (running.compareAndSet(true, false)) {
            // 停止回查服务
            if (checkService != null) {
                checkService.shutdown();
            }

            // 停止调度
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }

            // 持久化所有事务消息
            persistAllTransactionMessages();

            log.info("TransactionMessageService shutdown");
        }
    }

    /**
     * 发送 Half 消息（预处理消息）
     * 消息发送后不投递给消费者，等待本地事务执行结果
     *
     * @param transactionId 事务 ID
     * @param topic          主题
     * @param queueId        队列 ID
     * @param physicalOffset CommitLog 物理偏移量
     * @param size           消息大小
     * @param tagCode        标签哈希
     * @param messageBody    消息内容
     * @param properties     扩展属性
     * @return 事务消息
     */
    public TransactionMessage sendHalfMessage(String transactionId, String topic, int queueId,
                                               long physicalOffset, int size, long tagCode,
                                               String messageBody, Map<String, String> properties) {
        if (!running.get()) {
            log.warn("TransactionMessageService is not running");
            return null;
        }

        // 创建事务消息（预处理状态）
        TransactionMessage txMessage = TransactionMessage.createPrepared(
                transactionId, topic, queueId, physicalOffset, size, tagCode,
                messageBody, properties);

        // 添加到索引
        transactionIndex.put(transactionId, txMessage);
        addToStateIndex(txMessage);

        // 持久化
        persistTransactionMessage(txMessage);

        log.info("Half message sent: transactionId={}, topic={}, queueId={}, physicalOffset={}",
                transactionId, topic, queueId, physicalOffset);

        return txMessage;
    }

    /**
     * 提交事务消息
     * 本地事务执行成功后调用，将消息投递给消费者
     *
     * @param transactionId 事务 ID
     * @return 是否提交成功
     */
    public boolean commitTransaction(String transactionId) {
        TransactionMessage txMessage = transactionIndex.get(transactionId);
        if (txMessage == null) {
            log.warn("Transaction message not found: {}", transactionId);
            return false;
        }

        // 检查状态
        if (txMessage.getTransactionState() != TransactionMessage.TransactionState.PREPARED
                && txMessage.getTransactionState() != TransactionMessage.TransactionState.UNKNOWN) {
            log.warn("Transaction state is not PREPARED or UNKNOWN: transactionId={}, state={}",
                    transactionId, txMessage.getTransactionState());
            return false;
        }

        // 提交
        txMessage.commit();
        txMessage.markEnd();

        // 从待回查索引中移除
        pendingCheckIndex.remove(transactionId);

        // 更新状态索引
        updateStateIndex(txMessage);

        // 分发到 ConsumerQueue
        dispatchToConsumerQueue(txMessage);

        // 持久化
        persistTransactionMessage(txMessage);

        log.info("Transaction committed: transactionId={}, topic={}, queueId={}",
                transactionId, txMessage.getTopic(), txMessage.getQueueId());

        return true;
    }

    /**
     * 回滚事务消息
     * 本地事务执行失败后调用，删除消息
     *
     * @param transactionId 事务 ID
     * @return 是否回滚成功
     */
    public boolean rollbackTransaction(String transactionId) {
        TransactionMessage txMessage = transactionIndex.get(transactionId);
        if (txMessage == null) {
            log.warn("Transaction message not found: {}", transactionId);
            return false;
        }

        // 检查状态
        if (txMessage.getTransactionState() == TransactionMessage.TransactionState.COMMIT
                || txMessage.getTransactionState() == TransactionMessage.TransactionState.ROLLBACK
                || txMessage.getTransactionState() == TransactionMessage.TransactionState.END) {
            log.warn("Transaction already finished: transactionId={}, state={}",
                    transactionId, txMessage.getTransactionState());
            return false;
        }

        // 回滚
        txMessage.rollback();
        txMessage.markEnd();

        // 从待回查索引中移除
        pendingCheckIndex.remove(transactionId);

        // 更新状态索引
        updateStateIndex(txMessage);

        // 持久化
        persistTransactionMessage(txMessage);

        log.info("Transaction rolled back: transactionId={}, topic={}, queueId={}",
                transactionId, txMessage.getTopic(), txMessage.getQueueId());

        return true;
    }

    /**
     * 检查事务状态
     *
     * @param transactionId 事务 ID
     * @return 事务消息
     */
    public TransactionMessage getTransactionMessage(String transactionId) {
        return transactionIndex.get(transactionId);
    }

    /**
     * 获取事务状态
     *
     * @param transactionId 事务 ID
     * @return 事务状态
     */
    public TransactionMessage.TransactionState getTransactionState(String transactionId) {
        TransactionMessage txMessage = transactionIndex.get(transactionId);
        return txMessage != null ? txMessage.getTransactionState() : null;
    }

    /**
     * 分发到 ConsumerQueue
     */
    private void dispatchToConsumerQueue(TransactionMessage txMessage) {
        if (dispatchMessageService != null) {
            DispatchRequest request = DispatchRequest.builder()
                    .topic(txMessage.getTopic())
                    .queueId(txMessage.getQueueId())
                    .physicalOffset(txMessage.getPhysicalOffset())
                    .size(txMessage.getSize())
                    .tagCode(txMessage.getTagCode())
                    .timestamp(System.currentTimeMillis())
                    .build();

            dispatchMessageService.putDispatchRequest(request);

            log.info("Transaction message dispatched: transactionId={}, topic={}, queueId={}, offset={}",
                    txMessage.getTransactionId(), txMessage.getTopic(), txMessage.getQueueId(),
                    txMessage.getPhysicalOffset());
        } else {
            log.warn("DispatchMessageService not available");
        }
    }

    /**
     * 扫描事务消息
     * 检查超时的预处理消息，加入回查队列
     */
    private void scanTransactionMessages() {
        long now = System.currentTimeMillis();

        // 遍历预处理状态的消息
        Set<String> preparedSet = stateIndex.get(TransactionMessage.TransactionState.PREPARED);
        if (preparedSet == null || preparedSet.isEmpty()) {
            return;
        }

        for (String transactionId : new ArrayList<>(preparedSet)) {
            TransactionMessage txMessage = transactionIndex.get(transactionId);
            if (txMessage == null) {
                continue;
            }

            // 检查是否超时
            if (txMessage.isTimeout()) {
                // 加入待回查队列
                pendingCheckIndex.put(transactionId, txMessage);
                log.info("Transaction timeout, added to check queue: transactionId={}", transactionId);
            }
        }
    }

    /**
     * 添加到状态索引
     */
    private void addToStateIndex(TransactionMessage txMessage) {
        Set<String> set = stateIndex.get(txMessage.getTransactionState());
        if (set != null) {
            set.add(txMessage.getTransactionId());
        }
    }

    /**
     * 更新状态索引
     */
    private void updateStateIndex(TransactionMessage txMessage) {
        // 从所有状态集中移除
        for (Set<String> set : stateIndex.values()) {
            set.remove(txMessage.getTransactionId());
        }
        // 添加到当前状态
        addToStateIndex(txMessage);
    }

    /**
     * 执行回查
     * 由 TransactionCheckService 调用
     */
    public void checkTransaction(String transactionId) {
        TransactionMessage txMessage = transactionIndex.get(transactionId);
        if (txMessage == null) {
            log.warn("Transaction message not found for check: {}", transactionId);
            return;
        }

        // 增加回查次数
        txMessage.incrementCheckCount();

        // 检查是否超过最大回查次数
        if (txMessage.isExceedMaxCheck()) {
            // 超时，默认回滚
            log.warn("Transaction check exceed max count, rollback: transactionId={}, checkCount={}",
                    transactionId, txMessage.getCheckCount());
            rollbackTransaction(transactionId);
            return;
        }

        // 持久化回查次数
        persistTransactionMessage(txMessage);

        log.info("Transaction check: transactionId={}, checkCount={}, state={}",
                transactionId, txMessage.getCheckCount(), txMessage.getTransactionState());
    }

    /**
     * 接收回查结果
     *
     * @param transactionId 事务 ID
     * @param commit        是否提交
     */
    public void receiveCheckResult(String transactionId, boolean commit) {
        TransactionMessage txMessage = transactionIndex.get(transactionId);
        if (txMessage == null) {
            log.warn("Transaction message not found for result: {}", transactionId);
            return;
        }

        if (commit) {
            commitTransaction(transactionId);
        } else {
            rollbackTransaction(transactionId);
        }

        // 从待回查索引中移除
        pendingCheckIndex.remove(transactionId);
    }

    /**
     * 获取待回查事务数量
     */
    public int getPendingCheckCount() {
        return pendingCheckIndex.size();
    }

    /**
     * 获取指定状态的事务数量
     */
    public int getTransactionCountByState(TransactionMessage.TransactionState state) {
        Set<String> set = stateIndex.get(state);
        return set != null ? set.size() : 0;
    }

    /**
     * 持久化事务消息到磁盘
     */
    private void persistTransactionMessage(TransactionMessage txMessage) {
        try {
            // 按日期分目录存储
            String dateStr = dateFormat.format(new Date(txMessage.getCreateTime()));
            String dirPath = transactionDir + File.separator + dateStr;
            File dir = new File(dirPath);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String filePath = dirPath + File.separator + "transaction_" + txMessage.getTopic() + ".json";

            try (PrintWriter writer = new PrintWriter(new FileWriter(filePath, true))) {
                writer.println(formatToJson(txMessage));
            }
        } catch (IOException e) {
            log.error("Persist transaction message error: transactionId={}", txMessage.getTransactionId(), e);
        }
    }

    /**
     * 持久化所有事务消息
     */
    private void persistAllTransactionMessages() {
        for (TransactionMessage txMessage : transactionIndex.values()) {
            persistTransactionMessage(txMessage);
        }
        log.info("All transaction messages persisted");
    }

    /**
     * 加载已存在的事务消息
     */
    private void loadTransactionMessages() {
        File dir = new File(transactionDir);
        if (!dir.exists()) {
            return;
        }

        // 递归加载所有日期目录
        loadTransactionMessagesFromDir(dir);
    }

    /**
     * 从目录加载事务消息
     */
    private void loadTransactionMessagesFromDir(File dir) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                loadTransactionMessagesFromDir(file);
            } else if (file.getName().endsWith(".json")) {
                try {
                    List<String> lines = Files.readAllLines(file.toPath());
                    for (String line : lines) {
                        if (line.trim().isEmpty()) {
                            continue;
                        }
                        TransactionMessage txMessage = parseFromJson(line);
                        if (txMessage != null) {
                            // 只加载未结束的事务消息
                            if (txMessage.getTransactionState() != TransactionMessage.TransactionState.END) {
                                transactionIndex.put(txMessage.getTransactionId(), txMessage);
                                addToStateIndex(txMessage);

                                // 如果是预处理状态且超时，加入待回查队列
                                if (txMessage.needCheck() && txMessage.isTimeout()) {
                                    pendingCheckIndex.put(txMessage.getTransactionId(), txMessage);
                                }
                            }
                        }
                    }
                    log.info("Loaded transaction messages from file: {}", file.getName());
                } catch (Exception e) {
                    log.error("Load transaction messages error: file={}", file.getName(), e);
                }
            }
        }
    }

    /**
     * 格式化消息为 JSON
     */
    private String formatToJson(TransactionMessage txMessage) {
        return com.alibaba.fastjson2.JSON.toJSONString(txMessage);
    }

    /**
     * 从 JSON 解析消息
     */
    private TransactionMessage parseFromJson(String json) {
        try {
            return com.alibaba.fastjson2.JSON.parseObject(json, TransactionMessage.class);
        } catch (Exception e) {
            log.error("Parse transaction message error: {}", json, e);
            return null;
        }
    }

    /**
     * 获取服务状态
     */
    public String getStatus() {
        return String.format("TransactionMessageService{ running=%s, totalTransactions=%d, prepared=%d, pendingCheck=%d }",
                running.get(), transactionIndex.size(),
                getTransactionCountByState(TransactionMessage.TransactionState.PREPARED),
                getPendingCheckCount());
    }

    /**
     * 事务回查服务
     * 负责定期回查未决的事务消息
     */
    public static class TransactionCheckService {

        private static final Logger log = LoggerFactory.getLogger(TransactionCheckService.class);

        private final TransactionMessageService parent;
        private final ScheduledExecutorService scheduler;
        private final AtomicBoolean running = new AtomicBoolean(false);

        private long checkIntervalMs = 3000;
        private int maxBatchCheck = 100;

        public TransactionCheckService(TransactionMessageService parent) {
            this.parent = parent;
            this.scheduler = Executors.newScheduledThreadPool(1,
                    r -> new Thread(r, "TransactionCheckService-Scheduler"));
        }

        public void start() {
            if (running.compareAndSet(false, true)) {
                scheduler.scheduleAtFixedRate(this::doCheck,
                        checkIntervalMs, checkIntervalMs, TimeUnit.MILLISECONDS);
                log.info("TransactionCheckService started");
            }
        }

        public void shutdown() {
            if (running.compareAndSet(true, false)) {
                scheduler.shutdown();
                try {
                    if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                        scheduler.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    scheduler.shutdownNow();
                    Thread.currentThread().interrupt();
                }
                log.info("TransactionCheckService shutdown");
            }
        }

        /**
         * 执行回查
         */
        private void doCheck() {
            if (!running.get()) {
                return;
            }

            // 获取待回查的事务（最多批量处理）
            List<String> toCheck = new ArrayList<>();
            for (String transactionId : parent.pendingCheckIndex.keySet()) {
                if (toCheck.size() >= maxBatchCheck) {
                    break;
                }
                toCheck.add(transactionId);
            }

            for (String transactionId : toCheck) {
                try {
                    parent.checkTransaction(transactionId);
                } catch (Exception e) {
                    log.error("Check transaction error: transactionId={}", transactionId, e);
                }
            }
        }

        public void setCheckIntervalMs(long intervalMs) {
            this.checkIntervalMs = intervalMs;
        }
    }
}