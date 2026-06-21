package com.app.datadistribution.enums;

public enum ActivityType {

    // ================= COURSE =================
    COURSE_CREATED("🎓", "#fdf4ff", "Course Created"),
    COURSE_UPDATED("✏️", "#fdf4ff", "Course Updated"),
    COURSE_DELETED("🗑️", "#fee2e2", "Course Deleted"),
    COURSE_PUBLISHED("🎓", "#fdf4ff", "Course Published"),

    // ================= USER =================
    USER_CREATED("🆕", "#f0fdf4", "User Created"),
    USER_UPDATED("✏️", "#e0f2fe", "User Updated"),
    USER_DELETED("🗑️", "#fee2e2", "User Deleted"),
    USER_REACTIVATED("♻️", "#f0fdf4", "User Reactivated"),

    // ================= AUTH =================
    LOGIN("🔐", "#ecfeff", "User Login"),
    LOGIN_FAILED("❌", "#fee2e2", "Failed Login"),
    REGISTER("🆕", "#f0fdf4", "User Registered"),
    TOKEN_REFRESH("🔄", "#eff6ff", "Token Refreshed"),
    LOGOUT("🚪", "#fef3c7", "User Logout"),
    GLOBAL_LOGOUT("🌍", "#fef3c7", "Logged out from all devices"),

    // ================= ROLE & PERMISSION =================
    ROLE_CREATED("🔐", "#f0fdf4", "Role Created"),
    ROLE_UPDATED("✏️", "#e0f2fe", "Role Updated"),
    ROLE_DELETED("🗑️", "#fee2e2", "Role Deleted"),
    ROLE_ACTIVATED("♻️", "#f0fdf4", "Role Activated"),
    ROLE_DEACTIVATED("⏸️", "#fef3c7", "Role Deactivated"),
    PERMISSION_CREATED("🔑", "#f0fdf4", "Permission Created"),
    PERMISSION_UPDATED("✏️", "#e0f2fe", "Permission Updated"),
    PERMISSION_DELETED("🗑️", "#fee2e2", "Permission Deleted"),
    PERMISSION_ASSIGNED("🔗", "#e0f2fe", "Permission Assigned To Role"),
    PERMISSION_REMOVED("❌", "#fee2e2", "Permission Removed From Role"),
    USER_ROLE_CHANGED("🔄", "#e0f2fe", "User Role Changed");

    private final String icon;
    private final String iconBg;
    private final String defaultTitle;

    ActivityType(String icon, String iconBg, String defaultTitle) {
        this.icon = icon;
        this.iconBg = iconBg;
        this.defaultTitle = defaultTitle;
    }

    public String getIcon() {
        return icon;
    }

    public String getIconBg() {
        return iconBg;
    }

    public String getDefaultTitle() {
        return defaultTitle;
    }
}
