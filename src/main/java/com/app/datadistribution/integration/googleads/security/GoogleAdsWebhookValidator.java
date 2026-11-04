package com.app.datadistribution.integration.googleads.security;

import com.app.datadistribution.integration.googleads.config.GoogleAdsIntegrationProperties;
import com.app.datadistribution.integration.googleads.exception.GoogleAdsAuthException;
import com.app.datadistribution.integration.googleads.exception.GoogleAdsDisabledException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
@RequiredArgsConstructor
@Slf4j
public class GoogleAdsWebhookValidator {

    private final GoogleAdsIntegrationProperties properties;

    /**
     * Validates incoming google_key secret from payload using constant-time comparison.
     * Fails closed if integration is disabled or configured webhook key is missing.
     *
     * @param providedKey The secret string sent in payload "google_key"
     * @throws GoogleAdsDisabledException if integration is disabled (HTTP 503)
     * @throws GoogleAdsAuthException if key is missing, server key is unconfigured, or mismatch (HTTP 401)
     */
    public void validate(String providedKey) {
        if (!properties.isEnabled()) {
            log.warn("Google Ads webhook rejected: Integration is disabled in server configuration");
            throw new GoogleAdsDisabledException("Google Ads lead integration is currently disabled");
        }

        String configuredKey = properties.getWebhookKey();
        if (configuredKey == null || configuredKey.trim().isBlank()) {
            log.error("Google Ads webhook key is not configured on the server (failing closed)");
            throw new GoogleAdsAuthException("Google Ads webhook integration is unconfigured or unavailable");
        }

        if (providedKey == null || providedKey.trim().isBlank()) {
            log.warn("Google Ads webhook rejected: Missing google_key in request payload");
            throw new GoogleAdsAuthException("Missing google_key in request payload");
        }

        byte[] expectedBytes = configuredKey.trim().getBytes(StandardCharsets.UTF_8);
        byte[] providedBytes = providedKey.trim().getBytes(StandardCharsets.UTF_8);

        // Constant-time comparison to prevent timing attacks
        if (!MessageDigest.isEqual(expectedBytes, providedBytes)) {
            log.warn("Google Ads webhook rejected: Invalid google_key provided");
            throw new GoogleAdsAuthException("Invalid google_key provided");
        }
    }
}

