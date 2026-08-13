package com.app.datadistribution.dto.lead;

import com.app.datadistribution.dto.course.CourseSummaryDTO;
import com.app.datadistribution.dto.user.UserResponse;
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
    private CourseSummaryDTO course;
    private BoardResponse board;
    private GradeResponse grade;
    private String remarks;
    private LeadStatusResponse currentStatus;
    private UserResponse assignedTo;
    private UserResponse createdBy;
    private boolean active;
    private LocalDateTime lastContactedAt;
    private LocalDateTime nextFollowUpDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
