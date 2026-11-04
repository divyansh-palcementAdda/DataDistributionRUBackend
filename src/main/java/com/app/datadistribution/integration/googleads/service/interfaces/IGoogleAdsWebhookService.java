package com.app.datadistribution.integration.googleads.service.interfaces;

import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.integration.googleads.dto.GoogleAdsWebhookRequest;

public interface IGoogleAdsWebhookService {

    /**
     * Ingests an incoming Google Ads Lead Form webhook payload.
     * Handles secret validation, test leads, duplicate idempotent re-deliveries,
     * canonical lead matching and enrichment, and new lead creation.
     *
     * @param request Parsed request payload
     * @param rawPayload Raw JSON payload string for auditing (with secret masked)
     * @throws BadRequestException on invalid or malformed data
     */
    void processWebhook(GoogleAdsWebhookRequest request, String rawPayload) throws BadRequestException;
}

