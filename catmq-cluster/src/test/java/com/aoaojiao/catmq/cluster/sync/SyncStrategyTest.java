package com.aoaojiao.catmq.cluster.sync;

import com.aoaojiao.catmq.cluster.model.BrokerInfo;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * 同步策略测试
 *
 * @author DD
 */
public class SyncStrategyTest {

    private List<BrokerInfo> slaves;
    private byte[] message;

    @Before
    public void setUp() {
        slaves = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            BrokerInfo slave = BrokerInfo.builder()
                    .brokerName("slave_" + i)
                    .address("127.0.0." + i + ":9876")
                    .build();
            slaves.add(slave);
        }
        message = "test message content".getBytes();
    }

    @Test
    public void testSyncStrategyAllAck() {
        SyncStrategy sync = new SyncWriteStrategy();

        // 模拟所有从节点成功
        AtomicInteger successCount = new AtomicInteger(0);
        sync.setSlaveReplicator((broker, msg) -> {
            successCount.incrementAndGet();
            return true;
        });

        SyncResult result = sync.sync(slaves, message);

        assertTrue("应该同步成功", result.isSuccess());
        assertEquals("应该成功3次", 3, result.getSuccessCount());
        assertEquals("应该失败0次", 0, result.getFailedCount());
    }

    @Test
    public void testSyncStrategyPartialFail() {
        SyncStrategy sync = new SyncWriteStrategy();

        AtomicInteger index = new AtomicInteger(0);
        sync.setSlaveReplicator((broker, msg) -> {
            // 前2个成功，第3个失败
            return index.incrementAndGet() <= 2;
        });

        SyncResult result = sync.sync(slaves, message);

        assertFalse("应该同步失败", result.isSuccess());
        assertEquals("应该成功2次", 2, result.getSuccessCount());
        assertEquals("应该失败1次", 1, result.getFailedCount());
    }

    @Test
    public void testAsyncStrategyAlwaysSuccess() {
        SyncStrategy async = new AsyncWriteStrategy();

        AtomicInteger replicationCount = new AtomicInteger(0);
        async.setSlaveReplicator((broker, msg) -> {
            replicationCount.incrementAndGet();
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return true;
        });

        long start = System.currentTimeMillis();
        SyncResult result = async.sync(slaves, message);
        long elapsed = System.currentTimeMillis() - start;

        assertTrue("异步应该立即返回成功", result.isSuccess());
        assertTrue("应该立即返回（小于100ms），实际: " + elapsed, elapsed < 100);
    }

    @Test
    public void testSemiSyncStrategyDefaultOneAck() {
        // 默认需要至少1个确认
        SyncStrategy semiSync = new SemiSyncWriteStrategy(1);

        AtomicInteger index = new AtomicInteger(0);
        semiSync.setSlaveReplicator((broker, msg) -> {
            // 只要有1个成功
            return index.incrementAndGet() == 1;
        });

        SyncResult result = semiSync.sync(slaves, message);

        assertTrue("半同步应该成功（至少1个确认）", result.isSuccess());
        assertEquals("应该成功1次", 1, result.getSuccessCount());
    }

    @Test
    public void testSemiSyncStrategyMultipleAck() {
        // 需要至少2个确认
        SyncStrategy semiSync = new SemiSyncWriteStrategy(2);

        AtomicInteger index = new AtomicInteger(0);
        semiSync.setSlaveReplicator((broker, msg) -> {
            // 前2个成功，第3个失败
            return index.incrementAndGet() <= 2;
        });

        SyncResult result = semiSync.sync(slaves, message);

        assertTrue("半同步应该成功（2个确认满足）", result.isSuccess());
        assertEquals("应该成功2次", 2, result.getSuccessCount());
    }

    @Test
    public void testSemiSyncStrategyNotEnoughAck() {
        // 需要至少2个确认，但只有1个成功
        SyncStrategy semiSync = new SemiSyncWriteStrategy(2);

        AtomicInteger index = new AtomicInteger(0);
        semiSync.setSlaveReplicator((broker, msg) -> {
            return index.incrementAndGet() == 1;
        });

        SyncResult result = semiSync.sync(slaves, message);

        assertFalse("半同步应该失败（只有1个确认）", result.isSuccess());
        assertEquals("应该成功1次", 1, result.getSuccessCount());
        assertEquals("应该失败2次", 2, result.getFailedCount());
    }

    @Test
    public void testEmptySlaves() {
        List<BrokerInfo> emptySlaves = new ArrayList<>();

        SyncStrategy sync = new SyncWriteStrategy();
        SyncResult result = sync.sync(emptySlaves, message);

        assertTrue("没有从节点时应该成功", result.isSuccess());
        assertEquals("应该成功0次", 0, result.getSuccessCount());
    }

    @Test
    public void testSyncStrategyFactory() {
        SyncStrategy sync = SyncStrategyFactory.getStrategy(SyncStrategy.Type.SYNC);
        assertTrue("应该是SyncWriteStrategy实例", sync instanceof SyncWriteStrategy);

        SyncStrategy async = SyncStrategyFactory.getStrategy(SyncStrategy.Type.ASYNC);
        assertTrue("应该是AsyncWriteStrategy实例", async instanceof AsyncWriteStrategy);

        SyncStrategy semiSync = SyncStrategyFactory.getStrategy(SyncStrategy.Type.SEMI_SYNC);
        assertTrue("应该是SemiSyncWriteStrategy实例", semiSync instanceof SemiSyncWriteStrategy);
    }

    @Test
    public void testSyncResultDetails() {
        SyncStrategy sync = new SyncWriteStrategy();

        AtomicInteger index = new AtomicInteger(0);
        sync.setSlaveReplicator((broker, msg) -> {
            return index.incrementAndGet() <= 2;
        });

        SyncResult result = sync.sync(slaves, message);

        List<String> successBrokers = result.getSuccessBrokers();
        List<String> failedBrokers = result.getFailedBrokers();

        assertEquals("应该有2个成功的broker", 2, successBrokers.size());
        assertEquals("应该有1个失败的broker", 1, failedBrokers.size());
        assertTrue("slave_1应该成功", successBrokers.contains("slave_1"));
        assertTrue("slave_2应该成功", successBrokers.contains("slave_2"));
        assertTrue("slave_3应该失败", failedBrokers.contains("slave_3"));
    }
}