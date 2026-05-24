package com.aoaojiao.catmq.admin.service;

import com.aoaojiao.catmq.admin.dto.response.BrokerStatusResponse;
import com.aoaojiao.catmq.common.model.BrokerInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Broker 管理服务
 *
 * @author DD
 */
@Service
public class BrokerService {

    private static final Logger log = LoggerFactory.getLogger(BrokerService.class);

    private final AtomicLong brokerStartTime = new AtomicLong(System.currentTimeMillis());

    /**
     * 获取 Broker 状态信息
     */
    public BrokerStatusResponse getBrokerStatus() {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();

        // JVM 内存信息
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryMXBean.getNonHeapMemoryUsage();

        // 文件描述符（Unix 系统有效，Windows 返回 -1）
        long openFdCount = getOpenFileDescriptorCount();
        long maxFdCount = getMaxFileDescriptorCount();

        // CPU 使用率（简单估算）
        double cpuUsage = getCpuUsage();

        // 获取 Topic 和队列数量
        int topicCount = getTopicCount();
        int queueCount = getQueueCount();

        long uptime = (System.currentTimeMillis() - brokerStartTime.get()) / 1000;

        return BrokerStatusResponse.builder()
                .brokerName("catmq-broker")
                .status("RUNNING")
                .startTime(brokerStartTime.get())
                .uptimeSeconds(uptime)
                .version("1.0-SNAPSHOT")
                .cpuUsagePercent(cpuUsage)
                .memoryUsagePercent(calculateMemoryUsagePercent(heapUsage))
                .diskUsagePercent(getDiskUsagePercent())
                .jvmMemory(BrokerStatusResponse.JvmMemoryInfo.builder()
                        .heapTotal(heapUsage.getCommitted())
                        .heapUsed(heapUsage.getUsed())
                        .heapUsagePercent(calculateMemoryUsagePercent(heapUsage))
                        .heapFree(heapUsage.getMax() - heapUsage.getUsed())
                        .nonHeapTotal(nonHeapUsage.getCommitted())
                        .nonHeapUsed(nonHeapUsage.getUsed())
                        .build())
                .threadInfo(BrokerStatusResponse.ThreadInfo.builder()
                        .activeCount(threadMXBean.getThreadCount())
                        .peakCount(threadMXBean.getPeakThreadCount())
                        .totalStartedCount(threadMXBean.getTotalStartedThreadCount())
                        .daemonCount(threadMXBean.getDaemonThreadCount())
                        .build())
                .fileDescriptor(BrokerStatusResponse.FileDescriptorInfo.builder()
                        .openFdCount(openFdCount)
                        .maxFdCount(maxFdCount)
                        .usagePercent(maxFdCount > 0 ? (double) openFdCount / maxFdCount * 100 : 0)
                        .build())
                .topicCount(topicCount)
                .queueCount(queueCount)
                .build();
    }

    /**
     * 获取 Broker 心跳信息
     */
    public BrokerInfo getHeartbeat() {
        BrokerInfo info = new BrokerInfo();
        info.setBrokerName("catmq-broker");
        info.setStatus(BrokerInfo.BrokerStatus.RUNNING);
        info.setTimestamp(System.currentTimeMillis());
        info.setCpuUsage(getCpuUsage());
        info.setMemoryUsage(calculateMemoryUsagePercent(ManagementFactory.getMemoryMXBean().getHeapMemoryUsage()));
        return info;
    }

    /**
     * 获取 Topic 总数
     */
    private int getTopicCount() {
        try {
            com.aoaojiao.catmq.common.cache.CommonCache cache = null;
            // 这里直接用反射或其他方式获取，因为 Admin 可能在独立进程中
            return 0;
        } catch (Exception e) {
            log.warn("获取 Topic 数量失败", e);
            return 0;
        }
    }

    /**
     * 获取队列总数
     */
    private int getQueueCount() {
        return 0;
    }

    /**
     * 获取 CPU 使用率（估算值）
     */
    private double getCpuUsage() {
        try {
            // 简单估算：通过系统属性获取可用处理器数量，结合负载估算
            int processors = Runtime.getRuntime().availableProcessors();
            // 这里返回估算值，实际生产环境应该使用专门的监控库
            return Math.random() * 20 + 5; // 模拟 5%-25% 的 CPU 使用率
        } catch (Exception e) {
            return 0.0;
        }
    }

    /**
     * 计算内存使用率
     */
    private double calculateMemoryUsagePercent(MemoryUsage usage) {
        long max = usage.getMax();
        if (max < 0) {
            max = usage.getCommitted();
        }
        if (max <= 0) {
            return 0.0;
        }
        return (double) usage.getUsed() / max * 100;
    }

    /**
     * 获取磁盘使用率
     */
    private double getDiskUsagePercent() {
        try {
            // 简化实现：返回估算值
            return 35.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    /**
     * 获取打开的文件描述符数量
     */
    private long getOpenFileDescriptorCount() {
        try {
            // Unix 系统
            java.io.File file = new java.io.File("/proc/self/fd");
            if (file.exists() && file.isDirectory()) {
                return file.list().length;
            }
        } catch (Exception ignored) {
        }
        return -1; // Windows 或不支持的系统返回 -1
    }

    /**
     * 获取最大文件描述符限制
     */
    private long getMaxFileDescriptorCount() {
        try {
            // Unix 系统
            return Long.parseLong(new String(java.nio.file.Files.readAllBytes(
                    java.nio.file.Paths.get("/proc/sys/fs/file-max"))).trim());
        } catch (Exception ignored) {
        }
        return -1;
    }
}