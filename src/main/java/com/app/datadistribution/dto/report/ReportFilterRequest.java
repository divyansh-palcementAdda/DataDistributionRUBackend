package com.app.datadistribution.dto.report;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
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
public class ReportFilterRequest {
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fromDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate toDate;

    private String datePreset; // TODAY, YESTERDAY, THIS_WEEK, THIS_MONTH, ALL_TIME, THIS_SESSION, CUSTOM
    private String sessionId; // e.g. 2026-27

    private UUID departmentId;
    private UUID userId;
    private UUID courseId;
    private UUID leadStatusId;

    private String reportMode; // SELF, DEPARTMENTAL

    @Builder.Default
    private Integer page = 0;

    @Builder.Default
    private Integer size = 10;

    @Builder.Default
    private String sortBy = "courseName";

    @Builder.Default
    private String sortDirection = "ASC";
}
