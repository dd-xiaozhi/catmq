# Task 07: 提取硬编码配置为可配置项

## 问题描述

项目中存在硬编码的配置值，应该提取为可配置项。

## 具体问题

### 心跳间隔硬编码
**文件**: `catmq-broker/src/main/java/com/aoaojiao/catmq/broker/BrokerStartup.java:300`
```java
}, 30000, 30000, TimeUnit.MILLISECONDS);
```
心跳间隔 30 秒硬编码在代码中。

## 解决方案

1. 在 `BrokerConfig` 中添加心跳间隔配置项 `heartbeatInterval`
2. 默认值为 30000ms (30秒)
3. 从配置文件或环境变量读取
4. 替换代码中的硬编码值

## 验收标准

1. 心跳间隔可配置
2. 有合理的默认值
3. 项目编译通过: `mvn clean compile`
