package com.aoaojiao.catmq.store.transaction;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * 事务消息服务测试
 *
 * @author DD
 */
public class TransactionMessageServiceTest {

    private TransactionMessageService txService;
    private String testStorePath;

    @Before
    public void setUp() {
        txService = new TransactionMessageService();
        testStorePath = "target/test-tx-store-" + System.currentTimeMillis();
        new File(testStorePath).mkdirs();
        txService.init(testStorePath);
        txService.start();
    }

    @After
    public void tearDown() {
        txService.shutdown();
    }

    @Test
    public void testSendHalfMessage() {
        String transactionId = "tx_001";
        String topic = "test_topic";

        TransactionMessage txMsg = txService.sendHalfMessage(
                transactionId, topic, 0, 0L, 100, 12345L,
                "test message body", null);

        assertNotNull("应该返回事务消息", txMsg);
        assertEquals("事务ID应该匹配", transactionId, txMsg.getTransactionId());
        assertEquals("主题应该匹配", topic, txMsg.getTopic());
        assertEquals("状态应该是PREPARED",
                TransactionMessage.TransactionState.PREPARED, txMsg.getTransactionState());

        // 验证能获取到事务消息
        TransactionMessage retrieved = txService.getTransactionMessage(transactionId);
        assertNotNull("应该能获取到事务消息", retrieved);
    }

    @Test
    public void testCommitTransaction() {
        String transactionId = "tx_commit_001";

        // 发送 Half 消息
        TransactionMessage txMsg = txService.sendHalfMessage(
                transactionId, "test_topic", 0, 0L, 100, 12345L,
                "test message", null);

        assertNotNull("应该返回事务消息", txMsg);
        assertEquals("状态应该是PREPARED",
                TransactionMessage.TransactionState.PREPARED, txMsg.getTransactionState());

        // 提交事务
        boolean committed = txService.commitTransaction(transactionId);
        assertTrue("应该提交成功", committed);

        // 验证状态
        TransactionMessage updated = txService.getTransactionMessage(transactionId);
        assertNotNull("应该能获取到事务消息", updated);
        assertEquals("状态应该是END",
                TransactionMessage.TransactionState.END, updated.getTransactionState());
        assertTrue("应该是已提交", updated.isCommitted());
    }

    @Test
    public void testRollbackTransaction() {
        String transactionId = "tx_rollback_001";

        // 发送 Half 消息
        txService.sendHalfMessage(
                transactionId, "test_topic", 0, 0L, 100, 12345L,
                "test message", null);

        // 回滚事务
        boolean rolledBack = txService.rollbackTransaction(transactionId);
        assertTrue("应该回滚成功", rolledBack);

        // 验证状态
        TransactionMessage updated = txService.getTransactionMessage(transactionId);
        assertNotNull("应该能获取到事务消息", updated);
        assertEquals("状态应该是END",
                TransactionMessage.TransactionState.END, updated.getTransactionState());
        assertTrue("应该是已回滚", updated.isRolledBack());
    }

    @Test
    public void testCommitNonExistTransaction() {
        boolean committed = txService.commitTransaction("non_exist_tx");
        assertFalse("不存在的事务提交应该失败", committed);
    }

    @Test
    public void testRollbackNonExistTransaction() {
        boolean rolledBack = txService.rollbackTransaction("non_exist_tx");
        assertFalse("不存在的事务回滚应该失败", rolledBack);
    }

    @Test
    public void testCommitAlreadyCommittedTransaction() {
        String transactionId = "tx_double_commit";

        // 发送 Half 消息并提交
        txService.sendHalfMessage(
                transactionId, "test_topic", 0, 0L, 100, 12345L,
                "test message", null);
        txService.commitTransaction(transactionId);

        // 再次提交应该失败
        boolean committedAgain = txService.commitTransaction(transactionId);
        assertFalse("已提交的事务再次提交应该失败", committedAgain);
    }

    @Test
    public void testRollbackAlreadyRolledBackTransaction() {
        String transactionId = "tx_double_rollback";

        // 发送 Half 消息并回滚
        txService.sendHalfMessage(
                transactionId, "test_topic", 0, 0L, 100, 12345L,
                "test message", null);
        txService.rollbackTransaction(transactionId);

        // 再次回滚应该失败
        boolean rolledBackAgain = txService.rollbackTransaction(transactionId);
        assertFalse("已回滚的事务再次回滚应该失败", rolledBackAgain);
    }

    @Test
    public void testGetTransactionState() {
        String transactionId = "tx_state_test";

        TransactionMessage.TransactionState before = txService.getTransactionState(transactionId);
        assertNull("发送前应该返回null", before);

        txService.sendHalfMessage(
                transactionId, "test_topic", 0, 0L, 100, 12345L,
                "test message", null);

        TransactionMessage.TransactionState state = txService.getTransactionState(transactionId);
        assertEquals("状态应该是PREPARED",
                TransactionMessage.TransactionState.PREPARED, state);
    }

