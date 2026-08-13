package com.app.datadistribution.dto.lead;

import jakarta.validation.constraints.NotBlank;
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
public class LeadStatusChangeRequest {

    private UUID newStatusId;
    private String statusCode;

    @NotBlank(message = "Feedback is required when changing lead status")
    private String feedback;
}
