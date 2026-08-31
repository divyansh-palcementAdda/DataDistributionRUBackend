package com.app.datadistribution.service.impl;

import com.app.datadistribution.dto.dropdown.CourseDropdownResponse;
import com.app.datadistribution.dto.dropdown.DropdownOptionResponse;
import com.app.datadistribution.dto.dropdown.DropdownPageResponse;
import com.app.datadistribution.dto.dropdown.LeadDropdownResponse;
import com.app.datadistribution.dto.dropdown.LeadStatusDropdownResponse;
import com.app.datadistribution.dto.dropdown.UserDropdownResponse;
import com.app.datadistribution.entity.Board;
import com.app.datadistribution.entity.Course;
import com.app.datadistribution.entity.CourseType;
import com.app.datadistribution.entity.Department;
import com.app.datadistribution.entity.Grade;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadSource;
import com.app.datadistribution.entity.LeadStatus;
import com.app.datadistribution.entity.Permission;
import com.app.datadistribution.entity.Role;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.SentimentCategory;
import com.app.datadistribution.enums.Status;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.AccessDeniedException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.repository.BoardRepository;
import com.app.datadistribution.repository.CourseRepository;
import com.app.datadistribution.repository.CourseTypeRepository;
import com.app.datadistribution.repository.DepartmentRepository;
import com.app.datadistribution.repository.GradeRepository;
import com.app.datadistribution.repository.LeadRepository;
import com.app.datadistribution.repository.LeadSourceRepository;
import com.app.datadistribution.repository.LeadStatusRepository;
import com.app.datadistribution.repository.PermissionRepository;
import com.app.datadistribution.repository.RoleRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.service.dto.UserDataScope.ScopeType;
import com.app.datadistribution.service.interfaces.IDropdownService;
import com.app.datadistribution.service.interfaces.ILeadDataScopeService;
import com.app.datadistribution.service.interfaces.IUserDataScopeService;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DropdownServiceImpl implements IDropdownService {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final LeadRepository leadRepository;
    private final LeadStatusRepository leadStatusRepository;
    private final LeadSourceRepository leadSourceRepository;
    private final CourseRepository courseRepository;
    private final CourseTypeRepository courseTypeRepository;
    private final BoardRepository boardRepository;
    private final GradeRepository gradeRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    private final IUserDataScopeService userDataScopeService;
    private final ILeadDataScopeService leadDataScopeService;

    @Override
    public List<UserDropdownResponse> getUsersDropdown(UUID departmentId, String role, String search)
            throws UnauthorizedException, AccessDeniedException, BadRequestException {

        UserDataScope scope = userDataScopeService.getScopeForCurrentUser();
        log.debug("User dropdown requested. Scope: {}, departmentId: {}, role: {}, search: {}",
                scope.getScopeType(), departmentId, role, search);

        // IDOR and Scope validation for departmentId
        if (departmentId != null) {
            if (scope.isDepartmentScope()) {
                if (scope.getDepartmentIds() == null || !scope.getDepartmentIds().contains(departmentId)) {
                    throw new AccessDeniedException("Access denied: You are not authorized to view users in this department.");
                }
            } else if (scope.isSelfScope()) {
                if (scope.getDepartmentIds() == null || !scope.getDepartmentIds().contains(departmentId)) {
                    throw new AccessDeniedException("Access denied: You are not authorized to view users in this department.");
                }
            }
        }

        Specification<User> spec = (root, query, cb) -> {
            query.distinct(true);
            List<Predicate> predicates = new ArrayList<>();

            // Active and non-deleted
            predicates.add(cb.isFalse(root.get("isDeleted")));
            predicates.add(cb.isTrue(root.get("active")));

            // Apply Data Scope
            if (scope.isDepartmentScope()) {
                Join<User, Department> deptJoin = root.join("departments", JoinType.INNER);
                predicates.add(cb.isFalse(deptJoin.get("isDeleted")));
                predicates.add(cb.isTrue(deptJoin.get("active")));

                if (departmentId != null) {
                    predicates.add(cb.equal(deptJoin.get("id"), departmentId));
                } else if (scope.getDepartmentIds() != null && !scope.getDepartmentIds().isEmpty()) {
                    predicates.add(deptJoin.get("id").in(scope.getDepartmentIds()));
                } else {
                    predicates.add(cb.disjunction());
                }
            } else if (scope.isSelfScope()) {
                // Counselors see self by default, or department members if department is mapped
                if (departmentId != null) {
                    Join<User, Department> deptJoin = root.join("departments", JoinType.INNER);
                    predicates.add(cb.isFalse(deptJoin.get("isDeleted")));
                    predicates.add(cb.isTrue(deptJoin.get("active")));
                    predicates.add(cb.equal(deptJoin.get("id"), departmentId));
                } else {
                    predicates.add(cb.equal(root.get("id"), scope.getUserId()));
                }
            } else if (scope.isSystemScope()) {
                if (departmentId != null) {
                    Join<User, Department> deptJoin = root.join("departments", JoinType.INNER);
                    predicates.add(cb.isFalse(deptJoin.get("isDeleted")));
                    predicates.add(cb.isTrue(deptJoin.get("active")));
                    predicates.add(cb.equal(deptJoin.get("id"), departmentId));
                }
            }

            // Optional role filter
            if (role != null && !role.isBlank()) {
                Join<User, Role> roleJoin = root.join("roles", JoinType.INNER);
                predicates.add(cb.isFalse(roleJoin.get("isDeleted")));
                predicates.add(cb.isTrue(roleJoin.get("active")));
                predicates.add(cb.equal(cb.upper(roleJoin.get("name")), role.trim().toUpperCase()));
            }

            // Optional search filter
            if (search != null && !search.isBlank()) {
                String searchPattern = "%" + search.trim().toLowerCase() + "%";
                Predicate nameMatch = cb.like(cb.lower(root.get("firstName")), searchPattern);
                Predicate lastNameMatch = cb.like(cb.lower(root.get("lastName")), searchPattern);
                Predicate usernameMatch = cb.like(cb.lower(root.get("username")), searchPattern);
                Predicate emailMatch = cb.like(cb.lower(root.get("email")), searchPattern);
                predicates.add(cb.or(nameMatch, lastNameMatch, usernameMatch, emailMatch));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        List<User> users = userRepository.findAll(spec, Sort.by(Sort.Direction.ASC, "firstName", "lastName"));

        return users.stream()
                .map(u -> UserDropdownResponse.builder()
                        .id(u.getId())
                        .name(buildUserDisplayName(u))
                        .username(u.getUsername())
                        .email(u.getEmail())
                        .build())
                .collect(Collectors.toList());
    }

    private String buildUserDisplayName(User u) {
        String first = u.getFirstName() != null ? u.getFirstName().trim() : "";
        String last = u.getLastName() != null ? u.getLastName().trim() : "";
        String full = (first + " " + last).trim();
        return !full.isEmpty() ? full : u.getUsername();
    }

    @Override
    public List<DropdownOptionResponse> getDepartmentsDropdown(String search)
            throws UnauthorizedException, AccessDeniedException, BadRequestException {

        UserDataScope scope = userDataScopeService.getScopeForCurrentUser();
        log.debug("Department dropdown requested. Scope: {}, search: {}", scope.getScopeType(), search);

        Specification<Department> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("isDeleted")));
            predicates.add(cb.isTrue(root.get("active")));

            if (scope.isDepartmentScope() || scope.isSelfScope()) {
                Set<UUID> allowedDeptIds = scope.getDepartmentIds();
                if (allowedDeptIds == null || allowedDeptIds.isEmpty()) {
                    predicates.add(cb.disjunction());
                } else {
                    predicates.add(root.get("id").in(allowedDeptIds));
                }
            }

            if (search != null && !search.isBlank()) {
                String searchPattern = "%" + search.trim().toLowerCase() + "%";
                Predicate nameMatch = cb.like(cb.lower(root.get("name")), searchPattern);
                Predicate codeMatch = cb.like(cb.lower(root.get("code")), searchPattern);
                predicates.add(cb.or(nameMatch, codeMatch));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        List<Department> departments = departmentRepository.findAll(spec, Sort.by(Sort.Direction.ASC, "name"));

        return departments.stream()
                .map(d -> DropdownOptionResponse.builder()
                        .id(d.getId())
                        .name(d.getName())
                        .code(d.getCode())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public DropdownPageResponse<LeadDropdownResponse> getLeadsDropdown(int page, int size, String search, UUID departmentId, UUID statusId)
            throws UnauthorizedException, AccessDeniedException, BadRequestException {

        UserDataScope scope = leadDataScopeService.getCurrentUserScope();
        log.debug("Lead dropdown requested. Scope: {}, page: {}, size: {}, search: {}",
                scope.getScopeType(), page, size, search);

        // IDOR check on requested departmentId for non-admin
        if (departmentId != null) {
            if (scope.isDepartmentScope() || scope.isSelfScope()) {
                if (scope.getDepartmentIds() == null || !scope.getDepartmentIds().contains(departmentId)) {
                    throw new AccessDeniedException("Access denied: You are not authorized to view leads in this department.");
                }
            }
        }

        // Base Scope Specification
        Specification<Lead> spec = leadDataScopeService.getLeadScopeSpecification(scope);

        // Lead specific filters
        Specification<Lead> filterSpec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("isDeleted")));
            predicates.add(cb.isTrue(root.get("active")));

            if (departmentId != null) {
                predicates.add(cb.equal(root.get("department").get("id"), departmentId));
            }
            if (statusId != null) {
                predicates.add(cb.equal(root.get("currentStatus").get("id"), statusId));
            }
            if (search != null && !search.isBlank()) {
                String searchPattern = "%" + search.trim().toLowerCase() + "%";
                Predicate codeMatch = cb.like(cb.lower(root.get("leadCode")), searchPattern);
                Predicate nameMatch = cb.like(cb.lower(root.get("fullName")), searchPattern);
                Predicate phoneMatch = cb.like(cb.lower(root.get("phoneNumber")), searchPattern);
                predicates.add(cb.or(codeMatch, nameMatch, phoneMatch));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Specification<Lead> finalSpec = spec != null ? spec.and(filterSpec) : filterSpec;

        int pageNum = Math.max(0, page);
        int pageSize = size > 0 ? Math.min(size, 100) : 20;
        PageRequest pageRequest = PageRequest.of(pageNum, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Lead> leadPage = leadRepository.findAll(finalSpec, pageRequest);

        List<LeadDropdownResponse> content = leadPage.getContent().stream()
                .map(l -> LeadDropdownResponse.builder()
                        .id(l.getId())
                        .leadCode(l.getLeadCode())
                        .fullName(l.getFullName())
                        .phoneNumber(l.getPhoneNumber())
                        .build())
                .collect(Collectors.toList());

        return DropdownPageResponse.<LeadDropdownResponse>builder()
                .content(content)
                .pageNumber(leadPage.getNumber())
                .pageSize(leadPage.getSize())
                .totalElements(leadPage.getTotalElements())
                .totalPages(leadPage.getTotalPages())
                .last(leadPage.isLast())
                .build();
    }

    @Override
    public List<LeadStatusDropdownResponse> getLeadStatusesDropdown(SentimentCategory sentimentCategory) {
        List<LeadStatus> statuses = leadStatusRepository.findAll().stream()
                .filter(s -> s != null && s.isActive() && !s.isDeleted())
                .filter(s -> sentimentCategory == null || s.getSentimentCategory() == sentimentCategory)
                .sorted(Comparator.comparing(LeadStatus::getDisplayOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(LeadStatus::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .collect(Collectors.toList());

        return statuses.stream()
                .map(s -> LeadStatusDropdownResponse.builder()
                        .id(s.getId())
                        .name(s.getName())
                        .code(s.getCode())
                        .sentimentCategory(s.getSentimentCategory() != null ? s.getSentimentCategory().name() : null)
                        .displayOrder(s.getDisplayOrder())
                        .isFollowUpStatus(s.isFollowUpStatus())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public List<com.app.datadistribution.dto.dropdown.FollowUpStatusDropdownResponse> getFollowUpStatusesDropdown() {
        return java.util.Arrays.stream(com.app.datadistribution.enums.FollowUpStatus.values())
                .map(status -> com.app.datadistribution.dto.dropdown.FollowUpStatusDropdownResponse.builder()
                        .name(status.name())
                        .displayName(status.getDisplayName() != null ? status.getDisplayName() : status.name())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public List<LeadStatusDropdownResponse> getFollowUpLeadStatusesDropdown() {
        List<LeadStatus> statuses = leadStatusRepository.findByActiveTrueAndIsFollowUpStatusTrueAndIsDeletedFalseOrderByDisplayOrderAsc();

        return statuses.stream()
                .map(s -> LeadStatusDropdownResponse.builder()
                        .id(s.getId())
                        .name(s.getName())
                        .code(s.getCode())
                        .sentimentCategory(s.getSentimentCategory() != null ? s.getSentimentCategory().name() : null)
                        .displayOrder(s.getDisplayOrder())
                        .isFollowUpStatus(s.isFollowUpStatus())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public List<DropdownOptionResponse> getLeadSourcesDropdown(String search) {
        List<LeadSource> sources = leadSourceRepository.findAll().stream()
                .filter(s -> s != null && s.isActive() && !s.isDeleted())
                .filter(s -> {
                    if (search == null || search.isBlank()) return true;
                    String pattern = search.trim().toLowerCase();
                    return (s.getName() != null && s.getName().toLowerCase().contains(pattern))
                            || (s.getCode() != null && s.getCode().toLowerCase().contains(pattern));
                })
                .sorted(Comparator.comparing(LeadSource::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .collect(Collectors.toList());

        return sources.stream()
                .map(s -> DropdownOptionResponse.builder()
                        .id(s.getId())
                        .name(s.getName())
                        .code(s.getCode())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public List<CourseDropdownResponse> getCoursesDropdown(UUID courseTypeId, String search) {
        List<Course> courses = courseRepository.findAll().stream()
                .filter(c -> c != null && c.getStatus() == Status.ACTIVE && !c.isDeleted())
                .filter(c -> courseTypeId == null || (c.getCourseType() != null && courseTypeId.equals(c.getCourseType().getId())))
                .filter(c -> {
                    if (search == null || search.isBlank()) return true;
                    String pattern = search.trim().toLowerCase();
                    return (c.getCourseName() != null && c.getCourseName().toLowerCase().contains(pattern))
                            || (c.getCourseCode() != null && c.getCourseCode().toLowerCase().contains(pattern));
                })
                .sorted(Comparator.comparing(Course::getCourseName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .collect(Collectors.toList());

        return courses.stream()
                .map(c -> CourseDropdownResponse.builder()
                        .id(c.getId())
                        .name(c.getCourseName())
                        .code(c.getCourseCode())
                        .courseTypeId(c.getCourseType() != null ? c.getCourseType().getId() : null)
                        .courseTypeName(c.getCourseType() != null ? c.getCourseType().getName() : null)
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public List<DropdownOptionResponse> getCourseTypesDropdown(String search) {
        List<CourseType> courseTypes = courseTypeRepository.findAll().stream()
                .filter(ct -> ct != null && ct.getStatus() == Status.ACTIVE && !ct.isDeleted())
                .filter(ct -> {
                    if (search == null || search.isBlank()) return true;
                    String pattern = search.trim().toLowerCase();
                    return ct.getName() != null && ct.getName().toLowerCase().contains(pattern);
                })
                .sorted(Comparator.comparing(CourseType::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .collect(Collectors.toList());

        return courseTypes.stream()
                .map(ct -> DropdownOptionResponse.builder()
                        .id(ct.getId())
                        .name(ct.getName())
                        .code(null)
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public List<DropdownOptionResponse> getBoardsDropdown(String search) {
        List<Board> boards = boardRepository.findAll().stream()
                .filter(b -> b != null && b.isActive() && !b.isDeleted())
                .filter(b -> {
                    if (search == null || search.isBlank()) return true;
                    String pattern = search.trim().toLowerCase();
                    return (b.getName() != null && b.getName().toLowerCase().contains(pattern))
                            || (b.getCode() != null && b.getCode().toLowerCase().contains(pattern));
                })
                .sorted(Comparator.comparing(Board::getDisplayOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Board::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .collect(Collectors.toList());

        return boards.stream()
                .map(b -> DropdownOptionResponse.builder()
                        .id(b.getId())
                        .name(b.getName())
                        .code(b.getCode())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public List<DropdownOptionResponse> getGradesDropdown(String search) {
        List<Grade> grades = gradeRepository.findAll().stream()
                .filter(g -> g != null && g.isActive() && !g.isDeleted())
                .filter(g -> {
                    if (search == null || search.isBlank()) return true;
                    String pattern = search.trim().toLowerCase();
                    return (g.getName() != null && g.getName().toLowerCase().contains(pattern))
                            || (g.getCode() != null && g.getCode().toLowerCase().contains(pattern));
                })
                .sorted(Comparator.comparing(Grade::getDisplayOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Grade::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .collect(Collectors.toList());

        return grades.stream()
                .map(g -> DropdownOptionResponse.builder()
                        .id(g.getId())
                        .name(g.getName())
                        .code(g.getCode())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public List<DropdownOptionResponse> getRolesDropdown(String search) {
        List<Role> roles = roleRepository.findAll().stream()
                .filter(r -> r != null && r.isActive() && !r.isDeleted())
                .filter(r -> {
                    if (search == null || search.isBlank()) return true;
                    String pattern = search.trim().toLowerCase();
                    return r.getName() != null && r.getName().toLowerCase().contains(pattern);
                })
                .sorted(Comparator.comparing(Role::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .collect(Collectors.toList());

        return roles.stream()
                .map(r -> DropdownOptionResponse.builder()
                        .id(r.getId())
                        .name(r.getName())
                        .code(null)
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public List<DropdownOptionResponse> getPermissionsDropdown(String search) {
        List<Permission> permissions = permissionRepository.findAll().stream()
                .filter(p -> p != null && p.isActive() && !p.isDeleted())
                .filter(p -> {
                    if (search == null || search.isBlank()) return true;
                    String pattern = search.trim().toLowerCase();
                    return p.getName() != null && p.getName().toLowerCase().contains(pattern);
                })
                .sorted(Comparator.comparing(Permission::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .collect(Collectors.toList());

        return permissions.stream()
                .map(p -> DropdownOptionResponse.builder()
                        .id(p.getId())
                        .name(p.getName())
                        .code(null)
                        .build())
                .collect(Collectors.toList());
    }
}
