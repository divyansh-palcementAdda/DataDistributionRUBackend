package com.app.datadistribution.dto.lead;

import java.util.Arrays;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum LeadBulkUploadColumnDefinition {

    FULL_NAME("Full Name *", "fullName", true, "Text (Max 150 chars)", "John Doe", "Required. Full name of the candidate/student (1 to 150 characters)."),
    PHONE_NUMBER("Phone Number *", "phoneNumber", true, "Numeric / Phone format", "+919876543210", "Required. Primary contact number (7 to 20 digits, optional + or dashes). Must be unique."),
    ALTERNATE_PHONE_NUMBER("Alternate Phone Number", "alternatePhoneNumber", false, "Numeric / Phone format", "+919876543211", "Optional. Secondary contact number."),
    EMAIL("Email", "email", false, "Valid Email address", "john.doe@example.com", "Optional. Candidate email address (valid email format, Max 100 chars)."),
    CITY("City", "city", false, "Text", "Mumbai", "Optional. Candidate city of residence."),
    STATE("State", "state", false, "Text", "Maharashtra", "Optional. Candidate state of residence."),
    COUNTRY("Country", "country", false, "Text", "India", "Optional. Candidate country."),
    SOURCE_DETAILS("Source Details", "sourceDetails", false, "Text", "Education Expo 2026", "Optional. Specific campaign, event, or inquiry details."),
    COURSE_INTERESTED("Course Interested", "courseInterested", false, "Text", "Computer Science Engineering", "Optional. Specific course or specialization name."),
    REMARKS("Remarks", "remarks", false, "Text", "High intent lead, requested callback", "Optional. Initial notes or remarks for counselor.");

    private final String headerName;
    private final String fieldKey;
    private final boolean required;
    private final String dataType;
    private final String sampleValue;
    private final String description;

    public static List<LeadBulkUploadColumnDefinition> getAllColumns() {
        return Arrays.asList(values());
    }
}
