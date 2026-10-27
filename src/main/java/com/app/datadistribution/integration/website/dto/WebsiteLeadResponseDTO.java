package com.app.datadistribution.integration.website.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebsiteLeadResponseDTO {

    private String externalLeadId;
    private UUID leadId;
    private String leadCode;
    private String sourceCode;
    private String status;
    private String message;
}
