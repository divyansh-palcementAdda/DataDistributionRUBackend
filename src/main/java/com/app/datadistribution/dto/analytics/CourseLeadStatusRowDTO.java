package com.app.datadistribution.dto.analytics;

import java.util.Map;
import java.util.UUID;
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
public class CourseLeadStatusRowDTO {
    private UUID courseId;
    private String courseName;
    private String courseCode;
    private Map<String, Long> statusCounts;
    private Long total;
}
