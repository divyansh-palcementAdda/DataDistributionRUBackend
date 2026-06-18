package com.app.datadistribution.dto.lead;

import com.app.datadistribution.enums.LeadStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

    @NotNull(message = "New status is required")
    private LeadStatus newStatus;

    @NotBlank(message = "Feedback is required when changing lead status")
    private String feedback;
}
