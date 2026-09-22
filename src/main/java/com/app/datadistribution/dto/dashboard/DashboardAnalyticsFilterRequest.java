package com.app.datadistribution.dto.dashboard;

import com.app.datadistribution.enums.DashboardGroupBy;
import com.fasterxml.jackson.annotation.JsonProperty;
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
    private List<UUID> programIds;
    private UUID programId;
    private List<UUID> assignedUserIds;
    private List<UUID> createdByUserIds;

    // Allotted / Unallotted filter (true = assignedTo is not null, false = assignedTo is null)
    private Boolean allotted;

    // Currently Working filter (true = user active session & updated matching leads within 15m)
    private Boolean currentlyWorking;

    // Multi-source filter (true = COUNT(DISTINCT source) > 1, false = COUNT(DISTINCT source) <= 1)
    private Boolean multiSource;

    // Availed / Unavailed filter
    @JsonProperty("isAvailed")
    private Boolean isAvailed;
    private Boolean availed;

    private UUID availedByUserId;
    private List<UUID> availedByUserIds;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate availedFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate availedTo;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate assignedFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate assignedTo;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate updatedFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate updatedTo;

    private String leadCode;
    private String search;

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

    private List<UUID> leadStatusHistoryIds;
    private String leadStatusHistory;

    // Singular & alias setters for Spring MVC parameter binding compatibility
    public void setCourseTypeId(UUID courseTypeId) {
        if (courseTypeId != null) {
            if (this.courseTypeIds == null) {
                this.courseTypeIds = new java.util.ArrayList<>();
            }
            if (!this.courseTypeIds.contains(courseTypeId)) {
                this.courseTypeIds.add(courseTypeId);
            }
        }
    }

    public void setLeadSourceId(UUID leadSourceId) {
        if (leadSourceId != null) {
            if (this.leadSourceIds == null) {
                this.leadSourceIds = new java.util.ArrayList<>();
            }
            if (!this.leadSourceIds.contains(leadSourceId)) {
                this.leadSourceIds.add(leadSourceId);
            }
        }
    }

    public void setSourceId(UUID sourceId) {
        setLeadSourceId(sourceId);
    }

    public void setStatusId(UUID statusId) {
        if (statusId != null) {
            if (this.leadStatusIds == null) {
                this.leadStatusIds = new java.util.ArrayList<>();
            }
            if (!this.leadStatusIds.contains(statusId)) {
                this.leadStatusIds.add(statusId);
            }
        }
    }

    public void setLeadStatusId(UUID leadStatusId) {
        setStatusId(leadStatusId);
    }

    public void setStatusIds(List<UUID> statusIds) {
        if (statusIds != null && !statusIds.isEmpty()) {
            if (this.leadStatusIds == null) {
                this.leadStatusIds = new java.util.ArrayList<>(statusIds);
            } else {
                for (UUID id : statusIds) {
                    if (!this.leadStatusIds.contains(id)) {
                        this.leadStatusIds.add(id);
                    }
                }
            }
        }
    }

    public List<UUID> getStatusIds() {
        return this.leadStatusIds;
    }

    public void setBoardId(UUID boardId) {
        if (boardId != null) {
            if (this.boardIds == null) {
                this.boardIds = new java.util.ArrayList<>();
            }
            if (!this.boardIds.contains(boardId)) {
                this.boardIds.add(boardId);
            }
        }
    }

    public void setGradeId(UUID gradeId) {
        if (gradeId != null) {
            if (this.gradeIds == null) {
                this.gradeIds = new java.util.ArrayList<>();
            }
            if (!this.gradeIds.contains(gradeId)) {
                this.gradeIds.add(gradeId);
            }
        }
    }

    public void setCourseId(UUID courseId) {
        if (courseId != null) {
            if (this.courseIds == null) {
                this.courseIds = new java.util.ArrayList<>();
            }
            if (!this.courseIds.contains(courseId)) {
                this.courseIds.add(courseId);
            }
        }
    }

    public void setInterestedCourseId(UUID interestedCourseId) {
        setCourseId(interestedCourseId);
    }

    public void setInterestedCourseIds(List<UUID> interestedCourseIds) {
        if (interestedCourseIds != null && !interestedCourseIds.isEmpty()) {
            if (this.courseIds == null) {
                this.courseIds = new java.util.ArrayList<>(interestedCourseIds);
            } else {
                for (UUID id : interestedCourseIds) {
                    if (!this.courseIds.contains(id)) {
                        this.courseIds.add(id);
                    }
                }
            }
        }
    }

    public void setDepartmentId(UUID departmentId) {
        if (departmentId != null) {
            if (this.departmentIds == null) {
                this.departmentIds = new java.util.ArrayList<>();
            }
            if (!this.departmentIds.contains(departmentId)) {
                this.departmentIds.add(departmentId);
            }
        }
    }

    public void setProgramId(UUID programId) {
        this.programId = programId;
        if (programId != null) {
            if (this.programIds == null) {
                this.programIds = new java.util.ArrayList<>();
            }
            if (!this.programIds.contains(programId)) {
                this.programIds.add(programId);
            }
        }
    }

    public void setProgramIds(List<UUID> programIds) {
        this.programIds = programIds;
    }

    public void setAssignedUserId(UUID assignedUserId) {
        if (assignedUserId != null) {
            if (this.assignedUserIds == null) {
                this.assignedUserIds = new java.util.ArrayList<>();
            }
            if (!this.assignedUserIds.contains(assignedUserId)) {
                this.assignedUserIds.add(assignedUserId);
            }
        }
    }

    public void setUserId(UUID userId) {
        setAssignedUserId(userId);
    }

    public void setUnallotted(Boolean unallotted) {
        if (Boolean.TRUE.equals(unallotted)) {
            this.allotted = false;
        }
    }

    public void setLeadStatusHistoryId(UUID leadStatusHistoryId) {
        if (leadStatusHistoryId != null) {
            if (this.leadStatusHistoryIds == null) {
                this.leadStatusHistoryIds = new java.util.ArrayList<>();
            }
            if (!this.leadStatusHistoryIds.contains(leadStatusHistoryId)) {
                this.leadStatusHistoryIds.add(leadStatusHistoryId);
            }
        }
    }

    public DashboardAnalyticsFilterRequest copy() {
        return DashboardAnalyticsFilterRequest.builder()
                .leadStatusIds(this.leadStatusIds != null ? new java.util.ArrayList<>(this.leadStatusIds) : null)
                .leadSourceIds(this.leadSourceIds != null ? new java.util.ArrayList<>(this.leadSourceIds) : null)
                .courseIds(this.courseIds != null ? new java.util.ArrayList<>(this.courseIds) : null)
                .registeredCourseIds(this.registeredCourseIds != null ? new java.util.ArrayList<>(this.registeredCourseIds) : null)
                .courseTypeIds(this.courseTypeIds != null ? new java.util.ArrayList<>(this.courseTypeIds) : null)
                .boardIds(this.boardIds != null ? new java.util.ArrayList<>(this.boardIds) : null)
                .gradeIds(this.gradeIds != null ? new java.util.ArrayList<>(this.gradeIds) : null)
                .departmentIds(this.departmentIds != null ? new java.util.ArrayList<>(this.departmentIds) : null)
                .assignedUserIds(this.assignedUserIds != null ? new java.util.ArrayList<>(this.assignedUserIds) : null)
                .createdByUserIds(this.createdByUserIds != null ? new java.util.ArrayList<>(this.createdByUserIds) : null)
                .allotted(this.allotted)
                .multiSource(this.multiSource)
                .isAvailed(this.isAvailed)
                .availed(this.availed)
                .availedByUserId(this.availedByUserId)
                .availedByUserIds(this.availedByUserIds != null ? new java.util.ArrayList<>(this.availedByUserIds) : null)
                .availedFrom(this.availedFrom)
                .availedTo(this.availedTo)
                .assignedFrom(this.assignedFrom)
                .assignedTo(this.assignedTo)
                .updatedFrom(this.updatedFrom)
                .updatedTo(this.updatedTo)
                .leadCode(this.leadCode)
                .search(this.search)
                .startDate(this.startDate)
                .endDate(this.endDate)
                .fromDate(this.fromDate)
                .toDate(this.toDate)
                .groupBy(this.groupBy)
                .sortBy(this.sortBy)
                .sortDirection(this.sortDirection)
                .page(this.page)
                .pageSize(this.pageSize)
                .size(this.size)
                .scope(this.scope)
                .selfOnly(this.selfOnly)
                .leadStatusHistoryIds(this.leadStatusHistoryIds != null ? new java.util.ArrayList<>(this.leadStatusHistoryIds) : null)
                .leadStatusHistory(this.leadStatusHistory)
                .build();
    }

    public LocalDate getEffectiveStartDate() {
        return startDate != null ? startDate : fromDate;
    }

    public LocalDate getEffectiveEndDate() {
        return endDate != null ? endDate : toDate;
    }

    public Integer getEffectivePageSize() {
        return pageSize != null ? pageSize : size;
    }

    public Boolean getEffectiveIsAvailed() {
        return isAvailed != null ? isAvailed : availed;
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
