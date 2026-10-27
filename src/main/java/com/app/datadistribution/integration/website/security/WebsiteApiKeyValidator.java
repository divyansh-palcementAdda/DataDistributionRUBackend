package com.app.datadistribution.integration.website.security;

import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.integration.website.config.WebsiteIntegrationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebsiteApiKeyValidator {

    public static final String API_KEY_HEADER = "X-Website-API-Key";

    private final WebsiteIntegrationProperties properties;

    /**
     * Validates the incoming API key header against configured secret using constant-time comparison.
     * Fails closed if integration is disabled or server secret is missing/blank.
     *
     * @param providedApiKey The value of X-Website-API-Key header.
     * @throws UnauthorizedException if authentication fails.
     */
    public void validate(String providedApiKey) throws UnauthorizedException {
        if (!properties.isEnabled()) {
            log.warn("Website integration is currently disabled in server configuration");
            throw new UnauthorizedException("Website leads integration is disabled");
        }

        String configuredApiKey = properties.getApiKey();
        if (configuredApiKey == null || configuredApiKey.trim().isBlank()) {
            log.error("Website integration API key is not configured on the server (failing closed)");
            throw new UnauthorizedException("Website leads integration is unconfigured or unavailable");
        }

        if (providedApiKey == null || providedApiKey.trim().isBlank()) {
            log.warn("Website integration request rejected: missing {} header", API_KEY_HEADER);
            throw new UnauthorizedException("Missing " + API_KEY_HEADER + " header");
        }

        byte[] expectedBytes = configuredApiKey.trim().getBytes(StandardCharsets.UTF_8);
        byte[] providedBytes = providedApiKey.trim().getBytes(StandardCharsets.UTF_8);

        // Constant-time comparison to prevent timing attacks
        if (!MessageDigest.isEqual(expectedBytes, providedBytes)) {
            log.warn("Website integration request rejected: invalid API key provided");
            throw new UnauthorizedException("Invalid Website Integration API Key");
        }
    }
}
