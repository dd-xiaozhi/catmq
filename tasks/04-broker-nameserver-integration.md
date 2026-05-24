# Task 04: 添加 Broker 与 NameServer 集成测试

## 问题描述

Broker 与 NameServer 的交互缺少真实的集成测试。虽然代码实现了注册和心跳功能，但没有端到端验证。

## 现有代码

- `BrokerStartup.registerToNameServer()` - Broker 启动时注册
- `NameServerClient.registerBroker()` - 发送注册请求
- `NameServerClient.sendHeartBeat()` - 发送心跳

## 需要添加的测试

### BrokerNameServerIntegrationTest
1. **testBrokerRegister** - Broker 启动并成功注册到 NameServer
2. **testBrokerHeartbeat** - Broker 发送心跳，NameServer 正确响应
3. **testBrokerAutoRemoveOnTimeout** - Broker 异常停止后超时被移除
4. **testTopicRouteQuery** - 通过 NameServer 查询 Topic 路由信息
5. **testMultipleBrokerRegistration** - 多个 Broker 同时注册

## 技术要求

- 使用嵌入式 NameServer 进行测试
- 使用真实的 Netty 连接
- 使用临时端口避免冲突
- 测试完成后正确清理资源

## 验收标准

1. 集成测试覆盖 Broker 与 NameServer 的主要交互流程
2. 测试可以在 CI 环境中独立运行
3. 所有测试通过: `mvn test -pl catmq-broker`
