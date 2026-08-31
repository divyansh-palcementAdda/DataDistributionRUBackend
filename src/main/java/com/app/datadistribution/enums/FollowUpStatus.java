package com.app.datadistribution.enums;

public enum FollowUpStatus {
    PENDING("Pending"),
    UPCOMING("Upcoming"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled"),
    MISSED("Missed");

    private final String displayName;

    FollowUpStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
