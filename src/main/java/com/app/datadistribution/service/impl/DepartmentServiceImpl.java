package com.app.datadistribution.service.impl;

import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.department.DepartmentRequest;
import com.app.datadistribution.dto.department.DepartmentResponse;
import com.app.datadistribution.dto.department.DepartmentSummaryDTO;
import com.app.datadistribution.dto.user.UserResponse;
import com.app.datadistribution.entity.Department;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.RoleType;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.DuplicateResourceException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.mapper.UserMapper;
import com.app.datadistribution.repository.DepartmentRepository;
import com.app.datadistribution.repository.LeadRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.service.interfaces.IDepartmentService;
import com.app.datadistribution.service.interfaces.IUserDataScopeService;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DepartmentServiceImpl implements IDepartmentService {

    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final LeadRepository leadRepository;
    private final IUserDataScopeService dataScopeService;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public DepartmentResponse createDepartment(DepartmentRequest request) throws BadRequestException, UnauthorizedException {
        if (departmentRepository.existsByNameIgnoreCaseAndIsDeletedFalse(request.getName().trim())) {
            throw new DuplicateResourceException("Department with name already exists: " + request.getName());
        }
        if (departmentRepository.existsByCodeIgnoreCaseAndIsDeletedFalse(request.getCode().trim())) {
            throw new DuplicateResourceException("Department with code already exists: " + request.getCode());
        }

        Department department = Department.builder()
                .name(request.getName().trim())
                .code(request.getCode().trim().toUpperCase())
                .description(request.getDescription())
                .active(request.isActive())
                .build();

        Department saved = departmentRepository.save(department);
        log.info("Created department: {} ({})", saved.getName(), saved.getCode());
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public DepartmentResponse updateDepartment(UUID id, DepartmentRequest request) throws BadRequestException, ResourcesNotFoundException, UnauthorizedException {
        Department department = departmentRepository.findById(id)
                .filter(d -> !d.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Department not found: " + id));

        if (departmentRepository.existsByNameIgnoreCaseAndIsDeletedFalseAndIdNot(request.getName().trim(), id)) {
            throw new DuplicateResourceException("Department with name already exists: " + request.getName());
        }
        if (departmentRepository.existsByCodeIgnoreCaseAndIsDeletedFalseAndIdNot(request.getCode().trim(), id)) {
            throw new DuplicateResourceException("Department with code already exists: " + request.getCode());
        }

        department.setName(request.getName().trim());
        department.setCode(request.getCode().trim().toUpperCase());
        department.setDescription(request.getDescription());
        department.setActive(request.isActive());

        Department updated = departmentRepository.save(department);
        log.info("Updated department: {} ({})", updated.getName(), updated.getCode());
        return mapToResponse(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public DepartmentResponse getDepartmentById(UUID id) throws ResourcesNotFoundException, UnauthorizedException, BadRequestException {
        Department department = departmentRepository.findById(id)
                .filter(d -> !d.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Department not found: " + id));

        validateUserAccessToDepartment(department.getId());
        return mapToResponse(department);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DepartmentResponse> getAllDepartments(PageRequestDTO pageRequest, Boolean active, String search) throws UnauthorizedException, BadRequestException {
        UserDataScope dataScope = dataScopeService.getScopeForCurrentUser();

        Sort.Direction direction = Sort.Direction.fromString(
                pageRequest.getSortDirection() != null ? pageRequest.getSortDirection() : "ASC"
        );
        String sortBy = pageRequest.getSortBy() != null ? pageRequest.getSortBy() : "name";
        Pageable pageable = PageRequest.of(pageRequest.getPage(), pageRequest.getSize(), Sort.by(direction, sortBy));

        Specification<Department> spec = (root, query, cb) -> cb.equal(root.get("isDeleted"), false);

        if (active != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("active"), active));
        }

        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("code")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern)
            ));
        }

        if (!dataScope.isAdmin() && dataScope.getDepartmentIds() != null && !dataScope.getDepartmentIds().isEmpty()) {
            spec = spec.and((root, query, cb) -> root.get("id").in(dataScope.getDepartmentIds()));
        }

        Page<Department> page = departmentRepository.findAll(spec, pageable);
        return page.map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DepartmentResponse> getAllActiveDepartments() throws UnauthorizedException, BadRequestException {
        UserDataScope dataScope = dataScopeService.getScopeForCurrentUser();
        List<Department> list = departmentRepository.findByActiveTrueAndIsDeletedFalse();

        if (!dataScope.isAdmin() && dataScope.getDepartmentIds() != null) {
            list = list.stream().filter(d -> dataScope.getDepartmentIds().contains(d.getId())).collect(Collectors.toList());
        }

        return list.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteDepartment(UUID id) throws BadRequestException, ResourcesNotFoundException, UnauthorizedException {
        Department department = departmentRepository.findById(id)
                .filter(d -> !d.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Department not found: " + id));

        long referencedLeads = leadRepository.count((root, query, cb) -> cb.and(
                cb.equal(root.get("isDeleted"), false),
                cb.equal(root.get("department").get("id"), id)
        ));

        if (referencedLeads > 0) {
            throw new BadRequestException("Cannot delete department with active leads assigned (" + referencedLeads + " leads)");
        }

        department.setDeleted(true);
        department.setActive(false);
        departmentRepository.save(department);
        log.info("Soft deleted department: {}", department.getName());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DepartmentResponse> getUserDepartments(UUID userId) throws ResourcesNotFoundException, UnauthorizedException {
        User user = userRepository.findById(userId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("User not found: " + userId));

        if (user.getDepartments() == null) return Collections.emptyList();
        return user.getDepartments().stream()
                .filter(d -> !d.isDeleted())
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void assignDepartmentsToUser(UUID userId, List<UUID> departmentIds) throws BadRequestException, ResourcesNotFoundException, UnauthorizedException {
        User user = userRepository.findById(userId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("User not found: " + userId));

        if (isAdminUser(user)) {
            throw new BadRequestException("Admin and SUPER_ADMIN users cannot be assigned to specific departments as they have system-wide access.");
        }

        Set<Department> newDepartments = new HashSet<>();
        if (departmentIds != null && !departmentIds.isEmpty()) {
            for (UUID dId : departmentIds) {
                Department dept = departmentRepository.findById(dId)
                        .filter(d -> !d.isDeleted())
                        .orElseThrow(() -> new ResourcesNotFoundException("Department not found: " + dId));
                newDepartments.add(dept);
            }
        }

        user.setDepartments(newDepartments);
        userRepository.save(user);
        log.info("Assigned {} departments to user {}", newDepartments.size(), user.getUsername());
    }

    @Override
    @Transactional
    public void addDepartmentToUser(UUID userId, UUID departmentId) throws BadRequestException, ResourcesNotFoundException, UnauthorizedException {
        User user = userRepository.findById(userId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("User not found: " + userId));

        if (isAdminUser(user)) {
            throw new BadRequestException("Admin and SUPER_ADMIN users cannot be assigned to specific departments as they have system-wide access.");
        }

        Department dept = departmentRepository.findById(departmentId)
                .filter(d -> !d.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Department not found: " + departmentId));

        if (user.getDepartments() == null) {
            user.setDepartments(new HashSet<>());
        }
        user.getDepartments().add(dept);
        userRepository.save(user);
        log.info("Mapped user {} to department {}", user.getUsername(), dept.getName());
    }

    @Override
    @Transactional
    public void removeDepartmentFromUser(UUID userId, UUID departmentId) throws BadRequestException, ResourcesNotFoundException, UnauthorizedException {
        User user = userRepository.findById(userId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("User not found: " + userId));

        if (user.getDepartments() != null) {
            user.getDepartments().removeIf(d -> d.getId().equals(departmentId));
            userRepository.save(user);
            log.info("Removed department {} from user {}", departmentId, user.getUsername());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> getDepartmentUsers(UUID departmentId) throws ResourcesNotFoundException, UnauthorizedException, BadRequestException {
        Department dept = departmentRepository.findById(departmentId)
                .filter(d -> !d.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Department not found: " + departmentId));

        validateUserAccessToDepartment(dept.getId());

        List<User> users = userRepository.findAll().stream()
                .filter(u -> !u.isDeleted() && u.getDepartments() != null && u.getDepartments().contains(dept))
                .collect(Collectors.toList());

        return users.stream().map(userMapper::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> getDepartmentHods(UUID departmentId) throws ResourcesNotFoundException, UnauthorizedException, BadRequestException {
        List<UserResponse> allUsers = getDepartmentUsers(departmentId);
        return allUsers.stream()
                .filter(u -> u.getRoles() != null && u.getRoles().stream().anyMatch(r -> r.toUpperCase().contains("HOD") || r.toUpperCase().contains("HEAD")))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> getDepartmentCounsellors(UUID departmentId) throws ResourcesNotFoundException, UnauthorizedException, BadRequestException {
        List<UserResponse> allUsers = getDepartmentUsers(departmentId);
        return allUsers.stream()
                .filter(u -> u.getRoles() == null || u.getRoles().stream().noneMatch(r -> RoleType.SUPER_ADMIN.name().equalsIgnoreCase(r) || RoleType.ADMIN.name().equalsIgnoreCase(r)))
                .collect(Collectors.toList());
    }

    private DepartmentResponse mapToResponse(Department dept) {
        List<User> assignedUsers = userRepository.findAll().stream()
                .filter(u -> !u.isDeleted() && u.getDepartments() != null && u.getDepartments().stream().anyMatch(d -> d.getId().equals(dept.getId())))
                .collect(Collectors.toList());

        List<UserResponse> hods = assignedUsers.stream()
                .filter(u -> isHODUser(u))
                .map(userMapper::toDto)
                .collect(Collectors.toList());

        List<UserResponse> counsellors = assignedUsers.stream()
                .filter(u -> !isAdminUser(u) && !isHODUser(u))
                .map(userMapper::toDto)
                .collect(Collectors.toList());

        return DepartmentResponse.builder()
                .id(dept.getId())
                .name(dept.getName())
                .code(dept.getCode())
                .description(dept.getDescription())
                .active(dept.isActive())
                .userCount(assignedUsers.size())
                .hods(hods)
                .counsellors(counsellors)
                .createdAt(dept.getCreatedAt())
                .updatedAt(dept.getUpdatedAt())
                .build();
    }

    private boolean isAdminUser(User user) {
        if (user.getRoles() == null) return false;
        return user.getRoles().stream()
                .anyMatch(r -> RoleType.SUPER_ADMIN.name().equalsIgnoreCase(r.getName())
                        || RoleType.ADMIN.name().equalsIgnoreCase(r.getName()));
    }

    private boolean isHODUser(User user) {
        if (user.getRoles() == null) return false;
        return user.getRoles().stream()
                .anyMatch(r -> r.getName().toUpperCase().contains("HOD") || r.getName().toUpperCase().contains("HEAD"));
    }

    private void validateUserAccessToDepartment(UUID departmentId) throws UnauthorizedException, BadRequestException {
        UserDataScope dataScope = dataScopeService.getScopeForCurrentUser();
        if (!dataScope.isAdmin() && (dataScope.getDepartmentIds() == null || !dataScope.getDepartmentIds().contains(departmentId))) {
            throw new UnauthorizedException("You are not authorized to access department: " + departmentId);
        }
    }
}
