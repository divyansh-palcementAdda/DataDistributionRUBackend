package com.app.datadistribution.dto.segregation;

import java.util.List;
import java.util.UUID;
import com.app.datadistribution.common.PageResponseDTO;
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
public class CourseSegregationResponseDTO {
    private UUID courseTypeId;
    private String courseTypeName;
    private UUID leadSourceId;
    private String leadSourceName;
    private UUID boardId;
    private String boardName;
    private UUID gradeId;
    private String gradeName;
    private long totalLeads;
    private long allottedLeads;
    private long unallottedLeads;
    private long availedLeads;
    private List<LeadStatusColumnDTO> statusColumns;
    private PageResponseDTO<CourseSegregationRowDTO> courses;
}
