package com.app.datadistribution.integration.website.service.interfaces;

import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.integration.website.dto.WebsiteLeadRequestDTO;
import com.app.datadistribution.integration.website.dto.WebsiteLeadResponseDTO;

public interface IWebsiteLeadService {

    /**
     * Ingests, validates, deduplicates, and creates a lead from a website form submission.
     *
     * @param request  Validated payload from the website form.
     * @param clientIp Client IP for logging and audit.
     * @return Submission response containing lead identifiers and status.
     * @throws BadRequestException if the source code is unsupported or required data fails validation.
     */
    WebsiteLeadResponseDTO ingestLead(WebsiteLeadRequestDTO request, String clientIp) throws BadRequestException;
}
