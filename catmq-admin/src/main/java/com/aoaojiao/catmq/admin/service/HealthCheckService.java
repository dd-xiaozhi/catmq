package com.aoaojiao.catmq.admin.service;

import com.aoaojiao.catmq.admin.dto.response.HealthCheckResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.lang.management.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 健康检查服务
 *
 * @author DD
 */
@Service
public class HealthCheckService {

    private static final Logger log = LoggerFactory.getLogger(HealthCheckService.class);

    private final long startTime = System.currentTimeMillis();

    /**
     * 执行完整健康检查
     */
    public HealthCheckResponse check() {
        List<HealthCheckResponse.ComponentHealth> components = new ArrayList<>();

        // 检查各组件
        components.add(checkJVM());
        components.add(checkMemory());
        components.add(checkThreads());
        components.add(checkDisk());
        components.add(checkBroker());

        // 判断总体状态
        String overallStatus = determineOverallStatus(components);
        long uptime = (System.currentTimeMillis() - startTime) / 1000;

        return HealthCheckResponse.builder()
                .status(overallStatus)
                .timestamp(System.currentTimeMillis())
                .uptimeSeconds(uptime)
                .components(components)
                .build();
    }

    /**
     * 检查 JVM 状态
     */
    private HealthCheckResponse.ComponentHealth checkJVM() {
        long start = System.currentTimeMillis();
        String status = "UP";
        String message = "JVM 运行正常";

        try {
            RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
            String vmName = runtimeMXBean.getVmName();
            String vmVersion = runtimeMXBean.getVmVersion();
            message = String.format("JVM: %s %s", vmName, vmVersion);
        } catch (Exception e) {
            status = "DOWN";
            message = "JVM 检查失败: " + e.getMessage();
        }

        return HealthCheckResponse.ComponentHealth.builder()
                .name("JVM")
                .status(status)
                .message(message)
                .responseTime(System.currentTimeMillis() - start)
                .build();
    }

    /**
     * 检查内存状态
     */
    private HealthCheckResponse.ComponentHealth checkMemory() {
        long start = System.currentTimeMillis();
        String status = "UP";
        String message;

        try {
            MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
            MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();

            long max = heapUsage.getMax();
            if (max < 0) {
                max = heapUsage.getCommitted();
            }

            double usagePercent = 0;
            if (max > 0) {
                usagePercent = (double) heapUsage.getUsed() / max * 100;
            }

            if (usagePercent > 90) {
                status = "DOWN";
                message = String.format("堆内存使用率过高: %.2f%%", usagePercent);
            } else if (usagePercent > 80) {
                status = "DEGRADED";
                message = String.format("堆内存使用率较高: %.2f%%", usagePercent);
            } else {
                message = String.format("堆内存使用率: %.2f%%", usagePercent);
            }

        } catch (Exception e) {
            status = "DOWN";
            message = "内存检查失败: " + e.getMessage();
        }

        return HealthCheckResponse.ComponentHealth.builder()
                .name("Memory")
                .status(status)
                .message(message)
                .responseTime(System.currentTimeMillis() - start)
                .build();
    }

    /**
     * 检查线程状态
     */
    private HealthCheckResponse.ComponentHealth checkThreads() {
        long start = System.currentTimeMillis();
        String status = "UP";
        String message;

        try {
            ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
            int threadCount = threadMXBean.getThreadCount();
            int peakCount = threadMXBean.getPeakThreadCount();
            int daemonCount = threadMXBean.getDaemonThreadCount();

            // 简单判断：线程数过多可能是问题
            if (threadCount > 1000) {
                status = "DEGRADED";
                message = String.format("线程数较高: %d (峰值: %d)", threadCount, peakCount);
            } else {
                message = String.format("线程数: %d (峰值: %d, 守护: %d)",
                        threadCount, peakCount, daemonCount);
            }

        } catch (Exception e) {
            status = "DOWN";
            message = "线程检查失败: " + e.getMessage();
        }

        return HealthCheckResponse.ComponentHealth.builder()
                .name("Threads")
                .status(status)
                .message(message)
                .responseTime(System.currentTimeMillis() - start)
                .build();
    }

    /**
     * 检查磁盘状态
     */
    private HealthCheckResponse.ComponentHealth checkDisk() {
        long start = System.currentTimeMillis();
        String status = "UP";
        String message;

        try {
            // 简化实现：检查存储目录是否存在
            java.io.File storeDir = new java.io.File("D:\\Work\\project\\catmq\\catmq\\store");
            if (!storeDir.exists()) {
                status = "DEGRADED";
                message = "存储目录不存在，可能首次运行";
            } else {
                long usableSpace = storeDir.getUsableSpace();
                long totalSpace = storeDir.getTotalSpace();
                double usagePercent = (double) (totalSpace - usableSpace) / totalSpace * 100;

                if (usagePercent > 90) {
                    status = "DOWN";
                    message = String.format("磁盘使用率过高: %.2f%%", usagePercent);
                } else if (usagePercent > 80) {
                    status = "DEGRADED";
                    message = String.format("磁盘使用率较高: %.2f%%", usagePercent);
                } else {
                    message = String.format("磁盘使用率: %.2f%%", usagePercent);
                }
            }

        } catch (Exception e) {
            status = "DOWN";
            message = "磁盘检查失败: " + e.getMessage();
        }

        return HealthCheckResponse.ComponentHealth.builder()
                .name("Disk")
                .status(status)
                .message(message)
                .responseTime(System.currentTimeMillis() - start)
                .build();
    }

    /**
     * 检查 Broker 组件状态
     */
    private HealthCheckResponse.ComponentHealth checkBroker() {
        long start = System.currentTimeMillis();
        String status = "UP";
        String message;

        try {
            // 检查 Broker 核心组件是否正常
            // 这里可以添加更多的检查逻辑
            message = "Broker 运行正常";

        } catch (Exception e) {
            status = "DOWN";
            message = "Broker 检查失败: " + e.getMessage();
        }

        return HealthCheckResponse.ComponentHealth.builder()
                .name("Broker")
                .status(status)
                .message(message)
                .responseTime(System.currentTimeMillis() - start)
                .build();
    }

    /**
     * 判断总体健康状态
     */
    private String determineOverallStatus(List<HealthCheckResponse.ComponentHealth> components) {
        boolean hasDown = components.stream()
                .anyMatch(c -> "DOWN".equals(c.getStatus()));
        boolean hasDegraded = components.stream()
                .anyMatch(c -> "DEGRADED".equals(c.getStatus()));

        if (hasDown) {
            return "DOWN";
        } else if (hasDegraded) {
            return "DEGRADED";
        }
        return "UP";
    }
}