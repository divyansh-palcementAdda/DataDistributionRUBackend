package com.app.datadistribution.config;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.app.datadistribution.enums.PermissionEntity;
import com.app.datadistribution.enums.PermissionGroup;
import com.app.datadistribution.enums.PermissionOperationType;
import com.app.datadistribution.enums.PermissionType;

import lombok.Builder;
import lombok.Getter;

public final class PermissionMetadataRegistry {

    @Getter
    @Builder
    public static class PermissionMetadata {
        private final String code;
        private final String description;
        private final PermissionEntity entity;
        private final PermissionGroup permissionGroup;
        private final PermissionOperationType permissionType;
        private final String fieldKey;
        private final String fieldLabel;
        private final String fieldGroup;
        private final Integer displayOrder;
    }

    private static final Map<PermissionType, PermissionMetadata> REGISTRY = buildRegistry();

    public static PermissionMetadata getMetadata(PermissionType type) {
        return REGISTRY.get(type);
    }

    public static Map<PermissionType, PermissionMetadata> getAll() {
        return Collections.unmodifiableMap(REGISTRY);
    }

    private static Map<PermissionType, PermissionMetadata> buildRegistry() {
        Map<PermissionType, PermissionMetadata> map = new HashMap<>();

        // ==========================================
        // 1. LEAD FIELD-LEVEL PERMISSIONS (LEAD_FIELD)
        // ==========================================
        registerLeadField(map, PermissionType.LEAD_FIELD_FULL_NAME_READ, PermissionType.LEAD_FIELD_FULL_NAME_WRITE,
                "fullName", "Full Name", "Personal & Contact Information", 10, "lead candidate's full name");

        registerLeadField(map, PermissionType.LEAD_FIELD_PHONE_NUMBER_READ, PermissionType.LEAD_FIELD_PHONE_NUMBER_WRITE,
                "phoneNumber", "Phone Number", "Personal & Contact Information", 20, "lead primary phone number");

        registerLeadField(map, PermissionType.LEAD_FIELD_ALTERNATE_PHONE_READ, PermissionType.LEAD_FIELD_ALTERNATE_PHONE_WRITE,
                "alternatePhone", "Alternate Phone", "Personal & Contact Information", 30, "lead secondary/alternate phone");

        registerLeadField(map, PermissionType.LEAD_FIELD_EMAIL_READ, PermissionType.LEAD_FIELD_EMAIL_WRITE,
                "email", "Email Address", "Personal & Contact Information", 40, "lead email address");

        registerLeadField(map, PermissionType.LEAD_FIELD_CITY_READ, PermissionType.LEAD_FIELD_CITY_WRITE,
                "city", "City", "Location & Address Information", 50, "lead residential city");

        registerLeadField(map, PermissionType.LEAD_FIELD_STATE_READ, PermissionType.LEAD_FIELD_STATE_WRITE,
                "state", "State", "Location & Address Information", 60, "lead state / province");

        registerLeadField(map, PermissionType.LEAD_FIELD_COUNTRY_READ, PermissionType.LEAD_FIELD_COUNTRY_WRITE,
                "country", "Country", "Location & Address Information", 70, "lead country of residence");

        registerLeadField(map, PermissionType.LEAD_FIELD_PREFERRED_LOCATION_READ, PermissionType.LEAD_FIELD_PREFERRED_LOCATION_WRITE,
                "preferredLocation", "Preferred Location", "Location & Address Information", 80, "preferred campus / branch location");

        registerLeadField(map, PermissionType.LEAD_FIELD_PROGRAM_READ, PermissionType.LEAD_FIELD_PROGRAM_WRITE,
                "program", "Program", "Academic & Course Information", 90, "academic program");

        registerLeadField(map, PermissionType.LEAD_FIELD_COURSE_TYPE_READ, PermissionType.LEAD_FIELD_COURSE_TYPE_WRITE,
                "courseType", "Course Type", "Academic & Course Information", 100, "course category / type");

        registerLeadField(map, PermissionType.LEAD_FIELD_COURSE_READ, PermissionType.LEAD_FIELD_COURSE_WRITE,
                "course", "Course", "Academic & Course Information", 110, "primary interested course");

        registerLeadField(map, PermissionType.LEAD_FIELD_INTERESTED_COURSES_READ, PermissionType.LEAD_FIELD_INTERESTED_COURSES_WRITE,
                "interestedCourses", "Interested Courses", "Academic & Course Information", 120, "additional courses expressing interest");

        registerLeadField(map, PermissionType.LEAD_FIELD_BOARD_READ, PermissionType.LEAD_FIELD_BOARD_WRITE,
                "board", "Board", "Academic & Course Information", 130, "educational examination board");

        registerLeadField(map, PermissionType.LEAD_FIELD_GRADE_READ, PermissionType.LEAD_FIELD_GRADE_WRITE,
                "grade", "Grade", "Academic & Course Information", 140, "academic grade / score");

        registerLeadField(map, PermissionType.LEAD_FIELD_LEAD_SOURCE_READ, PermissionType.LEAD_FIELD_LEAD_SOURCE_WRITE,
                "leadSource", "Lead Source", "Lead Acquisition & Assignment", 150, "origin source of the lead");

        registerLeadField(map, PermissionType.LEAD_FIELD_SOURCE_DETAILS_READ, PermissionType.LEAD_FIELD_SOURCE_DETAILS_WRITE,
                "sourceDetails", "Source Details", "Lead Acquisition & Assignment", 160, "additional campaign or source details");

        registerLeadField(map, PermissionType.LEAD_FIELD_DEPARTMENT_READ, PermissionType.LEAD_FIELD_DEPARTMENT_WRITE,
                "department", "Department", "Lead Acquisition & Assignment", 170, "assigned department");

        registerLeadField(map, PermissionType.LEAD_FIELD_ASSIGNED_TO_READ, PermissionType.LEAD_FIELD_ASSIGNED_TO_WRITE,
                "assignedTo", "Assigned To", "Lead Acquisition & Assignment", 180, "assigned counselor / user");

        registerLeadField(map, PermissionType.LEAD_FIELD_AVAILED_READ, PermissionType.LEAD_FIELD_AVAILED_WRITE,
                "availed", "Availed Status", "Lead Acquisition & Assignment", 190, "lead availed/unavailed contact status");

        registerLeadField(map, PermissionType.LEAD_FIELD_CURRENT_STATUS_READ, PermissionType.LEAD_FIELD_CURRENT_STATUS_WRITE,
                "currentStatus", "Current Status", "Status & Follow Up Details", 200, "current disposition / lead status");

        registerLeadField(map, PermissionType.LEAD_FIELD_STATUS_HISTORY_READ, null,
                "statusHistory", "Status History", "Status & Follow Up Details", 210, "historical timeline of status changes");

        registerLeadField(map, PermissionType.LEAD_FIELD_FOLLOW_UP_READ, PermissionType.LEAD_FIELD_FOLLOW_UP_WRITE,
                "followUp", "Follow Up", "Status & Follow Up Details", 220, "follow-up schedule and updates");

        registerLeadField(map, PermissionType.LEAD_FIELD_REMARKS_READ, PermissionType.LEAD_FIELD_REMARKS_WRITE,
                "remarks", "Remarks", "Status & Follow Up Details", 230, "internal notes and remarks");

        registerLeadField(map, PermissionType.LEAD_FIELD_VISIT_PLANNING_READ, PermissionType.LEAD_FIELD_VISIT_PLANNING_WRITE,
                "visitPlanning", "Visit Planning", "Status & Follow Up Details", 240, "campus visit planning details");

        registerLeadField(map, PermissionType.LEAD_FIELD_LEAD_CODE_READ, null,
                "leadCode", "Lead Code", "System & Audit Fields", 250, "unique system generated lead identifier");

        registerLeadField(map, PermissionType.LEAD_FIELD_AUDIT_INFO_READ, null,
                "auditInfo", "Audit Info (Created/Updated)", "System & Audit Fields", 260, "creation, update, and owner audit metadata");

        // ==========================================
        // 2. GENERAL SYSTEM PERMISSIONS (GENERAL_SYSTEM)
        // ==========================================
        // Lead Core
        reg(map, PermissionType.LEAD_READ, PermissionEntity.LEAD, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.VIEW, "View leads list and basic details");
        reg(map, PermissionType.LEAD_CREATE, PermissionEntity.LEAD, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.CREATE, "Create a new lead manually");
        reg(map, PermissionType.LEAD_UPDATE, PermissionEntity.LEAD, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.UPDATE, "Update lead details");
        reg(map, PermissionType.LEAD_DELETE, PermissionEntity.LEAD, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.DELETE, "Soft delete a lead");
        reg(map, PermissionType.LEAD_ASSIGN, PermissionEntity.LEAD, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.ASSIGN, "Assign leads to counselors/users");
        reg(map, PermissionType.LEAD_STATUS_CHANGE, PermissionEntity.LEAD, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.UPDATE, "Change lead workflow status");
        reg(map, PermissionType.LEAD_FEEDBACK_CREATE, PermissionEntity.LEAD, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.CREATE, "Add call feedback notes to a lead");
        reg(map, PermissionType.LEAD_FOLLOWUP_CREATE, PermissionEntity.LEAD, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.CREATE, "Schedule follow-up tasks for a lead");
        reg(map, PermissionType.LEAD_HISTORY_READ, PermissionEntity.LEAD, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.VIEW, "View lead activity history and audit logs");
        reg(map, PermissionType.LEAD_BULK_UPLOAD, PermissionEntity.LEAD, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.UPLOAD, "Import leads via Excel/CSV file upload");
        reg(map, PermissionType.LEAD_BULK_UPLOAD_TEMPLATE_DOWNLOAD, PermissionEntity.LEAD, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.EXPORT, "Download bulk upload Excel template");
        reg(map, PermissionType.LEAD_DISTRIBUTE, PermissionEntity.LEAD, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.EXECUTE, "Execute lead distribution engine algorithm");
        reg(map, PermissionType.LEAD_DISTRIBUTION_PREVIEW, PermissionEntity.LEAD, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.VIEW, "Preview lead distribution allocation results");
        reg(map, PermissionType.LEAD_AVAIL, PermissionEntity.LEAD, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.UPDATE, "Mark leads as availed or unavailed");
        reg(map, PermissionType.LEAD_REASSIGN, PermissionEntity.LEAD, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.ASSIGN, "Reassign an individual lead to another user");
        reg(map, PermissionType.LEAD_BULK_REASSIGN, PermissionEntity.LEAD, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.ASSIGN, "Bulk reassign selected leads to counselors");
        reg(map, PermissionType.LEAD_INTERESTED_COURSE_UPDATE, PermissionEntity.LEAD, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.UPDATE, "Update interested courses on a lead");
        reg(map, PermissionType.LEAD_REGISTERED_COURSE_UPDATE, PermissionEntity.LEAD, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.UPDATE, "Update registered courses on a lead");

        // Follow Up
        reg(map, PermissionType.FOLLOWUP_VIEW, PermissionEntity.FOLLOW_UP, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.VIEW, "View scheduled follow-ups");
        reg(map, PermissionType.FOLLOWUP_CREATE, PermissionEntity.FOLLOW_UP, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.CREATE, "Create follow-up task");
        reg(map, PermissionType.FOLLOWUP_UPDATE, PermissionEntity.FOLLOW_UP, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.UPDATE, "Update follow-up status or schedule");
        reg(map, PermissionType.FOLLOWUP_DELETE, PermissionEntity.FOLLOW_UP, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.DELETE, "Cancel or delete follow-up task");
        reg(map, PermissionType.FOLLOW_UP_REASSIGN, PermissionEntity.FOLLOW_UP, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.ASSIGN, "Reassign follow-up task to another counselor");
        reg(map, PermissionType.FOLLOW_UP_BULK_REASSIGN, PermissionEntity.FOLLOW_UP, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.ASSIGN, "Bulk reassign follow-ups across counselors");
        reg(map, PermissionType.FOLLOW_UP_REASSIGN_OVERRIDE_WORKLOAD, PermissionEntity.FOLLOW_UP, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.MANAGE, "Override counselor workload limit when reassigning follow-ups");

        // Feedback
        reg(map, PermissionType.FEEDBACK_VIEW, PermissionEntity.FEEDBACK, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.VIEW, "View lead feedback entries");
        reg(map, PermissionType.FEEDBACK_CREATE, PermissionEntity.FEEDBACK, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.CREATE, "Add call feedback notes");
        reg(map, PermissionType.FEEDBACK_UPDATE, PermissionEntity.FEEDBACK, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.UPDATE, "Edit call feedback notes");
        reg(map, PermissionType.FEEDBACK_DELETE, PermissionEntity.FEEDBACK, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.DELETE, "Delete call feedback notes");

        // Dashboard
        reg(map, PermissionType.DASHBOARD_VIEW, PermissionEntity.DASHBOARD, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.VIEW, "View operational dashboard metrics");
        reg(map, PermissionType.DASHBOARD_VIEW_ALL, PermissionEntity.DASHBOARD, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.VIEW, "View system-wide dashboard metrics across all departments");
        reg(map, PermissionType.DASHBOARD_VIEW_DEPARTMENT, PermissionEntity.DASHBOARD, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.VIEW, "View department-scoped dashboard metrics");
        reg(map, PermissionType.DASHBOARD_COURSE_TYPE_VIEW, PermissionEntity.DASHBOARD, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.VIEW, "View dashboard breakdown by course type");
        reg(map, PermissionType.DASHBOARD_CARD_VIEW, PermissionEntity.DASHBOARD, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.VIEW, "View dashboard summary metric cards");
        reg(map, PermissionType.DASHBOARD_CARD_PREFERENCE_UPDATE, PermissionEntity.DASHBOARD, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.UPDATE, "Save custom dashboard card display preferences");
        reg(map, PermissionType.DASHBOARD_CARD_ORDER_UPDATE, PermissionEntity.DASHBOARD, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.UPDATE, "Reorder dashboard cards");
        reg(map, PermissionType.DASHBOARD_USER_PREFERENCE_MANAGE, PermissionEntity.DASHBOARD, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.MANAGE, "Manage user dashboard card defaults");
        reg(map, PermissionType.DASHBOARD_LOW_DATA_USERS_VIEW, PermissionEntity.DASHBOARD, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.VIEW, "View low data user metrics card");
        reg(map, PermissionType.DASHBOARD_LOW_DATA_USERS_READ, PermissionEntity.DASHBOARD, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.VIEW, "Read low data user list");
        reg(map, PermissionType.DASHBOARD_USERS_NOT_LOGGED_IN_VIEW, PermissionEntity.DASHBOARD, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.VIEW, "View unlogged users dashboard widget");
        reg(map, PermissionType.DASHBOARD_USERS_NOT_LOGGED_IN_READ, PermissionEntity.DASHBOARD, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.VIEW, "Read unlogged users details");
        reg(map, PermissionType.DASHBOARD_FOLLOWUP_USERS_NOT_LOGGED_IN_11AM_VIEW, PermissionEntity.DASHBOARD, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.VIEW, "View 11 AM follow-up attendance card");
        reg(map, PermissionType.DASHBOARD_FOLLOWUP_USERS_NOT_LOGGED_IN_11AM_READ, PermissionEntity.DASHBOARD, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.VIEW, "Read 11 AM follow-up attendance details");

        // Data Segregation
        reg(map, PermissionType.DATA_SEGREGATION_VIEW, PermissionEntity.DATA_SEGREGATION, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.VIEW, "View data segregation reports");
        reg(map, PermissionType.DATA_SEGREGATION_FULL_FLOW_VIEW, PermissionEntity.DATA_SEGREGATION, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.VIEW, "View full-flow data segregation pipeline");
        reg(map, PermissionType.DATA_SEGREGATION_COURSE_TYPE_VIEW, PermissionEntity.DATA_SEGREGATION, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.VIEW, "View segregation breakdown by course type");
        reg(map, PermissionType.DATA_SEGREGATION_SOURCE_VIEW, PermissionEntity.DATA_SEGREGATION, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.VIEW, "View segregation breakdown by lead source");
        reg(map, PermissionType.DATA_SEGREGATION_BOARD_VIEW, PermissionEntity.DATA_SEGREGATION, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.VIEW, "View segregation breakdown by board");
        reg(map, PermissionType.DATA_SEGREGATION_GRADE_VIEW, PermissionEntity.DATA_SEGREGATION, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.VIEW, "View segregation breakdown by grade");
        reg(map, PermissionType.DATA_SEGREGATION_USER_ANALYTICS, PermissionEntity.DATA_SEGREGATION, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.VIEW, "View counselor performance analytics segregation");
        reg(map, PermissionType.DATA_SEGREGATION_LEAD_STATUS_ANALYTICS, PermissionEntity.DATA_SEGREGATION, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.VIEW, "View status conversion analytics segregation");
        reg(map, PermissionType.DATA_SEGREGATION_COURSE_VIEW, PermissionEntity.DATA_SEGREGATION, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.VIEW, "View course-level segregation data");
        reg(map, PermissionType.DATA_SEGREGATION_COURSE_USER_VIEW, PermissionEntity.DATA_SEGREGATION, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.VIEW, "View course-user mapped segregation");
        reg(map, PermissionType.DATA_SEGREGATION_USER_ALLOCATION_VIEW, PermissionEntity.DATA_SEGREGATION, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.VIEW, "View total users with allotted data analytics card");
        reg(map, PermissionType.DATA_SEGREGATION_CURRENTLY_WORKING_USERS_VIEW, PermissionEntity.DATA_SEGREGATION, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.VIEW, "View users currently working analytics card");
        reg(map, PermissionType.DATA_SEGREGATION_USER_ALLOCATION_USERS_VIEW, PermissionEntity.DATA_SEGREGATION, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.VIEW, "View user allocation detailed list");
        reg(map, PermissionType.DATA_SEGREGATION_COURSE_USER_STATUS_ANALYTICS_VIEW, PermissionEntity.DATA_SEGREGATION, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.VIEW, "View Course-wise and User-wise lead status analytics");

        // User Activity
        reg(map, PermissionType.USER_ACTIVITY_VIEW, PermissionEntity.USER_ACTIVITY, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.VIEW, "View user activity timeline");
        reg(map, PermissionType.USER_ACTIVITY_DETAILS, PermissionEntity.USER_ACTIVITY, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.VIEW, "View detailed user audit log payloads");

        // Email
        reg(map, PermissionType.EMAIL_SEND, PermissionEntity.EMAIL, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.EXECUTE, "Send manual or triggered emails");
        reg(map, PermissionType.EMAIL_LOG_VIEW, PermissionEntity.EMAIL, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.VIEW, "View email delivery logs and statuses");
        reg(map, PermissionType.EMAIL_CONFIG_VIEW, PermissionEntity.EMAIL, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.VIEW, "View SMTP email configuration");
        reg(map, PermissionType.EMAIL_CONFIG_TEST, PermissionEntity.EMAIL, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.EXECUTE, "Send test email to verify SMTP connection");

        // User
        reg(map, PermissionType.USER_READ, PermissionEntity.USER, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.VIEW, "View users and profiles");
        reg(map, PermissionType.USER_CREATE, PermissionEntity.USER, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.CREATE, "Create a new user account");
        reg(map, PermissionType.USER_UPDATE, PermissionEntity.USER, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.UPDATE, "Update user profile and status");
        reg(map, PermissionType.USER_DELETE, PermissionEntity.USER, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.DELETE, "Deactivate or delete user account");
        reg(map, PermissionType.USER_ROLE_ASSIGN, PermissionEntity.USER, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.ASSIGN, "Assign roles to user accounts");
        reg(map, PermissionType.USER_COURSE_MATRIX_VIEW, PermissionEntity.USER, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.VIEW, "View user-course assignment matrix");
        reg(map, PermissionType.USER_PROGRAM_MATRIX_VIEW, PermissionEntity.USER, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.VIEW, "View user-program assignment matrix");

        // Auth
        reg(map, PermissionType.AUTH_READ, PermissionEntity.AUTH, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.VIEW, "View active auth sessions and tokens");
        reg(map, PermissionType.AUTH_CREATE, PermissionEntity.AUTH, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.CREATE, "Authenticate / login credentials");
        reg(map, PermissionType.AUTH_UPDATE, PermissionEntity.AUTH, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.UPDATE, "Refresh or update authentication session");
        reg(map, PermissionType.AUTH_DELETE, PermissionEntity.AUTH, PermissionGroup.GENERAL_SYSTEM, PermissionOperationType.DELETE, "Revoke token / logout session");

        // ==========================================
        // 3. SYSTEM CONFIGURATION PERMISSIONS (SYSTEM_CONFIG)
        // ==========================================
        // Roles & Permissions
        reg(map, PermissionType.ROLE_READ, PermissionEntity.ROLE, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.VIEW, "View system roles and details");
        reg(map, PermissionType.ROLE_CREATE, PermissionEntity.ROLE, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.CREATE, "Create a new security role");
        reg(map, PermissionType.ROLE_UPDATE, PermissionEntity.ROLE, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.UPDATE, "Update role metadata");
        reg(map, PermissionType.ROLE_DELETE, PermissionEntity.ROLE, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.DELETE, "Delete custom security role");
        reg(map, PermissionType.ROLE_ASSIGN_PERMISSION, PermissionEntity.ROLE, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.ASSIGN, "Configure permission grants for a role");

        reg(map, PermissionType.PERMISSION_READ, PermissionEntity.PERMISSION, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.VIEW, "View permissions catalog");
        reg(map, PermissionType.PERMISSION_CREATE, PermissionEntity.PERMISSION, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.CREATE, "Define a new dynamic permission");
        reg(map, PermissionType.PERMISSION_UPDATE, PermissionEntity.PERMISSION, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.UPDATE, "Update dynamic permission metadata");
        reg(map, PermissionType.PERMISSION_DELETE, PermissionEntity.PERMISSION, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.DELETE, "Deactivate or delete permission");

        // Departments
        reg(map, PermissionType.DEPARTMENT_VIEW, PermissionEntity.DEPARTMENT, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.VIEW, "View departments list");
        reg(map, PermissionType.DEPARTMENT_CREATE, PermissionEntity.DEPARTMENT, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.CREATE, "Create a new department");
        reg(map, PermissionType.DEPARTMENT_UPDATE, PermissionEntity.DEPARTMENT, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.UPDATE, "Update department details");
        reg(map, PermissionType.DEPARTMENT_DELETE, PermissionEntity.DEPARTMENT, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.DELETE, "Delete department");
        reg(map, PermissionType.DEPARTMENT_USER_VIEW, PermissionEntity.DEPARTMENT, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.VIEW, "View users belonging to department");
        reg(map, PermissionType.DEPARTMENT_USER_ASSIGN, PermissionEntity.DEPARTMENT, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.ASSIGN, "Assign users to department");
        reg(map, PermissionType.DEPARTMENT_USER_REMOVE, PermissionEntity.DEPARTMENT, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.DELETE, "Remove user from department");
        reg(map, PermissionType.DEPARTMENT_HOD_VIEW, PermissionEntity.DEPARTMENT, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.VIEW, "View HODs of department");
        reg(map, PermissionType.DEPARTMENT_COUNSELLOR_VIEW, PermissionEntity.DEPARTMENT, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.VIEW, "View counselors of department");
        reg(map, PermissionType.DEPARTMENT_DATA_VIEW, PermissionEntity.DEPARTMENT, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.VIEW, "View department data allocation");
        reg(map, PermissionType.DEPARTMENT_DATA_CREATE, PermissionEntity.DEPARTMENT, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.CREATE, "Create department data rule");
        reg(map, PermissionType.DEPARTMENT_DATA_UPDATE, PermissionEntity.DEPARTMENT, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.UPDATE, "Update department data rule");
        reg(map, PermissionType.DEPARTMENT_DATA_DELETE, PermissionEntity.DEPARTMENT, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.DELETE, "Delete department data rule");

        // Lead Statuses
        reg(map, PermissionType.LEAD_STATUS_VIEW, PermissionEntity.LEAD_STATUS, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.VIEW, "View lead statuses and stages");
        reg(map, PermissionType.LEAD_STATUS_CREATE, PermissionEntity.LEAD_STATUS, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.CREATE, "Create a new lead status");
        reg(map, PermissionType.LEAD_STATUS_UPDATE, PermissionEntity.LEAD_STATUS, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.UPDATE, "Update lead status definition");
        reg(map, PermissionType.LEAD_STATUS_DELETE, PermissionEntity.LEAD_STATUS, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.DELETE, "Delete lead status");
        reg(map, PermissionType.LEAD_STATUS_HISTORY_VIEW, PermissionEntity.LEAD_STATUS, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.VIEW, "View global lead status change logs");

        // Lead Sources
        reg(map, PermissionType.LEADSOURCE_READ, PermissionEntity.LEADSOURCE, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.VIEW, "View lead sources");
        reg(map, PermissionType.LEADSOURCE_CREATE, PermissionEntity.LEADSOURCE, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.CREATE, "Create lead source");
        reg(map, PermissionType.LEADSOURCE_UPDATE, PermissionEntity.LEADSOURCE, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.UPDATE, "Update lead source");
        reg(map, PermissionType.LEADSOURCE_DELETE, PermissionEntity.LEADSOURCE, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.DELETE, "Delete lead source");

        // Courses & Programs
        reg(map, PermissionType.COURSE_VIEW, PermissionEntity.COURSE, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.VIEW, "View courses catalog");
        reg(map, PermissionType.COURSE_CREATE, PermissionEntity.COURSE, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.CREATE, "Create a new course");
        reg(map, PermissionType.COURSE_UPDATE, PermissionEntity.COURSE, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.UPDATE, "Update course details");
        reg(map, PermissionType.COURSE_DELETE, PermissionEntity.COURSE, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.DELETE, "Delete course");

        reg(map, PermissionType.PROGRAM_VIEW, PermissionEntity.PROGRAM, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.VIEW, "View academic programs");
        reg(map, PermissionType.PROGRAM_CREATE, PermissionEntity.PROGRAM, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.CREATE, "Create academic program");
        reg(map, PermissionType.PROGRAM_UPDATE, PermissionEntity.PROGRAM, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.UPDATE, "Update academic program");
        reg(map, PermissionType.PROGRAM_DELETE, PermissionEntity.PROGRAM, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.DELETE, "Delete academic program");

        reg(map, PermissionType.COURSE_TYPE_VIEW, PermissionEntity.COURSE_TYPE, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.VIEW, "View course categories/types");
        reg(map, PermissionType.COURSE_TYPE_CREATE, PermissionEntity.COURSE_TYPE, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.CREATE, "Create course type");
        reg(map, PermissionType.COURSE_TYPE_UPDATE, PermissionEntity.COURSE_TYPE, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.UPDATE, "Update course type");
        reg(map, PermissionType.COURSE_TYPE_DELETE, PermissionEntity.COURSE_TYPE, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.DELETE, "Delete course type");

        reg(map, PermissionType.COURSE_TEMPLATE_VIEW, PermissionEntity.COURSE_TEMPLATE, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.VIEW, "View course outreach templates");
        reg(map, PermissionType.COURSE_TEMPLATE_CREATE, PermissionEntity.COURSE_TEMPLATE, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.CREATE, "Create course outreach template");
        reg(map, PermissionType.COURSE_TEMPLATE_UPDATE, PermissionEntity.COURSE_TEMPLATE, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.UPDATE, "Update course outreach template");
        reg(map, PermissionType.COURSE_TEMPLATE_DELETE, PermissionEntity.COURSE_TEMPLATE, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.DELETE, "Delete course outreach template");
        reg(map, PermissionType.COURSE_TEMPLATE_SEND, PermissionEntity.COURSE_TEMPLATE, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.EXECUTE, "Dispatch course template messages");
        reg(map, PermissionType.COURSE_TEMPLATE_SEND_EMAIL, PermissionEntity.COURSE_TEMPLATE, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.EXECUTE, "Dispatch course template via Email");
        reg(map, PermissionType.COURSE_TEMPLATE_SEND_WHATSAPP, PermissionEntity.COURSE_TEMPLATE, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.EXECUTE, "Dispatch course template via WhatsApp");
        reg(map, PermissionType.COURSE_TEMPLATE_IMAGE_SELECT, PermissionEntity.COURSE_TEMPLATE, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.UPDATE, "Select promotional banner for template");

        reg(map, PermissionType.COURSE_IMAGE_VIEW, PermissionEntity.COURSE_IMAGE, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.VIEW, "View course marketing images");
        reg(map, PermissionType.COURSE_IMAGE_UPLOAD, PermissionEntity.COURSE_IMAGE, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.UPLOAD, "Upload course marketing image");
        reg(map, PermissionType.COURSE_IMAGE_UPDATE, PermissionEntity.COURSE_IMAGE, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.UPDATE, "Update course marketing image");
        reg(map, PermissionType.COURSE_IMAGE_DELETE, PermissionEntity.COURSE_IMAGE, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.DELETE, "Delete course marketing image");

        reg(map, PermissionType.COURSE_USP_VIEW, PermissionEntity.COURSE_USP, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.VIEW, "View course unique selling points (USPs)");
        reg(map, PermissionType.COURSE_USP_CREATE, PermissionEntity.COURSE_USP, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.CREATE, "Create course USP");
        reg(map, PermissionType.COURSE_USP_UPDATE, PermissionEntity.COURSE_USP, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.UPDATE, "Update course USP");
        reg(map, PermissionType.COURSE_USP_DELETE, PermissionEntity.COURSE_USP, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.DELETE, "Delete course USP");

        // Boards & Grades
        reg(map, PermissionType.BOARD_VIEW, PermissionEntity.BOARD, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.VIEW, "View education boards");
        reg(map, PermissionType.BOARD_CREATE, PermissionEntity.BOARD, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.CREATE, "Create education board");
        reg(map, PermissionType.BOARD_UPDATE, PermissionEntity.BOARD, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.UPDATE, "Update education board");
        reg(map, PermissionType.BOARD_DELETE, PermissionEntity.BOARD, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.DELETE, "Delete education board");

        reg(map, PermissionType.GRADE_VIEW, PermissionEntity.GRADE, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.VIEW, "View academic grades");
        reg(map, PermissionType.GRADE_CREATE, PermissionEntity.GRADE, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.CREATE, "Create academic grade");
        reg(map, PermissionType.GRADE_UPDATE, PermissionEntity.GRADE, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.UPDATE, "Update academic grade");
        reg(map, PermissionType.GRADE_DELETE, PermissionEntity.GRADE, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.DELETE, "Delete academic grade");

        // Dropdowns
        reg(map, PermissionType.DROPDOWN_USER_VIEW, PermissionEntity.DROPDOWN, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.VIEW, "Access users list dropdown options");
        reg(map, PermissionType.DROPDOWN_DEPARTMENT_VIEW, PermissionEntity.DROPDOWN, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.VIEW, "Access departments dropdown options");
        reg(map, PermissionType.DROPDOWN_LEAD_VIEW, PermissionEntity.DROPDOWN, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.VIEW, "Access leads dropdown options");
        reg(map, PermissionType.DROPDOWN_COURSE_VIEW, PermissionEntity.DROPDOWN, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.VIEW, "Access courses dropdown options");
        reg(map, PermissionType.DROPDOWN_PROGRAM_VIEW, PermissionEntity.DROPDOWN, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.VIEW, "Access programs dropdown options");
        reg(map, PermissionType.DROPDOWN_STATUS_VIEW, PermissionEntity.DROPDOWN, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.VIEW, "Access lead statuses dropdown options");
        reg(map, PermissionType.DROPDOWN_SOURCE_VIEW, PermissionEntity.DROPDOWN, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.VIEW, "Access lead sources dropdown options");
        reg(map, PermissionType.DROPDOWN_GRADE_VIEW, PermissionEntity.DROPDOWN, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.VIEW, "Access grades dropdown options");
        reg(map, PermissionType.DROPDOWN_BOARD_VIEW, PermissionEntity.DROPDOWN, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.VIEW, "Access boards dropdown options");
        reg(map, PermissionType.DROPDOWN_COURSE_TYPE_VIEW, PermissionEntity.DROPDOWN, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.VIEW, "Access course types dropdown options");
        reg(map, PermissionType.DROPDOWN_ROLE_VIEW, PermissionEntity.DROPDOWN, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.VIEW, "Access roles dropdown options");
        reg(map, PermissionType.DROPDOWN_PERMISSION_VIEW, PermissionEntity.DROPDOWN, PermissionGroup.SYSTEM_CONFIG, PermissionOperationType.VIEW, "Access permissions dropdown options");

        return map;
    }

