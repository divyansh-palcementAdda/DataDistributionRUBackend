package com.app.datadistribution.dto.analytics;

import com.app.datadistribution.common.PageResponseDTO;
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
public class CourseStatusAnalyticsResponseDTO {
    private List<LeadStatusColumnDTO> statuses;
    private PageResponseDTO<CourseLeadStatusRowDTO> courses;
    private Map<String, Long> grandTotalStatusCounts;
    private Long grandTotal;
}
