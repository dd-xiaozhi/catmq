# Task 06: 添加 Client 与 Broker 集成测试

## 问题描述

Client 与 Broker 的交互缺少端到端集成测试：
- `MessageProducer` 发送消息
- `MessageConsumer` 消费消息
- `ConnectionManager` 连接管理

## 需要添加的测试

### ClientBrokerIntegrationTest
1. **testSendAndConsumeMessage** - 发送消息并成功消费
2. **testBatchSendAndConsume** - 批量发送和消费
3. **testAsyncSend** - 异步发送消息
4. **testMessageFilter** - 按 tag 或 key 过滤消息
5. **testConsumeFromSpecificOffset** - 从指定偏移量开始消费

### ConnectionManagerTest
1. **testConnectionEstablishment** - 测试连接建立
2. **testConnectionReuse** - 测试连接复用
3. **testConnectionFailureHandling** - 测试连接失败处理

## 技术要求

- 使用嵌入式 Broker 进行测试
- 使用真实的 Netty 连接
- 测试完成后正确清理资源

## 验收标准

1. 集成测试覆盖 Client 与 Broker 的主要交互流程
2. 测试验证消息发送-消费完整流程
3. 所有测试通过: `mvn test -pl catmq-client`
