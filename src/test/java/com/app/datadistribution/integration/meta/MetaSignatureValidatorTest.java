package com.app.datadistribution.integration.meta;

import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.app.datadistribution.integration.meta.service.MetaSignatureValidator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetaSignatureValidatorTest {

    private MetaSignatureValidator validator;
    private final String appSecret = "test_meta_app_secret_12345";

    @BeforeEach
    void setUp() {
        validator = new MetaSignatureValidator();
    }

    private String calculateExpectedSignature(byte[] payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(keySpec);
        byte[] hmac = mac.doFinal(payload);
        StringBuilder sb = new StringBuilder(hmac.length * 2);
        for (byte b : hmac) {
            sb.append(String.format("%02x", b));
        }
        return "sha256=" + sb.toString();
    }

    @Test
    @DisplayName("Should accept valid signature with sha256= prefix")
    void shouldAcceptValidSignature() throws Exception {
        byte[] payload = "{\"object\":\"page\",\"entry\":[]}".getBytes(StandardCharsets.UTF_8);
        String signature = calculateExpectedSignature(payload, appSecret);

        boolean result = validator.isValidSignature(payload, signature, appSecret);
        assertTrue(result);
    }

    @Test
    @DisplayName("Should accept valid signature without prefix")
    void shouldAcceptValidSignatureWithoutPrefix() throws Exception {
        byte[] payload = "{\"object\":\"page\",\"entry\":[]}".getBytes(StandardCharsets.UTF_8);
        String signature = calculateExpectedSignature(payload, appSecret).replace("sha256=", "");

        boolean result = validator.isValidSignature(payload, signature, appSecret);
        assertTrue(result);
    }

    @Test
    @DisplayName("Should reject tampered payload")
    void shouldRejectTamperedPayload() throws Exception {
        byte[] payload = "{\"object\":\"page\",\"entry\":[]}".getBytes(StandardCharsets.UTF_8);
        String signature = calculateExpectedSignature(payload, appSecret);

        byte[] tampered = "{\"object\":\"page\",\"entry\":[{\"tampered\":true}]}".getBytes(StandardCharsets.UTF_8);
        boolean result = validator.isValidSignature(tampered, signature, appSecret);
        assertFalse(result);
    }

    @Test
    @DisplayName("Should reject invalid or mismatched signature")
    void shouldRejectInvalidSignature() {
        byte[] payload = "{\"object\":\"page\",\"entry\":[]}".getBytes(StandardCharsets.UTF_8);
        String invalidSignature = "sha256=abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";

        boolean result = validator.isValidSignature(payload, invalidSignature, appSecret);
        assertFalse(result);
    }

    @Test
    @DisplayName("Should reject when signature header is null or blank")
    void shouldRejectWhenSignatureHeaderMissing() {
        byte[] payload = "{}".getBytes(StandardCharsets.UTF_8);
        assertFalse(validator.isValidSignature(payload, null, appSecret));
        assertFalse(validator.isValidSignature(payload, "   ", appSecret));
    }

    @Test
    @DisplayName("Should reject when app secret is null or blank")
    void shouldRejectWhenAppSecretMissing() {
        byte[] payload = "{}".getBytes(StandardCharsets.UTF_8);
        assertFalse(validator.isValidSignature(payload, "sha256=123456", null));
        assertFalse(validator.isValidSignature(payload, "sha256=123456", "   "));
    }
}
