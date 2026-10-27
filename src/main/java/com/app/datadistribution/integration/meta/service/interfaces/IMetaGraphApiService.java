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
}
