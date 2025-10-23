# catmq

从0到1手写mq消息队列





**技术选型**：

- Java8
- Netty



# 实现内容
## 文件存储模块设计（✅）

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



## consumerQueue 设计

consumerQueue 文件结构设计
```text
consumerQueue
  - topic
    - topic_queue
        - 00000000
        - 00000001
        - ........
```

consumerQueue dispatch 分发器实现

consumerQueue 根据索引定位拉取数据



## nameServer 设计

借鉴 nacos 的设计思路落地 catmq 的 nameServer



## 客户端SDK设计



## broker高可用架构设计

broker controller 节点选取

broker 集群 ip 获取

负载均衡算法实现

主从节点数据同步
- 异步刷新
- 同步刷新
- 半同步刷新

主从切换



## 特殊消息设计

延迟消息

消息重试功能

死信队列

事务消息



## 可视化管理控制平台设计

基本的api

可观测指标

监控broker健康状态