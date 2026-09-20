package com.app.datadistribution.service.impl;

import com.app.datadistribution.dto.lead.LeadRequest;
import com.app.datadistribution.dto.lead.LeadResponse;
import com.app.datadistribution.entity.Course;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadSource;
import com.app.datadistribution.enums.PermissionType;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.service.interfaces.ILeadFieldSecurityService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class LeadFieldSecurityServiceImpl implements ILeadFieldSecurityService {

    private Set<String> getCurrentUserAuthorities() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Collections.emptySet();
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(String::toUpperCase)
                .collect(Collectors.toSet());
    }

    private boolean isUserAdmin(Set<String> authorities) {
        return authorities.contains("ROLE_SUPER_ADMIN")
                || authorities.contains("ROLE_ADMIN")
                || authorities.contains("SUPER_ADMIN")
                || authorities.contains("ADMIN");
    }

    @Override
    public boolean hasReadPermission(String permissionName) {
        Set<String> authorities = getCurrentUserAuthorities();
        if (isUserAdmin(authorities)) {
            return true;
        }
        return authorities.contains(permissionName.toUpperCase());
    }

    @Override
    public boolean hasWritePermission(String permissionName) {
        Set<String> authorities = getCurrentUserAuthorities();
        if (isUserAdmin(authorities)) {
            return true;
        }
        return authorities.contains(permissionName.toUpperCase());
    }

    private void checkWritePermission(Set<String> authorities, PermissionType permissionType, String fieldDisplayName) throws UnauthorizedException {
        if (isUserAdmin(authorities)) {
            return;
        }
        if (!authorities.contains(permissionType.name())) {
            log.warn("Unauthorized field update attempt for '{}'. Missing permission: {}", fieldDisplayName, permissionType.name());
            throw new UnauthorizedException("You do not have permission to modify field: " + fieldDisplayName);
        }
    }

    private boolean isDifferent(Object val1, Object val2) {
        if (val1 == null && val2 == null) return false;
        if (val1 == null || val2 == null) {
            if (val1 instanceof String && ((String) val1).trim().isEmpty() && val2 == null) return false;
            if (val2 instanceof String && ((String) val2).trim().isEmpty() && val1 == null) return false;
            return true;
        }
        if (val1 instanceof String && val2 instanceof String) {
            return !((String) val1).trim().equals(((String) val2).trim());
        }
        return !val1.equals(val2);
    }

    private boolean isDifferentCollections(Set<UUID> set1, List<UUID> list2) {
        Set<UUID> s1 = set1 != null ? set1 : Collections.emptySet();
        Set<UUID> s2 = list2 != null ? new HashSet<>(list2) : Collections.emptySet();
        return !s1.equals(s2);
    }

    @Override
    public void validateFieldUpdates(Lead existingLead, LeadRequest request) throws UnauthorizedException {
        if (existingLead == null || request == null) {
            return;
        }

        Set<String> authorities = getCurrentUserAuthorities();
        if (isUserAdmin(authorities)) {
            return;
        }

        // 1. Full Name
        if (request.getFullName() != null && isDifferent(existingLead.getFullName(), request.getFullName())) {
            checkWritePermission(authorities, PermissionType.LEAD_FIELD_FULL_NAME_WRITE, "Full Name");
        }

        // 2. Phone Number
        if (request.getPhoneNumber() != null && isDifferent(existingLead.getPhoneNumber(), request.getPhoneNumber())) {
            checkWritePermission(authorities, PermissionType.LEAD_FIELD_PHONE_NUMBER_WRITE, "Phone Number");
        }

        // 3. Alternate Phone Number
        if (request.getAlternatePhoneNumber() != null && isDifferent(existingLead.getAlternatePhoneNumber(), request.getAlternatePhoneNumber())) {
            checkWritePermission(authorities, PermissionType.LEAD_FIELD_ALTERNATE_PHONE_WRITE, "Alternate Phone Number");
        }

        // 4. Email
        if (request.getEmail() != null && isDifferent(existingLead.getEmail(), request.getEmail())) {
            checkWritePermission(authorities, PermissionType.LEAD_FIELD_EMAIL_WRITE, "Email Address");
        }

        // 5. City
        if (request.getCity() != null && isDifferent(existingLead.getCity(), request.getCity())) {
            checkWritePermission(authorities, PermissionType.LEAD_FIELD_CITY_WRITE, "City");
        }

        // 6. State
        if (request.getState() != null && isDifferent(existingLead.getState(), request.getState())) {
            checkWritePermission(authorities, PermissionType.LEAD_FIELD_STATE_WRITE, "State");
        }

        // 7. Country
        if (request.getCountry() != null && isDifferent(existingLead.getCountry(), request.getCountry())) {
            checkWritePermission(authorities, PermissionType.LEAD_FIELD_COUNTRY_WRITE, "Country");
        }

        // 8. Preferred Location
        if (isDifferent(existingLead.getPreferredStudyState(), request.getPreferredStudyState())
                || isDifferent(existingLead.getPreferredStudyCity(), request.getPreferredStudyCity())) {
            checkWritePermission(authorities, PermissionType.LEAD_FIELD_PREFERRED_LOCATION_WRITE, "Preferred Location");
        }

        // 9. Lead Sources
        if (request.getLeadSourceIds() != null) {
            Set<UUID> existingSourceIds = existingLead.getLeadSources() != null
                    ? existingLead.getLeadSources().stream().map(LeadSource::getId).collect(Collectors.toSet())
                    : Collections.emptySet();
            if (isDifferentCollections(existingSourceIds, request.getLeadSourceIds())) {
                checkWritePermission(authorities, PermissionType.LEAD_FIELD_LEAD_SOURCE_WRITE, "Lead Source");
            }
        }

        // 10. Source Details
        if (request.getSourceDetails() != null && isDifferent(existingLead.getSourceDetails(), request.getSourceDetails())) {
            checkWritePermission(authorities, PermissionType.LEAD_FIELD_SOURCE_DETAILS_WRITE, "Source Details");
        }

        // 11. Course / Registered Course
        UUID regCourseId = request.getRegisteredCourseId() != null ? request.getRegisteredCourseId() : request.getCourseId();
        UUID existingCourseId = existingLead.getCourse() != null ? existingLead.getCourse().getId() : null;
        if (regCourseId != null && isDifferent(existingCourseId, regCourseId)) {
            checkWritePermission(authorities, PermissionType.LEAD_FIELD_COURSE_WRITE, "Course");
        }

        // 12. Interested Courses
        if (request.getInterestedCourseIds() != null) {
            Set<UUID> existingInterestedIds = existingLead.getInterestedCourses() != null
                    ? existingLead.getInterestedCourses().stream().map(Course::getId).collect(Collectors.toSet())
                    : Collections.emptySet();
            if (isDifferentCollections(existingInterestedIds, request.getInterestedCourseIds())) {
                checkWritePermission(authorities, PermissionType.LEAD_FIELD_INTERESTED_COURSES_WRITE, "Interested Courses");
            }
        }

        // 13. Program
        UUID existingProgramId = existingLead.getProgram() != null ? existingLead.getProgram().getId() : null;
        if (request.getProgramId() != null && isDifferent(existingProgramId, request.getProgramId())) {
            checkWritePermission(authorities, PermissionType.LEAD_FIELD_PROGRAM_WRITE, "Program");
        }

        // 14. Board
        UUID existingBoardId = existingLead.getBoard() != null ? existingLead.getBoard().getId() : null;
        if (request.getBoardId() != null && isDifferent(existingBoardId, request.getBoardId())) {
            checkWritePermission(authorities, PermissionType.LEAD_FIELD_BOARD_WRITE, "Board");
        }

        // 15. Grade
        UUID existingGradeId = existingLead.getGrade() != null ? existingLead.getGrade().getId() : null;
        if (request.getGradeId() != null && isDifferent(existingGradeId, request.getGradeId())) {
            checkWritePermission(authorities, PermissionType.LEAD_FIELD_GRADE_WRITE, "Grade");
        }

        // 16. Department
        UUID existingDeptId = existingLead.getDepartment() != null ? existingLead.getDepartment().getId() : null;
        if (request.getDepartmentId() != null && isDifferent(existingDeptId, request.getDepartmentId())) {
            checkWritePermission(authorities, PermissionType.LEAD_FIELD_DEPARTMENT_WRITE, "Department");
        }

        // 17. Remarks
        if (request.getRemarks() != null && isDifferent(existingLead.getRemarks(), request.getRemarks())) {
            checkWritePermission(authorities, PermissionType.LEAD_FIELD_REMARKS_WRITE, "Remarks");
        }

        // 18. Campus Visit Planning
        if (isDifferent(existingLead.getPlanningToVisitUniversity(), request.getPlanningToVisitUniversity())
                || isDifferent(existingLead.getVisitDate(), request.getVisitDate())
                || isDifferent(existingLead.getVisitTime(), request.getVisitTime())
                || isDifferent(existingLead.getVisitRemarks(), request.getVisitRemarks())) {
            checkWritePermission(authorities, PermissionType.LEAD_FIELD_VISIT_PLANNING_WRITE, "Campus Visit Planning");
        }

        // 19. Assigned User
        UUID existingAssignedId = existingLead.getAssignedTo() != null ? existingLead.getAssignedTo().getId() : null;
        if (request.getAssignedToUserId() != null && isDifferent(existingAssignedId, request.getAssignedToUserId())) {
            checkWritePermission(authorities, PermissionType.LEAD_FIELD_ASSIGNED_TO_WRITE, "Assigned Counselor");
        }

        // 20. Current Status
        UUID existingStatusId = existingLead.getCurrentStatus() != null ? existingLead.getCurrentStatus().getId() : null;
        if (request.getStatusId() != null && isDifferent(existingStatusId, request.getStatusId())) {
            checkWritePermission(authorities, PermissionType.LEAD_FIELD_CURRENT_STATUS_WRITE, "Lead Status");
        }

        // 21. Next Follow-up Date
        if (request.getNextFollowUpDate() != null && isDifferent(existingLead.getNextFollowUpDate(), request.getNextFollowUpDate())) {
            checkWritePermission(authorities, PermissionType.LEAD_FIELD_FOLLOW_UP_WRITE, "Follow-up Date");
        }
    }

    @Override
    public LeadResponse sanitizeResponse(LeadResponse r) {
        if (r == null) {
            return null;
        }
        Set<String> authorities = getCurrentUserAuthorities();
        if (isUserAdmin(authorities)) {
            return r;
        }

        // Contact
        if (!authorities.contains(PermissionType.LEAD_FIELD_FULL_NAME_READ.name())) {
            r.setFullName(null);
        }
        if (!authorities.contains(PermissionType.LEAD_FIELD_PHONE_NUMBER_READ.name())) {
            r.setPhoneNumber(null);
        }
        if (!authorities.contains(PermissionType.LEAD_FIELD_ALTERNATE_PHONE_READ.name())) {
            r.setAlternatePhoneNumber(null);
        }
        if (!authorities.contains(PermissionType.LEAD_FIELD_EMAIL_READ.name())) {
            r.setEmail(null);
        }
        if (!authorities.contains(PermissionType.LEAD_FIELD_CITY_READ.name())) {
            r.setCity(null);
        }
        if (!authorities.contains(PermissionType.LEAD_FIELD_STATE_READ.name())) {
            r.setState(null);
        }
        if (!authorities.contains(PermissionType.LEAD_FIELD_COUNTRY_READ.name())) {
            r.setCountry(null);
        }
        if (!authorities.contains(PermissionType.LEAD_FIELD_PREFERRED_LOCATION_READ.name())) {
            r.setPreferredStudyState(null);
            r.setPreferredStudyCity(null);
        }

        // Academic
        if (!authorities.contains(PermissionType.LEAD_FIELD_PROGRAM_READ.name())) {
            r.setProgram(null);
        }
        if (!authorities.contains(PermissionType.LEAD_FIELD_COURSE_TYPE_READ.name())) {
            r.setInterestedCourseTypes(null);
        }
        if (!authorities.contains(PermissionType.LEAD_FIELD_COURSE_READ.name())) {
            r.setCourse(null);
            r.setRegisteredCourse(null);
        }
        if (!authorities.contains(PermissionType.LEAD_FIELD_INTERESTED_COURSES_READ.name())) {
            r.setInterestedCourses(null);
        }
        if (!authorities.contains(PermissionType.LEAD_FIELD_BOARD_READ.name())) {
            r.setBoard(null);
        }
        if (!authorities.contains(PermissionType.LEAD_FIELD_GRADE_READ.name())) {
            r.setGrade(null);
        }

        // Source & Business
        if (!authorities.contains(PermissionType.LEAD_FIELD_LEAD_SOURCE_READ.name())) {
            r.setLeadSources(null);
            r.setMultiSource(false);
        }
        if (!authorities.contains(PermissionType.LEAD_FIELD_SOURCE_DETAILS_READ.name())) {
            r.setSourceDetails(null);
        }
        if (!authorities.contains(PermissionType.LEAD_FIELD_DEPARTMENT_READ.name())) {
            r.setDepartment(null);
        }
        if (!authorities.contains(PermissionType.LEAD_FIELD_REMARKS_READ.name())) {
            r.setRemarks(null);
        }

        // Campus Visit
        if (!authorities.contains(PermissionType.LEAD_FIELD_VISIT_PLANNING_READ.name())) {
            r.setPlanningToVisitUniversity(false);
            r.setVisitDate(null);
            r.setVisitTime(null);
            r.setVisitRemarks(null);
        }

        // Assignment & Availed
        if (!authorities.contains(PermissionType.LEAD_FIELD_ASSIGNED_TO_READ.name())) {
            r.setAssignedTo(null);
        }
        if (!authorities.contains(PermissionType.LEAD_FIELD_AVAILED_READ.name())) {
            r.setAvailed(false);
            r.setAvailedAt(null);
            r.setAvailedBy(null);
        }

        // Status & Follow-up
        if (!authorities.contains(PermissionType.LEAD_FIELD_CURRENT_STATUS_READ.name())) {
            r.setCurrentStatus(null);
        }
        if (!authorities.contains(PermissionType.LEAD_FIELD_FOLLOW_UP_READ.name())) {
            r.setNextFollowUpDate(null);
        }

        // System & Audit
        if (!authorities.contains(PermissionType.LEAD_FIELD_LEAD_CODE_READ.name())) {
            r.setLeadCode(null);
        }
        if (!authorities.contains(PermissionType.LEAD_FIELD_AUDIT_INFO_READ.name())) {
            r.setCreatedBy(null);
        }

        return r;
    }

    @Override
    public List<LeadResponse> sanitizeResponses(List<LeadResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            return responses;
        }
        Set<String> authorities = getCurrentUserAuthorities();
        if (isUserAdmin(authorities)) {
            return responses;
        }
        for (LeadResponse r : responses) {
            sanitizeResponse(r);
        }
        return responses;
    }
}
