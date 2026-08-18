package com.app.datadistribution.enums;

public enum DashboardGroupBy {
    LEAD_STATUS,
    LEAD_SOURCE,
    COURSE,
    REGISTERED_COURSE,
    COURSE_TYPE,
    BOARD,
    GRADE,
    DEPARTMENT,
    ASSIGNED_USER;

    public static DashboardGroupBy fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return DashboardGroupBy.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
