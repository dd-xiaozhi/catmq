package com.aoaojiao.catmq.nameserver.protocol;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BaseRequest 序列化/反序列化测试
 *
 * @author DD
 */
public class BaseRequestTest {

    @Test
    public void testSerializeAndDeserialize() {
        BaseRequest request = new BaseRequest();
        request.setRequestId(12345L);

        // 序列化
        String json = JSON.toJSONString(request);
        assertThat(json).isNotNull();
        assertThat(json).contains("requestId");

        // 反序列化
        BaseRequest deserialized = JSON.parseObject(json, BaseRequest.class);
        assertThat(deserialized).isNotNull();
        assertThat(deserialized.getRequestId()).isEqualTo(12345L);
    }

    @Test
    public void testSerializeWithJsonObject() {
        BaseRequest request = new BaseRequest();
        request.setRequestId(999L);

        JSONObject jsonObject = (JSONObject) JSON.toJSON(request);
        assertThat(jsonObject.getLong("requestId")).isEqualTo(999L);
    }

    @Test
    public void testDeserializeFromJsonObject() {
        String json = "{\"requestId\":888}";
        BaseRequest request = JSON.parseObject(json, BaseRequest.class);
        assertThat(request.getRequestId()).isEqualTo(888L);
    }
}
