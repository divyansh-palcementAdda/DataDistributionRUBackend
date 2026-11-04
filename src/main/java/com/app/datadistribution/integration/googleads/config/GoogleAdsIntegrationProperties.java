package com.app.datadistribution.integration.googleads.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.integrations.google-ads")
@Getter
@Setter
public class GoogleAdsIntegrationProperties {

    /**
     * Whether the native Google Ads Lead Form webhook integration is enabled.
     * Defaults to false in production until explicitly activated via env.
     */
    private boolean enabled = false;

    /**
     * Dedicated secret webhook key configured in Google Ads (up to 50 characters).
     * Must match the 'google_key' sent in incoming webhook payloads.
     */
    private String webhookKey;

    /**
     * DDS username to attribute for audit logs (defaults to "superadmin").
     */
    private String systemUsername = "superadmin";
}

