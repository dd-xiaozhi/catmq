package com.aoaojiao.catmq.admin.client;

import com.aoaojiao.catmq.admin.config.BrokerConfig;
import com.aoaojiao.catmq.admin.dto.response.BrokerStatusResponse;
import com.aoaojiao.catmq.common.model.BrokerInfo;
import com.aoaojiao.catmq.common.model.BrokerInfo.BrokerStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Broker HTTP 客户端
 * 用于调用 Broker 的 HTTP API 获取真实数据
 *
 * @author DD
 */
@Component
public class BrokerHttpClient {

    private static final Logger log = LoggerFactory.getLogger(BrokerHttpClient.class);

    private final RestTemplate restTemplate;
    private final BrokerConfig brokerConfig;
    private final ObjectMapper objectMapper;

    public BrokerHttpClient(RestTemplate restTemplate, BrokerConfig brokerConfig) {
        this.restTemplate = restTemplate;
        this.brokerConfig = brokerConfig;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 获取 Broker 状态
     */
    public BrokerStatusResponse getBrokerStatus() {
        try {
            String url = brokerConfig.getAddress() + "/broker/status";
            String response = restTemplate.getForObject(url, String.class);
            return parseBrokerStatusResponse(response);
        } catch (Exception e) {
            log.warn("Failed to get broker status from {}, using fallback", brokerConfig.getAddress(), e);
            return createFallbackBrokerStatus();
        }
    }

    /**
     * 获取 Broker 心跳
     */
    public BrokerInfo getHeartbeat() {
        try {
            String url = brokerConfig.getAddress() + "/broker/heartbeat";
            String response = restTemplate.getForObject(url, String.class);
            return parseBrokerInfo(response);
        } catch (Exception e) {
            log.warn("Failed to get broker heartbeat from {}, using fallback", brokerConfig.getAddress(), e);
            return createFallbackHeartbeat();
        }
    }

    private BrokerStatusResponse parseBrokerStatusResponse(String json) {
        try {
            Map<String, Object> data = objectMapper.readValue(json, Map.class);
            return BrokerStatusResponse.builder()
                    .brokerName(getString(data, "brokerName"))
                    .status(getString(data, "status"))
                    .startTime(getLong(data, "startTime"))
                    .uptimeSeconds(getLong(data, "uptimeSeconds"))
                    .version(getString(data, "version"))
                    .cpuUsagePercent(getDouble(data, "cpuUsagePercent"))
                    .memoryUsagePercent(getDouble(data, "memoryUsagePercent"))
                    .diskUsagePercent(getDouble(data, "diskUsagePercent"))
                    .topicCount(getInteger(data, "topicCount"))
                    .queueCount(getInteger(data, "queueCount"))
                    .jvmMemory(parseJvmMemory(data.get("jvmMemory")))
                    .threadInfo(parseThreadInfo(data.get("threadInfo")))
                    .fileDescriptor(parseFileDescriptor(data.get("fileDescriptor")))
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse broker status response", e);
            return createFallbackBrokerStatus();
        }
    }

    private BrokerStatusResponse.JvmMemoryInfo parseJvmMemory(Object obj) {
        if (obj == null) return null;
        try {
            Map<String, Object> data = (Map<String, Object>) obj;
            return BrokerStatusResponse.JvmMemoryInfo.builder()
                    .heapTotal(getLong(data, "heapTotal"))
                    .heapUsed(getLong(data, "heapUsed"))
                    .heapUsagePercent(getDouble(data, "heapUsagePercent"))
                    .heapFree(getLong(data, "heapFree"))
                    .nonHeapTotal(getLong(data, "nonHeapTotal"))
                    .nonHeapUsed(getLong(data, "nonHeapUsed"))
                    .build();
        } catch (Exception e) {
            return null;
        }
    }

    private BrokerStatusResponse.ThreadInfo parseThreadInfo(Object obj) {
        if (obj == null) return null;
        try {
            Map<String, Object> data = (Map<String, Object>) obj;
            return BrokerStatusResponse.ThreadInfo.builder()
                    .activeCount(getInteger(data, "activeCount"))
                    .peakCount(getInteger(data, "peakCount"))
                    .totalStartedCount(getLong(data, "totalStartedCount"))
                    .daemonCount(getInteger(data, "daemonCount"))
                    .build();
        } catch (Exception e) {
            return null;
        }
    }

    private BrokerStatusResponse.FileDescriptorInfo parseFileDescriptor(Object obj) {
        if (obj == null) return null;
        try {
            Map<String, Object> data = (Map<String, Object>) obj;
            return BrokerStatusResponse.FileDescriptorInfo.builder()
                    .openFdCount(getLong(data, "openFdCount"))
                    .maxFdCount(getLong(data, "maxFdCount"))
                    .usagePercent(getDouble(data, "usagePercent"))
                    .build();
        } catch (Exception e) {
            return null;
        }
    }

    private BrokerInfo parseBrokerInfo(String json) {
        try {
            Map<String, Object> data = objectMapper.readValue(json, Map.class);
            return BrokerInfo.builder()
                    .brokerName(getString(data, "brokerName"))
                    .status(parseStatus(getString(data, "status")))
                    .timestamp(getLong(data, "timestamp"))
                    .cpuUsage(getDouble(data, "cpuUsage"))
                    .memoryUsage(getDouble(data, "memoryUsage"))
                    .topicCount(getInteger(data, "topicCount"))
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse broker heartbeat response", e);
            return createFallbackHeartbeat();
        }
    }

    private BrokerStatus parseStatus(String status) {
        if (status == null) {
            return BrokerStatus.INACTIVE;
        }
        try {
            return BrokerStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            // Handle unknown status values
            if ("UNAVAILABLE".equalsIgnoreCase(status)) {
                return BrokerStatus.INACTIVE;
            }
            return BrokerStatus.RUNNING;
        }
    }

    private String getString(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value != null ? value.toString() : null;
    }

    private Long getLong(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) return null;
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return null;
    }

    private Double getDouble(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) return null;
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return null;
    }

    private Integer getInteger(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) return null;
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return null;
    }

    private BrokerStatusResponse createFallbackBrokerStatus() {
        return BrokerStatusResponse.builder()
                .brokerName("unknown")
                .status("UNAVAILABLE")
                .uptimeSeconds(0L)
                .version("unknown")
                .cpuUsagePercent(0.0)
                .memoryUsagePercent(0.0)
                .diskUsagePercent(0.0)
                .topicCount(0)
                .queueCount(0)
                .build();
    }

    private BrokerInfo createFallbackHeartbeat() {
        return BrokerInfo.builder()
                .brokerName("unknown")
                .status(BrokerStatus.INACTIVE)
                .timestamp(System.currentTimeMillis())
                .cpuUsage(0.0)
                .memoryUsage(0.0)
                .topicCount(0)
                .build();
    }
}
