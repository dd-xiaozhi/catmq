# Task 03: 为 catmq-cluster 模块添加单元测试

## 问题描述

catmq-cluster 模块是集群高可用功能的核心，但没有任何测试覆盖：
- `ClusterManager` - 集群管理器（核心类，无测试）
- `LeaderElector` - 主节点选举
- `BrokerRegistry` - Broker 注册中心
- `MasterFailoverController` - 主从切换控制器

## 需要添加的测试

### 1. ClusterManagerTest
- 测试集群初始化
- 测试 Broker 注册和注销
- 测试集群状态管理

### 2. LeaderElectorTest
- 测试主节点选举逻辑
- 测试主节点故障时的重新选举
- 测试选举过程中的状态变化

### 3. BrokerRegistryTest
- 测试 Broker 注册
- 测试 Broker 心跳更新
- 测试超时 Broker 自动注销

### 4. MasterFailoverControllerTest
- 测试主节点故障检测
- 测试主从切换流程
- 测试切换后的状态同步

## 技术要求

- 使用 JUnit 4 框架
- 使用 Mockito 模拟 Zookeeper/Curator 依赖
- 避免依赖真实的 ZooKeeper 环境

## 验收标准

1. 为每个核心类创建单元测试
2. 测试覆盖率达到 70% 以上
3. 所有测试通过: `mvn test -pl catmq-cluster`
