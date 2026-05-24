package com.aoaojiao.catmq.nameserver.protocol;

import com.alibaba.fastjson2.JSON;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TopicRouteRequest 序列化测试
 *
 * @author DD
 */
public class TopicRouteRequestTest {

    @Test
    public void testConstruction() {
        TopicRouteRequest request = new TopicRouteRequest();
        assertThat(request).isNotNull();
    }

    @Test
    public void testSetTopic() {
        TopicRouteRequest request = new TopicRouteRequest();
        request.setTopic("order_pay_topic");

        assertThat(request.getTopic()).isEqualTo("order_pay_topic");
    }

    @Test
    public void testSerialize() {
        TopicRouteRequest request = new TopicRouteRequest();
        request.setRequestId(2001L);
        request.setTopic("test_topic");

        String json = JSON.toJSONString(request);
        assertThat(json).isNotNull();
        assertThat(json).contains("\"topic\":\"test_topic\"");
    }

    @Test
    public void testDeserialize() {
        String json = "{\"requestId\":2001,\"topic\":\"my_topic\"}";
        TopicRouteRequest request = JSON.parseObject(json, TopicRouteRequest.class);

        assertThat(request.getRequestId()).isEqualTo(2001L);
        assertThat(request.getTopic()).isEqualTo("my_topic");
    }

    @Test
    public void testSerializeAndDeserialize() {
        TopicRouteRequest original = new TopicRouteRequest();
        original.setRequestId(12345L);
        original.setTopic("order_topic");

        String json = JSON.toJSONString(original);
        TopicRouteRequest deserialized = JSON.parseObject(json, TopicRouteRequest.class);

        assertThat(deserialized.getRequestId()).isEqualTo(original.getRequestId());
        assertThat(deserialized.getTopic()).isEqualTo(original.getTopic());
    }

    @Test
    public void testInheritFromBaseRequest() {
        TopicRouteRequest request = new TopicRouteRequest();
        request.setRequestId(999L);
        request.setTopic("inherit_topic");

        // 验证继承自 BaseRequest
        assertThat(request).isInstanceOf(BaseRequest.class);
        assertThat(request.getRequestId()).isEqualTo(999L);
        assertThat(request.getTopic()).isEqualTo("inherit_topic");
    }
}