    @Test
    public void testTransactionCountByState() {
        // 发送多个事务消息
        for (int i = 0; i < 5; i++) {
            txService.sendHalfMessage(
                    "tx_state_" + i, "test_topic", 0, 0L, 100, 12345L,
                    "test message " + i, null);
        }

        // 提交2个
        for (int i = 0; i < 2; i++) {
            txService.commitTransaction("tx_state_" + i);
        }

        int preparedCount = txService.getTransactionCountByState(
                TransactionMessage.TransactionState.PREPARED);
        int endCount = txService.getTransactionCountByState(
                TransactionMessage.TransactionState.END);

        assertEquals("应该有3个PREPARED状态的事务", 3, preparedCount);
        assertEquals("应该有2个END状态的事务", 2, endCount);
    }

    @Test
    public void testSendHalfMessageWithProperties() {
        String transactionId = "tx_prop_001";
        Map<String, String> properties = new HashMap<>();
        properties.put("key1", "value1");
        properties.put("key2", "value2");

        TransactionMessage txMsg = txService.sendHalfMessage(
                transactionId, "test_topic", 0, 0L, 100, 12345L,
                "test message body", properties);

        assertNotNull("应该返回事务消息", txMsg);
        assertNotNull("应该有properties", txMsg.getProperties());
        assertEquals("应该有2个属性", 2, txMsg.getProperties().size());
        assertEquals("key1的值应该匹配", "value1", txMsg.getProperties().get("key1"));
    }

    @Test
    public void testReceiveCheckResultCommit() {
        String transactionId = "tx_check_result_commit";

        txService.sendHalfMessage(
                transactionId, "test_topic", 0, 0L, 100, 12345L,
                "test message", null);

        // 模拟回查结果为提交
        txService.receiveCheckResult(transactionId, true);

        TransactionMessage txMsg = txService.getTransactionMessage(transactionId);
        assertNotNull("应该能获取到事务消息", txMsg);
        assertEquals("状态应该是END",
                TransactionMessage.TransactionState.END, txMsg.getTransactionState());
        assertTrue("应该是已提交", txMsg.isCommitted());
    }

    @Test
    public void testReceiveCheckResultRollback() {
        String transactionId = "tx_check_result_rollback";

        txService.sendHalfMessage(
                transactionId, "test_topic", 0, 0L, 100, 12345L,
                "test message", null);

        // 模拟回查结果为回滚
        txService.receiveCheckResult(transactionId, false);

        TransactionMessage txMsg = txService.getTransactionMessage(transactionId);
        assertNotNull("应该能获取到事务消息", txMsg);
        assertEquals("状态应该是END",
                TransactionMessage.TransactionState.END, txMsg.getTransactionState());
        assertTrue("应该是已回滚", txMsg.isRolledBack());
    }

    @Test
    public void testCheckTransactionIncrementCheckCount() {
        String transactionId = "tx_check_count";

        TransactionMessage txMsg = txService.sendHalfMessage(
                transactionId, "test_topic", 0, 0L, 100, 12345L,
                "test message", null);

        assertEquals("初始回查次数应该是0", 0, txMsg.getCheckCount());

        // 手动执行回查
        txService.checkTransaction(transactionId);

        TransactionMessage updated = txService.getTransactionMessage(transactionId);
        assertEquals("回查1次后应该是1", 1, updated.getCheckCount());
    }

    @Test
    public void testExceedMaxCheckCountRollback() {
        String transactionId = "tx_exceed_max_check";

        TransactionMessage txMsg = txService.sendHalfMessage(
                transactionId, "test_topic", 0, 0L, 100, 12345L,
                "test message", null);

        assertEquals("初始回查次数应该是0", 0, txMsg.getCheckCount());
        assertEquals("最大回查次数应该是5", 5, txMsg.getMaxCheckCount());

        // 执行6次回查（超过最大次数）
        for (int i = 0; i < 6; i++) {
            txService.checkTransaction(transactionId);
        }

        TransactionMessage updated = txService.getTransactionMessage(transactionId);
        assertNotNull("应该能获取到事务消息", updated);
        assertEquals("状态应该是END（自动回滚）",
                TransactionMessage.TransactionState.END, updated.getTransactionState());
        assertTrue("应该是已回滚", updated.isRolledBack());
    }

    @Test
    public void testStatus() {
        String status = txService.getStatus();
        assertNotNull("应该返回状态字符串", status);
        assertTrue("状态应该包含running信息", status.contains("running"));
        assertTrue("状态应该包含事务数量信息", status.contains("totalTransactions"));
        assertTrue("状态应该包含prepared信息", status.contains("prepared"));
        assertTrue("状态应该包含pendingCheck信息", status.contains("pendingCheck"));
    }
}