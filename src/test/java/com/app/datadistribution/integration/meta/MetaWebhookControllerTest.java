package com.app.datadistribution.integration.meta;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.app.datadistribution.integration.meta.config.MetaProperties;
import com.app.datadistribution.integration.meta.controller.MetaWebhookController;
import com.app.datadistribution.integration.meta.dto.MetaWebhookPayloadDTO;
import com.app.datadistribution.integration.meta.entity.MetaWebhookEvent;
import com.app.datadistribution.integration.meta.service.MetaSignatureValidator;
import com.app.datadistribution.integration.meta.service.interfaces.IMetaLeadIngestionService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetaWebhookControllerTest {

    @Mock
    private MetaProperties metaProperties;

    @Mock
    private MetaSignatureValidator signatureValidator;

    @Mock
    private IMetaLeadIngestionService leadIngestionService;

    @InjectMocks
    private MetaWebhookController controller;

    private static final String VERIFY_TOKEN = "test_verify_token_123";
    private static final String APP_SECRET = "test_app_secret_456";

    @BeforeEach
    void setUp() {
    }

    @Test
    @DisplayName("Verification Handshake: Should return challenge on valid mode and token")
    void shouldReturnChallengeWhenVerificationMatches() {
        when(metaProperties.isEnabled()).thenReturn(true);
        when(metaProperties.getVerifyToken()).thenReturn(VERIFY_TOKEN);

        ResponseEntity<String> response = controller.verifyWebhook("subscribe", VERIFY_TOKEN, "11582012");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("11582012", response.getBody());
    }

    @Test
    @DisplayName("Verification Handshake: Should return 403 when token does not match")
    void shouldReturn403WhenTokenMismatch() {
        when(metaProperties.isEnabled()).thenReturn(true);
        when(metaProperties.getVerifyToken()).thenReturn(VERIFY_TOKEN);

        ResponseEntity<String> response = controller.verifyWebhook("subscribe", "wrong_token", "11582012");

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("Verification token mismatch", response.getBody());
    }

    @Test
    @DisplayName("Verification Handshake: Should return 403 when mode is not subscribe")
    void shouldReturn403WhenModeIsNotSubscribe() {
        when(metaProperties.isEnabled()).thenReturn(true);
        when(metaProperties.getVerifyToken()).thenReturn(VERIFY_TOKEN);

        ResponseEntity<String> response = controller.verifyWebhook("other_mode", VERIFY_TOKEN, "11582012");

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("Verification token mismatch", response.getBody());
    }

    @Test
    @DisplayName("Webhook Event: Should accept valid payload and signature, then record event")
    void shouldAcceptValidWebhookEvent() {
        when(metaProperties.isEnabled()).thenReturn(true);
        String payloadJson = "{"
                + "\"object\":\"page\","
                + "\"entry\":[{"
                + "  \"id\":\"1001\","
                + "  \"time\":1718000000,"
                + "  \"changes\":[{"
                + "    \"field\":\"leadgen\","
                + "    \"value\":{"
                + "      \"leadgen_id\":\"9990001\","
                + "      \"page_id\":\"1001\","
                + "      \"form_id\":\"2002\","
                + "      \"ad_id\":\"3003\""
                + "    }"
                + "  }]"
                + "}]"
                + "}";
        byte[] payloadBytes = payloadJson.getBytes(StandardCharsets.UTF_8);
        String signature = "sha256=validsig";

        when(metaProperties.getAppSecret()).thenReturn(APP_SECRET);
        when(signatureValidator.isValidSignature(payloadBytes, signature, APP_SECRET)).thenReturn(true);

        UUID savedEventId = UUID.randomUUID();
        MetaWebhookEvent savedEvent = MetaWebhookEvent.builder().leadgenId("9990001").build();
        savedEvent.setId(savedEventId);

        when(leadIngestionService.recordWebhookEvent(any(MetaWebhookPayloadDTO.Value.class), anyString()))
                .thenReturn(savedEvent);

        ResponseEntity<String> response = controller.handleWebhookEvent(signature, payloadBytes);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("EVENT_RECEIVED", response.getBody());

        verify(leadIngestionService).recordWebhookEvent(any(MetaWebhookPayloadDTO.Value.class), eq(payloadJson));
        verify(leadIngestionService).processEventAsync(eq(savedEventId));
    }

    @Test
    @DisplayName("Webhook Event: Should reject event when signature is invalid")
    void shouldRejectInvalidSignature() {
        when(metaProperties.isEnabled()).thenReturn(true);
        byte[] payloadBytes = "{}".getBytes(StandardCharsets.UTF_8);
        String signature = "sha256=invalidsig";

        when(metaProperties.getAppSecret()).thenReturn(APP_SECRET);
        when(signatureValidator.isValidSignature(payloadBytes, signature, APP_SECRET)).thenReturn(false);

        ResponseEntity<String> response = controller.handleWebhookEvent(signature, payloadBytes);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Invalid signature", response.getBody());

        verify(leadIngestionService, never()).recordWebhookEvent(any(), any());
        verify(leadIngestionService, never()).processEventAsync(any());
    }

    @Test
    @DisplayName("Webhook Event: Should return 400 for malformed JSON payload")
    void shouldReturn400ForMalformedJson() {
        when(metaProperties.isEnabled()).thenReturn(true);
        byte[] malformedBytes = "not a valid json".getBytes(StandardCharsets.UTF_8);
        String signature = "sha256=validsig";

        when(metaProperties.getAppSecret()).thenReturn(APP_SECRET);
        when(signatureValidator.isValidSignature(malformedBytes, signature, APP_SECRET)).thenReturn(true);

        ResponseEntity<String> response = controller.handleWebhookEvent(signature, malformedBytes);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Malformed JSON payload", response.getBody());
    }

    @Test
    @DisplayName("Admin Retry: Should invoke retryEvent on single event ID")
    void shouldInvokeRetryEventOnController() {
        UUID eventId = UUID.randomUUID();
        when(leadIngestionService.retryEvent(eventId)).thenReturn(true);

        ResponseEntity<String> response = controller.retryEvent(eventId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("RETRY_TRIGGERED", response.getBody());
        verify(leadIngestionService).retryEvent(eventId);
    }

    @Test
    @DisplayName("Admin Bulk Retry: Should invoke retryFailedEvents")
    void shouldInvokeRetryFailedEventsOnController() {
        when(leadIngestionService.retryFailedEvents()).thenReturn(3);

        ResponseEntity<String> response = controller.retryFailedEvents();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Retried 3 events", response.getBody());
        verify(leadIngestionService).retryFailedEvents();
    }
}
