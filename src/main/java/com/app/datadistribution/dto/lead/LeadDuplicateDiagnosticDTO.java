package com.app.datadistribution.dto.lead;

import java.util.List;
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
public class LeadDuplicateDiagnosticDTO {
    private String normalizedPhone;
    private String normalizedName;
    private String matchClassification; // "EXACT_DUPLICATE", "COMPATIBLE_MISSING_EMAIL", "AMBIGUOUS_MULTIPLE_MATCH", "SAME_PHONE_DIFFERENT_EMAIL", "SAME_PHONE_DIFFERENT_NAME"
    private int candidateCount;
    private List<LeadCandidateSummaryDTO> candidates;
}

