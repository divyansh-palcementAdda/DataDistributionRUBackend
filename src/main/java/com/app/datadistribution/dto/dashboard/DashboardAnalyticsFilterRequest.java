package com.app.datadistribution.dto.dashboard;

import com.app.datadistribution.enums.DashboardGroupBy;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.util.List;
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
public class DashboardAnalyticsFilterRequest {

    private List<UUID> leadStatusIds;
    private List<UUID> leadSourceIds;
    private List<UUID> courseIds;            // Interested courses
    private List<UUID> registeredCourseIds;  // Registered course
    private List<UUID> courseTypeIds;
    private List<UUID> boardIds;
    private List<UUID> gradeIds;
    private List<UUID> departmentIds;
    private List<UUID> assignedUserIds;
    private List<UUID> createdByUserIds;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fromDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate toDate;

    private DashboardGroupBy groupBy;

    private String sortBy;        // COUNT, NAME, CODE
    private String sortDirection; // ASC, DESC

    private Integer page;
    private Integer pageSize;
    private Integer size;

    private String scope;         // DEFAULT, SYSTEM, DEPARTMENT, SELF
    private Boolean selfOnly;     // true to force self-scope data

    public LocalDate getEffectiveStartDate() {
        return startDate != null ? startDate : fromDate;
    }

    public LocalDate getEffectiveEndDate() {
        return endDate != null ? endDate : toDate;
    }

    public Integer getEffectivePageSize() {
        return pageSize != null ? pageSize : size;
    }

    public String getEffectiveScope() {
        if (Boolean.TRUE.equals(selfOnly)) {
            return "SELF";
        }
        if (scope != null && !scope.isBlank()) {
            return scope.trim().toUpperCase();
        }
        return null;
    }
}
