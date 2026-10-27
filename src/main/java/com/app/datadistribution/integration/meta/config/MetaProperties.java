package com.app.datadistribution.integration.meta.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

@Configuration
@ConfigurationProperties(prefix = "app.meta")
@Getter
@Setter
public class MetaProperties {

    /**
     * Whether Meta Lead Ads integration is enabled.
     */
    private boolean enabled = true;

    /**
     * Meta App ID.
     */
    private String appId;

    /**
     * Meta App Secret used for verifying webhook HMAC signatures (X-Hub-Signature-256).
     */
    private String appSecret;

    /**
     * Configurable token for Webhook verification handshake (hub.verify_token).
     */
    private String verifyToken = "dds_meta_verify_token_default";

    /**
     * System user or Page Access Token used to retrieve lead details via Graph API.
     */
    private String pageAccessToken;

    /**
     * Meta Graph API base version (e.g. v21.0).
     */
    private String graphApiVersion = "v21.0";

    /**
     * Graph API base URL.
     */
    private String graphApiBaseUrl = "https://graph.facebook.com";

    /**
     * Authorized Facebook Page ID (optional filter/validation).
     */
    private String pageId;

    /**
     * Username of the system user under which imported leads are created in DDS.
     */
    private String systemUsername = "superadmin";

    /**
     * Maximum retry attempts for failed Graph API / lead ingestion events.
     */
    private int maxRetries = 5;

    /**
     * Retry cron expression. Default every 2 minutes.
     */
    private String retryCron = "0 */2 * * * *";
}
