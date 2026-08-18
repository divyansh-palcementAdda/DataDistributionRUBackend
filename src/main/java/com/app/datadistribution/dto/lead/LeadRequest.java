package com.app.datadistribution.dto.lead;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class LeadRequest {

    private String leadCode;

    @NotBlank(message = "Full name is required")
    @Size(max = 150, message = "Full name must be less than 150 characters")
    private String fullName;

    @NotBlank(message = "Phone number is required")
    @Size(max = 20, message = "Phone number must be less than 20 characters")
    private String phoneNumber;

    @Size(max = 20, message = "Alternate phone number must be less than 20 characters")
    private String alternatePhoneNumber;

    @Email(message = "Email must be a valid email address")
    @Size(max = 100, message = "Email must be less than 100 characters")
    private String email;

    @Size(max = 100, message = "City must be less than 100 characters")
    private String city;

    @Size(max = 100, message = "State must be less than 100 characters")
    private String state;

    @Size(max = 100, message = "Country must be less than 100 characters")
    private String country;

    private List<UUID> leadSourceIds;

    @Size(max = 255, message = "Source details must be less than 255 characters")
    private String sourceDetails;

    @Size(max = 150, message = "Course interested must be less than 150 characters")
    private String courseInterested;

    private List<UUID> interestedCourseIds;

    private UUID courseId;

    private UUID registeredCourseId;

    private UUID boardId;

    private UUID gradeId;

    private UUID departmentId;

    private String remarks;

    private UUID assignedToUserId;

    private UUID statusId;

    @Builder.Default
    private boolean active = true;

    private LocalDateTime nextFollowUpDate;
}
