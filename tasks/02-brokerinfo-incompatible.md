# Task 02: 修复 BrokerInfo 类不兼容问题

## 问题描述

当前存在多个 BrokerInfo 版本，字段定义完全不同，无法通用：

### 1. catmq-common.protocol.BrokerInfo
字段: brokerName, brokerIp, brokerPort, brokerId(int), weight, lastUpdateTimestamp, alive, topicList[], clusterName

### 2. catmq-cluster.model.BrokerInfo
字段: brokerId(String), brokerName, host, port, role(BrokerRole), status(BrokerStatus), weight, startTime, lastHeartbeat, available

### 3. catmq-admin.model.BrokerInfo
字段: brokerName, status, timestamp, cpuUsage, memoryUsage, diskUsage...

**问题**: catmq-cluster 的 BrokerInfo 与其他两个完全不兼容，字段名和类型都不同。

## 解决方案

1. 分析三个 BrokerInfo 类的字段使用场景
2. 设计一个统一的 BrokerInfo 类，包含所有必要的字段
3. 将统一后的 BrokerInfo 放在 catmq-common 模块
4. 更新 catmq-cluster、catmq-nameserver、catmq-admin 使用统一的 BrokerInfo
5. 删除重复的 BrokerInfo 类

**关键设计决策**:
- brokerId: 统一为 String 类型（更灵活）
- 地址信息: 统一使用 brokerIp + brokerPort
- 集群相关: 添加 role, status, clusterName
- 监控相关: 添加 cpuUsage, memoryUsage, diskUsage 等
- 时间相关: 统一使用 lastHeartbeat 而非 lastUpdateTimestamp

## 验收标准

1. BrokerInfo 只在 catmq-common 中存在一份定义
2. 所有模块使用统一的 BrokerInfo
3. catmq-cluster 不再有独立的 BrokerInfo 定义
4. 项目编译通过: `mvn clean compile`
5. 所有测试通过: `mvn test`
