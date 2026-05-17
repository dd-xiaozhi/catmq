# AGENTS.md

This file provides guidance to Qoder (qoder.com) when working with code in this repository.

## 项目概述

CatMQ 是一个从 0 到 1 手写的消息队列，借鉴 RocketMQ 和 Kafka 的设计思路。技术栈：Java 8, Netty, Fastjson2。

## 常用命令

```bash
# 编译整个项目
mvn clean compile

# 运行测试
mvn test

# 运行单个测试类
mvn test -Dtest=CommitLogAppendHandlerTest

# 打包
mvn clean package

# 安装到本地仓库
mvn clean install

# 跳过测试
mvn clean compile -DskipTests
```

## 模块架构

```
catmq
├── catmq-common       # 公共模块：数据模型、缓存、工具类
├── catmq-store        # 消息存储核心：CommitLog、ConsumerQueue、MMap 内存映射
├── catmq-broker       # Broker 服务：启动入口、配置加载、消息追加
├── catmq-nameserver   # 命名服务（规划中）
├── catmq-client       # 客户端 SDK（规划中）
└── catmq-cluster      # 集群相关：高可用、主从同步（规划中）
```

## 核心设计

### 文件存储结构
```
catmq/store/
├── commitLog/                    # 消息存储目录
│   └── {topic}/
│       └── 00000000              # 按序号命名的 CommitLog 文件
├── consumeQueue/                 # 消费队列索引
│   └── {topic}/
│       └── {queueId}/
│           └── 00000000
├── catmq-topic.json              # Topic 元信息持久化文件
└── consume-queue-offset.json      # 消费位移持久化文件
```

### 核心类职责

| 类 | 模块 | 职责 |
|---|------|------|
| `BrokerStartup` | broker | Broker 启动入口，负责初始化和加载 |
| `CommitLogAppendHandler` | store | 消息追加到 CommitLog 的入口 |
| `CommitLog` | store | 单个 CommitLog 文件操作，封装 MMap |
| `CommitLogManager` | store | 管理所有 Topic 的 CommitLog 实例 |
| `MMapUtil` | store | MMap 内存映射工具，处理读写和内存释放 |
| `CatmqTopicLoader` | broker | 从 JSON 文件加载/持久化 Topic 信息 |
| `ConsumeQueueOffsetLoader` | broker | 加载/持久化消费位移信息 |
| `CommonCache` | common | 全局静态缓存，存储 Topic 列表和消费位移 |

### 数据流
1. `BrokerStartup.start()` → 初始化 `ConfigContext`
2. `CatmqTopicLoader.load()` → 从 JSON 加载 Topic 信息到 `CommonCache`
3. `CommitLogAppendHandler.prepareLoadingToMMap()` → 将 CommitLog 文件映射到内存
4. `appendMessage()` → 追加消息到 MMap 映射的 CommitLog 文件
5. 定时任务 → 将 `CommonCache` 中的变更写回 JSON 文件

### 关键设计点
- **MMap 文件映射**：使用 `MappedByteBuffer` 实现零拷贝写入，避免传统 IO 的拷贝开销
- **CommitLog 自动扩容**：文件满时自动创建下一个序号文件（如 00000001）
- **文件命名规范**：8 位数字序号（如 `00000000`），不足前面补 0
- **默认 CommitLog 大小**：1GB（`StoreConstant.DEFAULT_MESSAGE_COMMIT_LOG_FILE_SIZE`）

## 代码风格

- 包名：`com.aoaojiao.catmq.{模块名}`
- 使用 Lombok 简化 POJO（`@Data`, `@Builder`, `@AllArgsConstructor`）
- 使用 Fastjson2 进行 JSON 序列化/反序列化
- 配置路径硬编码在 `MessageStoreConfig` 中（TODO: 后续改为环境变量或系统属性）

## 注意事项

1. **MMap 内存释放**：使用 `MMapUtil.clean()` 释放堆外内存，不能依赖 GC
2. **文件写入**：写入消息时需加锁（`PutMessageReentrantLock`）保证顺序
3. **Windows 路径**：`MessageStoreConfig` 中 storePath 配置为 Windows 路径，跨平台需调整
4. **测试数据**：测试会在 `D:\Work\project\catmq\catmq\store\` 下生成实际的 CommitLog 文件
