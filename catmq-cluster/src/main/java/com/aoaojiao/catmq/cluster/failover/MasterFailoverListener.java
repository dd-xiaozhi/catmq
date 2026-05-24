package com.aoaojiao.catmq.cluster.failover;

import com.aoaojiao.catmq.common.model.BrokerInfo;

/**
 * 主从切换监听器
 * 监听主从切换事件
 *
 * @author DD
 */
public interface MasterFailoverListener {

    /**
     * 当主节点故障时调用
     *
     * @param failedMaster 故障的主节点
     */
    void onMasterFailed(BrokerInfo failedMaster);

    /**
     * 当新主节点被选举出来时调用
     *
     * @param newMaster 新主节点
     */
    void onNewMasterElected(BrokerInfo newMaster);

    /**
     * 当主从切换完成时调用
     *
     * @param oldMaster 旧主节点
     * @param newMaster 新主节点
     */
    void onFailoverComplete(BrokerInfo oldMaster, BrokerInfo newMaster);

    /**
     * 当主从切换失败时调用
     *
     * @param error 错误信息
     */
    void onFailoverFailed(String error);

    /**
     * 当故障检测开始时调用
     *
     * @param suspectedMaster 疑似故障的主节点
     */
    void onFailureDetectionStarted(BrokerInfo suspectedMaster);

    /**
     * 当故障检测结束时调用
     *
     * @param suspectedMaster 疑似故障的主节点
     * @param confirmed       是否确实故障
     */
    void onFailureDetectionCompleted(BrokerInfo suspectedMaster, boolean confirmed);
}