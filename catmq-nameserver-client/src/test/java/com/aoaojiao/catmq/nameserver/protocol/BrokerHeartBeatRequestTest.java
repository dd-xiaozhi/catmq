package com.aoaojiao.catmq.nameserver.protocol;

import com.alibaba.fastjson2.JSON;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BrokerHeartBeatRequest 序列化测试
 *
 * @author DD
 */
public class BrokerHeartBeatRequestTest {

    @Test
    public void testConstruction() {
        BrokerHeartBeatRequest request = new BrokerHeartBeatRequest();
        assertThat(request).isNotNull();
    }

    @Test
    public void testSetFields() {
        BrokerHeartBeatRequest request = new BrokerHeartBeatRequest();
        request.setRequestId(1001L);
        request.setBrokerName("test-broker");
        request.setTopicList(new String[]{"topic1", "topic2"});

        assertThat(request.getRequestId()).isEqualTo(1001L);
        assertThat(request.getBrokerName()).isEqualTo("test-broker");
        assertThat(request.getTopicList()).hasSize(2);
        assertThat(request.getTopicList()[0]).isEqualTo("topic1");
        assertThat(request.getTopicList()[1]).isEqualTo("topic2");
    }

    @Test
    public void testSerialize() {
        BrokerHeartBeatRequest request = new BrokerHeartBeatRequest();
        request.setRequestId(2002L);
        request.setBrokerName("broker-001");
        request.setTopicList(new String[]{"order_topic", "payment_topic"});

        String json = JSON.toJSONString(request);
        assertThat(json).isNotNull();
        assertThat(json).contains("\"brokerName\":\"broker-001\"");
        assertThat(json).contains("\"topicList\"");
    }

    @Test
    public void testDeserialize() {
        String json = "{\"requestId\":3003,\"brokerName\":\"broker-002\",\"topicList\":[\"t1\",\"t2\"]}";
        BrokerHeartBeatRequest request = JSON.parseObject(json, BrokerHeartBeatRequest.class);

        assertThat(request.getRequestId()).isEqualTo(3003L);
        assertThat(request.getBrokerName()).isEqualTo("broker-002");
        assertThat(request.getTopicList()).hasSize(2);
        assertThat(request.getTopicList()[0]).isEqualTo("t1");
        assertThat(request.getTopicList()[1]).isEqualTo("t2");
    }

    @Test
    public void testSerializeAndDeserialize() {
        BrokerHeartBeatRequest original = new BrokerHeartBeatRequest();
        original.setRequestId(12345L);
        original.setBrokerName("heartbeat-broker");
        original.setTopicList(new String[]{"topicA", "topicB", "topicC"});

        String json = JSON.toJSONString(original);
        BrokerHeartBeatRequest deserialized = JSON.parseObject(json, BrokerHeartBeatRequest.class);

        assertThat(deserialized.getRequestId()).isEqualTo(original.getRequestId());
        assertThat(deserialized.getBrokerName()).isEqualTo(original.getBrokerName());
        assertThat(deserialized.getTopicList()).isEqualTo(original.getTopicList());
    }

    @Test
    public void testEmptyTopicList() {
        BrokerHeartBeatRequest request = new BrokerHeartBeatRequest();
        request.setRequestId(1L);
        request.setBrokerName("empty-broker");
        request.setTopicList(new String[]{});

        String json = JSON.toJSONString(request);
        BrokerHeartBeatRequest deserialized = JSON.parseObject(json, BrokerHeartBeatRequest.class);

        assertThat(deserialized.getTopicList()).isEmpty();
    }
}
