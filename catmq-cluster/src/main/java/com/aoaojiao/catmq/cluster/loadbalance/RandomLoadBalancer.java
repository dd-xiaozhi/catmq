package com.aoaojiao.catmq.cluster.loadbalance;

import com.aoaojiao.catmq.cluster.model.BrokerInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 随机负载均衡器
 * 随机选择一个 Broker
 *
 * @author DD
 */
public class RandomLoadBalancer implements LoadBalancer {

    private static final Logger logger = LoggerFactory.getLogger(RandomLoadBalancer.class);

    @Override
    public BrokerInfo select(List<BrokerInfo> brokers, String key) {
        if (brokers == null || brokers.isEmpty()) {
            logger.warn("随机负载均衡：无可用 Broker");
            return null;
        }

        int size = brokers.size();
        if (size == 1) {
            return brokers.get(0);
        }

        // 使用 ThreadLocalRandom 避免竞争
        int index = ThreadLocalRandom.current().nextInt(size);
        BrokerInfo selected = brokers.get(index);

        logger.debug("随机选择 Broker：index={}, broker={}", index, selected.getBrokerId());
        return selected;
    }

    @Override
    public String getName() {
        return "RANDOM";
    }
}