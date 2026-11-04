package com.app.datadistribution.integration.googleads.controller;

import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.integration.googleads.dto.GoogleAdsWebhookRequest;
import com.app.datadistribution.integration.googleads.exception.GoogleAdsAuthException;
import com.app.datadistribution.integration.googleads.exception.GoogleAdsDisabledException;
import com.app.datadistribution.integration.googleads.service.interfaces.IGoogleAdsWebhookService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/integrations/google-ads")
@RequiredArgsConstructor
@Tag(name = "Google Ads Lead Form Webhook", description = "Native Webhook receiver for Google Ads Lead Form submissions")
public class GoogleAdsWebhookController {

    private final IGoogleAdsWebhookService webhookService;
    private final ObjectMapper objectMapper;

    @PostMapping(value = "/leads", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
        summary = "Receive Google Ads Lead Form Submission",
        description = "Public webhook endpoint for Google Ads Lead Forms. Authenticated via 'google_key' payload secret."
    )
    public ResponseEntity<Map<String, Object>> handleWebhook(@RequestBody(required = false) byte[] rawPayloadBytes) {
        if (rawPayloadBytes == null || rawPayloadBytes.length == 0) {
            log.warn("Google Ads webhook request rejected: empty body");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Collections.emptyMap());
        }

        String rawJson = new String(rawPayloadBytes, StandardCharsets.UTF_8);
        GoogleAdsWebhookRequest request;
        try {
            request = objectMapper.readValue(rawJson, GoogleAdsWebhookRequest.class);
        } catch (Exception e) {
            log.warn("Google Ads webhook rejected: malformed JSON payload ({})", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Collections.emptyMap());
        }

        try {
            webhookService.processWebhook(request, rawJson);
            return ResponseEntity.ok(Collections.emptyMap());
        } catch (GoogleAdsDisabledException e) {
            log.warn("Google Ads webhook rejected: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Collections.emptyMap());
        } catch (GoogleAdsAuthException e) {
            log.warn("Google Ads webhook authentication failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Collections.emptyMap());
        } catch (BadRequestException e) {
            log.warn("Google Ads webhook payload validation failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Collections.emptyMap());
        } catch (Exception e) {
            log.error("Unexpected error processing Google Ads webhook lead", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Collections.emptyMap());
        }
    }
}

