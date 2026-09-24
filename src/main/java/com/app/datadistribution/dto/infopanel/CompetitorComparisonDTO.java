package com.app.datadistribution.dto.infopanel;

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
public class CompetitorComparisonDTO {
    private String courseFeePerYear;
    private String duration;
    private String odds;
    private String eligibility;
    private String hostel;
    private String distanceFromCity;
    private String registrationFee;
    private String averagePlacements;
    private String highestPlacement;
}
