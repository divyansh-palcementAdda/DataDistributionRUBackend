package com.app.datadistribution.dto.report;

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
public class ReportSummaryDTO {
    private long totalAllotted;
    private long totalAvailed;
    private long totalUnallotted;
    private long totalRegistered;
    private double conversionRate;
    private long totalFollowUps;
}
