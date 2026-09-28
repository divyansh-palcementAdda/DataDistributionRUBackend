package com.app.datadistribution.dto.report;

import java.util.List;
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
public class UserPerformanceReportResponse {
    private ReportSummaryDTO summary;
    private List<ReportRowDTO> rows;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean last;
    private String activeSession;
}
