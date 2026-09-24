package com.app.datadistribution.dto.analytics;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeadStatusAnalyticsFilterRequest {

    // Context IDs
    private UUID courseTypeId;
    private UUID leadSourceId;
    private UUID boardId;
    private UUID gradeId;
    private UUID assignedUserId;

    // Date preset: ALL_TIME, TODAY, YESTERDAY, THIS_WEEK, THIS_MONTH, CUSTOM
    private String datePreset;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fromDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate toDate;

    // Lead Status filters
    private UUID statusId;
    private List<UUID> statusIds;

    // Allotted / Unallotted filters
    private Boolean allotted;
    private Boolean unallotted;

    // Historical Status filters
    private UUID leadStatusHistoryId;
    private List<UUID> leadStatusHistoryIds;
    private String leadStatusHistory;

    // Search, Pagination & Sorting
    private String search;
    private Integer page;
    private Integer size;
    private String sortBy; // e.g. "total", "courseName", "userName", or status code like "RAW"
    private String sortDirection; // "ASC" or "DESC"

    // Optional flag to group historical statuses
    private Boolean useHistoricalStatus;

    // Aliases and setters for flexible Spring parameter binding
    public void setCategoryId(UUID categoryId) {
        this.courseTypeId = categoryId;
    }

    public void setSourceId(UUID sourceId) {
        this.leadSourceId = sourceId;
    }

    public void setSpecializationId(UUID specializationId) {
        this.boardId = specializationId;
    }

    public void setCounselorId(UUID counselorId) {
        this.assignedUserId = counselorId;
    }

    public void setUserId(UUID userId) {
        this.assignedUserId = userId;
    }

    public void setDateFrom(LocalDate dateFrom) {
        this.startDate = dateFrom;
    }

    public void setDateTo(LocalDate dateTo) {
        this.endDate = dateTo;
    }

    public void setLeadStatusId(UUID leadStatusId) {
        this.statusId = leadStatusId;
    }

    public void setLeadStatusIds(List<UUID> leadStatusIds) {
        this.statusIds = leadStatusIds;
    }

    public LocalDate getEffectiveStartDate() {
        if (datePreset != null && !datePreset.isBlank()) {
            ZoneId zoneId = ZoneId.of("Asia/Kolkata");
            LocalDate today = LocalDate.now(zoneId);
            switch (datePreset.trim().toUpperCase()) {
                case "TODAY":
                    return today;
                case "YESTERDAY":
                    return today.minusDays(1);
                case "THIS_WEEK":
                    return today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                case "THIS_MONTH":
                    return today.withDayOfMonth(1);
                case "ALL_TIME":
                    return null;
                case "CUSTOM":
                default:
                    break;
            }
        }
        return startDate != null ? startDate : fromDate;
    }

    public LocalDate getEffectiveEndDate() {
        if (datePreset != null && !datePreset.isBlank()) {
            ZoneId zoneId = ZoneId.of("Asia/Kolkata");
            LocalDate today = LocalDate.now(zoneId);
            switch (datePreset.trim().toUpperCase()) {
                case "TODAY":
                    return today;
                case "YESTERDAY":
                    return today.minusDays(1);
                case "THIS_WEEK":
                    return today;
                case "THIS_MONTH":
                    return today;
                case "ALL_TIME":
                    return null;
                case "CUSTOM":
                default:
                    break;
            }
        }
        return endDate != null ? endDate : toDate;
    }

    public int getEffectivePage() {
        return (page != null && page >= 0) ? page : 0;
    }

    public int getEffectiveSize() {
        return (size != null && size > 0) ? size : 10;
    }

    public String getEffectiveSortBy() {
        return (sortBy != null && !sortBy.isBlank()) ? sortBy.trim() : "total";
    }

    public String getEffectiveSortDirection() {
        if (sortDirection != null && !sortDirection.isBlank()) {
            return sortDirection.trim().toUpperCase();
        }
        // Default to DESC for totals/status counts, ASC for names
        String sb = getEffectiveSortBy().toLowerCase();
        if (sb.contains("name")) {
            return "ASC";
        }
        return "DESC";
    }
}
