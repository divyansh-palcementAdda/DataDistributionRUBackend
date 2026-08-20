package com.app.datadistribution.dto.lead;

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
public class LeadDistributionFilterRequest {

    private List<UUID> courseTypeIds;
    private UUID courseTypeId;
    private List<UUID> courseIds;
    private UUID courseId;
    private List<UUID> gradeIds;
    private UUID gradeId;
    private List<UUID> boardIds;
    private UUID boardId;
    private List<UUID> leadSourceIds;
    private UUID leadSourceId;
    private List<UUID> leadStatusIds;
    private UUID statusId;
    private UUID departmentId;
    private LocalDate createdDateStart;
    private LocalDate createdDateEnd;
}
