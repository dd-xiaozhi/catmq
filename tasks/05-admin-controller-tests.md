# Task 05: 为 Admin 模块 Controller 添加单元测试

## 问题描述

catmq-admin 模块的 Controller 大部分没有测试覆盖：

**无测试的 Controller**:
- TopicController
- MetricsController
- HealthController
- ConsumerGroupController
- MessageController
- AlertController

**有测试的 Controller**:
- BrokerController (已有 BrokerControllerTest)

## 需要添加的测试

### 1. TopicControllerTest
- 测试 Topic 列表查询
- 测试 Topic 创建/删除（如果有）
- 测试 Topic 详情查询

### 2. MetricsControllerTest
- 测试 JVM 内存指标获取
- 测试线程信息获取
- 测试 GC 信息获取

### 3. HealthControllerTest
- 测试健康检查端点
- 测试各项健康指标

### 4. ConsumerGroupControllerTest
- 测试消费组列表查询
- 测试消费进度查询

### 5. AlertControllerTest
- 测试告警规则管理
- 测试告警通知配置

## 技术要求

- 使用 Spring Boot Test
- 使用 MockMvc 进行 Controller 测试
- 避免依赖真实的外部服务

## 验收标准

1. 为每个 Controller 创建单元测试
2. 测试覆盖主要 API 端点
3. 所有测试通过: `mvn test -pl catmq-admin`
