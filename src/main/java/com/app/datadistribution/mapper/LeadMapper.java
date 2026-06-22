package com.app.datadistribution.mapper;

import com.app.datadistribution.dto.lead.*;
import com.app.datadistribution.entity.*;
import com.app.datadistribution.dto.course.CourseSummaryDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {UserMapper.class}, builder = @org.mapstruct.Builder(disableBuilder = true))
public interface LeadMapper {

    // --- LeadSource ---
    LeadSourceResponse toDto(LeadSource source);
    
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    LeadSource toEntity(LeadSourceRequest dto);

    // --- Lead ---
    @Mapping(source = "createdByUser", target = "createdBy")
    LeadResponse toDto(Lead lead);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "source", ignore = true)
    @Mapping(target = "assignedTo", ignore = true)
    @Mapping(target = "createdByUser", ignore = true)
    @Mapping(target = "feedbacks", ignore = true)
    @Mapping(target = "followUps", ignore = true)
    @Mapping(target = "statusHistories", ignore = true)
    @Mapping(target = "assignmentHistories", ignore = true)
    Lead toEntity(LeadRequest dto);

    // --- LeadFeedback ---
    @Mapping(source = "createdByUser", target = "createdBy")
    LeadFeedbackResponse toDto(LeadFeedback feedback);

    // --- LeadStatusHistory ---
    @Mapping(source = "changedByUser", target = "changedBy")
    @Mapping(source = "createdAt", target = "changedAt")
    LeadStatusHistoryResponse toDto(LeadStatusHistory history);

    // --- LeadAssignmentHistory ---
    @Mapping(source = "changedByUser", target = "changedBy")
    @Mapping(source = "createdAt", target = "changedAt")
    LeadAssignmentHistoryResponse toDto(LeadAssignmentHistory history);

    // --- LeadFollowUp ---
    @Mapping(source = "createdByUser", target = "createdBy")
    LeadFollowUpResponse toDto(LeadFollowUp followUp);

    default CourseSummaryDTO mapCourse(Course course) {
        if (course == null) {
            return CourseSummaryDTO.builder()
                    .courseName("NO_COURSE")
                    .build();
        }
        return CourseSummaryDTO.builder()
                .id(course.getId())
                .courseName(course.getCourseName())
                .courseCode(course.getCourseCode())
                .status(course.getStatus())
                .build();
    }
}
