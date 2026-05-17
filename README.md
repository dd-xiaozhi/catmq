# catmq

从0到1手写MQ消息队列



**技术选型**：

- Java 8
- Netty 4.x
- JUnit 4 (单元测试)
- Spring Boot 3.x (管理控制台)



# 实现内容

## 文件存储模块设计（✅ 已完成）

commitLog 文件结构设计：借鉴 RocketMQ commitLog 文件设计 和 Kafka 多副本设计两者结合
```text
commitLog
  - topic01
    - 00000000
    - 00000001
    - ........
  - topic.....
```

topic_info 文件结构设计：

```json
[
  {
    "commitLogModel": {
      "filename": "00000000",	// 文件名
      "offset": 0,	// 当前写入开始位置
      "offsetLimit": 100	// commitLog 最大偏移量
    },
    "createAt": 1760992009,
    "queueModelList": [
      {
        "currentOffset": 0,
        "id": 0,
        "maxOffset": 0,
        "minOffset": 0
      }
    ],
    "topic": "order_pay_topic",
    "updateAt": 1760992009
  },
  {
    "commitLogModel": {
      "filename": "00000000",
      "offset": 0,
      "offsetLimit": 100
    },
    "createAt": 1760992009,
    "queueModelList": [
      {
        "currentOffset": 0,
        "id": 0,
        "maxOffset": 0,
        "minOffset": 0
      }
    ],
    "topic": "order_cancel_topic",
    "updateAt": 1760992009
  }
]
```

topic_info 文件信息加载：broker启动的时候加载所有的队列配置信息到缓存中

commitLog自动扩容：commitLog 文件满了自动创建新的 commitLog 文件，按照文件名序号加1的方式往下扩展(目前暂时没做超过指定大小的序号后的操作)

启动预加载 commitLog 文件：在 broker 启动时加通过 topic-info 的信息可以预加载 commitLog 文件到内存中

### 核心实现细节

- **MMap 内存映射**：基于 `FileChannel.map()` 实现零拷贝写入，MappedByteBuffer 直接映射文件 I/O，性能远高于传统 Stream 方式
- **写入并发控制**：`CommitLog` 使用 `PutMessageReentrantLock` 锁保护追加操作，保证多线程写入时数据不交叉
- **消费进度持久化**：`consume-queue-offset.json` 记录每个消费者的消费偏移量，重启后自动恢复，继续消费
- **定时刷盘机制**：后台线程每 3 秒将内存中的 Topic 元信息、消费偏移量刷入磁盘，防止异常宕机时数据丢失
- **统一缓存层**：`CommonCache` 封装 Topic 列表、消费偏移量等热点数据，提供只读访问接口
- **线程池管理**：`CommonThreadPool` 统一管理刷盘线程池，避免资源泄漏

> 相关类：`CommitLog`、`MMapUtil`、`CommitLogAppendHandler`、`BrokerStartup`、`CatmqTopicLoader`、`ConsumeQueueOffsetLoader`、`CommonCache`、`CommonThreadPool`



## consumerQueue 设计（✅ 已完成）

consumerQueue 文件结构设计
```text
consumerQueue
  - topic
    - topic_queue
        - 00000000
        - 00000001
        - ........
```

consumerQueue dispatch 分发器实现 ✅

consumerQueue 根据索引定位拉取数据 ✅

### 核心实现细节

- **异步消息分发**：`DispatchMessageService` 将 CommitLog 写入的消息异步分发到 ConsumerQueue 索引，避免阻塞写入
- **固定索引格式**：每条索引固定 20 字节（physicalOffset:8 + size:4 + tagCode:8），支持高效二分查找
- **消息拉取**：`PullService` 根据逻辑 offset 从 ConsumerQueue 获取索引，再从 CommitLog 读取完整消息内容
- **消费进度管理**：`CommonCache` 维护队列最大偏移量，支持消费进度跟踪

> 相关类：`ConsumerQueue`、`ConsumerQueueManager`、`DispatchMessageService`、`PullService`、`CQIndex`



## nameServer 设计（✅ 已完成）

借鉴 nacos 的设计思路落地 catmq 的 nameServer

### 核心实现

- **路由信息管理**：`RouteInfoManager` 维护 Broker 注册表、Topic 路由表，支持动态注册/注销
- **心跳检测**：自动检测 Broker 存活状态，超时自动下线
- **Topic 路由**：支持根据 Topic 查询可用的 Broker 列表
- **持久化存储**：支持将路由信息持久化到 JSON 文件

> 相关类：`RouteInfoManager`、`NameServer`、`BrokerInfo`、`TopicRouteInfo`



## 客户端SDK设计（✅ 已完成）

