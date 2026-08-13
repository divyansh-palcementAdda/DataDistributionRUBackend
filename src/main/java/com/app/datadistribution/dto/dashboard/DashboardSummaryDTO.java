package com.app.datadistribution.dto.dashboard;

import java.util.List;
import java.util.Map;
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
public class DashboardSummaryDTO {
    private String scope; // SYSTEM, DEPARTMENT, SELF
    private long totalLeads;
    private long totalFollowUpsToday;
    private long counsellorsLoggedToday;
    private long counsellorsCurrentlyWorking;
    private double conversationRatio;
    private List<DashboardSectionDTO> sections;
    private Map<String, Object> additionalMetrics;
}
