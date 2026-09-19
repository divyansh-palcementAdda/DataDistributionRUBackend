package com.app.datadistribution.dto.segregation;

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
public class CourseSegregationRowDTO {
    private UUID courseId;
    private String courseName;
    private String courseCode;
    private long total;
    private long allotted;
    private long unallotted;
    private long availed;
    private Map<String, Long> statusCounts;
}
