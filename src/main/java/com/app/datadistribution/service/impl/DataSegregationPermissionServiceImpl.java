package com.app.datadistribution.service.impl;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.app.datadistribution.dto.segregation.DataSegregationCapabilitiesDTO;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.PermissionType;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.service.interfaces.IDataSegregationPermissionService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class DataSegregationPermissionServiceImpl implements IDataSegregationPermissionService {

    @Override
    public DataSegregationCapabilitiesDTO getCapabilities() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return buildCapabilitiesFromAuthorities(Collections.emptySet());
        }

        Set<String> authorities = new HashSet<>();
        if (auth.getAuthorities() != null) {
            auth.getAuthorities().stream()
                    .filter(java.util.Objects::nonNull)
                    .map(GrantedAuthority::getAuthority)
                    .filter(java.util.Objects::nonNull)
                    .map(String::toUpperCase)
                    .forEach(authorities::add);
        }

        if (auth.getPrincipal() instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
            if (userDetails.getAuthorities() != null) {
                userDetails.getAuthorities().stream()
                        .filter(java.util.Objects::nonNull)
                        .map(GrantedAuthority::getAuthority)
                        .filter(java.util.Objects::nonNull)
                        .map(String::toUpperCase)
                        .forEach(authorities::add);
            }
        }

        return buildCapabilitiesFromAuthorities(authorities);
    }

    @Override
    public DataSegregationCapabilitiesDTO getCapabilitiesForUser(User user) {
        if (user == null || user.getRoles() == null) {
            return buildCapabilitiesFromAuthorities(Collections.emptySet());
        }

        Set<String> authorities = new HashSet<>();
        user.getRoles().stream()
                .filter(r -> r.isActive() && r.getPermissions() != null)
                .flatMap(r -> r.getPermissions().stream())
                .filter(p -> p != null && p.isActive())
                .forEach(p -> authorities.add(p.getName().toUpperCase()));

        user.getRoles().stream()
                .filter(r -> r.isActive())
                .forEach(r -> {
                    authorities.add(r.getName().toUpperCase());
                    if (!r.getName().toUpperCase().startsWith("ROLE_")) {
                        authorities.add("ROLE_" + r.getName().toUpperCase());
                    }
                });

        return buildCapabilitiesFromAuthorities(authorities);
    }

    private DataSegregationCapabilitiesDTO buildCapabilitiesFromAuthorities(Set<String> authorities) {
        boolean isAdmin = authorities.contains("ROLE_SUPER_ADMIN")
                || authorities.contains("SUPER_ADMIN")
                || authorities.contains("ROLE_ADMIN")
                || authorities.contains("ADMIN");

        boolean hasFullFlow = isAdmin || authorities.contains(PermissionType.DATA_SEGREGATION_FULL_FLOW_VIEW.name());
        boolean hasBaseView = isAdmin || authorities.contains(PermissionType.DATA_SEGREGATION_VIEW.name()) || hasFullFlow;

        if (!hasBaseView) {
            return DataSegregationCapabilitiesDTO.builder()
                    .canView(false)
                    .canViewFullFlow(false)
                    .canViewCourseType(false)
                    .canViewSource(false)
                    .canViewBoard(false)
                    .canViewGrade(false)
                    .canViewUserAnalytics(false)
                    .canViewLeadStatusAnalytics(false)
                    .build();
        }

        boolean canCourseType = hasFullFlow || authorities.contains(PermissionType.DATA_SEGREGATION_COURSE_TYPE_VIEW.name());
        boolean canSource = hasFullFlow || authorities.contains(PermissionType.DATA_SEGREGATION_SOURCE_VIEW.name());
        boolean canBoard = hasFullFlow || authorities.contains(PermissionType.DATA_SEGREGATION_BOARD_VIEW.name());
        boolean canGrade = hasFullFlow || authorities.contains(PermissionType.DATA_SEGREGATION_GRADE_VIEW.name());

        boolean canUserAnalytics = hasFullFlow
                || authorities.contains(PermissionType.DATA_SEGREGATION_USER_ANALYTICS.name())
                || authorities.contains(PermissionType.DATA_SEGREGATION_VIEW.name());

        boolean canLeadStatusAnalytics = hasFullFlow
                || authorities.contains(PermissionType.DATA_SEGREGATION_LEAD_STATUS_ANALYTICS.name())
                || authorities.contains(PermissionType.DATA_SEGREGATION_VIEW.name());

        return DataSegregationCapabilitiesDTO.builder()
                .canView(true)
                .canViewFullFlow(hasFullFlow)
                .canViewCourseType(canCourseType)
                .canViewSource(canSource)
                .canViewBoard(canBoard)
                .canViewGrade(canGrade)
                .canViewUserAnalytics(canUserAnalytics)
                .canViewLeadStatusAnalytics(canLeadStatusAnalytics)
                .build();
    }

    @Override
    public void validateBaseAccess() throws UnauthorizedException {
        DataSegregationCapabilitiesDTO caps = getCapabilities();
        if (!caps.isCanView()) {
            throw new UnauthorizedException("Access to Data Segregation is denied. Required permission: DATA_SEGREGATION_VIEW");
        }
    }

    @Override
    public void validateCourseTypeAccess() throws UnauthorizedException {
        validateBaseAccess();
        DataSegregationCapabilitiesDTO caps = getCapabilities();
        if (!caps.isCanViewCourseType()) {
            throw new UnauthorizedException("You do not have permission to view Course Type segregation.");
        }
    }

    @Override
    public void validateMatrixAccess(UUID courseTypeId, UUID leadSourceId, UUID boardId, UUID gradeId) throws UnauthorizedException {
        validateBaseAccess();
        DataSegregationCapabilitiesDTO caps = getCapabilities();

        if (courseTypeId != null && !caps.isCanViewCourseType()) {
            throw new UnauthorizedException("You do not have permission to view Course Type segregation.");
        }
        if (leadSourceId != null && !caps.isCanViewSource()) {
            throw new UnauthorizedException("You do not have permission to view Lead Source segregation.");
        }
        if (boardId != null && !caps.isCanViewBoard()) {
            throw new UnauthorizedException("You do not have permission to view Board segregation.");
        }
        if (gradeId != null && !caps.isCanViewGrade()) {
            throw new UnauthorizedException("You do not have permission to view Grade segregation.");
        }
    }

    @Override
    public void validateUserAnalyticsAccess(UUID courseTypeId, UUID leadSourceId, UUID boardId, UUID gradeId) throws UnauthorizedException {
        validateBaseAccess();
        DataSegregationCapabilitiesDTO caps = getCapabilities();

        if (!caps.isCanViewUserAnalytics()) {
            throw new UnauthorizedException("You do not have permission to view user segregation analytics.");
        }
        if (courseTypeId != null && !caps.isCanViewCourseType()) {
            throw new UnauthorizedException("You do not have permission to view Course Type segregation.");
        }
        if (leadSourceId != null && !caps.isCanViewSource()) {
            throw new UnauthorizedException("You do not have permission to view Lead Source segregation.");
        }
        if (boardId != null && !caps.isCanViewBoard()) {
            throw new UnauthorizedException("You do not have permission to view Board segregation analytics.");
        }
        if (gradeId != null && !caps.isCanViewGrade()) {
            throw new UnauthorizedException("You do not have permission to view Grade segregation analytics.");
        }
    }

    @Override
    public void validateLeadStatusAnalyticsAccess(UUID courseTypeId, UUID leadSourceId, UUID boardId, UUID gradeId) throws UnauthorizedException {
        validateBaseAccess();
        DataSegregationCapabilitiesDTO caps = getCapabilities();

        if (!caps.isCanViewLeadStatusAnalytics()) {
            throw new UnauthorizedException("You do not have permission to view lead status segregation analytics.");
        }
        if (courseTypeId != null && !caps.isCanViewCourseType()) {
            throw new UnauthorizedException("You do not have permission to view Course Type segregation.");
        }
        if (leadSourceId != null && !caps.isCanViewSource()) {
            throw new UnauthorizedException("You do not have permission to view Lead Source segregation.");
        }
        if (boardId != null && !caps.isCanViewBoard()) {
            throw new UnauthorizedException("You do not have permission to view Board segregation status analytics.");
        }
        if (gradeId != null && !caps.isCanViewGrade()) {
            throw new UnauthorizedException("You do not have permission to view Grade segregation status analytics.");
        }
    }
}
