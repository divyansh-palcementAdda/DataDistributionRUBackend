package com.app.datadistribution.service.impl;

import org.springframework.cache.annotation.CacheEvict;
import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.user.UserPageResponse;
import com.app.datadistribution.dto.user.UserRequest;
import com.app.datadistribution.dto.user.UserResponse;
import com.app.datadistribution.dto.user.UserUpdateRequest;
import com.app.datadistribution.entity.Role;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.ActivityType;
import com.app.datadistribution.enums.RoleType;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.DuplicateResourceException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.mapper.UserMapper;
import com.app.datadistribution.repository.RoleRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.interfaces.IActivityLogService;
import com.app.datadistribution.service.interfaces.IUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements IUserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
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
            Role defaultRole = roleRepository.findByName(RoleType.USER.name())
                    .orElseThrow(() -> new ResourcesNotFoundException("Default Role USER not found"));
            roles.add(defaultRole);
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
                .roles(roles)
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

        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setUsername(request.getUsername());
        
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            // Invalidate current logins since password has changed
            user.setTokenVersion(user.getTokenVersion() + 1);
        }

        user.setProfileImage(request.getProfileImage());
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

    private Specification<User> hasRolesIn(List<String> roleNames) {
        return (root, query, cb) -> {
            query.distinct(true);
            Join<User, Role> rolesJoin = root.join("roles", JoinType.INNER);
            return cb.and(
                    rolesJoin.get("name").in(roleNames),
                    cb.equal(rolesJoin.get("isDeleted"), false)
            );
        };
    }

    private Specification<User> filterByActiveStatus(String status) {
        return (root, query, cb) -> {
            if ("ACTIVE".equalsIgnoreCase(status)) {
                return cb.equal(root.get("active"), true);
            }
            return cb.equal(root.get("active"), false);
        };
    }

    private Specification<User> searchUsers(String keyword) {
        return (root, query, cb) -> {
            String searchPattern = "%" + keyword.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("firstName")), searchPattern),
                    cb.like(cb.lower(root.get("lastName")), searchPattern),
                    cb.like(cb.lower(root.get("email")), searchPattern),
                    cb.like(cb.lower(root.get("username")), searchPattern),
                    cb.like(cb.lower(root.get("department")), searchPattern)
            );
        };
    }
}
