package com.app.datadistribution.integration.cms.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class MatchedStudentDTO {
    @JsonAlias({"id", "studentId"})
    private String studentId;

    @JsonAlias({"enrollmentId", "enrollmentNumber", "applicationNumber"})
    private String enrollmentNumber;

    @JsonAlias({"fullName", "studentName"})
    private String studentName;

    private String fatherName;
    private String motherName;

    @JsonAlias({"phoneNumber", "mobile", "phone"})
    private String mobile;

    @JsonAlias({"email", "studentEmail"})
    private String email;

    private String courseName;
    private String session;

    @JsonAlias({"institutionName", "institute", "institution"})
    private String institute;

    private String admissionStatus;
    private int matchScore;
    private List<String> matchReasons;
}
