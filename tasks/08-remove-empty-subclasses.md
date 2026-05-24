# Task 08: 清理仅继承无扩展的空子类

## 问题描述

协议类统一到 catmq-common 后，部分模块中的类仅继承 catmq-common 中的类而无任何扩展，这些空子类应该被删除。

## 具体问题

### catmq-nameserver 模块中的空子类

1. **TopicRouteResponse.java**
   - 仅继承 `catmq-common.protocol.TopicRouteResponse`
   - 无任何扩展字段或方法

2. **BrokerRegisterRequest.java**
   - 仅继承 `catmq-common.protocol.BrokerRegisterRequest`
   - 无任何扩展字段或方法

### 其他可能的空子类

检查并清理所有仅继承无扩展的类。

## 解决方案

1. 检查 catmq-nameserver/protocol 目录下的所有类
2. 删除仅继承无扩展的空子类
3. 更新所有引用这些类的代码，直接使用 catmq-common 中的类
4. 确保编译和测试通过

## 验收标准

1. 删除所有仅继承无扩展的空子类
2. 没有因为删除导致的编译错误
3. 所有测试通过: `mvn test`
