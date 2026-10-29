package com.enterprise.procurement.config;

import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.format.DateTimeFormatter;

/**
 * 统一 LocalDateTime 的 JSON 格式。
 *
 * <p>背景：{@code spring.jackson.date-format} 只作用于 {@link java.util.Date}，
 * 对 {@code java.time.LocalDateTime} 无效，默认会输出 ISO-8601（例如 2026-10-29T19:00:00），
 * 而前端页面按 {@code yyyy-MM-dd HH:mm:ss} 展示。之前所有时间字段在页面上都带一个 "T"，
 * 属于前后端契约不一致的细节。这里统一在序列化层收敛，避免每个 DTO 都挂 {@code @JsonFormat}。</p>
 */
@Configuration
public class JacksonConfig {

    public static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer localDateTimeCustomizer() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);
        return builder -> builder
                .serializers(new LocalDateTimeSerializer(formatter))
                .deserializers(new LocalDateTimeDeserializer(formatter));
    }
}
