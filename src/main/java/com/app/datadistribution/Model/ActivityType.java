package com.app.datadistribution.Model;

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
    GLOBAL_LOGOUT("🌍", "#fef3c7", "Logged out from all devices");

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