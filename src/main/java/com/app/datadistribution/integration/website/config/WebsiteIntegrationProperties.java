package com.app.datadistribution.integration.website.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.integrations.website")
@Getter
@Setter
public class WebsiteIntegrationProperties {

    /**
     * Whether the university website leads integration endpoint is active.
     */
    private boolean enabled = true;

    /**
     * Dedicated secret API key for website submissions.
     * Must be provided via X-Website-API-Key header.
     */
    private String apiKey;

    /**
     * DDS username to attribute for audit logs (defaults to "superadmin").
     */
    private String systemUsername = "superadmin";

    /**
     * Max requests allowed per minute per client IP.
     */
    private int rateLimitPerMinute = 60;

    /**
     * Burst request allowance above rate limit.
     */
    private int rateLimitBurst = 10;
}
