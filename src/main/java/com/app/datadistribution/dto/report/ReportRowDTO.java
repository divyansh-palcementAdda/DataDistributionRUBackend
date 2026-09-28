package com.app.datadistribution.dto.report;

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
public class ReportRowDTO {
    private UUID departmentId;
    private String departmentName;
    private UUID userId;
    private String userName;
    private UUID courseId;
    private String courseName;
    private String courseCode;
    private UUID leadStatusId;
    private String leadStatusName;
    private String leadStatusCode;
    private long totalAllotted;
    private long totalAvailed;
    private long totalUnallotted;
    private long totalRegistered;
    private double conversionRate;
    private long totalFollowUps;
}
