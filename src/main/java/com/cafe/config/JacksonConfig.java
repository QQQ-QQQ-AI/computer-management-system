package com.cafe.config;

import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Jackson 序列化配置。
 *
 * 不配会有两个问题：
 * 1. LocalDateTime 默认按 ISO-8601 输出成 "2026-09-28T14:01:19"，前端展示要额外处理；
 * 2. 时区默认取 JVM 默认时区，容器或服务器时区不一致时，传给 ECharts 的日期会整体偏移一天。
 *
 * 这里统一固定为东八区、yyyy-MM-dd HH:mm:ss，保证页面显示与图表时间轴一致。
 */
@Configuration
public class JacksonConfig {

    public static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    public static final String DATE_PATTERN = "yyyy-MM-dd";

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        DateTimeFormatter dateTime = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);
        DateTimeFormatter date = DateTimeFormatter.ofPattern(DATE_PATTERN);

        return builder -> {
            builder.simpleDateFormat(DATE_TIME_PATTERN);
            builder.timeZone(java.util.TimeZone.getTimeZone("Asia/Shanghai"));
            builder.serializers(
                    new LocalDateTimeSerializer(dateTime),
                    new LocalDateSerializer(date));
            builder.deserializers(
                    new LocalDateTimeDeserializer(dateTime),
                    new LocalDateDeserializer(date));
        };
    }
}
