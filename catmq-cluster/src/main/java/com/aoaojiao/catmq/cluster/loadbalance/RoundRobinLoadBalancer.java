package com.aoaojiao.catmq.cluster.loadbalance;

import com.aoaojiao.catmq.common.model.BrokerInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 轮询负载均衡器
 * 依次选择每个 Broker，实现请求的均匀分布
 *
 * @author DD
 */
public class RoundRobinLoadBalancer implements LoadBalancer {

    private static final Logger logger = LoggerFactory.getLogger(RoundRobinLoadBalancer.class);

    /**
     * 计数器，用于轮询
     */
    private final AtomicInteger counter = new AtomicInteger(0);

    /**
     * 选中的 Broker 数量（用于统计）
     */
    private final AtomicInteger selectedCount = new AtomicInteger(0);

    @Override
    public BrokerInfo select(List<BrokerInfo> brokers, String key) {
        if (brokers == null || brokers.isEmpty()) {
            logger.warn("轮询负载均衡：无可用 Broker");
            return null;
        }

        int size = brokers.size();
        if (size == 1) {
            selectedCount.incrementAndGet();
            return brokers.get(0);
        }

        // 使用 getAndIncrement 实现轮询
        int index = Math.abs(counter.getAndIncrement()) % size;
        BrokerInfo selected = brokers.get(index);
        selectedCount.incrementAndGet();

        logger.debug("轮询选择 Broker：index={}, broker={}", index, selected.getBrokerId());
        return selected;
    }

    @Override
    public String getName() {
        return "ROUND_ROBIN";
    }

    @Override
    public void reset() {
        counter.set(0);
    }

    /**
     * 获取已选择的次数
     *
     * @return 选择次数
     */
    public int getSelectedCount() {
        return selectedCount.get();
    }
}