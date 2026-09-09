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
public class UnassignedLeadDTO {
    private UUID leadId;
    private String leadCode;
    private String leadFullName;
    private String reason;
}
