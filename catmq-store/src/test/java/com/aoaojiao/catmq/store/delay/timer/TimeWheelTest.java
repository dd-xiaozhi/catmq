package com.aoaojiao.catmq.store.delay.timer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * 时间轮测试
 *
 * @author DD
 */
public class TimeWheelTest {

    private TimeWheel timeWheel;

    @Before
    public void setUp() {
        timeWheel = new TimeWheel(100L, 10, (key, data) -> {
            // 默认空回调
        }, "TEST");
        timeWheel.start();
    }

    @After
    public void tearDown() {
        timeWheel.stop();
    }

    @Test
    public void testAddTask() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger counter = new AtomicInteger(0);

        String taskKey = "test_task_1";
        TimeWheel testWheel = new TimeWheel(100L, 10, (key, data) -> {
            counter.incrementAndGet();
            latch.countDown();
        }, "TEST");
        testWheel.start();

        try {
            testWheel.addTask(taskKey, 200L, null);

            boolean completed = latch.await(1, TimeUnit.SECONDS);
            assertTrue("任务应该被触发", completed);
            assertEquals("应该触发1次", 1, counter.get());
        } finally {
            testWheel.stop();
        }
    }

    @Test
    public void testRemoveTask() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger counter = new AtomicInteger(0);

        String taskKey = "test_task_remove";
        TimeWheel testWheel = new TimeWheel(100L, 10, (key, data) -> {
            counter.incrementAndGet();
            latch.countDown();
        }, "TEST");
        testWheel.start();

        try {
            testWheel.addTask(taskKey, 500L, null);

            // 立即移除
            boolean removed = testWheel.removeTask(taskKey);
            assertTrue("应该移除成功", removed);

            // 等待看是否会被触发
            boolean completed = latch.await(1, TimeUnit.SECONDS);
            assertFalse("任务已移除，不应该被触发", completed);
            assertEquals("不应该触发", 0, counter.get());
        } finally {
            testWheel.stop();
        }
    }

    @Test
    public void testMultipleTasks() throws InterruptedException {
        int taskCount = 5;
        CountDownLatch latch = new CountDownLatch(taskCount);
        List<String> executedKeys = new ArrayList<>();

        TimeWheel testWheel = new TimeWheel(100L, 10, (key, data) -> {
            synchronized (executedKeys) {
                executedKeys.add(key);
            }
            latch.countDown();
        }, "TEST");
        testWheel.start();

        try {
            for (int i = 0; i < taskCount; i++) {
                String key = "multi_task_" + i;
                testWheel.addTask(key, (i + 1) * 100L, null);
            }

            boolean completed = latch.await(2, TimeUnit.SECONDS);
            assertTrue("所有任务应该被触发", completed);
            assertEquals("所有任务都应该执行", taskCount, executedKeys.size());
        } finally {
            testWheel.stop();
        }
    }

    @Test
    public void testTaskTriggerOrder() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(3);
        List<String> order = new ArrayList<>();

        TimeWheel testWheel = new TimeWheel(100L, 10, (key, data) -> {
            synchronized (order) {
                order.add(key);
            }
            latch.countDown();
        }, "TEST");
        testWheel.start();

        try {
            // 按倒序添加，但应该按延迟时间顺序触发
            testWheel.addTask("task_C", 300L, null);
            testWheel.addTask("task_A", 100L, null);
            testWheel.addTask("task_B", 200L, null);

            latch.await(2, TimeUnit.SECONDS);

            assertEquals("顺序应该是 A -> B -> C", Arrays.asList("task_A", "task_B", "task_C"), order);
        } finally {
            testWheel.stop();
        }
    }

    @Test
    public void testDuplicateTask() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger counter = new AtomicInteger(0);

        String key = "duplicate_key";
        TimeWheel testWheel = new TimeWheel(100L, 10, (k, d) -> {
            counter.incrementAndGet();
            latch.countDown();
        }, "TEST");
        testWheel.start();

        try {
            testWheel.addTask(key, 100L, null);
            // 添加相同key的任务，会替换第一个
            boolean added = testWheel.addTask(key, 200L, null);
            // 实际实现可能返回true或false，取决于是否允许覆盖

            boolean completed = latch.await(1, TimeUnit.SECONDS);
            assertTrue("任务应该触发", completed);
            assertEquals("只应该触发1次", 1, counter.get());
        } finally {
            testWheel.stop();
        }
    }

    @Test
    public void testZeroDelay() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger counter = new AtomicInteger(0);

        TimeWheel testWheel = new TimeWheel(100L, 10, (k, d) -> {
            counter.incrementAndGet();
            latch.countDown();
        }, "TEST");
        testWheel.start();

        try {
            testWheel.addTask("zero_delay", 0L, null);

            boolean completed = latch.await(500, TimeUnit.MILLISECONDS);
            assertTrue("0延迟任务应该立即触发", completed);
            assertEquals("应该触发1次", 1, counter.get());
        } finally {
            testWheel.stop();
        }
    }

    @Test
    public void testTaskWithData() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        final String[] receivedData = new String[1];

        String testData = "test_data_content";
        TimeWheel testWheel = new TimeWheel(100L, 10, (k, d) -> {
            receivedData[0] = (String) d;
            latch.countDown();
        }, "TEST");
        testWheel.start();

        try {
            testWheel.addTask("data_task", 100L, testData);

            boolean completed = latch.await(1, TimeUnit.SECONDS);
            assertTrue("应该触发", completed);
            assertEquals("数据应该匹配", testData, receivedData[0]);
        } finally {
            testWheel.stop();
        }
    }

    @Test
    public void testGetTaskCacheSize() {
        assertEquals("初始应该为0", 0, timeWheel.getTaskCacheSize());

        timeWheel.addTask("task1", 1000L, null);
        assertEquals("添加1个后应该为1", 1, timeWheel.getTaskCacheSize());

        timeWheel.addTask("task2", 2000L, null);
        assertEquals("添加2个后应该为2", 2, timeWheel.getTaskCacheSize());
    }

    @Test
    public void testStop() {
        TimeWheel testWheel = new TimeWheel(100L, 10, (k, d) -> {}, "TEST");
        testWheel.start();

        testWheel.addTask("stop_test", 500L, null);
        assertEquals("停止前应该有1个任务", 1, testWheel.getTaskCacheSize());

        testWheel.stop();

        // 停止后添加任务应该失败或不影响
        try {
            testWheel.addTask("after_stop", 100L, null);
        } catch (Exception e) {
            // 预期行为，可能抛出异常或静默失败
        }
    }
}