package com.aoaojiao.catmq.cluster.election;

import com.aoaojiao.catmq.cluster.model.BrokerInfo;
import com.aoaojiao.catmq.cluster.model.ClusterConfig;

/**
 * Leader 选举监听器接口
 * 监听主节点选举结果的变化
 *
 * @author DD
 */
public interface LeaderElectorListener {

    /**
     * 当本节点成为主节点时调用
     *
     * @param leaderInfo 主节点信息
     */
    void onElectedAsLeader(BrokerInfo leaderInfo);

    /**
     * 当本节点失去主节点身份时调用
     *
     * @param previousLeaderId 之前的主节点 ID
     */
    void onLeaderRemoved(String previousLeaderId);

    /**
     * 当主节点变更时调用
     *
     * @param oldLeader 旧主节点
     * @param newLeader 新主节点
     */
    void onLeaderChanged(BrokerInfo oldLeader, BrokerInfo newLeader);

    /**
     * 当选举出错时调用
     *
     * @param error 错误信息
     */
    void onElectionError(Exception error);

    /**
     * 选举状态变化
     *
     * @param isLeader 当前是否为主节点
     * @param clusterConfig 集群配置
     */
    void onStateChanged(boolean isLeader, ClusterConfig clusterConfig);
}