### 生产者（MessageProducer）

- **同步发送**：支持 String 和 byte[] 类型的消息发送
- **异步发送**：支持回调函数，在发送完成时通知
- **消息构建器**：提供 Builder 模式配置发送参数
- **重试机制**：支持配置最大重试次数

### 消费者（MessageConsumer）

- **订阅机制**：支持按 Topic 和 QueueId 订阅消息
- **消息拉取**：支持从指定偏移量拉取消息
- **消费结果**：返回 SUCCESS/RETRY_LATER/SKIP 三种消费结果
- **消费组支持**：支持消费者组概念

> 相关类：`MessageProducer`、`MessageConsumer`、`ConnectionManager`、`ClientConfig`



## broker高可用架构设计（✅ 已完成）

### 核心实现

- **Broker 节点管理**：`BrokerInfo` 维护节点信息，支持 MASTER/SLAVE/FOLLOWER 角色
- **Controller 选举**：基于 ZooKeeper 实现 Master 选举
- **负载均衡算法**：
  - 轮询（ROUND_ROBIN）
  - 随机（RANDOM）
  - 一致性哈希（CONSISTENT_HASH）
- **主从同步策略**：
  - **异步刷新（ASYNC）**：主节点写入后立即返回，从节点异步同步
  - **同步刷新（SYNC）**：主节点等待所有从节点写入成功才返回
  - **半同步刷新（SEMI_SYNC）**：主节点等待至少 N 个从节点确认

> 相关类：`SyncStrategy`、`SyncWriteStrategy`、`AsyncWriteStrategy`、`SemiSyncWriteStrategy`、`LoadBalancer`、`RoundRobinLoadBalancer`、`RandomLoadBalancer`、`ConsistentHashLoadBalancer`



## 特殊消息设计（✅ 已完成）

### 延迟消息（DelayMessage）

- **时间轮调度**：基于时间轮算法实现高效延迟任务调度
- **多级时间轮**：支持秒级和毫秒级精度的时间轮
- **延迟持久化**：支持将延迟消息持久化到磁盘，重启后可恢复

### 消息重试功能

- **自动重试**：消费失败后自动重试，支持配置最大重试次数
- **重试间隔**：支持指数退避策略

### 死信队列（DeadLetterQueue）

- **死信消息存储**：消费失败超过最大重试次数后进入死信队列
- **批量处理**：支持批量重试或删除死信消息
- **死信原因记录**：记录原始消息ID、死信原因、重试次数等信息

### 事务消息（TransactionMessage）

- **Half 消息**：事务开启时先发送 Prepared 消息
- **本地事务**：支持用户自定义本地事务执行逻辑
- **提交/回滚**：根据本地事务结果提交或回滚事务
- **事务状态**：PREPARED → END（COMMITED/ROLLED_BACK）

> 相关类：`DelayMessageService`、`DeadLetterQueueService`、`TransactionMessageService`、`TimeWheel`



## 可视化管理控制平台设计（✅ 已完成）

基于 Spring Boot 3.x 构建的管理控制台

### API 接口

- **Broker 状态查询**：`GET /api/v1/broker/status`
- **Broker 心跳查询**：`GET /api/v1/broker/heartbeat`
- **Broker 配置查询**：`GET /api/v1/broker/config`

### 监控指标

- **JVM 内存信息**：堆内存/非堆内存使用情况
- **线程信息**：活跃线程数、峰值线程数
- **GC 信息**：GC 次数、耗时
- **文件描述符**：打开的 FD 数量

> 相关类：`BrokerController`、`BrokerService`、`MetricsService`、`BrokerStatusResponse`



# 项目结构

```
catmq/
├── catmq-common/          # 公共模块（缓存、工具类）
├── catmq-store/           # 存储模块（CommitLog、ConsumerQueue）
├── catmq-client/          # 客户端SDK（生产者、消费者）
├── catmq-broker/          # Broker核心实现
├── catmq-nameserver/      # 名称服务
├── catmq-cluster/        # 集群高可用
└── catmq-admin/           # 管理控制台
```



# 测试覆盖

项目包含完整的单元测试和集成测试，覆盖所有核心模块：

- `TimeWheelTest` - 时间轮调度测试
- `DelayMessageServiceTest` - 延迟消息服务测试
- `TransactionMessageServiceTest` - 事务消息服务测试
- `RouteInfoManagerTest` - 路由信息管理测试
- `SyncStrategyTest` - 同步策略测试
- `LoadBalancerTest` - 负载均衡测试
- `MessageConsumerTest` - 消费者测试
- `MessageProducerTest` - 生产者测试
- `BrokerControllerTest` - 管理接口测试

运行测试：
```bash
mvn test
```