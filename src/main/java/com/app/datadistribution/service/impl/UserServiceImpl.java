package com.app.datadistribution.service.impl;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.user.UserPageResponse;
import com.app.datadistribution.dto.user.UserRequest;
import com.app.datadistribution.dto.user.UserResponse;
import com.app.datadistribution.dto.user.UserUpdateRequest;
import com.app.datadistribution.entity.Department;
import com.app.datadistribution.entity.Role;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.ActivityType;
import com.app.datadistribution.enums.HodAccessType;
import com.app.datadistribution.enums.RoleType;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.DuplicateResourceException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.mapper.UserMapper;
import com.app.datadistribution.repository.DepartmentRepository;
import com.app.datadistribution.repository.RoleRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.interfaces.IActivityLogService;
import com.app.datadistribution.service.interfaces.IUserService;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements IUserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final IActivityLogService activityLogService;

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID userId) throws ResourcesNotFoundException {
        User user = userRepository.findById(userId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("User not found with id: " + userId));
        return userMapper.toDto(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserPageResponse getUsers(PageRequestDTO pageRequest) {
        Sort.Direction direction = Sort.Direction.fromString(pageRequest.getSortDirection());
        Pageable pageable = PageRequest.of(pageRequest.getPage(), pageRequest.getSize(), Sort.by(direction, pageRequest.getSortBy()));

        Specification<User> spec = Specification.where(isNotDeleted());
        if (pageRequest.getSearch() != null && !pageRequest.getSearch().isBlank()) {
            spec = spec.and(searchUsers(pageRequest.getSearch()));
        }

        Page<User> userPage = userRepository.findAll(spec, pageable);
        return toUserPageResponse(userPage);
    }

    @Override
    @Transactional(readOnly = true)
    public UserPageResponse getUsersByRoles(List<String> roleNames, String status, PageRequestDTO pageRequest)
            throws BadRequestException, ResourcesNotFoundException {
        if (roleNames == null || roleNames.isEmpty()) {
            throw new BadRequestException("At least one role name is required");
        }

        List<String> normalizedRoles = roleNames.stream()
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .distinct()
                .collect(Collectors.toList());

        if (normalizedRoles.isEmpty()) {
            throw new BadRequestException("At least one role name is required");
        }

        for (String roleName : normalizedRoles) {
            roleRepository.findByNameAndIsDeletedFalse(roleName)
                    .orElseThrow(() -> new ResourcesNotFoundException("Role not found: " + roleName));
        }

        if (status != null && !status.isBlank()
                && !status.equalsIgnoreCase("ACTIVE")
                && !status.equalsIgnoreCase("INACTIVE")) {
            throw new BadRequestException("Invalid status. Allowed values: ACTIVE, INACTIVE");
        }

        Sort.Direction direction = Sort.Direction.fromString(pageRequest.getSortDirection());
        Pageable pageable = PageRequest.of(pageRequest.getPage(), pageRequest.getSize(), Sort.by(direction, pageRequest.getSortBy()));

        Specification<User> spec = Specification.where(isNotDeleted())
                .and(hasRolesIn(normalizedRoles));

        if (status != null && !status.isBlank()) {
            spec = spec.and(filterByActiveStatus(status));
        }

        if (pageRequest.getSearch() != null && !pageRequest.getSearch().isBlank()) {
            spec = spec.and(searchUsers(pageRequest.getSearch()));
        }

        Page<User> userPage = userRepository.findAll(spec, pageable);
        return toUserPageResponse(userPage);
    }

    @Override
    @Transactional
    public UserResponse createUser(UserRequest request) throws BadRequestException {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("Username is already taken: " + request.getUsername());
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email is already registered: " + request.getEmail());
        }

        Set<Role> roles = request.getRoles().stream()
                .map(roleName -> roleRepository.findByName(roleName)
                        .orElseThrow(() -> new ResourcesNotFoundException("Role not found: " + roleName)))
                .collect(Collectors.toSet());

        if (roles.isEmpty()) {
            Role defaultRole = roleRepository.findByName(RoleType.COUNSELOR.name())
                    .or(() -> roleRepository.findByName(RoleType.USER.name()))
                    .orElseThrow(() -> new ResourcesNotFoundException("Default Role COUNSELOR not found"));
            roles.add(defaultRole);
        }

        boolean isAdmin = roles.stream().anyMatch(r -> RoleType.SUPER_ADMIN.name().equalsIgnoreCase(r.getName()) || RoleType.ADMIN.name().equalsIgnoreCase(r.getName()));
        boolean requiresDepartment = roles.stream().anyMatch(r -> RoleType.HOD.name().equalsIgnoreCase(r.getName())
                || RoleType.COUNSELOR.name().equalsIgnoreCase(r.getName())
                || RoleType.USER.name().equalsIgnoreCase(r.getName())
                || r.getName().toUpperCase().contains("HOD")
                || r.getName().toUpperCase().contains("HEAD")
                || r.getName().toUpperCase().contains("COUNSELOR"));

        Set<Department> departments = new HashSet<>();
        if (request.getDepartmentIds() != null && !request.getDepartmentIds().isEmpty()) {
            if (isAdmin) {
                throw new BadRequestException("Admin and SUPER_ADMIN users cannot be assigned to specific departments as they have system-wide access.");
            }
            for (UUID dId : request.getDepartmentIds()) {
                Department dept = departmentRepository.findById(dId)
                        .filter(d -> !d.isDeleted())
                        .orElseThrow(() -> new ResourcesNotFoundException("Department not found: " + dId));
                departments.add(dept);
            }
        } else if (!isAdmin && requiresDepartment) {
            throw new BadRequestException("Department mapping is required for HOD and COUNSELOR roles.");
        }

        boolean isHod = roles.stream().anyMatch(r -> RoleType.HOD.name().equalsIgnoreCase(r.getName()) || r.getName().toUpperCase().contains("HOD") || r.getName().toUpperCase().contains("HEAD"));
        HodAccessType hodAccessType = request.getHodAccessType();
        if (isHod && hodAccessType == null) {
            hodAccessType = HodAccessType.FULL_ACCESS;
        }

        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .active(request.isActive())
                .locked(request.isLocked())
                .emailVerified(request.isEmailVerified())
                .profileImage(request.getProfileImage())
                .hodAccessType(hodAccessType)
                .roles(roles)
                .departments(departments)
                .tokenVersion(1L)
                .build();

        User saved = userRepository.save(user);
        activityLogService.logActivity(ActivityType.USER_CREATED, "Created user: " + saved.getUsername());
        return userMapper.toDto(saved);
    }

    @Override
    @Transactional
    @CacheEvict(value = "userPermissions", allEntries = true)
    public UserResponse updateUser(UUID userId, UserUpdateRequest request) throws ResourcesNotFoundException, BadRequestException {
        User user = userRepository.findById(userId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("User not found with id: " + userId));

        if (!user.getUsername().equalsIgnoreCase(request.getUsername()) && userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("Username is already taken: " + request.getUsername());
        }

        if (!user.getEmail().equalsIgnoreCase(request.getEmail()) && userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email is already registered: " + request.getEmail());
        }

        Set<Role> roles = request.getRoles().stream()
                .map(roleName -> roleRepository.findByName(roleName)
                        .orElseThrow(() -> new ResourcesNotFoundException("Role not found: " + roleName)))
                .collect(Collectors.toSet());

        boolean isAdmin = roles.stream().anyMatch(r -> RoleType.SUPER_ADMIN.name().equalsIgnoreCase(r.getName()) || RoleType.ADMIN.name().equalsIgnoreCase(r.getName()));
        boolean requiresDepartment = roles.stream().anyMatch(r -> RoleType.HOD.name().equalsIgnoreCase(r.getName())
                || RoleType.COUNSELOR.name().equalsIgnoreCase(r.getName())
                || RoleType.USER.name().equalsIgnoreCase(r.getName())
                || r.getName().toUpperCase().contains("HOD")
                || r.getName().toUpperCase().contains("HEAD")
                || r.getName().toUpperCase().contains("COUNSELOR"));

        if (request.getDepartmentIds() != null) {
            if (isAdmin && !request.getDepartmentIds().isEmpty()) {
                throw new BadRequestException("Admin and SUPER_ADMIN users cannot be assigned to specific departments as they have system-wide access.");
            }
            Set<Department> newDepartments = new HashSet<>();
            if (!isAdmin) {
                for (UUID dId : request.getDepartmentIds()) {
                    Department dept = departmentRepository.findById(dId)
                            .filter(d -> !d.isDeleted())
                            .orElseThrow(() -> new ResourcesNotFoundException("Department not found: " + dId));
                    newDepartments.add(dept);
                }
                if (requiresDepartment && newDepartments.isEmpty()) {
                    throw new BadRequestException("Department mapping is required for HOD and COUNSELOR roles.");
                }
            }
            user.setDepartments(newDepartments);
        } else if (isAdmin) {
            user.setDepartments(new HashSet<>());
        }

        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setUsername(request.getUsername());

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            user.setTokenVersion(user.getTokenVersion() + 1);
        }

        user.setProfileImage(request.getProfileImage());
        boolean isHod = roles.stream().anyMatch(r -> RoleType.HOD.name().equalsIgnoreCase(r.getName()) || r.getName().toUpperCase().contains("HOD") || r.getName().toUpperCase().contains("HEAD"));
        if (request.getHodAccessType() != null) {
            user.setHodAccessType(request.getHodAccessType());
        } else if (isHod && user.getHodAccessType() == null) {
            user.setHodAccessType(HodAccessType.FULL_ACCESS);
        }
        user.setRoles(roles);
        user.setActive(request.isActive());
        user.setLocked(request.isLocked());
        user.setEmailVerified(request.isEmailVerified());

        User updated = userRepository.save(user);
        activityLogService.logActivity(ActivityType.USER_UPDATED, "Updated user: " + updated.getUsername());
        return userMapper.toDto(updated);
    }

    @Override
    @Transactional
    @CacheEvict(value = "userPermissions", allEntries = true)
    public void deleteUser(UUID userId) throws ResourcesNotFoundException {
        User user = userRepository.findById(userId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("User not found with id: " + userId));

        user.setDeleted(true);
        user.setActive(false);
        userRepository.save(user);
        activityLogService.logActivity(ActivityType.USER_DELETED, "Soft deleted user: " + user.getUsername());
    }

    @Override
    @Transactional
    @CacheEvict(value = "userPermissions", allEntries = true)
    public void assignRole(UUID userId, UUID roleId) throws ResourcesNotFoundException, BadRequestException {
        User user = userRepository.findById(userId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("User not found with id: " + userId));

        Role role = roleRepository.findByIdAndIsDeletedFalse(roleId)
                .orElseThrow(() -> new ResourcesNotFoundException("Role not found with id: " + roleId));

        if (!role.isActive()) {
            throw new BadRequestException("Cannot assign inactive role '" + role.getName() + "' to user");
        }

        Set<Role> roles = new HashSet<>();
        roles.add(role);
        user.setRoles(roles);

        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);

        log.info("Assigned role '{}' to user '{}'", role.getName(), user.getUsername());
        activityLogService.logActivity(ActivityType.USER_ROLE_CHANGED, 
                String.format("Changed role of user '%s' to '%s'", user.getUsername(), role.getName()));
    }

    private UserPageResponse toUserPageResponse(Page<User> userPage) {
        List<UserResponse> content = userPage.getContent().stream()
                .map(userMapper::toDto)
                .collect(Collectors.toList());

        return UserPageResponse.builder()
                .content(content)
                .page(userPage.getNumber())
                .size(userPage.getSize())
                .totalElements(userPage.getTotalElements())
                .totalPages(userPage.getTotalPages())
                .last(userPage.isLast())
                .build();
    }

    private Specification<User> isNotDeleted() {
        return (root, query, cb) -> cb.equal(root.get("isDeleted"), false);
    }

    private Specification<User> filterByActiveStatus(String status) {
        boolean active = status.equalsIgnoreCase("ACTIVE");
        return (root, query, cb) -> cb.equal(root.get("active"), active);
    }

    private Specification<User> hasRolesIn(List<String> roleNames) {
        return (root, query, cb) -> {
            query.distinct(true);
            Join<User, Role> roleJoin = root.join("roles", JoinType.INNER);
            return cb.and(
                    cb.equal(roleJoin.get("isDeleted"), false),
                    roleJoin.get("name").in(roleNames)
            );
        };
    }

    private Specification<User> searchUsers(String keyword) {
        return (root, query, cb) -> {
            String pattern = "%" + keyword.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("firstName")), pattern),
                    cb.like(cb.lower(root.get("lastName")), pattern),
                    cb.like(cb.lower(root.get("username")), pattern),
                    cb.like(cb.lower(root.get("email")), pattern),
                    cb.like(cb.lower(root.get("phone")), pattern)
            );
        };
    }
}
