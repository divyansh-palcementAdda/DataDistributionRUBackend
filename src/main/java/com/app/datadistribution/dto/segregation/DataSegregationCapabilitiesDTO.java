package com.app.datadistribution.dto.segregation;

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
public class DataSegregationCapabilitiesDTO {
    private boolean canView;
    private boolean canViewFullFlow;
    private boolean canViewCourseType;
    private boolean canViewSource;
    private boolean canViewBoard;
    private boolean canViewGrade;
    private boolean canViewUserAnalytics;
    private boolean canViewLeadStatusAnalytics;
}
