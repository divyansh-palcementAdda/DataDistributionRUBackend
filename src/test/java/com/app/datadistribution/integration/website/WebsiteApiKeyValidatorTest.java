package com.app.datadistribution.integration.website;

import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.integration.website.config.WebsiteIntegrationProperties;
import com.app.datadistribution.integration.website.security.WebsiteApiKeyValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebsiteApiKeyValidatorTest {

    @Mock
    private WebsiteIntegrationProperties properties;

    @InjectMocks
    private WebsiteApiKeyValidator validator;

    private static final String VALID_KEY = "test_super_secret_website_api_key_12345";

    @BeforeEach
    void setUp() {
    }

    @Test
    @DisplayName("Valid API key should pass validation without exception")
    void shouldPassWhenApiKeyMatches() {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getApiKey()).thenReturn(VALID_KEY);

        assertDoesNotThrow(() -> validator.validate(VALID_KEY));
    }

    @Test
    @DisplayName("Invalid API key should throw UnauthorizedException")
    void shouldRejectWhenApiKeyIsInvalid() {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getApiKey()).thenReturn(VALID_KEY);

        UnauthorizedException ex = assertThrows(
                UnauthorizedException.class,
                () -> validator.validate("wrong_key_67890")
        );
        assertEquals("Invalid Website Integration API Key", ex.getMessage());
    }

    @Test
    @DisplayName("Missing (null) API key header should throw UnauthorizedException")
    void shouldRejectWhenApiKeyHeaderIsNull() {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getApiKey()).thenReturn(VALID_KEY);

        UnauthorizedException ex = assertThrows(
                UnauthorizedException.class,
                () -> validator.validate(null)
        );
        assertEquals("Missing X-Website-API-Key header", ex.getMessage());
    }

    @Test
    @DisplayName("Empty or whitespace API key header should throw UnauthorizedException")
    void shouldRejectWhenApiKeyHeaderIsBlank() {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getApiKey()).thenReturn(VALID_KEY);

        UnauthorizedException ex = assertThrows(
                UnauthorizedException.class,
                () -> validator.validate("   ")
        );
        assertEquals("Missing X-Website-API-Key header", ex.getMessage());
    }

    @Test
    @DisplayName("Server property missing/unconfigured should fail closed and throw UnauthorizedException")
    void shouldFailClosedWhenServerApiKeyIsNotConfigured() {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getApiKey()).thenReturn(null);

        UnauthorizedException ex = assertThrows(
                UnauthorizedException.class,
                () -> validator.validate(VALID_KEY)
        );
        assertEquals("Website leads integration is unconfigured or unavailable", ex.getMessage());
    }

    @Test
    @DisplayName("Integration disabled in properties should throw UnauthorizedException")
    void shouldRejectWhenIntegrationIsDisabled() {
        when(properties.isEnabled()).thenReturn(false);

        UnauthorizedException ex = assertThrows(
                UnauthorizedException.class,
                () -> validator.validate(VALID_KEY)
        );
        assertEquals("Website leads integration is disabled", ex.getMessage());
    }
}
