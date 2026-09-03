package com.app.datadistribution.integration.cms.dto;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class StudentVerificationRequest {
    private UUID leadId;
    private String studentName;
    private String fatherName;
    private String motherName;
    private String mobile;
    private String alternateMobile;
    private String email;
    private String alternateEmail;
    private String dateOfBirth;
    private String gender;
    private String courseName;
    private String session;
    private String city;
    private String state;
    private String institute;
    private String enrollmentId;

    public boolean hasIdentifyingFields() {
        return (mobile != null && !mobile.isBlank())
                || (email != null && !email.isBlank())
                || (studentName != null && !studentName.isBlank())
                || (enrollmentId != null && !enrollmentId.isBlank());
    }
}
