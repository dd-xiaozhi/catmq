package com.aoaojiao.catmq.store.delay.timer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 时间轮调度器
 * 用于实现高效的时间触发延迟任务调度
 * 采用分层时间轮设计，支持秒级和毫秒级精度
 *
 * @author DD
 */
public class TimeWheel {

    private static final Logger log = LoggerFactory.getLogger(TimeWheel.class);

    /**
     * 时间轮槽位数（每个层级）
     */
    private final int wheelSize;

    /**
     * 时间刻度间隔（毫秒）
     */
    private final long tickMs;

    /**
     * 当前时间刻度
     */
    private final AtomicInteger currentTick;

    /**
     * 时间轮槽位
     */
    private final Bucket[] buckets;

    /**
     * 过期任务映射，用于去重（避免同一任务被多次调度）
     * Key: taskKey, Value: 过期时间
     */
    private final ConcurrentHashMap<String, Long> taskCache;

    /**
     * 上层时间轮
     */
    private final TimeWheel overflowWheel;

    /**
     * 时间轮是否运行
     */
    private final AtomicBoolean running;

    /**
     * 时钟线程
     */
    private final Thread clockThread;

    /**
     * 延迟回调接口
     */
    private final DelayTaskHandler taskHandler;

    /**
     * 时间轮层级名称（用于日志）
     */
    private final String levelName;

    /**
     * 创建单层时间轮
     *
     * @param tickMs      时间刻度间隔（毫秒）
     * @param wheelSize   槽位数量
     * @param taskHandler 任务处理器
     * @param levelName   层级名称
     */
    public TimeWheel(long tickMs, int wheelSize, DelayTaskHandler taskHandler, String levelName) {
        this(tickMs, wheelSize, 0, taskHandler, levelName, null);
    }

    /**
     * 创建时间轮（递归构造多层时间轮）
     *
     * @param tickMs        时间刻度间隔（毫秒）
     * @param wheelSize     槽位数量
     * @param currentTick   起始时间刻度
     * @param taskHandler   任务处理器
     * @param levelName     层级名称
     * @param overflowWheel 上层时间轮
     */
    private TimeWheel(long tickMs, int wheelSize, long currentTick,
                     DelayTaskHandler taskHandler, String levelName,
                     TimeWheel overflowWheel) {
        this.tickMs = tickMs;
        this.wheelSize = wheelSize;
        this.currentTick = new AtomicInteger((int) (currentTick % wheelSize));
        this.taskCache = new ConcurrentHashMap<>();
        this.running = new AtomicBoolean(false);
        this.taskHandler = taskHandler;
        this.levelName = levelName;
        this.overflowWheel = overflowWheel;

        // 初始化槽位
        this.buckets = new Bucket[wheelSize];
        for (int i = 0; i < wheelSize; i++) {
            this.buckets[i] = new Bucket(i);
        }

        this.clockThread = new Thread(this::clockLoop, "TimeWheel-" + levelName);
        this.clockThread.setDaemon(true);
    }

    /**
     * 启动时间轮
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            clockThread.start();
            log.info("TimeWheel started: level={}, tickMs={}, wheelSize={}", levelName, tickMs, wheelSize);
        }
    }

    /**
     * 停止时间轮
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            clockThread.interrupt();
            try {
                clockThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            log.info("TimeWheel stopped: level={}", levelName);
        }
    }

    /**
     * 添加延迟任务
     *
     * @param taskKey   任务唯一标识
     * @param delayMs   延迟时间（毫秒）
     * @param taskData  任务数据
     * @return 是否添加成功
     */
    public boolean addTask(String taskKey, long delayMs, Object taskData) {
        if (!running.get()) {
            log.warn("TimeWheel is not running, task ignored: {}", taskKey);
            return false;
        }

        // 计算过期时间戳
        long expireTime = System.currentTimeMillis() + delayMs;

        // 检查是否已经在缓存中（避免重复调度）
        Long existingExpire = taskCache.get(taskKey);
        if (existingExpire != null && existingExpire <= expireTime) {
            log.debug("Task already scheduled with earlier or equal expire time: {}", taskKey);
            return false;
        }

        // 更新缓存
        taskCache.put(taskKey, expireTime);

        // 计算应该放置的槽位
        long tick = expireTime / tickMs;
        int bucketIndex = (int) (tick % wheelSize);

        // 放入对应槽位
        Bucket bucket = buckets[bucketIndex];
        bucket.addTask(taskKey, expireTime, taskData);

        log.debug("Task added: key={}, delayMs={}, expireTime={}, bucket={}",
                taskKey, delayMs, expireTime, bucketIndex);

        return true;
    }

