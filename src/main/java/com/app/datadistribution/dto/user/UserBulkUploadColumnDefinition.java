package com.app.datadistribution.dto.user;

import com.app.datadistribution.common.bulkupload.BulkUploadFieldDefinition;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum UserBulkUploadColumnDefinition {

    NAME(
            "Name",
            "name",
            false,
            "Text (Max 150 chars)",
            "Rahul Sharma",
            "Full name of the user (can be used alternatively instead of separate First Name / Last Name).",
            new HashSet<>(Arrays.asList("name", "full name", "fullname", "user full name", "candidate name"))
    ),
    FIRST_NAME(
            "First Name *",
            "firstName",
            false,
            "Text (2 to 50 chars)",
            "Rahul",
            "Required if 'Name' is not provided. First name of the user.",
            new HashSet<>(Arrays.asList("first name", "firstname", "first", "fname", "given name"))
    ),
    LAST_NAME(
            "Last Name *",
            "lastName",
            false,
            "Text (2 to 50 chars)",
            "Sharma",
            "Last name / surname of the user.",
            new HashSet<>(Arrays.asList("last name", "lastname", "last", "lname", "surname"))
    ),
    EMAIL(
            "Email *",
            "email",
            true,
            "Valid Email Address",
            "rahul.sharma@example.com",
            "Required. Unique email address for system authentication.",
            new HashSet<>(Arrays.asList("email", "email address", "mail", "user email", "email id"))
    ),
    MOBILE(
            "Mobile",
            "mobile",
            false,
            "Numeric (10 to 15 digits)",
            "9876543210",
            "Optional. Primary mobile phone number.",
            new HashSet<>(Arrays.asList("mobile", "mobile number", "phone", "phone number", "contact", "contact number"))
    ),
    USERNAME(
            "Username *",
            "username",
            false,
            "Alphanumeric / Dots / Hyphens",
            "rahul.sharma",
            "Unique login username. If omitted, automatically derived from email or name.",
            new HashSet<>(Arrays.asList("username", "user id", "userid", "login id", "user handle"))
    ),
    ROLE(
            "Role *",
            "role",
            true,
            "Role Name (e.g. COUNSELOR, HOD)",
            "COUNSELOR",
            "Required. Assigned user role in the system.",
            new HashSet<>(Arrays.asList("role", "user role", "designation", "role name"))
    ),
    DEPARTMENT_NAME(
            "Department Name *",
            "departmentName",
            true,
            "Text (Pre-created Department)",
            "Computer Science & Engineering",
            "Required for non-admin users. Exact pre-created Department Name.",
            new HashSet<>(Arrays.asList("department name", "department", "dept name", "dept", "department_name"))
    ),
    STATUS(
            "Status",
            "status",
            false,
            "ACTIVE / INACTIVE",
            "ACTIVE",
            "Optional. Account status (Defaults to ACTIVE).",
            new HashSet<>(Arrays.asList("status", "active", "user status", "state"))
    ),
    PASSWORD(
            "Password",
            "password",
            false,
            "Text (Min 6 chars)",
            "",
            "Optional initial password (Defaults to 'User@123' if omitted).",
            new HashSet<>(Arrays.asList("password", "initial password", "pass"))
    ),
    HOD_ACCESS_TYPE(
            "HOD Access Type",
            "hodAccessType",
            false,
            "FULL_ACCESS / VIEW_ONLY / NO_ACCESS",
            "FULL_ACCESS",
            "Optional for HOD role. Default is FULL_ACCESS.",
            new HashSet<>(Arrays.asList("hod access type", "hod access", "access type", "access"))
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
                .headerName(headerName)
                .fieldKey(fieldKey)
                .required(required)
                .dataType(dataType)
                .sampleValue(sampleValue)
                .description(description)
                .aliases(aliases)
                .build();
    }

    public static List<BulkUploadFieldDefinition> getAllFieldDefinitions() {
        return Arrays.stream(values())
                .map(UserBulkUploadColumnDefinition::toFieldDefinition)
                .toList();
    }
}
