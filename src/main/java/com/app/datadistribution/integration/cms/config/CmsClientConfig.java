package com.app.datadistribution.integration.cms.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class CmsClientConfig {

    @Value("${cms.connect-timeout:10000}")
    private int connectTimeout;

    @Value("${cms.read-timeout:15000}")
    private int readTimeout;

    @Bean(name = "cmsRestTemplate")
    public RestTemplate cmsRestTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofMillis(connectTimeout))
                .readTimeout(Duration.ofMillis(readTimeout))
                .build();
    }
}