    /**
     * 移除任务
     *
     * @param taskKey 任务唯一标识
     * @return 是否成功移除
     */
    public boolean removeTask(String taskKey) {
        // 从缓存中移除
        Long expireTime = taskCache.remove(taskKey);
        if (expireTime == null) {
            return false;
        }

        // 计算槽位并从对应 bucket 中移除
        long tick = expireTime / tickMs;
        int bucketIndex = (int) (tick % wheelSize);
        return buckets[bucketIndex].removeTask(taskKey);
    }

    /**
     * 时钟主循环
     */
    private void clockLoop() {
        long lastCheckTime = System.currentTimeMillis();

        while (running.get()) {
            try {
                // 等待一个刻度
                Thread.sleep(tickMs);

                // 获取当前刻度
                int current = currentTick.get();
                Bucket bucket = buckets[current];

                // 处理过期任务
                processBucket(bucket);

                // 推进时间轮
                int nextTick = (current + 1) % wheelSize;
                currentTick.set(nextTick);

                // 如果到达最后一个槽位，推进上层时间轮
                if (nextTick == 0 && overflowWheel != null) {
                    overflowWheel.advanceClock();
                }

            } catch (InterruptedException e) {
                log.info("TimeWheel clock interrupted: level={}", levelName);
                break;
            } catch (Exception e) {
                log.error("TimeWheel clock error: level={}", levelName, e);
            }
        }
    }

    /**
     * 处理槽位中的过期任务
     */
    private void processBucket(Bucket bucket) {
        Map<String, Bucket.TaskEntry> tasks = bucket.getTasksAndClear();
        if (tasks == null || tasks.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();

        for (Map.Entry<String, Bucket.TaskEntry> entry : tasks.entrySet()) {
            String taskKey = entry.getKey();
            Bucket.TaskEntry taskEntry = entry.getValue();

            // 检查任务是否过期
            if (taskEntry.expireTime <= now) {
                // 再次检查缓存，确认任务仍然有效
                Long cachedExpire = taskCache.get(taskKey);
                if (cachedExpire != null && cachedExpire.equals(taskEntry.expireTime)) {
                    // 从缓存中移除
                    taskCache.remove(taskKey);

                    // 执行任务
                    try {
                        if (taskHandler != null) {
                            taskHandler.onTrigger(taskKey, taskEntry.data);
                        }
                    } catch (Exception e) {
                        log.error("Task execution error: key={}", taskKey, e);
                    }
                }
            } else {
                // 任务未过期，重新调度（可能时间轮刚推进）
                addTask(taskKey, taskEntry.expireTime - now, taskEntry.data);
            }
        }
    }

    /**
     * 推进时钟（当低层时间轮转满时调用）
     */
    private void advanceClock() {
        int next = (currentTick.get() + 1) % wheelSize;
        currentTick.set(next);
    }

    /**
     * 推进到指定时间
     *
     * @param expireTime 过期时间戳
     */
    public void advanceTo(long expireTime) {
        long tick = expireTime / tickMs;
        int targetTick = (int) (tick % wheelSize);
        currentTick.set(targetTick);
    }

    /**
     * 获取当前时间刻度
     */
    public int getCurrentTick() {
        return currentTick.get();
    }

    /**
     * 是否运行中
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * 获取任务缓存大小
     */
    public int getTaskCacheSize() {
        return taskCache.size();
    }

    /**
     * 延迟任务处理器接口
     */
    public interface DelayTaskHandler {
        /**
         * 任务触发时的回调
         *
         * @param taskKey 任务唯一标识
         * @param data    任务数据
         */
        void onTrigger(String taskKey, Object data);
    }

    /**
     * 时间轮槽位
     */
    public static class Bucket {
        private final int index;
        private final ConcurrentHashMap<String, TaskEntry> tasks;

        public Bucket(int index) {
            this.index = index;
            this.tasks = new ConcurrentHashMap<>();
        }

        /**
         * 添加任务
         */
        public void addTask(String taskKey, long expireTime, Object data) {
            tasks.put(taskKey, new TaskEntry(expireTime, data));
        }

        /**
         * 移除任务
         */
        public boolean removeTask(String taskKey) {
            return tasks.remove(taskKey) != null;
        }

        /**
         * 获取并清空任务
         */
        public Map<String, TaskEntry> getTasksAndClear() {
            if (tasks.isEmpty()) {
                return null;
            }
            Map<String, TaskEntry> result = new ConcurrentHashMap<>(tasks);
            tasks.clear();
            return result;
        }

        /**
         * 任务条目
         */
        public static class TaskEntry {
            public final long expireTime;
            public final Object data;

            public TaskEntry(long expireTime, Object data) {
                this.expireTime = expireTime;
                this.data = data;
            }
        }
    }

    @Override
    public String toString() {
        return "TimeWheel{" +
                "levelName='" + levelName + '\'' +
                ", tickMs=" + tickMs +
                ", wheelSize=" + wheelSize +
                ", currentTick=" + currentTick.get() +
                ", running=" + running.get() +
                ", taskCacheSize=" + taskCache.size() +
                '}';
    }
}