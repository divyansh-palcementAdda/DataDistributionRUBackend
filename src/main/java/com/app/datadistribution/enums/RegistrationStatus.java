package com.app.datadistribution.enums;

public enum RegistrationStatus {
    NONE("None"),
    CHECK_PENDING("Registration Check Pending"),
    CHECK_SUCCESSFUL("Registration Check Successful"),
    CHECK_REJECTED("Registration Check Rejected"),
    COMPLETED_MATCHED("Registration Completed / Matched"),
    MANUALLY_APPROVED("Manually Approved");

    private final String displayName;

    RegistrationStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
