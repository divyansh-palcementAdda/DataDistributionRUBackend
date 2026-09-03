package com.app.datadistribution.integration.cms.dto;

import com.app.datadistribution.integration.cms.enums.MatchStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StudentVerificationResponse {
    private boolean verified;
    private MatchStatus matchStatus;
    private int confidenceScore;
    private boolean multipleMatches;
    private int totalCandidatesEvaluated;
    private List<MatchedStudentDTO> matchedStudents;
    private String message;
}
