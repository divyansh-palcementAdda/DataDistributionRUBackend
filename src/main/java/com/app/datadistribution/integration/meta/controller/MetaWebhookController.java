package com.app.datadistribution.integration.meta.controller;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.app.datadistribution.integration.meta.config.MetaProperties;
import com.app.datadistribution.integration.meta.dto.MetaWebhookPayloadDTO;
import com.app.datadistribution.integration.meta.entity.MetaWebhookEvent;
import com.app.datadistribution.integration.meta.service.MetaSignatureValidator;
import com.app.datadistribution.integration.meta.service.interfaces.IMetaLeadIngestionService;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/webhooks/meta")
@RequiredArgsConstructor
@Tag(name = "Meta Lead Ads Webhook", description = "Public Webhook endpoints for Meta (Facebook & Instagram) Lead Ads integration")
public class MetaWebhookController {

    private final MetaProperties metaProperties;
    private final MetaSignatureValidator signatureValidator;
    private final IMetaLeadIngestionService leadIngestionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Webhook Verification Endpoint (GET).
     * Invoked by Meta when setting up or verifying the Webhook subscription.
     */
    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "Verify Meta webhook subscription")
    public ResponseEntity<String> verifyWebhook(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String verifyToken,
            @RequestParam(name = "hub.challenge", required = false) String challenge) {

        log.info("Received Meta webhook verification handshake. mode: {}", mode);

        if (!metaProperties.isEnabled()) {
            log.warn("Meta integration is disabled. Rejecting verification request.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Meta integration disabled");
        }

        String expectedToken = metaProperties.getVerifyToken();
        if ("subscribe".equalsIgnoreCase(mode) && expectedToken != null && expectedToken.equals(verifyToken)) {
            log.info("Meta webhook verification succeeded. Returning challenge.");
            return ResponseEntity.ok(challenge);
        } else {
            log.warn("Meta webhook verification failed: Invalid mode or token mismatch.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Verification token mismatch");
        }
    }

    /**
     * Webhook Event Notification Receiver (POST).
     * Invoked by Meta in real time whenever a user submits an Instant Form on Facebook or Instagram.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Receive Meta leadgen webhook notifications")
    public ResponseEntity<String> handleWebhookEvent(
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signatureHeader,
            @RequestBody byte[] rawPayloadBytes) {

        if (!metaProperties.isEnabled()) {
            log.warn("Meta integration is disabled. Ignoring incoming webhook event.");
            return ResponseEntity.ok("DISABLED");
        }

        // 1. Verify Meta HMAC-SHA256 signature
        boolean isValidSignature = signatureValidator.isValidSignature(
                rawPayloadBytes,
                signatureHeader,
                metaProperties.getAppSecret()
        );

        if (!isValidSignature) {
            log.warn("Meta webhook event rejected: Invalid or missing X-Hub-Signature-256");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid signature");
        }

        String rawPayload = new String(rawPayloadBytes, StandardCharsets.UTF_8);

        // 2. Parse and process payload
        try {
            MetaWebhookPayloadDTO payload = objectMapper.readValue(rawPayload, MetaWebhookPayloadDTO.class);

            if ("page".equalsIgnoreCase(payload.getObject()) && payload.getEntry() != null) {
                for (MetaWebhookPayloadDTO.Entry entry : payload.getEntry()) {
                    if (entry.getChanges() != null) {
                        for (MetaWebhookPayloadDTO.Change change : entry.getChanges()) {
                            if ("leadgen".equalsIgnoreCase(change.getField()) && change.getValue() != null) {
                                MetaWebhookPayloadDTO.Value val = change.getValue();

                                // Optional Page ID filtering if configured
                                if (metaProperties.getPageId() != null && !metaProperties.getPageId().isBlank()) {
                                    if (val.getPageId() != null && !metaProperties.getPageId().equals(val.getPageId())) {
                                        log.info("Ignoring leadgen event from unconfigured page_id: {}", val.getPageId());
                                        continue;
                                    }
                                }

                                // 3. Durably persist event in MySQL
                                MetaWebhookEvent savedEvent = leadIngestionService.recordWebhookEvent(val, rawPayload);

                                // 4. Trigger asynchronous processing
                                leadIngestionService.processEventAsync(savedEvent.getId());
                            }
                        }
                    }
                }
            }

            // Return 200 OK promptly to Meta
            return ResponseEntity.ok("EVENT_RECEIVED");

        } catch (Exception e) {
            log.error("Failed to parse Meta webhook payload", e);
            // Even if payload parsing fails, return 200 or 400 depending on malformed JSON
            return ResponseEntity.badRequest().body("Malformed JSON payload");
        }
    }

    /**
     * Administrative endpoint to manually reprocess a failed Meta webhook event.
     */
    @PostMapping("/events/{id}/retry")
    @Operation(summary = "Administratively retry a Meta webhook event by ID")
    public ResponseEntity<String> retryEvent(@PathVariable("id") UUID id) {
        boolean retried = leadIngestionService.retryEvent(id);
        if (retried) {
            return ResponseEntity.ok("RETRY_TRIGGERED");
        } else {
            return ResponseEntity.badRequest().body("Event not found or already processed");
        }
    }

    /**
     * Administrative endpoint to reprocess all failed Meta webhook events.
     */
    @PostMapping("/events/retry-failed")
    @Operation(summary = "Administratively retry all failed Meta webhook events")
    public ResponseEntity<String> retryFailedEvents() {
        int count = leadIngestionService.retryFailedEvents();
        return ResponseEntity.ok("Retried " + count + " events");
    }
}
