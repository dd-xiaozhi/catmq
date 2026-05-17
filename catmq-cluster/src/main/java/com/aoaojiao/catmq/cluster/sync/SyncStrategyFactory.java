package com.aoaojiao.catmq.cluster.sync;

import com.aoaojiao.catmq.cluster.model.BrokerInfo;
import com.aoaojiao.catmq.cluster.model.ClusterConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 同步策略工厂
 * 根据配置创建和管理同步策略实例
 *
 * @author DD
 */
public class SyncStrategyFactory {

    private static final Logger logger = LoggerFactory.getLogger(SyncStrategyFactory.class);

    /**
     * 策略缓存
     */
    private static final Map<ClusterConfig.SyncMode, SyncStrategy> strategies = new ConcurrentHashMap<>();

    /**
     * 根据模式创建同步策略
     *
     * @param mode 同步模式
     * @return 同步策略
     */
    public static SyncStrategy create(ClusterConfig.SyncMode mode) {
        return strategies.computeIfAbsent(mode, m -> {
            logger.info("创建同步策略：{}", m);
            switch (m) {
                case SYNC:
                    return new SyncWriteStrategy();
                case ASYNC:
                    return new AsyncWriteStrategy();
                case SEMI_SYNC:
                    return new SemiSyncWriteStrategy();
                default:
                    logger.warn("未知的同步模式：{}，使用默认异步", m);
                    return new AsyncWriteStrategy();
            }
        });
    }

    /**
     * 执行同步写入
     *
     * @param mode         同步模式
     * @param master       主节点
     * @param slaves       从节点列表
     * @param data         数据
     * @param clusterConfig 配置
     * @return 同步结果
     */
    public static SyncResult write(ClusterConfig.SyncMode mode, BrokerInfo master,
                                    List<BrokerInfo> slaves, byte[] data, ClusterConfig clusterConfig) {
        SyncStrategy strategy = create(mode);
        return strategy.write(master, slaves, data, clusterConfig);
    }

    /**
     * 清空策略缓存
     */
    public static void clear() {
        strategies.clear();
        logger.info("清空同步策略缓存");
    }

    /**
     * 关闭所有策略（释放资源）
     */
    public static void shutdown() {
        for (Map.Entry<ClusterConfig.SyncMode, SyncStrategy> entry : strategies.entrySet()) {
            SyncStrategy strategy = entry.getValue();
            if (strategy instanceof AsyncWriteStrategy) {
                ((AsyncWriteStrategy) strategy).shutdown();
            } else if (strategy instanceof SyncWriteStrategy) {
                ((SyncWriteStrategy) strategy).shutdown();
            } else if (strategy instanceof SemiSyncWriteStrategy) {
                ((SemiSyncWriteStrategy) strategy).shutdown();
            }
        }
        clear();
    }
}