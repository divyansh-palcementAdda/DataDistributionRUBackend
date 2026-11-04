package com.app.datadistribution.integration.googleads;

import com.app.datadistribution.integration.googleads.config.GoogleAdsIntegrationProperties;
import com.app.datadistribution.integration.googleads.exception.GoogleAdsAuthException;
import com.app.datadistribution.integration.googleads.exception.GoogleAdsDisabledException;
import com.app.datadistribution.integration.googleads.security.GoogleAdsWebhookValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GoogleAdsWebhookValidatorTest {

    private GoogleAdsIntegrationProperties properties;
    private GoogleAdsWebhookValidator validator;

    private static final String VALID_50_CHAR_SECRET = "abcdefghijklmnopqrstuvwxyz1234567890ABCDEFGHIJKLMN";

    @BeforeEach
    void setUp() {
        properties = new GoogleAdsIntegrationProperties();
        validator = new GoogleAdsWebhookValidator(properties);
    }

    @Test
    @DisplayName("Should pass when integration is enabled and exact 50-character key matches")
    void shouldPassWhenValidKeyMatches() {
        properties.setEnabled(true);
        properties.setWebhookKey(VALID_50_CHAR_SECRET);

        assertDoesNotThrow(() -> validator.validate(VALID_50_CHAR_SECRET));
    }

    @Test
    @DisplayName("Should throw GoogleAdsDisabledException (503) when integration is disabled")
    void shouldThrowWhenDisabled() {
        properties.setEnabled(false);
        properties.setWebhookKey(VALID_50_CHAR_SECRET);

        assertThrows(GoogleAdsDisabledException.class, () -> validator.validate(VALID_50_CHAR_SECRET));
    }

    @Test
    @DisplayName("Should throw GoogleAdsAuthException (401) when server secret is not configured")
    void shouldThrowWhenServerSecretNullOrEmpty() {
        properties.setEnabled(true);
        properties.setWebhookKey(null);

        assertThrows(GoogleAdsAuthException.class, () -> validator.validate(VALID_50_CHAR_SECRET));

        properties.setWebhookKey("   ");
        assertThrows(GoogleAdsAuthException.class, () -> validator.validate(VALID_50_CHAR_SECRET));
    }

    @Test
    @DisplayName("Should throw GoogleAdsAuthException (401) when incoming key is null or empty")
    void shouldThrowWhenIncomingKeyNullOrEmpty() {
        properties.setEnabled(true);
        properties.setWebhookKey(VALID_50_CHAR_SECRET);

        assertThrows(GoogleAdsAuthException.class, () -> validator.validate(null));
        assertThrows(GoogleAdsAuthException.class, () -> validator.validate(""));
        assertThrows(GoogleAdsAuthException.class, () -> validator.validate("   "));
    }

    @Test
    @DisplayName("Should throw GoogleAdsAuthException (401) on secret key mismatch")
    void shouldThrowOnKeyMismatch() {
        properties.setEnabled(true);
        properties.setWebhookKey(VALID_50_CHAR_SECRET);

        assertThrows(GoogleAdsAuthException.class, () -> validator.validate("wrong_secret_key_1234567890abcdefghijklmnopqrstuv"));
    }
}

