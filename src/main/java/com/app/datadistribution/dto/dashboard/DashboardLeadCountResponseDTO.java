package com.app.datadistribution.dto.dashboard;

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
public class DashboardLeadCountResponseDTO {
    private String type; // "ALLOTTED", "UNALLOTTED", "AVAILED"
    private long count;
}
