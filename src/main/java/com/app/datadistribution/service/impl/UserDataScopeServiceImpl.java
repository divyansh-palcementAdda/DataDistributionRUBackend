package com.app.datadistribution.service.impl;

import com.app.datadistribution.entity.Department;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.PermissionType;
import com.app.datadistribution.enums.RoleType;
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
    public UserDataScope getScopeForCurrentUser() throws UnauthorizedException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new UnauthorizedException("User is not authenticated");
        }
        String username = auth.getName();
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourcesNotFoundException("User not found with username: " + username));

        return getScopeForUser(currentUser);
    }

    @Override
    @Transactional(readOnly = true)
    public UserDataScope getScopeForUser(User user) {
        boolean admin = isAdmin(user);
        boolean hod = isHOD(user);

        if (admin) {
            List<Department> allDepts = departmentRepository.findByActiveTrueAndIsDeletedFalse();
            Set<UUID> allDeptIds = allDepts.stream().map(Department::getId).collect(Collectors.toSet());
            Set<UUID> allUserIds = userRepository.findAll().stream()
                    .filter(u -> !u.isDeleted())
                    .map(User::getId)
                    .collect(Collectors.toSet());

            return UserDataScope.builder()
                    .scopeType(ScopeType.SYSTEM)
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

        Set<UUID> userDeptIds = user.getDepartments() != null ?
                user.getDepartments().stream().filter(d -> d.isActive() && !d.isDeleted()).map(Department::getId).collect(Collectors.toSet())
                : new HashSet<>();

        if (hod) {
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
                    .userId(user.getId())
                    .currentUser(user)
                    .departmentIds(userDeptIds)
                    .departmentUserIds(memberUserIds)
                    .hodAccessType(user.getHodAccessType())
                    .isAdmin(false)
                    .isHod(true)
                    .isCounsellor(false)
                    .build();
        }

        // Counsellor / Standard User Scope
        Set<UUID> selfUserId = new HashSet<>();
        selfUserId.add(user.getId());

        return UserDataScope.builder()
                .scopeType(ScopeType.SELF)
                .userId(user.getId())
                .currentUser(user)
                .departmentIds(userDeptIds)
                .departmentUserIds(selfUserId)
                .hodAccessType(user.getHodAccessType())
                .isAdmin(false)
                .isHod(false)
                .isCounsellor(true)
                .build();
    }

    private boolean isAdmin(User user) {
        if (user.getRoles() == null) return false;
        return user.getRoles().stream()
                .anyMatch(r -> RoleType.SUPER_ADMIN.name().equalsIgnoreCase(r.getName())
                        || RoleType.ADMIN.name().equalsIgnoreCase(r.getName()))
                || hasPermission(user, PermissionType.DASHBOARD_VIEW_ALL.name());
    }

    private boolean isHOD(User user) {
        if (user.getRoles() == null) return false;
        return user.getRoles().stream()
                .anyMatch(r -> r.getName().toUpperCase().contains("HOD") || r.getName().toUpperCase().contains("HEAD"))
                || hasPermission(user, PermissionType.DASHBOARD_VIEW_DEPARTMENT.name());
    }

    private boolean hasPermission(User user, String permName) {
        if (user.getRoles() == null) return false;
        return user.getRoles().stream()
                .flatMap(r -> r.getPermissions().stream())
                .anyMatch(p -> p.getName().equalsIgnoreCase(permName));
    }
}
