package com.app.datadistribution.dto.lead;

import com.app.datadistribution.dto.course.CourseSummaryDTO;
import com.app.datadistribution.dto.department.DepartmentSummaryDTO;
import com.app.datadistribution.dto.user.UserSummaryResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
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
public class LeadResponse {
    private UUID id;
    private String leadCode;
    private String fullName;
    private String phoneNumber;
    private String alternatePhoneNumber;
    private String email;
    private String city;
    private String state;
    private String country;
    private List<LeadSourceResponse> leadSources;
    private String sourceDetails;
    private String courseInterested;
    private List<CourseSummaryDTO> interestedCourses;
    private List<com.app.datadistribution.dto.course.CourseTypeResponseDTO> interestedCourseTypes;
    private CourseSummaryDTO course;
    private CourseSummaryDTO registeredCourse;
    private BoardResponse board;
    private GradeResponse grade;
    private DepartmentSummaryDTO department;
    private String remarks;
    private LeadStatusResponse currentStatus;
    private UserSummaryResponse assignedTo;
    private UserSummaryResponse createdBy;
    private boolean active;

    @JsonProperty("isAvailed")
    private boolean isAvailed;

    private LocalDateTime availedAt;
    private UserSummaryResponse availedBy;
    private LocalDateTime lastContactedAt;
    private LocalDateTime nextFollowUpDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private com.app.datadistribution.enums.RegistrationStatus registrationStatus;
    private String enrollmentId;
    private String registrationCheckFailureReason;
    private LocalDateTime registrationCheckedAt;
    private LocalDateTime registrationApprovedAt;
    private UserSummaryResponse registrationApprovedBy;
    private Integer cmsMatchScore;
    private String cmsMatchedStudentData;



    @JsonProperty("lastConnected")
    public LocalDateTime getLastConnected() {
        return lastContactedAt;
    }

    public void setLastConnected(LocalDateTime lastConnected) {
        this.lastContactedAt = lastConnected;
    }
}
