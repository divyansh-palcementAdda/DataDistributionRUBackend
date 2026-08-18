package com.app.datadistribution.enums;

public enum HodAccessType {
    FULL_ACCESS,
    VIEW_ONLY;

    public static HodAccessType fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return HodAccessType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
