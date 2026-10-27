package com.app.datadistribution.integration.meta.service.interfaces;

import java.util.UUID;

import com.app.datadistribution.integration.meta.dto.MetaWebhookPayloadDTO;
import com.app.datadistribution.integration.meta.entity.MetaWebhookEvent;

public interface IMetaLeadIngestionService {

    /**
     * Persists an incoming webhook event into durable storage with status PENDING.
     * Guaranteed to be idempotent: duplicate leadgen_id will not create duplicate events.
     *
     * @param eventValue Value object containing leadgen_id, page_id, form_id, ad_id
     * @param rawPayload Full raw JSON payload for audit and debug
     * @return Persisted MetaWebhookEvent
     */
    MetaWebhookEvent recordWebhookEvent(MetaWebhookPayloadDTO.Value eventValue, String rawPayload);

    /**
     * Processes a MetaWebhookEvent: retrieves lead details from Graph API,
     * maps fields safely, creates Lead in DDS, and updates event status.
     *
     * @param event The event to process
     */
    void processEvent(MetaWebhookEvent event);

    /**
     * Triggers asynchronous processing of an event by its ID.
     *
     * @param eventId The event ID to process
     */
    void processEventAsync(UUID eventId);

    /**
     * Administratively reprocesses a single event by ID (even if FAILED or FAILED_PERMANENTLY).
     *
     * @param eventId The event ID to retry
     * @return true if retried, false if not found or already PROCESSED
     */
    boolean retryEvent(UUID eventId);

    /**
     * Administratively reprocesses all failed events that do not yet have an associated lead.
     *
     * @return Number of events reprocessed
     */
    int retryFailedEvents();
}
