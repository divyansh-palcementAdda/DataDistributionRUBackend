package com.app.datadistribution.dto.lead;

import com.app.datadistribution.common.bulkupload.BulkUploadFieldDefinition;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum LeadBulkUploadColumnDefinition {

    FULL_NAME(
            "Full Name",
            "fullName",
            false,
            "Text (Max 150 chars)",
            "Rahul Sharma",
            "Optional. Full name of the candidate/student.",
            new HashSet<>(Arrays.asList("full name", "name", "student name", "candidate name", "fullname", "student", "candidatename", "studentname"))
    ),
    PHONE_NUMBER(
            "Phone Number",
            "phoneNumber",
            false,
            "Numeric / Phone format",
            "+919876543210",
            "Optional. Primary contact number.",
            new HashSet<>(Arrays.asList("phone number", "phone", "mobile", "mobile number", "contact", "contact number", "contact no", "phonenumber", "mobilenumber", "contactno"))
    ),
    ALTERNATE_PHONE_NUMBER(
            "Alternate Phone Number",
            "alternatePhoneNumber",
            false,
            "Numeric / Phone format",
            "+919876543211",
            "Optional. Secondary contact number.",
            new HashSet<>(Arrays.asList("alternate phone number", "alternate phone", "alt phone", "alternate mobile", "alt mobile", "alternate phonenumber", "altphonenumber", "alternate phone no"))
    ),
    EMAIL(
            "Email",
            "email",
            false,
            "Valid Email address",
            "rahul.sharma@example.com",
            "Optional. Candidate email address.",
            new HashSet<>(Arrays.asList("email", "email id", "email address", "mail", "emailid"))
    ),
    COURSE(
            "Interested Course",
            "course",
            false,
            "Text",
            "MBA",
            "Optional. Specific course name or code.",
            new HashSet<>(Arrays.asList("interested course", "course", "course interested", "course name", "course code", "courseinterested", "interestedcourse", "interested_course"))
    ),
    PROGRAM(
            "Program",
            "program",
            false,
            "Text",
            "School of Management",
            "Optional. Specific program or school name or code.",
            new HashSet<>(Arrays.asList("program", "school", "faculty", "institute", "program name", "program code", "programs"))
    ),
    COURSE_TYPE(
            "Course Type",
            "courseType",
            false,
            "Text",
            "UG",
            "Optional. Course type name (e.g. UG, PG, Diploma).",
            new HashSet<>(Arrays.asList("course type", "coursetype", "type of course", "degree type", "course_type"))
    ),
    LEAD_SOURCE(
            "Lead Source",
            "leadSource",
            false,
            "Text",
            "Website",
            "Optional. Specific lead source name.",
            new HashSet<>(Arrays.asList("lead source", "source", "leadsource", "source name", "lead_source"))
    ),
    SOURCE_DETAILS(
            "Source Details",
            "sourceDetails",
            false,
            "Text",
            "Education Expo 2026",
            "Optional. Specific campaign, event, or inquiry details.",
            new HashSet<>(Arrays.asList("source details", "sourcedetails", "source note", "campaign", "event", "source_details"))
    ),
    BOARD(
            "Board",
            "board",
            false,
            "Text",
            "CBSE",
            "Optional. Education board name or code.",
            new HashSet<>(Arrays.asList("board", "education board", "board name"))
    ),
    GRADE(
            "Grade",
            "grade",
            false,
            "Text",
            "12th",
            "Optional. Candidate grade or standard.",
            new HashSet<>(Arrays.asList("grade", "class", "standard", "grade name"))
    ),
    STREAM(
            "Stream",
            "stream",
            false,
            "Text",
            "Commerce",
            "Optional. Specific stream name or code (e.g. Science, Commerce, Arts).",
            new HashSet<>(Arrays.asList("stream", "stream name", "discipline"))
    ),
    DEPARTMENT(
            "Department",
            "department",
            false,
            "Text",
            "Admissions",
            "Optional. Assigned department name or code.",
            new HashSet<>(Arrays.asList("department", "dept", "department name", "dept name"))
    ),
    CITY(
            "City",
            "city",
            false,
            "Text",
            "Mumbai",
            "Optional. Candidate city of residence.",
            new HashSet<>(Arrays.asList("city", "town"))
    ),
    STATE(
            "State",
            "state",
            false,
            "Text",
            "Maharashtra",
            "Optional. Candidate state of residence.",
            new HashSet<>(Arrays.asList("state", "province"))
    ),
    COUNTRY(
            "Country",
            "country",
            false,
            "Text",
            "India",
            "Optional. Candidate country.",
            new HashSet<>(Arrays.asList("country", "nation"))
    ),
    REMARKS(
            "Remarks",
            "remarks",
            false,
            "Text",
            "High intent lead, requested callback",
            "Optional. Notes or remarks for counselor.",
            new HashSet<>(Arrays.asList("remarks", "remark", "notes", "note", "comment", "comments"))
    );

    private final String headerName;
    private final String fieldKey;
    private final boolean required;
    private final String dataType;
    private final String sampleValue;
    private final String description;
    private final Set<String> aliases;

    public BulkUploadFieldDefinition toFieldDefinition() {
        return BulkUploadFieldDefinition.builder()
                .headerName(this.headerName)
                .fieldKey(this.fieldKey)
                .required(this.required)
                .dataType(this.dataType)
                .sampleValue(this.sampleValue)
                .description(this.description)
                .aliases(this.aliases)
                .build();
    }

    public static List<BulkUploadFieldDefinition> getAllFieldDefinitions() {
        return Arrays.stream(values())
                .map(LeadBulkUploadColumnDefinition::toFieldDefinition)
                .toList();
    }

    public static List<LeadBulkUploadColumnDefinition> getAllColumns() {
        return Arrays.asList(values());
    }
}
