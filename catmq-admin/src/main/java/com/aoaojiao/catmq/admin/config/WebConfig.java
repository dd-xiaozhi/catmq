package com.aoaojiao.catmq.admin.config;

import com.aoaojiao.catmq.admin.dto.request.TopicCreateRequest;
import com.aoaojiao.catmq.admin.dto.response.*;
import com.aoaojiao.catmq.admin.model.AlertRecord;
import com.aoaojiao.catmq.admin.model.AlertRule;
import com.aoaojiao.catmq.common.model.BrokerInfo;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;

/**
 * Web 配置
 *
 * @author DD
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        // 注册常用类型的序列化器
        List<Class<?>> supportedTypes = new ArrayList<>();
        supportedTypes.add(ApiResponse.class);
        supportedTypes.add(TopicResponse.class);
        supportedTypes.add(TopicCreateRequest.class);
        supportedTypes.add(BrokerStatusResponse.class);
        supportedTypes.add(MetricsResponse.class);
        supportedTypes.add(ConsumerGroupResponse.class);
        supportedTypes.add(MessageResponse.class);
        supportedTypes.add(HealthCheckResponse.class);
        supportedTypes.add(AlertRule.class);
        supportedTypes.add(AlertRecord.class);
        supportedTypes.add(BrokerInfo.class);

        // 使用 Jackson 作为默认 JSON 转换器
        MappingJackson2HttpMessageConverter jacksonConverter = new MappingJackson2HttpMessageConverter();
        converters.add(0, jacksonConverter);
    }
}