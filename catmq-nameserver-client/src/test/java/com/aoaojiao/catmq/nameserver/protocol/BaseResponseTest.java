package com.aoaojiao.catmq.nameserver.protocol;

import com.alibaba.fastjson2.JSON;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BaseResponse 序列化/反序列化测试
 *
 * @author DD
 */
public class BaseResponseTest {

    @Test
    public void testSuccessStaticMethod() {
        BaseResponse response = BaseResponse.success(100L);
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getRequestId()).isEqualTo(100L);
        assertThat(response.getMessage()).isNull();
        assertThat(response.getData()).isNull();
    }

    @Test
    public void testSuccessWithMessage() {
        BaseResponse response = BaseResponse.success(200L, "Operation completed");
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getRequestId()).isEqualTo(200L);
        assertThat(response.getMessage()).isEqualTo("Operation completed");
    }

    @Test
    public void testSuccessWithData() {
        // 使用 Integer 而不是 String，强制调用 success(long, Object) 重载
        BaseResponse response = BaseResponse.success(300L, (Object) Integer.valueOf(123));
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getRequestId()).isEqualTo(300L);
        assertThat(response.getData()).isEqualTo(123);
    }

    @Test
    public void testFailStaticMethod() {
        BaseResponse response = BaseResponse.fail(400L, "Error occurred");
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getRequestId()).isEqualTo(400L);
        assertThat(response.getMessage()).isEqualTo("Error occurred");
    }

    @Test
    public void testSerializeAndDeserialize() {
        BaseResponse response = BaseResponse.success(500L, "test data");
        String json = JSON.toJSONString(response);

        BaseResponse deserialized = JSON.parseObject(json, BaseResponse.class);
        assertThat(deserialized.isSuccess()).isTrue();
        assertThat(deserialized.getRequestId()).isEqualTo(500L);
        assertThat(deserialized.getMessage()).isEqualTo("test data");
    }

    @Test
    public void testDeserializeFromJson() {
        String json = "{\"success\":true,\"requestId\":123,\"message\":\"ok\",\"data\":null}";
        BaseResponse response = JSON.parseObject(json, BaseResponse.class);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getRequestId()).isEqualTo(123L);
        assertThat(response.getMessage()).isEqualTo("ok");
    }
}
