package com.app.datadistribution.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.app.datadistribution.dto.infopanel.CompetitorComparisonDTO;
import com.app.datadistribution.dto.infopanel.CompetitorResponseDTO;
import com.app.datadistribution.dto.infopanel.CourseInfoPanelRequestDTO;
import com.app.datadistribution.dto.infopanel.CourseInfoPanelResponseDTO;
import com.app.datadistribution.dto.infopanel.InfoPanelPermissionMatrixDTO;
import com.app.datadistribution.entity.CourseInfoPanel;
import com.app.datadistribution.enums.PermissionType;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.service.interfaces.IInfoPanelSecurityService;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class InfoPanelSecurityServiceImpl implements IInfoPanelSecurityService {

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
    public boolean hasReadPermission(PermissionType type) {
        Set<String> authorities = getCurrentUserAuthorities();
        if (isUserAdmin(authorities)) {
            return true;
        }
        return authorities.contains(type.name());
    }

    @Override
    public boolean hasWritePermission(PermissionType type) {
        Set<String> authorities = getCurrentUserAuthorities();
        if (isUserAdmin(authorities)) {
            return true;
        }
        return authorities.contains(type.name());
    }

    private void checkWritePermission(Set<String> authorities, PermissionType permissionType, String fieldDisplayName)
            throws UnauthorizedException {
        if (!isUserAdmin(authorities) && !authorities.contains(permissionType.name())) {
            log.warn("Unauthorized Info Panel field update attempt for '{}'. Missing permission: {}",
                    fieldDisplayName, permissionType.name());
            throw new UnauthorizedException("You do not have permission to modify field: " + fieldDisplayName);
        }
    }

    @Override
    public void validateFieldUpdates(CourseInfoPanel existing, CourseInfoPanelRequestDTO request) throws UnauthorizedException {
        if (request == null) return;
        Set<String> authorities = getCurrentUserAuthorities();
        if (isUserAdmin(authorities)) {
            return;
        }

        // When creating new (existing == null) or updating fields
        if (existing == null || !Objects.equals(existing.getCourseFee(), request.getCourseFee())) {
            checkWritePermission(authorities, PermissionType.INFO_PANEL_FIELD_COURSE_FEE_WRITE, "Course Fee");
        }
        if (existing == null || !Objects.equals(existing.getDuration(), request.getDuration())) {
            checkWritePermission(authorities, PermissionType.INFO_PANEL_FIELD_DURATION_WRITE, "Duration");
        }
        if (existing == null || !Objects.equals(existing.getEligibility(), request.getEligibility())) {
            checkWritePermission(authorities, PermissionType.INFO_PANEL_FIELD_ELIGIBILITY_WRITE, "Eligibility");
        }
        if (existing == null || !Objects.equals(existing.getJobOpportunities(), request.getJobOpportunities())) {
            checkWritePermission(authorities, PermissionType.INFO_PANEL_FIELD_JOB_OPPORTUNITIES_WRITE, "Job Opportunities");
        }
        if (existing == null || !Objects.equals(existing.getHostelFee(), request.getHostelFee())) {
            checkWritePermission(authorities, PermissionType.INFO_PANEL_FIELD_HOSTEL_FEE_WRITE, "Hostel Fee");
        }
        if (existing == null || !Objects.equals(existing.getCourseDetails(), request.getCourseDetails())) {
            checkWritePermission(authorities, PermissionType.INFO_PANEL_FIELD_COURSE_DETAILS_WRITE, "Course Details");
        }
        if (existing == null || !Objects.equals(existing.getCourseSpecialities(), request.getCourseSpecialities())) {
            checkWritePermission(authorities, PermissionType.INFO_PANEL_FIELD_COURSE_SPECIALITIES_WRITE, "Course Specialities");
        }
        if (existing == null || !Objects.equals(existing.getRenaissanceUniversityUsps(), request.getRenaissanceUniversityUsps())) {
            checkWritePermission(authorities, PermissionType.INFO_PANEL_FIELD_RU_USPS_WRITE, "Renaissance University USPs");
        }
        if (existing == null || !Objects.equals(existing.getHowWeAreDifferent(), request.getHowWeAreDifferent())) {
            checkWritePermission(authorities, PermissionType.INFO_PANEL_FIELD_HOW_WE_ARE_DIFFERENT_WRITE, "How We Are Different");
        }
        if (existing == null || !Objects.equals(existing.getCallerGuidance(), request.getCallerGuidance())) {
            checkWritePermission(authorities, PermissionType.INFO_PANEL_FIELD_CALLER_GUIDANCE_WRITE, "Caller Guidance");
        }
    }

    @Override
    public void sanitizeResponse(CourseInfoPanelResponseDTO response) {
        if (response == null) return;
        Set<String> authorities = getCurrentUserAuthorities();
        boolean isAdmin = isUserAdmin(authorities);

        // Map of field permissions to attach to response
        Map<String, Boolean> perms = new LinkedHashMap<>();

        // Helper lambda to check read permission and nullify field if unauthorized
        perms.put("courseFee", isAdmin || authorities.contains(PermissionType.INFO_PANEL_FIELD_COURSE_FEE_READ.name()));
        perms.put("courseFee_edit", isAdmin || authorities.contains(PermissionType.INFO_PANEL_FIELD_COURSE_FEE_WRITE.name()));
        if (!perms.get("courseFee")) response.setCourseFee(null);

        perms.put("duration", isAdmin || authorities.contains(PermissionType.INFO_PANEL_FIELD_DURATION_READ.name()));
        perms.put("duration_edit", isAdmin || authorities.contains(PermissionType.INFO_PANEL_FIELD_DURATION_WRITE.name()));
        if (!perms.get("duration")) response.setDuration(null);

        perms.put("eligibility", isAdmin || authorities.contains(PermissionType.INFO_PANEL_FIELD_ELIGIBILITY_READ.name()));
        perms.put("eligibility_edit", isAdmin || authorities.contains(PermissionType.INFO_PANEL_FIELD_ELIGIBILITY_WRITE.name()));
        if (!perms.get("eligibility")) response.setEligibility(null);

        perms.put("jobOpportunities", isAdmin || authorities.contains(PermissionType.INFO_PANEL_FIELD_JOB_OPPORTUNITIES_READ.name()));
        perms.put("jobOpportunities_edit", isAdmin || authorities.contains(PermissionType.INFO_PANEL_FIELD_JOB_OPPORTUNITIES_WRITE.name()));
        if (!perms.get("jobOpportunities")) response.setJobOpportunities(null);

        perms.put("hostelFee", isAdmin || authorities.contains(PermissionType.INFO_PANEL_FIELD_HOSTEL_FEE_READ.name()));
        perms.put("hostelFee_edit", isAdmin || authorities.contains(PermissionType.INFO_PANEL_FIELD_HOSTEL_FEE_WRITE.name()));
        if (!perms.get("hostelFee")) response.setHostelFee(null);

        perms.put("courseDetails", isAdmin || authorities.contains(PermissionType.INFO_PANEL_FIELD_COURSE_DETAILS_READ.name()));
        perms.put("courseDetails_edit", isAdmin || authorities.contains(PermissionType.INFO_PANEL_FIELD_COURSE_DETAILS_WRITE.name()));
        if (!perms.get("courseDetails")) response.setCourseDetails(null);

        perms.put("courseSpecialities", isAdmin || authorities.contains(PermissionType.INFO_PANEL_FIELD_COURSE_SPECIALITIES_READ.name()));
        perms.put("courseSpecialities_edit", isAdmin || authorities.contains(PermissionType.INFO_PANEL_FIELD_COURSE_SPECIALITIES_WRITE.name()));
        if (!perms.get("courseSpecialities")) response.setCourseSpecialities(null);

        perms.put("renaissanceUniversityUsps", isAdmin || authorities.contains(PermissionType.INFO_PANEL_FIELD_RU_USPS_READ.name()));
        perms.put("renaissanceUniversityUsps_edit", isAdmin || authorities.contains(PermissionType.INFO_PANEL_FIELD_RU_USPS_WRITE.name()));
        if (!perms.get("renaissanceUniversityUsps")) response.setRenaissanceUniversityUsps(null);

        perms.put("howWeAreDifferent", isAdmin || authorities.contains(PermissionType.INFO_PANEL_FIELD_HOW_WE_ARE_DIFFERENT_READ.name()));
        perms.put("howWeAreDifferent_edit", isAdmin || authorities.contains(PermissionType.INFO_PANEL_FIELD_HOW_WE_ARE_DIFFERENT_WRITE.name()));
        if (!perms.get("howWeAreDifferent")) response.setHowWeAreDifferent(null);

        perms.put("callerGuidance", isAdmin || authorities.contains(PermissionType.INFO_PANEL_FIELD_CALLER_GUIDANCE_READ.name()));
        perms.put("callerGuidance_edit", isAdmin || authorities.contains(PermissionType.INFO_PANEL_FIELD_CALLER_GUIDANCE_WRITE.name()));
        if (!perms.get("callerGuidance")) response.setCallerGuidance(null);

        // Competitor Information Permissions
        boolean canSeeCollegeName = isAdmin || authorities.contains(PermissionType.INFO_PANEL_FIELD_COLLEGE_NAME_READ.name());
        boolean canSeeBranches = isAdmin || authorities.contains(PermissionType.INFO_PANEL_FIELD_BRANCHES_READ.name());
        boolean canSeeCompFee = isAdmin || authorities.contains(PermissionType.INFO_PANEL_FIELD_COMPETITOR_FEE_READ.name());
        boolean canSeeCompDuration = isAdmin || authorities.contains(PermissionType.INFO_PANEL_FIELD_COMPETITOR_DURATION_READ.name());
        boolean canSeeOdds = isAdmin || authorities.contains(PermissionType.INFO_PANEL_FIELD_ODDS_READ.name());
        boolean canSeeCompEligibility = isAdmin || authorities.contains(PermissionType.INFO_PANEL_FIELD_COMPETITOR_ELIGIBILITY_READ.name());
        boolean canSeeHostel = isAdmin || authorities.contains(PermissionType.INFO_PANEL_FIELD_HOSTEL_READ.name());
        boolean canSeeDistance = isAdmin || authorities.contains(PermissionType.INFO_PANEL_FIELD_DISTANCE_FROM_CITY_READ.name());
        boolean canSeeRegFee = isAdmin || authorities.contains(PermissionType.INFO_PANEL_FIELD_REGISTRATION_FEE_READ.name());
        boolean canSeeAvgPlacement = isAdmin || authorities.contains(PermissionType.INFO_PANEL_FIELD_AVERAGE_PLACEMENTS_READ.name());
        boolean canSeeHighestPlacement = isAdmin || authorities.contains(PermissionType.INFO_PANEL_FIELD_HIGHEST_PLACEMENT_READ.name());

        perms.put("collegeName", canSeeCollegeName);
        perms.put("collegeName_edit", isAdmin || authorities.contains(PermissionType.INFO_PANEL_FIELD_COLLEGE_NAME_WRITE.name()));
        perms.put("branches", canSeeBranches);
        perms.put("branches_edit", isAdmin || authorities.contains(PermissionType.INFO_PANEL_FIELD_BRANCHES_WRITE.name()));
        perms.put("courseFeePerYear", canSeeCompFee);
        perms.put("courseFeePerYear_edit", isAdmin || authorities.contains(PermissionType.INFO_PANEL_FIELD_COMPETITOR_FEE_WRITE.name()));
        perms.put("competitorDuration", canSeeCompDuration);
        perms.put("competitorDuration_edit", isAdmin || authorities.contains(PermissionType.INFO_PANEL_FIELD_COMPETITOR_DURATION_WRITE.name()));
        perms.put("odds", canSeeOdds);
        perms.put("odds_edit", isAdmin || authorities.contains(PermissionType.INFO_PANEL_FIELD_ODDS_WRITE.name()));
        perms.put("competitorEligibility", canSeeCompEligibility);
        perms.put("competitorEligibility_edit", isAdmin || authorities.contains(PermissionType.INFO_PANEL_FIELD_COMPETITOR_ELIGIBILITY_WRITE.name()));
        perms.put("hostel", canSeeHostel);
        perms.put("hostel_edit", isAdmin || authorities.contains(PermissionType.INFO_PANEL_FIELD_HOSTEL_WRITE.name()));
        perms.put("distanceFromCity", canSeeDistance);
        perms.put("distanceFromCity_edit", isAdmin || authorities.contains(PermissionType.INFO_PANEL_FIELD_DISTANCE_FROM_CITY_WRITE.name()));
        perms.put("registrationFee", canSeeRegFee);
        perms.put("registrationFee_edit", isAdmin || authorities.contains(PermissionType.INFO_PANEL_FIELD_REGISTRATION_FEE_WRITE.name()));
        perms.put("averagePlacements", canSeeAvgPlacement);
        perms.put("averagePlacements_edit", isAdmin || authorities.contains(PermissionType.INFO_PANEL_FIELD_AVERAGE_PLACEMENTS_WRITE.name()));
        perms.put("highestPlacement", canSeeHighestPlacement);
        perms.put("highestPlacement_edit", isAdmin || authorities.contains(PermissionType.INFO_PANEL_FIELD_HIGHEST_PLACEMENT_WRITE.name()));

        if (response.getCompetitors() != null) {
            for (CompetitorResponseDTO comp : response.getCompetitors()) {
                if (!canSeeCollegeName) comp.setCollegeName(null);
                if (!canSeeBranches) comp.setBranches(null);
                CompetitorComparisonDTO cmp = comp.getComparison();
                if (cmp != null) {
                    if (!canSeeCompFee) cmp.setCourseFeePerYear(null);
                    if (!canSeeCompDuration) cmp.setDuration(null);
                    if (!canSeeOdds) cmp.setOdds(null);
                    if (!canSeeCompEligibility) cmp.setEligibility(null);
                    if (!canSeeHostel) cmp.setHostel(null);
                    if (!canSeeDistance) cmp.setDistanceFromCity(null);
                    if (!canSeeRegFee) cmp.setRegistrationFee(null);
                    if (!canSeeAvgPlacement) cmp.setAveragePlacements(null);
                    if (!canSeeHighestPlacement) cmp.setHighestPlacement(null);
                }
            }
        }

        response.setFieldPermissions(perms);
    }

    @Override
    public Map<String, Boolean> getFieldPermissionsMap() {
        Set<String> authorities = getCurrentUserAuthorities();
        boolean isAdmin = isUserAdmin(authorities);
        Map<String, Boolean> map = new LinkedHashMap<>();

        // General
        map.put("INFO_PANEL_VIEW", isAdmin || authorities.contains(PermissionType.INFO_PANEL_VIEW.name()));
        map.put("INFO_PANEL_CREATE", isAdmin || authorities.contains(PermissionType.INFO_PANEL_CREATE.name()));
        map.put("INFO_PANEL_UPDATE", isAdmin || authorities.contains(PermissionType.INFO_PANEL_UPDATE.name()));
        map.put("INFO_PANEL_DELETE", isAdmin || authorities.contains(PermissionType.INFO_PANEL_DELETE.name()));
        map.put("INFO_PANEL_MANAGE", isAdmin || authorities.contains(PermissionType.INFO_PANEL_MANAGE.name()));

        return map;
    }

    @Override
    public InfoPanelPermissionMatrixDTO getPermissionMatrix() {
        Set<String> authorities = getCurrentUserAuthorities();
        boolean isAdmin = isUserAdmin(authorities);

        List<InfoPanelPermissionMatrixDTO.FieldGroupDTO> groups = new ArrayList<>();

        // Group 1: Course Information
        List<InfoPanelPermissionMatrixDTO.FieldPermissionDTO> courseFields = new ArrayList<>();
        courseFields.add(buildFieldPerm("courseFee", "Course Fee", PermissionType.INFO_PANEL_FIELD_COURSE_FEE_READ, PermissionType.INFO_PANEL_FIELD_COURSE_FEE_WRITE, authorities, isAdmin));
        courseFields.add(buildFieldPerm("duration", "Duration", PermissionType.INFO_PANEL_FIELD_DURATION_READ, PermissionType.INFO_PANEL_FIELD_DURATION_WRITE, authorities, isAdmin));
        courseFields.add(buildFieldPerm("eligibility", "Eligibility", PermissionType.INFO_PANEL_FIELD_ELIGIBILITY_READ, PermissionType.INFO_PANEL_FIELD_ELIGIBILITY_WRITE, authorities, isAdmin));
        courseFields.add(buildFieldPerm("jobOpportunities", "Job Opportunities", PermissionType.INFO_PANEL_FIELD_JOB_OPPORTUNITIES_READ, PermissionType.INFO_PANEL_FIELD_JOB_OPPORTUNITIES_WRITE, authorities, isAdmin));
        courseFields.add(buildFieldPerm("hostelFee", "Hostel Fee", PermissionType.INFO_PANEL_FIELD_HOSTEL_FEE_READ, PermissionType.INFO_PANEL_FIELD_HOSTEL_FEE_WRITE, authorities, isAdmin));
        courseFields.add(buildFieldPerm("courseDetails", "Course Details", PermissionType.INFO_PANEL_FIELD_COURSE_DETAILS_READ, PermissionType.INFO_PANEL_FIELD_COURSE_DETAILS_WRITE, authorities, isAdmin));
        courseFields.add(buildFieldPerm("courseSpecialities", "Course Specialities / USPs", PermissionType.INFO_PANEL_FIELD_COURSE_SPECIALITIES_READ, PermissionType.INFO_PANEL_FIELD_COURSE_SPECIALITIES_WRITE, authorities, isAdmin));
        courseFields.add(buildFieldPerm("renaissanceUniversityUsps", "Renaissance University USPs", PermissionType.INFO_PANEL_FIELD_RU_USPS_READ, PermissionType.INFO_PANEL_FIELD_RU_USPS_WRITE, authorities, isAdmin));
        courseFields.add(buildFieldPerm("howWeAreDifferent", "How We Are Different", PermissionType.INFO_PANEL_FIELD_HOW_WE_ARE_DIFFERENT_READ, PermissionType.INFO_PANEL_FIELD_HOW_WE_ARE_DIFFERENT_WRITE, authorities, isAdmin));
        courseFields.add(buildFieldPerm("callerGuidance", "Caller Guidance", PermissionType.INFO_PANEL_FIELD_CALLER_GUIDANCE_READ, PermissionType.INFO_PANEL_FIELD_CALLER_GUIDANCE_WRITE, authorities, isAdmin));

        groups.add(InfoPanelPermissionMatrixDTO.FieldGroupDTO.builder()
                .key("COURSE_INFORMATION")
                .label("Course Information")
                .fields(courseFields)
                .build());

        // Group 2: Competitor Information
        List<InfoPanelPermissionMatrixDTO.FieldPermissionDTO> competitorFields = new ArrayList<>();
        competitorFields.add(buildFieldPerm("collegeName", "College Name", PermissionType.INFO_PANEL_FIELD_COLLEGE_NAME_READ, PermissionType.INFO_PANEL_FIELD_COLLEGE_NAME_WRITE, authorities, isAdmin));
        competitorFields.add(buildFieldPerm("branches", "Branches", PermissionType.INFO_PANEL_FIELD_BRANCHES_READ, PermissionType.INFO_PANEL_FIELD_BRANCHES_WRITE, authorities, isAdmin));
        competitorFields.add(buildFieldPerm("courseFeePerYear", "Course Fee - Per Year", PermissionType.INFO_PANEL_FIELD_COMPETITOR_FEE_READ, PermissionType.INFO_PANEL_FIELD_COMPETITOR_FEE_WRITE, authorities, isAdmin));
        competitorFields.add(buildFieldPerm("competitorDuration", "Duration", PermissionType.INFO_PANEL_FIELD_COMPETITOR_DURATION_READ, PermissionType.INFO_PANEL_FIELD_COMPETITOR_DURATION_WRITE, authorities, isAdmin));
        competitorFields.add(buildFieldPerm("odds", "Odds", PermissionType.INFO_PANEL_FIELD_ODDS_READ, PermissionType.INFO_PANEL_FIELD_ODDS_WRITE, authorities, isAdmin));
        competitorFields.add(buildFieldPerm("competitorEligibility", "Eligibility", PermissionType.INFO_PANEL_FIELD_COMPETITOR_ELIGIBILITY_READ, PermissionType.INFO_PANEL_FIELD_COMPETITOR_ELIGIBILITY_WRITE, authorities, isAdmin));
        competitorFields.add(buildFieldPerm("hostel", "Hostel", PermissionType.INFO_PANEL_FIELD_HOSTEL_READ, PermissionType.INFO_PANEL_FIELD_HOSTEL_WRITE, authorities, isAdmin));
        competitorFields.add(buildFieldPerm("distanceFromCity", "Distance From City", PermissionType.INFO_PANEL_FIELD_DISTANCE_FROM_CITY_READ, PermissionType.INFO_PANEL_FIELD_DISTANCE_FROM_CITY_WRITE, authorities, isAdmin));
        competitorFields.add(buildFieldPerm("registrationFee", "Registration Fee", PermissionType.INFO_PANEL_FIELD_REGISTRATION_FEE_READ, PermissionType.INFO_PANEL_FIELD_REGISTRATION_FEE_WRITE, authorities, isAdmin));
        competitorFields.add(buildFieldPerm("averagePlacements", "Average Placement", PermissionType.INFO_PANEL_FIELD_AVERAGE_PLACEMENTS_READ, PermissionType.INFO_PANEL_FIELD_AVERAGE_PLACEMENTS_WRITE, authorities, isAdmin));
        competitorFields.add(buildFieldPerm("highestPlacement", "Highest Placement", PermissionType.INFO_PANEL_FIELD_HIGHEST_PLACEMENT_READ, PermissionType.INFO_PANEL_FIELD_HIGHEST_PLACEMENT_WRITE, authorities, isAdmin));

        groups.add(InfoPanelPermissionMatrixDTO.FieldGroupDTO.builder()
                .key("COMPETITOR_INFORMATION")
                .label("Competitor Information")
                .fields(competitorFields)
                .build());

        return InfoPanelPermissionMatrixDTO.builder()
                .entity("INFO_PANEL")
                .groups(groups)
                .build();
    }

    private InfoPanelPermissionMatrixDTO.FieldPermissionDTO buildFieldPerm(
            String key, String label, PermissionType read, PermissionType write, Set<String> authorities, boolean isAdmin) {
        return InfoPanelPermissionMatrixDTO.FieldPermissionDTO.builder()
                .key(key)
                .label(label)
                .view(isAdmin || authorities.contains(read.name()))
                .edit(isAdmin || authorities.contains(write.name()))
                .build();
    }
}
