package com.app.datadistribution.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.app.datadistribution.dto.course.CourseSummaryDTO;
import com.app.datadistribution.dto.department.DepartmentSummaryDTO;
import com.app.datadistribution.dto.feedback.FeedbackResponseDTO;
import com.app.datadistribution.dto.followup.FollowUpResponseDTO;
import com.app.datadistribution.dto.lead.BoardRequest;
import com.app.datadistribution.dto.lead.BoardResponse;
import com.app.datadistribution.dto.lead.GradeRequest;
import com.app.datadistribution.dto.lead.GradeResponse;
import com.app.datadistribution.dto.lead.LeadAssignmentHistoryResponse;
import com.app.datadistribution.dto.lead.LeadAvailedResponse;
import com.app.datadistribution.dto.lead.LeadFeedbackResponse;
import com.app.datadistribution.dto.lead.LeadFollowUpResponse;
import com.app.datadistribution.dto.lead.LeadRequest;
import com.app.datadistribution.dto.lead.LeadResponse;
import com.app.datadistribution.dto.lead.LeadSourceRequest;
import com.app.datadistribution.dto.lead.LeadSourceResponse;
import com.app.datadistribution.dto.lead.LeadStatusHistoryResponse;
import com.app.datadistribution.dto.lead.LeadStatusRequest;
import com.app.datadistribution.dto.lead.LeadStatusResponse;
import com.app.datadistribution.entity.Board;
import com.app.datadistribution.entity.Course;
import com.app.datadistribution.entity.Grade;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadAssignmentHistory;
import com.app.datadistribution.entity.LeadAvailed;
import com.app.datadistribution.entity.LeadFeedback;
import com.app.datadistribution.entity.LeadFollowUp;
import com.app.datadistribution.entity.LeadSource;
import com.app.datadistribution.entity.LeadStatus;
import com.app.datadistribution.entity.LeadStatusHistory;

@Mapper(componentModel = "spring", uses = {UserMapper.class}, builder = @org.mapstruct.Builder(disableBuilder = true))
public interface LeadMapper {


    // --- Board ---
    BoardResponse toDto(Board board);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    Board toEntity(BoardRequest dto);

    // --- Grade ---
    GradeResponse toDto(Grade grade);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    Grade toEntity(GradeRequest dto);

    // --- LeadStatus ---
    LeadStatusResponse toDto(LeadStatus status);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    LeadStatus toEntity(LeadStatusRequest dto);

    // --- LeadSource ---
    LeadSourceResponse toDto(LeadSource source);
    
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    LeadSource toEntity(LeadSourceRequest dto);

