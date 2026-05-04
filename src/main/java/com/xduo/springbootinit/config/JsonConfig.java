package com.xduo.springbootinit.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.jackson.JsonComponent;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.text.SimpleDateFormat;
import java.util.TimeZone;

/**
 * Spring MVC Json 配置

 */
@JsonComponent
public class JsonConfig {

    private static final TimeZone ASIA_SHANGHAI_TIME_ZONE = TimeZone.getTimeZone("Asia/Shanghai");
    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd'T'HH:mm:ssXXX";

    /**
     * 添加 Long 转 json 精度丢失的配置
     */
    @Bean
    public ObjectMapper jacksonObjectMapper(Jackson2ObjectMapperBuilder builder) {
        builder.timeZone(ASIA_SHANGHAI_TIME_ZONE);
        ObjectMapper objectMapper = builder.createXmlMapper(false).build();
        SimpleModule module = new SimpleModule();
        module.addSerializer(Long.class, ToStringSerializer.instance);
        module.addSerializer(Long.TYPE, ToStringSerializer.instance);
        objectMapper.registerModule(module);
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.setTimeZone(ASIA_SHANGHAI_TIME_ZONE);
        SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_TIME_PATTERN);
        dateFormat.setTimeZone(ASIA_SHANGHAI_TIME_ZONE);
        objectMapper.setDateFormat(dateFormat);
        return objectMapper;
    }
}
