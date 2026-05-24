# CatMQ 示例工程

本示例工程演示如何在业务应用中使用 CatMQ 消息队列。

## 工程结构

```
catmq-example/
├── pom.xml
└── src/main/
    ├── java/com/aoaojiao/catmq/example/
    │   ├── producer/          # 生产者示例
    │   │   └── OrderProducerExample.java
    │   └── consumer/         # 消费者示例
    │       └── OrderConsumerExample.java
    └── resources/
        ├── application.yml    # 应用配置
        └── logback.xml        # 日志配置
```

## 快速开始

### 1. 环境要求

- JDK 8+
- Maven 3.6+
- CatMQ Broker 已启动（默认端口 9091）

### 2. 编译工程

```bash
mvn clean compile
```

### 3. 运行示例

**启动生产者（发送订单消息）：**

```bash
mvn compile exec:java -Dexec.mainClass="com.aoaojiao.catmq.example.producer.OrderProducerExample"
```

**启动消费者（接收订单消息）：**

```bash
mvn compile exec:java -Dexec.mainClass="com.aoaojiao.catmq.example.consumer.OrderConsumerExample"
```

## 使用示例

### 生产者示例

```java
import com.aoaojiao.catmq.client.config.ClientConfig;
import com.aoaojiao.catmq.client.producer.MessageProducer;
import com.aoaojiao.catmq.client.model.SendMessageResponse;

// 1. 创建配置
ClientConfig config = ClientConfig.defaultConfig();
config.setBrokerAddress("localhost:9091");

// 2. 创建生产者
MessageProducer producer = new MessageProducer(config);

// 3. 启动
producer.start();

// 4. 发送消息
String topic = "order_topic";
String message = "{\"orderId\":1,\"amount\":99.99}";
SendMessageResponse response = producer.send(topic, message);

if (response.isSuccess()) {
    System.out.println("发送成功，消息ID: " + response.getMessageId());
}

// 5. 关闭
producer.shutdown();
```

### 消费者示例

```java
import com.aoaojiao.catmq.client.config.ClientConfig;
import com.aoaojiao.catmq.client.consumer.MessageConsumer;

// 1. 创建配置
ClientConfig config = ClientConfig.defaultConfig();
config.setBrokerAddress("localhost:9091");

// 2. 创建消费者
MessageConsumer consumer = new MessageConsumer(config);

// 3. 启动
consumer.start();

// 4. 订阅主题
consumer.subscribe("order_topic", (topic, body, properties) -> {
    System.out.println("收到消息: " + body);
    // 处理业务逻辑
    return MessageConsumer.ConsumeResult.SUCCESS;
});

// 5. 开始拉取
consumer.startPulling();

// 6. 关闭
consumer.shutdown();
```

## 配置说明

在 `application.yml` 中配置：

```yaml
catmq:
  broker:
    address: localhost:9091  # Broker 地址
```

## 消息格式

示例中的订单消息格式：

```json
{
  "orderId": 1,
  "orderNo": "ORD123456789",
  "amount": 99.99,
  "status": "PAID",
  "items": [
    {"productId": 1001, "name": "商品A", "quantity": 1}
  ]
}
```

## API 文档

### MessageProducer

| 方法 | 说明 |
|------|------|
| `send(topic, body)` | 同步发送字符串消息 |
| `send(topic, body, tags)` | 同步发送带标签的消息 |
| `sendAsync(topic, body, callback)` | 异步发送消息 |
| `start()` | 启动生产者 |
| `shutdown()` | 关闭生产者 |

### MessageConsumer

| 方法 | 说明 |
|------|------|
| `subscribe(topic, listener)` | 订阅主题 |
| `startPulling()` | 开始拉取消息 |
| `pull(topic, queueId, offset)` | 手动拉取消息 |
| `start()` | 启动消费者 |
| `shutdown()` | 关闭消费者 |

### ConsumeResult

| 枚举值 | 说明 |
|--------|------|
| `SUCCESS` | 消费成功 |
| `RETRY_LATER` | 稍后重试 |
| `SKIP` | 跳过消息 |
