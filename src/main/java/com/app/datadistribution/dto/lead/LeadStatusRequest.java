package com.app.datadistribution.dto.lead;

import com.app.datadistribution.enums.SentimentCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
public class LeadStatusRequest {

    @NotBlank(message = "Lead status name is required")
    @Size(max = 100, message = "Name must be less than 100 characters")
    private String name;

    @Size(max = 50, message = "Code must be less than 50 characters")
    private String code;

    @Size(max = 255, message = "Description must be less than 255 characters")
    private String description;

    @Builder.Default
    private boolean active = true;

    @Builder.Default
    private Integer displayOrder = 0;

    @Builder.Default
    private SentimentCategory sentimentCategory = SentimentCategory.NEUTRAL;

    private Boolean isFollowUpStatus;
}
