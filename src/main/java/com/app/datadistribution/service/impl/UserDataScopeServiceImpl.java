package com.app.datadistribution.service.impl;

import com.app.datadistribution.entity.Department;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.PermissionType;
import com.app.datadistribution.enums.RoleType;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.repository.DepartmentRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.service.dto.UserDataScope.ScopeType;
import com.app.datadistribution.service.interfaces.IUserDataScopeService;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserDataScopeServiceImpl implements IUserDataScopeService {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDataScope getScopeForCurrentUser() throws UnauthorizedException, BadRequestException {
        return getScopeForCurrentUser((String) null);
    }

    @Override
    @Transactional(readOnly = true)
    public UserDataScope getScopeForCurrentUser(String requestedScope) throws UnauthorizedException, BadRequestException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new UnauthorizedException("User is not authenticated");
        }
        String username = auth.getName();
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourcesNotFoundException("User not found with username: " + username));

        return getScopeForUser(currentUser, requestedScope);
    }

    @Override
    @Transactional(readOnly = true)
    public UserDataScope getScopeForCurrentUser(com.app.datadistribution.dto.dashboard.DashboardAnalyticsFilterRequest filterRequest) throws UnauthorizedException, BadRequestException {
        String requestedScope = filterRequest != null ? filterRequest.getEffectiveScope() : null;
        return getScopeForCurrentUser(requestedScope);
    }

    @Override
    @Transactional(readOnly = true)
    public UserDataScope getScopeForUser(User user) {
        try {
            return getScopeForUser(user, (String) null);
        } catch (BadRequestException | UnauthorizedException e) {
            // Default fallback if no requested scope
            log.error("Error resolving default scope for user {}", user.getUsername(), e);
            throw new RuntimeException(e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UserDataScope getScopeForUser(User user, com.app.datadistribution.dto.dashboard.DashboardAnalyticsFilterRequest filterRequest) throws BadRequestException, UnauthorizedException {
        String requestedScope = filterRequest != null ? filterRequest.getEffectiveScope() : null;
        return getScopeForUser(user, requestedScope);
    }

    @Override
    @Transactional(readOnly = true)
    public UserDataScope getScopeForUser(User user, String requestedScope) throws BadRequestException, UnauthorizedException {
        boolean admin = isAdmin(user);
        boolean hod = isHOD(user);

        ScopeType targetScopeType = null;
        if (requestedScope != null && !requestedScope.isBlank()) {
            String norm = requestedScope.trim().toUpperCase();
            if (!norm.equals("DEFAULT")) {
                try {
                    targetScopeType = ScopeType.valueOf(norm);
                } catch (IllegalArgumentException e) {
                    throw new BadRequestException("Invalid data scope requested: " + requestedScope);
                }
            }
        }

        // Validate scope authorization
        if (targetScopeType != null) {
            if (targetScopeType == ScopeType.SYSTEM && !admin) {
                throw new UnauthorizedException("You do not have access to data outside your permitted scope.");
            }
            if (targetScopeType == ScopeType.DEPARTMENT && !admin && !hod) {
                throw new UnauthorizedException("You do not have access to data outside your permitted scope.");
            }
        }

        // Determine default scope if not explicitly overridden
        if (targetScopeType == null) {
            if (admin) {
                targetScopeType = ScopeType.SYSTEM;
            } else if (hod) {
                targetScopeType = ScopeType.DEPARTMENT;
            } else {
                targetScopeType = ScopeType.SELF;
            }
        }

        Set<UUID> userDeptIds = user.getDepartments() != null ?
                user.getDepartments().stream().filter(d -> d.isActive() && !d.isDeleted()).map(Department::getId).collect(Collectors.toSet())
                : new HashSet<>();

        if (targetScopeType == ScopeType.SYSTEM) {
            List<Department> allDepts = departmentRepository.findByActiveTrueAndIsDeletedFalse();
            Set<UUID> allDeptIds = allDepts.stream().map(Department::getId).collect(Collectors.toSet());
            Set<UUID> allUserIds = userRepository.findAll().stream()
                    .filter(u -> !u.isDeleted())
                    .map(User::getId)
                    .collect(Collectors.toSet());

            return UserDataScope.builder()
                    .scopeType(ScopeType.SYSTEM)
                    .requestedScopeType(targetScopeType)
                    .isSelfScopeOnly(false)
                    .userId(user.getId())
                    .currentUser(user)
                    .departmentIds(allDeptIds)
                    .departmentUserIds(allUserIds)
                    .hodAccessType(user.getHodAccessType())
                    .isAdmin(true)
                    .isHod(false)
                    .isCounsellor(false)
                    .build();
        }

        if (targetScopeType == ScopeType.DEPARTMENT) {
            Set<UUID> memberUserIds = new HashSet<>();
            if (!userDeptIds.isEmpty()) {
                List<User> deptUsers = userRepository.findAll().stream()
                        .filter(u -> !u.isDeleted() && u.getDepartments() != null && u.getDepartments().stream().anyMatch(d -> userDeptIds.contains(d.getId())))
                        .collect(Collectors.toList());
                memberUserIds = deptUsers.stream().map(User::getId).collect(Collectors.toSet());
            }
            memberUserIds.add(user.getId());

            return UserDataScope.builder()
                    .scopeType(ScopeType.DEPARTMENT)
                    .requestedScopeType(targetScopeType)
                    .isSelfScopeOnly(false)
                    .userId(user.getId())
                    .currentUser(user)
                    .departmentIds(userDeptIds)
                    .departmentUserIds(memberUserIds)
                    .hodAccessType(user.getHodAccessType())
                    .isAdmin(admin)
                    .isHod(true)
                    .isCounsellor(false)
                    .build();
        }

        // Target Scope is SELF
        Set<UUID> selfUserId = new HashSet<>();
        selfUserId.add(user.getId());

        return UserDataScope.builder()
                .scopeType(ScopeType.SELF)
                .requestedScopeType(targetScopeType)
                .isSelfScopeOnly(true)
                .userId(user.getId())
                .currentUser(user)
                .departmentIds(userDeptIds)
                .departmentUserIds(selfUserId)
                .hodAccessType(user.getHodAccessType())
                .isAdmin(admin)
                .isHod(hod)
                .isCounsellor(!admin && !hod)
                .build();
    }

    private boolean isAdmin(User user) {
        if (user.getRoles() == null) return false;
        return user.getRoles().stream()
                .anyMatch(r -> RoleType.SUPER_ADMIN.name().equalsIgnoreCase(r.getName())
                        || RoleType.ADMIN.name().equalsIgnoreCase(r.getName())
                        || "ROLE_SUPER_ADMIN".equalsIgnoreCase(r.getName())
                        || "ROLE_ADMIN".equalsIgnoreCase(r.getName())
                        || "SUPER_ADMIN".equalsIgnoreCase(r.getName())
                        || "ADMIN".equalsIgnoreCase(r.getName()));
    }

    private boolean isHOD(User user) {
        if (user.getRoles() == null) return false;
        return user.getRoles().stream()
                .anyMatch(r -> RoleType.HOD.name().equalsIgnoreCase(r.getName())
                        || "ROLE_HOD".equalsIgnoreCase(r.getName())
                        || "HOD".equalsIgnoreCase(r.getName())
                        || r.getName().toUpperCase().contains("HOD")
                        || r.getName().toUpperCase().contains("HEAD"));
    }

    private boolean hasPermission(User user, String permName) {
        if (user.getRoles() == null) return false;
        return user.getRoles().stream()
                .filter(r -> r.getPermissions() != null)
                .flatMap(r -> r.getPermissions().stream())
                .anyMatch(p -> p.getName().equalsIgnoreCase(permName));
    }
}
