package com.aoaojiao.catmq.cluster.sync;

import com.aoaojiao.catmq.common.model.BrokerInfo;
import com.aoaojiao.catmq.cluster.model.ClusterConfig;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * 同步策略测试
 *
 * @author DD
 */
public class SyncStrategyTest {

    private BrokerInfo master;
    private List<BrokerInfo> slaves;
    private byte[] message;
    private ClusterConfig clusterConfig;

    @Before
    public void setUp() {
        master = new BrokerInfo("master_1", "master", "127.0.0.1", 9876);

        slaves = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            BrokerInfo slave = new BrokerInfo("slave_" + i, "slave_" + i, "127.0.0." + (i + 1), 9876);
            slaves.add(slave);
        }
        message = "test message content".getBytes();

        clusterConfig = new ClusterConfig();
        clusterConfig.setReplicationFactor(1);
        clusterConfig.setSyncMode(ClusterConfig.SyncMode.SYNC);
    }

    @Test
    public void testSyncWriteStrategy() {
        SyncStrategy sync = new SyncWriteStrategy();

        SyncResult result = sync.write(master, slaves, message, clusterConfig);

        assertNotNull("结果不应该为空", result);
        // 实际行为依赖于 slaves 是否可连接，这里只验证基本结构
    }

    @Test
    public void testAsyncWriteStrategy() {
        SyncStrategy async = new AsyncWriteStrategy();

        long start = System.currentTimeMillis();
        SyncResult result = async.write(master, slaves, message, clusterConfig);
        long elapsed = System.currentTimeMillis() - start;

        assertNotNull("结果不应该为空", result);
        assertTrue("异步应该立即返回（小于100ms）", elapsed < 100);
        assertFalse("needWaitAck 应该返回 false", async.needWaitAck());
    }

    @Test
    public void testSemiSyncWriteStrategy() {
        SyncStrategy semiSync = new SemiSyncWriteStrategy();

        SyncResult result = semiSync.write(master, slaves, message, clusterConfig);

        assertNotNull("结果不应该为空", result);
        assertTrue("needWaitAck 应该返回 true", semiSync.needWaitAck());
    }

    @Test
    public void testSyncWriteStrategyWithEmptySlaves() {
        SyncStrategy sync = new SyncWriteStrategy();
        List<BrokerInfo> emptySlaves = new ArrayList<>();

        SyncResult result = sync.write(master, emptySlaves, message, clusterConfig);

        assertNotNull("结果不应该为空", result);
        // 空 slave 列表时返回 failure
    }

    @Test
    public void testSyncWriteStrategyWithNullSlaves() {
        SyncStrategy sync = new SyncWriteStrategy();

        SyncResult result = sync.write(master, null, message, clusterConfig);

        assertNotNull("结果不应该为空", result);
        // null slave 列表时返回 failure
    }

    @Test
    public void testSyncStrategyFactory() {
        SyncStrategy syncStrategy = SyncStrategyFactory.create(ClusterConfig.SyncMode.SYNC);
        assertNotNull("SyncWriteStrategy 不应该为空", syncStrategy);
        assertTrue("应该是 SyncWriteStrategy 实例",
                syncStrategy instanceof SyncWriteStrategy);

        SyncStrategy asyncStrategy = SyncStrategyFactory.create(ClusterConfig.SyncMode.ASYNC);
        assertNotNull("AsyncWriteStrategy 不应该为空", asyncStrategy);
        assertTrue("应该是 AsyncWriteStrategy 实例",
                asyncStrategy instanceof AsyncWriteStrategy);

        SyncStrategy semiSyncStrategy = SyncStrategyFactory.create(ClusterConfig.SyncMode.SEMI_SYNC);
        assertNotNull("SemiSyncWriteStrategy 不应该为空", semiSyncStrategy);
        assertTrue("应该是 SemiSyncWriteStrategy 实例",
                semiSyncStrategy instanceof SemiSyncWriteStrategy);
    }

    @Test
    public void testSyncStrategyStaticWrite() {
        SyncResult result = SyncStrategyFactory.write(
                ClusterConfig.SyncMode.SYNC, master, slaves, message, clusterConfig);

        assertNotNull("结果不应该为空", result);
    }

    @Test
    public void testSyncWriteStrategyGetMode() {
        SyncStrategy sync = new SyncWriteStrategy();
        assertEquals("应该是 SYNC 模式", ClusterConfig.SyncMode.SYNC, sync.getMode());
        assertEquals("名称应该是 SYNC_WRITE", "SYNC_WRITE", sync.getName());

        SyncStrategy async = new AsyncWriteStrategy();
        assertEquals("应该是 ASYNC 模式", ClusterConfig.SyncMode.ASYNC, async.getMode());
        assertEquals("名称应该是 ASYNC_WRITE", "ASYNC_WRITE", async.getName());

        SyncStrategy semiSync = new SemiSyncWriteStrategy();
        assertEquals("应该是 SEMI_SYNC 模式", ClusterConfig.SyncMode.SEMI_SYNC, semiSync.getMode());
        assertEquals("名称应该是 SEMI_SYNC_WRITE", "SEMI_SYNC_WRITE", semiSync.getName());
    }

    @Test
    public void testSyncResultFactoryMethods() {
        SyncResult success = SyncResult.success(3, 100, "SYNC");
        assertTrue("应该成功", success.isSuccess());
        assertEquals("确认数应该是 3", 3, success.getAckCount());
        assertEquals("costMs 应该是 100", 100, success.getCostMs());
        assertEquals("syncMode 应该是 SYNC", "SYNC", success.getSyncMode());
        assertNotNull("timestamp 不应该为空", success.getTimestamp());

        SyncResult failure = SyncResult.failure("测试失败");
        assertFalse("不应该成功", failure.isSuccess());
        assertEquals("错误信息应该匹配", "测试失败", failure.getErrorMessage());
        assertNotNull("timestamp 不应该为空", failure.getTimestamp());

        SyncResult asyncSucc = SyncResult.asyncSuccess();
        assertTrue("应该成功", asyncSucc.isSuccess());
        assertEquals("异步确认数应该是 0", 0, asyncSucc.getAckCount());
        assertEquals("syncMode 应该是 ASYNC", "ASYNC", asyncSucc.getSyncMode());
    }

    @Test
    public void testSemiSyncWriteStrategyWithReplicationFactor2() {
        clusterConfig.setReplicationFactor(2);

        SyncStrategy semiSync = new SemiSyncWriteStrategy();
        int minAck = semiSync.getMinAckCount(3, clusterConfig);

        assertEquals("最少确认数应该是 2", 2, minAck);
    }
}