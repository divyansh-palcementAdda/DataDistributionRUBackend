package com.app.datadistribution.dto.lead;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeadSummaryDTO {

    private UUID id;
    private String leadCode;
    private String fullName;
    private String phoneNumber;
    private String whatsappNumber;
}
