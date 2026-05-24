# Task 01: 清理空方法和桩代码

## 问题描述

项目中存在未实现的空方法和桩代码，需要清理或实现。

## 具体问题

### 1. BrokerStartup.java 空方法
**文件**: `catmq-broker/src/main/java/com/aoaojiao/catmq/broker/BrokerStartup.java:230`
```java
private void prepareCommitLogFileInMMap() {
    // 空方法，应该被删除或实现
}
```

### 2. MessageConsumer.Builder.setConsumerGroup() 未实现
**文件**: `catmq-client/src/main/java/com/aoaojiao/catmq/client/consumer/MessageConsumer.java:456-457`
```java
public Builder setConsumerGroup(String group) {
    return this;  // 未实际设置 consumerGroup
}
```

## 解决方案

1. 删除 `prepareCommitLogFileInMMap()` 空方法
2. 实现 `MessageConsumer.Builder.setConsumerGroup()` 方法，正确设置 consumerGroup 字段

## 验收标准

1. `prepareCommitLogFileInMMap()` 方法被删除或已实现
2. `setConsumerGroup()` 方法正确设置 consumerGroup 值
3. 项目编译通过: `mvn clean compile`
4. 相关测试通过: `mvn test`