    // --- Lead ---
    default LeadResponse toDto(Lead lead) {
        if (lead == null) return null;

        List<CourseSummaryDTO> interestedCoursesDtos = null;
        List<com.app.datadistribution.dto.course.CourseTypeResponseDTO> interestedCourseTypesDtos = null;

        if (lead.getInterestedCourses() != null && !lead.getInterestedCourses().isEmpty()) {
            interestedCoursesDtos = lead.getInterestedCourses().stream()
                    .filter(c -> !c.isDeleted())
                    .map(this::mapCourse)
                    .collect(java.util.stream.Collectors.toList());

            interestedCourseTypesDtos = lead.getInterestedCourses().stream()
                    .filter(c -> !c.isDeleted() && c.getCourseType() != null && !c.getCourseType().isDeleted())
                    .map(Course::getCourseType)
                    .distinct()
                    .map(ct -> com.app.datadistribution.dto.course.CourseTypeResponseDTO.builder()
                            .id(ct.getId())
                            .name(ct.getName())
                            .description(ct.getDescription())
                            .status(ct.getStatus())
                            .createdAt(ct.getCreatedAt())
                            .updatedAt(ct.getUpdatedAt())
                            .build())
                    .collect(java.util.stream.Collectors.toList());
        }

        CourseSummaryDTO registeredCourseDto = mapCourse(lead.getCourse());

        List<LeadSourceResponse> leadSourcesDtos = null;
        if (lead.getLeadSources() != null) {
            leadSourcesDtos = lead.getLeadSources().stream()
                    .filter(s -> !s.isDeleted())
                    .map(this::toDto)
                    .collect(java.util.stream.Collectors.toList());
        }

        DepartmentSummaryDTO departmentDto = null;
        if (lead.getDepartment() != null && !lead.getDepartment().isDeleted()) {
            departmentDto = DepartmentSummaryDTO.builder()
                    .id(lead.getDepartment().getId())
                    .name(lead.getDepartment().getName())
                    .code(lead.getDepartment().getCode())
                    .build();
        }

        UserMapper userMapper = org.mapstruct.factory.Mappers.getMapper(UserMapper.class);

        boolean isAvailed = false;
        java.time.LocalDateTime availedAt = null;
        com.app.datadistribution.dto.user.UserResponse availedBy = null;

        if (lead.getAssignedTo() != null && lead.getAvailedRecords() != null && !lead.getAvailedRecords().isEmpty()) {
            java.util.UUID assignedUserId = lead.getAssignedTo().getId();
            for (com.app.datadistribution.entity.LeadAvailed la : lead.getAvailedRecords()) {
                if (!la.isDeleted() && la.getAvailedByUser() != null && assignedUserId.equals(la.getAvailedByUser().getId())) {
                    isAvailed = true;
                    availedAt = la.getAvailedAt();
                    availedBy = userMapper.toDto(la.getAvailedByUser());
                    break;
                }
            }
        }

        return LeadResponse.builder()
                .id(lead.getId())
                .leadCode(lead.getLeadCode())
                .fullName(lead.getFullName())
                .phoneNumber(lead.getPhoneNumber())
                .alternatePhoneNumber(lead.getAlternatePhoneNumber())
                .email(lead.getEmail())
                .city(lead.getCity())
                .state(lead.getState())
                .country(lead.getCountry())
                .leadSources(leadSourcesDtos)
                .sourceDetails(lead.getSourceDetails())
                .courseInterested(lead.getCourseInterested())
                .interestedCourses(interestedCoursesDtos)
                .interestedCourseTypes(interestedCourseTypesDtos)
                .course(registeredCourseDto)
                .registeredCourse(registeredCourseDto)
                .board(toDto(lead.getBoard()))
                .grade(toDto(lead.getGrade()))
                .department(departmentDto)
                .remarks(lead.getRemarks())
                .currentStatus(toDto(lead.getCurrentStatus()))
                .assignedTo(userMapper.toDto(lead.getAssignedTo()))
                .createdBy(userMapper.toDto(lead.getCreatedByUser()))
                .active(lead.isActive())
                .isAvailed(isAvailed)
                .availedAt(availedAt)
                .availedBy(availedBy)
                .lastContactedAt(lead.getLastContactedAt())
                .nextFollowUpDate(lead.getNextFollowUpDate())
                .createdAt(lead.getCreatedAt())
                .updatedAt(lead.getUpdatedAt())
                .build();
    }

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "leadSources", ignore = true)
    @Mapping(target = "interestedCourses", ignore = true)
    @Mapping(target = "currentStatus", ignore = true)
    @Mapping(target = "board", ignore = true)
    @Mapping(target = "grade", ignore = true)
    @Mapping(target = "department", ignore = true)
    @Mapping(target = "assignedTo", ignore = true)
    @Mapping(target = "createdByUser", ignore = true)
    @Mapping(target = "feedbacks", ignore = true)
    @Mapping(target = "followUps", ignore = true)
    @Mapping(target = "statusHistories", ignore = true)
    @Mapping(target = "assignmentHistories", ignore = true)
    @Mapping(target = "availedRecords", ignore = true)
    Lead toEntity(LeadRequest dto);

    // --- LeadFeedback ---
    @Mapping(source = "createdByUser", target = "createdBy")
    LeadFeedbackResponse toDto(LeadFeedback feedback);

    // --- LeadStatusHistory ---
    @Mapping(source = "changedByUser", target = "changedBy")
    @Mapping(source = "createdAt", target = "changedAt")
    @Mapping(source = "lead.id", target = "leadId")
    @Mapping(source = "lead.leadCode", target = "leadCode")
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
            return null;
        }
        com.app.datadistribution.dto.course.CourseTypeResponseDTO typeDto = null;
        if (course.getCourseType() != null) {
            typeDto = com.app.datadistribution.dto.course.CourseTypeResponseDTO.builder()
                    .id(course.getCourseType().getId())
                    .name(course.getCourseType().getName())
                    .description(course.getCourseType().getDescription())
                    .status(course.getCourseType().getStatus())
                    .createdAt(course.getCourseType().getCreatedAt())
                    .updatedAt(course.getCourseType().getUpdatedAt())
                    .build();
        }
        return CourseSummaryDTO.builder()
                .id(course.getId())
                .courseName(course.getCourseName())
                .courseCode(course.getCourseCode())
                .status(course.getStatus())
                .courseType(typeDto)
                .build();
    }

    // --- FollowUpResponseDTO ---
    @Mapping(source = "createdByUser", target = "createdBy")
    @Mapping(source = "lead.id", target = "leadId")
    @Mapping(source = "lead.leadCode", target = "leadCode")
    @Mapping(source = "lead.fullName", target = "leadFullName")
    FollowUpResponseDTO toFollowUpResponseDto(LeadFollowUp followUp);

    // --- FeedbackResponseDTO ---
    @Mapping(source = "createdByUser", target = "createdBy")
    @Mapping(source = "lead.id", target = "leadId")
    @Mapping(source = "lead.leadCode", target = "leadCode")
    @Mapping(source = "lead.fullName", target = "leadFullName")
    FeedbackResponseDTO toFeedbackResponseDto(LeadFeedback feedback);

    // --- LeadAvailed ---
    @Mapping(source = "availedByUser", target = "availedBy")
    @Mapping(source = "lead.id", target = "leadId")
    @Mapping(source = "lead.leadCode", target = "leadCode")
    @Mapping(target = "availed", constant = "true")
    LeadAvailedResponse toDto(LeadAvailed availed);
}
