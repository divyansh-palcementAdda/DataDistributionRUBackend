package com.app.datadistribution.dto.lead;

import jakarta.validation.constraints.NotBlank;
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
public class LeadFeedbackRequest {

    @NotBlank(message = "Feedback content is required")
    private String feedback;
}
