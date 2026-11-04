package com.app.datadistribution.integration.meta.service.interfaces;

import com.app.datadistribution.integration.meta.dto.MetaLeadResponseDTO;

public interface IMetaGraphApiService {

    /**
     * Retrieves the lead details from Meta Graph API using the leadgen_id.
     *
     * @param leadgenId The Meta leadgen ID received in the webhook event
     * @return MetaLeadResponseDTO containing form field data, or null/exception if retrieval failed
     */
    MetaLeadResponseDTO retrieveLead(String leadgenId);

    /**
     * Retrieves the campaign name from Meta Graph API using the campaignId or adId.
     * Uses an in-memory Caffeine cache to avoid duplicate API requests.
     * Fails safely by returning null if unavailable (does not fail lead ingestion).
     *
     * @param campaignId Optional Meta campaign ID
     * @param adId Optional Meta ad ID
     * @return The retrieved campaign name, or null if unavailable
     */
    String retrieveCampaignName(String campaignId, String adId);
}
