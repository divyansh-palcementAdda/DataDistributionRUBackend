package com.app.datadistribution.integration.meta.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MetaSignatureValidator {

    private static final String HMAC_SHA256_ALGORITHM = "HmacSHA256";
    private static final String SHA256_PREFIX = "sha256=";

    /**
     * Validates the Meta X-Hub-Signature-256 header against the raw request body bytes and app secret.
     *
     * @param rawBodyBytes Raw HTTP request body bytes
     * @param signatureHeader Received X-Hub-Signature-256 header value
     * @param appSecret Configured Meta App Secret
     * @return true if signature is valid, false otherwise
     */
    public boolean isValidSignature(byte[] rawBodyBytes, String signatureHeader, String appSecret) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            log.warn("Meta signature validation failed: Missing X-Hub-Signature-256 header");
            return false;
        }

        if (appSecret == null || appSecret.isBlank()) {
            log.error("Meta signature validation failed: App secret is not configured");
            return false;
        }

        if (rawBodyBytes == null) {
            rawBodyBytes = new byte[0];
        }

        String providedSignature = signatureHeader.trim();
        if (providedSignature.startsWith(SHA256_PREFIX)) {
            providedSignature = providedSignature.substring(SHA256_PREFIX.length());
        }

        try {
            Mac mac = Mac.getInstance(HMAC_SHA256_ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256_ALGORITHM);
            mac.init(secretKeySpec);

            byte[] calculatedHmacBytes = mac.doFinal(rawBodyBytes);
            String calculatedHex = bytesToHex(calculatedHmacBytes);

            // Constant-time comparison to prevent timing attacks
            boolean matches = MessageDigest.isEqual(
                    calculatedHex.getBytes(StandardCharsets.UTF_8),
                    providedSignature.toLowerCase().getBytes(StandardCharsets.UTF_8)
            );

            if (!matches) {
                log.warn("Meta signature validation failed: Signature mismatch");
            }
            return matches;
        } catch (Exception e) {
            log.error("Meta signature validation error", e);
            return false;
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
