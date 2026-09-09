package com.app.datadistribution.config;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;

@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter LOCAL_DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jackson2ObjectMapperBuilderCustomizer() {
        return builder -> {
            builder.deserializerByType(LocalDateTime.class, new FlexibleLocalDateTimeDeserializer());
            builder.serializerByType(LocalDateTime.class, new LocalDateTimeSerializer(LOCAL_DATETIME_FORMATTER));
            builder.timeZone("Asia/Kolkata");
        };
    }
}