    private static void registerLeadField(Map<PermissionType, PermissionMetadata> map,
                                          PermissionType readPerm,
                                          PermissionType writePerm,
                                          String fieldKey,
                                          String fieldLabel,
                                          String fieldGroup,
                                          int displayOrder,
                                          String fieldSubject) {
        if (readPerm != null) {
            map.put(readPerm, PermissionMetadata.builder()
                    .code(readPerm.name())
                    .description("View " + fieldSubject)
                    .entity(PermissionEntity.LEAD)
                    .permissionGroup(PermissionGroup.LEAD_FIELD)
                    .permissionType(PermissionOperationType.VIEW)
                    .fieldKey(fieldKey)
                    .fieldLabel(fieldLabel)
                    .fieldGroup(fieldGroup)
                    .displayOrder(displayOrder)
                    .build());
        }
        if (writePerm != null) {
            map.put(writePerm, PermissionMetadata.builder()
                    .code(writePerm.name())
                    .description("Edit " + fieldSubject)
                    .entity(PermissionEntity.LEAD)
                    .permissionGroup(PermissionGroup.LEAD_FIELD)
                    .permissionType(PermissionOperationType.EDIT)
                    .fieldKey(fieldKey)
                    .fieldLabel(fieldLabel)
                    .fieldGroup(fieldGroup)
                    .displayOrder(displayOrder + 1)
                    .build());
        }
    }

    private static void reg(Map<PermissionType, PermissionMetadata> map,
                            PermissionType type,
                            PermissionEntity entity,
                            PermissionGroup group,
                            PermissionOperationType opType,
                            String description) {
        map.put(type, PermissionMetadata.builder()
                .code(type.name())
                .description(description)
                .entity(entity)
                .permissionGroup(group)
                .permissionType(opType)
                .fieldKey(null)
                .fieldLabel(null)
                .fieldGroup(null)
                .displayOrder(null)
                .build());
    }
}
