# CatMQ 优化任务索引

## 任务列表

| 任务 | 优先级 | 描述 |
|------|--------|------|
| [01-empty-method-fix](01-empty-method-fix.md) | P1 | 清理空方法和桩代码 |
| [02-brokerinfo-incompatible](02-brokerinfo-incompatible.md) | P0 | 修复 BrokerInfo 类不兼容问题 |
| [03-cluster-module-tests](03-cluster-module-tests.md) | P1 | 为 catmq-cluster 模块添加单元测试 |
| [04-broker-nameserver-integration](04-broker-nameserver-integration.md) | P1 | 添加 Broker 与 NameServer 集成测试 |
| [05-admin-controller-tests](05-admin-controller-tests.md) | P2 | 为 Admin 模块 Controller 添加单元测试 |
| [06-client-broker-integration](06-client-broker-integration.md) | P1 | 添加 Client 与 Broker 集成测试 |
| [07-hardcode-config-extraction](07-hardcode-config-extraction.md) | P2 | 提取硬编码配置为可配置项 |
| [08-remove-empty-subclasses](08-remove-empty-subclasses.md) | P2 | 清理仅继承无扩展的空子类 |

## 执行优先级

```
P0 (最高): Task 02 - BrokerInfo 不兼容问题
P1 (高):   Task 01 - 空方法清理
P1 (高):   Task 03 - 集群模块测试
P1 (高):   Task 04 - Broker-NameServer 集成测试
P1 (高):   Task 06 - Client-Broker 集成测试
P2 (中):   Task 05 - Admin Controller 测试
P2 (中):   Task 07 - 硬编码配置提取
P2 (中):   Task 08 - 空子类清理
```
