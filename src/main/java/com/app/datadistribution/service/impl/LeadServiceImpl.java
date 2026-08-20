package com.app.datadistribution.service.impl;

import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.lead.*;
import com.app.datadistribution.entity.*;
import com.app.datadistribution.enums.RoleType;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.mapper.LeadMapper;
import com.app.datadistribution.repository.*;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.service.dto.UserDataScope.ScopeType;
import com.app.datadistribution.service.interfaces.ILeadService;
import com.app.datadistribution.service.interfaces.IUserDataScopeService;
import jakarta.persistence.Tuple;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeadServiceImpl implements ILeadService {

    private final LeadRepository leadRepository;
    private final LeadSourceRepository leadSourceRepository;
    private final LeadStatusRepository leadStatusRepository;
    private final BoardRepository boardRepository;
    private final GradeRepository gradeRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final LeadStatusHistoryRepository leadStatusHistoryRepository;
    private final LeadFeedbackRepository leadFeedbackRepository;
    private final CourseRepository courseRepository;
    private final IUserDataScopeService dataScopeService;
    private final LeadMapper leadMapper;

    private static final Set<String> ALLOWED_LEAD_SORT_FIELDS = Set.of(
            "id", "leadCode", "fullName", "phoneNumber", "email", "city", "state", "country",
            "currentStatus", "createdAt", "updatedAt", "lastContactedAt", "nextFollowUpDate"
    );

    @Override
    @Transactional
    public LeadResponse create(LeadRequest request) throws BadRequestException, UnauthorizedException {
        User currentUser = getCurrentUserEntity();
        
        Set<LeadSource> sources = resolveLeadSources(request.getLeadSourceIds());
        Set<Course> interestedCourses = resolveCourses(request.getInterestedCourseIds());

        User assignedTo = null;
        if (request.getAssignedToUserId() != null) {
            assignedTo = userRepository.findById(request.getAssignedToUserId())
                    .filter(u -> !u.isDeleted())
                    .orElseThrow(() -> new ResourcesNotFoundException("User not found with id: " + request.getAssignedToUserId()));
        }

        Department department = null;
        if (request.getDepartmentId() != null) {
            department = departmentRepository.findById(request.getDepartmentId())
                    .filter(d -> !d.isDeleted())
                    .orElseThrow(() -> new ResourcesNotFoundException("Department not found with id: " + request.getDepartmentId()));
        }

        if (assignedTo != null && department != null) {
            validateLeadAssignmentDepartment(assignedTo, department);
        }

        String leadCode = request.getLeadCode();
        if (leadCode == null || leadCode.isBlank()) {
            leadCode = "LEAD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } else if (leadRepository.existsByLeadCode(leadCode)) {
            throw new BadRequestException("Lead code already exists: " + leadCode);
        }

        UUID regCourseId = request.getRegisteredCourseId() != null ? request.getRegisteredCourseId() : request.getCourseId();
        Course course = null;
        if (regCourseId != null) {
            course = courseRepository.findById(regCourseId)
                    .filter(c -> !c.isDeleted())
                    .orElseThrow(() -> new ResourcesNotFoundException("Course not found with id: " + regCourseId));
        }

        Board board = null;
        if (request.getBoardId() != null) {
            board = boardRepository.findById(request.getBoardId())
                    .filter(b -> !b.isDeleted())
                    .orElseThrow(() -> new ResourcesNotFoundException("Board not found with id: " + request.getBoardId()));
        }

        Grade grade = null;
        if (request.getGradeId() != null) {
            grade = gradeRepository.findById(request.getGradeId())
                    .filter(g -> !g.isDeleted())
                    .orElseThrow(() -> new ResourcesNotFoundException("Grade not found with id: " + request.getGradeId()));
        }

        LeadStatus initialStatus = resolveInitialStatus(request.getStatusId());

        Lead lead = leadMapper.toEntity(request);
        lead.setLeadCode(leadCode);
        lead.setLeadSources(sources);
        lead.setInterestedCourses(interestedCourses);
        lead.setAssignedTo(assignedTo);
        lead.setCreatedByUser(currentUser);
        lead.setCourse(course);
        lead.setBoard(board);
        lead.setGrade(grade);
        lead.setDepartment(department);
        lead.setCurrentStatus(initialStatus);
        lead.setActive(true);

        Lead saved = leadRepository.save(lead);
        log.info("Created lead: {} ({})", saved.getFullName(), saved.getLeadCode());

        LeadStatusHistory initialHistory = LeadStatusHistory.builder()
                .lead(saved)
                .previousStatus(null)
                .newStatus(initialStatus)
                .changedByUser(currentUser)
                .feedback("Lead registered in system.")
                .build();
        leadStatusHistoryRepository.save(initialHistory);

        return leadMapper.toDto(saved);
    }

    @Override
    @Transactional
    public LeadResponse update(UUID id, LeadRequest request) {
        Lead lead = leadRepository.findById(id)
                .filter(l -> !l.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Lead not found with id: " + id));

        Set<LeadSource> sources = resolveLeadSources(request.getLeadSourceIds());

        if (request.getInterestedCourseIds() != null) {
            Set<Course> interestedCourses = resolveCourses(request.getInterestedCourseIds());
            lead.setInterestedCourses(interestedCourses);
        }

        User assignedTo = null;
        if (request.getAssignedToUserId() != null) {
            assignedTo = userRepository.findById(request.getAssignedToUserId())
                    .filter(u -> !u.isDeleted())
                    .orElseThrow(() -> new ResourcesNotFoundException("User not found with id: " + request.getAssignedToUserId()));
        }

        Department department = null;
        if (request.getDepartmentId() != null) {
            department = departmentRepository.findById(request.getDepartmentId())
                    .filter(d -> !d.isDeleted())
                    .orElseThrow(() -> new ResourcesNotFoundException("Department not found with id: " + request.getDepartmentId()));
        }

        if (assignedTo != null && department != null) {
            validateLeadAssignmentDepartment(assignedTo, department);
        }

        UUID regCourseId = request.getRegisteredCourseId() != null ? request.getRegisteredCourseId() : request.getCourseId();
        Course course = null;
        if (regCourseId != null) {
            course = courseRepository.findById(regCourseId)
                    .filter(c -> !c.isDeleted())
                    .orElseThrow(() -> new ResourcesNotFoundException("Course not found with id: " + regCourseId));
        }

        Board board = null;
        if (request.getBoardId() != null) {
            board = boardRepository.findById(request.getBoardId())
                    .filter(b -> !b.isDeleted())
                    .orElseThrow(() -> new ResourcesNotFoundException("Board not found with id: " + request.getBoardId()));
        }

        Grade grade = null;
        if (request.getGradeId() != null) {
            grade = gradeRepository.findById(request.getGradeId())
                    .filter(g -> !g.isDeleted())
                    .orElseThrow(() -> new ResourcesNotFoundException("Grade not found with id: " + request.getGradeId()));
        }

        LeadStatus oldStatus = lead.getCurrentStatus();
        LeadStatus newStatus = null;
        if (request.getStatusId() != null) {
            newStatus = leadStatusRepository.findById(request.getStatusId())
                    .filter(s -> !s.isDeleted())
                    .orElseThrow(() -> new ResourcesNotFoundException("Lead status not found with id: " + request.getStatusId()));
            lead.setCurrentStatus(newStatus);
        }

        lead.setFullName(request.getFullName());
        lead.setPhoneNumber(request.getPhoneNumber());
        lead.setAlternatePhoneNumber(request.getAlternatePhoneNumber());
        lead.setEmail(request.getEmail());
        lead.setCity(request.getCity());
        lead.setState(request.getState());
        lead.setCountry(request.getCountry());
        lead.setLeadSources(sources);
        lead.setSourceDetails(request.getSourceDetails());
        lead.setCourseInterested(request.getCourseInterested());
        lead.setRemarks(request.getRemarks());
        lead.setAssignedTo(assignedTo);
        lead.setCourse(course);
        lead.setBoard(board);
        lead.setGrade(grade);
        if (department != null) {
            lead.setDepartment(department);
        }
        lead.setActive(request.isActive());
        if (request.getNextFollowUpDate() != null) {
            lead.setNextFollowUpDate(request.getNextFollowUpDate());
        }

        Lead updated = leadRepository.save(lead);
        log.info("Updated lead: {}", updated.getLeadCode());

        // Automatic Status Change Detection & History Logging
        if (newStatus != null && (oldStatus == null || !oldStatus.getId().equals(newStatus.getId()))) {
            User currentUser = null;
            try {
                currentUser = getCurrentUserEntity();
            } catch (Exception e) {
                log.warn("Could not resolve authenticated user for lead update status audit");
            }
            String remark = request.getRemarks() != null && !request.getRemarks().isBlank()
                    ? request.getRemarks()
                    : "Lead status updated via general lead update.";

            LeadStatusHistory history = LeadStatusHistory.builder()
                    .lead(updated)
                    .previousStatus(oldStatus)
                    .newStatus(newStatus)
                    .changedByUser(currentUser)
                    .feedback(remark)
                    .build();
            leadStatusHistoryRepository.save(history);
            log.info("Recorded status change history for lead {}: {} -> {}", updated.getLeadCode(),
                    oldStatus != null ? oldStatus.getName() : "null", newStatus.getName());
        }

        return leadMapper.toDto(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public LeadResponse getById(UUID id) {
        Lead lead = leadRepository.findById(id)
                .filter(l -> !l.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Lead not found with id: " + id));
        return leadMapper.toDto(lead);
    }

    @Override
    @Transactional(readOnly = true)
    public LeadPageResponse getAllLeads(PageRequestDTO pageRequest, List<UUID> leadSourceIds, UUID courseId, List<UUID> interestedCourseIds, UUID registeredCourseId, UUID courseTypeId, Boolean withoutCourse, UUID statusId, List<UUID> statusIds, UUID boardId, List<UUID> boardIds, UUID gradeId, List<UUID> gradeIds) throws UnauthorizedException {
        UserDataScope dataScope = dataScopeService.getScopeForCurrentUser();

        String sortBy = pageRequest.getSortBy();
        if (sortBy == null || !ALLOWED_LEAD_SORT_FIELDS.contains(sortBy)) {
            sortBy = "createdAt";
        }
        Sort.Direction direction = Sort.Direction.fromString(
                pageRequest.getSortDirection() != null ? pageRequest.getSortDirection() : "ASC"
        );
        Pageable pageable = PageRequest.of(pageRequest.getPage(), pageRequest.getSize(), Sort.by(direction, sortBy));

        Specification<Lead> spec = Specification.where(isNotDeleted());

        if (dataScope.getScopeType() == ScopeType.SELF) {
            spec = spec.and(filterByAssignedOrCreatedUser(dataScope.getUserId()));
        } else if (dataScope.getScopeType() == ScopeType.DEPARTMENT) {
            spec = spec.and(filterByDepartmentScope(dataScope.getDepartmentIds(), dataScope.getDepartmentUserIds(), dataScope.getUserId()));
        }

        if (leadSourceIds != null && !leadSourceIds.isEmpty()) {
            spec = spec.and(filterBySources(leadSourceIds));
        }
        if (courseId != null) {
            spec = spec.and(filterByCourse(courseId));
        }
        if (interestedCourseIds != null && !interestedCourseIds.isEmpty()) {
            spec = spec.and(filterByInterestedCourses(interestedCourseIds));
        }
        if (registeredCourseId != null) {
            spec = spec.and(filterByCourse(registeredCourseId));
        }
        if (courseTypeId != null) {
            spec = spec.and(filterByCourseType(courseTypeId));
        }
        if (Boolean.TRUE.equals(withoutCourse)) {
            spec = spec.and(filterWithoutCourse());
        }
        if (statusId != null) {
            spec = spec.and(filterByStatus(statusId));
        } else if (statusIds != null && !statusIds.isEmpty()) {
            spec = spec.and(filterByStatusIds(statusIds));
        }
        if (boardId != null) {
            spec = spec.and(filterByBoard(boardId));
        } else if (boardIds != null && !boardIds.isEmpty()) {
            spec = spec.and(filterByBoardIds(boardIds));
        }
        if (gradeId != null) {
            spec = spec.and(filterByGrade(gradeId));
        } else if (gradeIds != null && !gradeIds.isEmpty()) {
            spec = spec.and(filterByGradeIds(gradeIds));
        }
        if (pageRequest.getSearch() != null && !pageRequest.getSearch().isBlank()) {
            spec = spec.and(searchLeads(pageRequest.getSearch()));
        }

        Page<Lead> page = leadRepository.findAll(spec, pageable);
        List<LeadResponse> content = page.getContent().stream()
                .map(leadMapper::toDto)
                .collect(Collectors.toList());

        return LeadPageResponse.builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    @Override
    @Transactional
    public LeadResponse addInterestedCourses(UUID leadId, List<UUID> courseIds) {
        Lead lead = leadRepository.findById(leadId)
                .filter(l -> !l.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Lead not found with id: " + leadId));

        Set<Course> coursesToAdd = resolveCourses(courseIds);
        if (lead.getInterestedCourses() == null) {
            lead.setInterestedCourses(new HashSet<>());
        }
        lead.getInterestedCourses().addAll(coursesToAdd);

        Lead updated = leadRepository.save(lead);
        log.info("Added {} interested courses to lead {}", coursesToAdd.size(), lead.getLeadCode());
        return leadMapper.toDto(updated);
    }

    @Override
    @Transactional
    public LeadResponse removeInterestedCourse(UUID leadId, UUID courseId) {
        Lead lead = leadRepository.findById(leadId)
                .filter(l -> !l.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Lead not found with id: " + leadId));

        if (lead.getInterestedCourses() != null) {
            lead.getInterestedCourses().removeIf(c -> c.getId().equals(courseId));
        }

        Lead updated = leadRepository.save(lead);
        log.info("Removed interested course {} from lead {}", courseId, lead.getLeadCode());
        return leadMapper.toDto(updated);
    }

    @Override
    @Transactional
    public LeadResponse registerCourse(UUID leadId, UUID courseId) {
        Lead lead = leadRepository.findById(leadId)
                .filter(l -> !l.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Lead not found with id: " + leadId));

        Course course = courseRepository.findById(courseId)
                .filter(c -> !c.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Course not found with id: " + courseId));

        lead.setCourse(course);
        if (lead.getInterestedCourses() == null) {
            lead.setInterestedCourses(new HashSet<>());
        }
        lead.getInterestedCourses().add(course);

        Lead updated = leadRepository.save(lead);
        log.info("Registered lead {} in course {}", lead.getLeadCode(), course.getCourseName());
        return leadMapper.toDto(updated);
    }

    @Override
    @Transactional
    public void deleteLead(UUID id) {
        Lead lead = leadRepository.findById(id)
                .filter(l -> !l.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Lead not found with id: " + id));
        lead.setDeleted(true);
        leadRepository.save(lead);
        log.info("Soft deleted lead: {}", lead.getLeadCode());
    }

    @Override
    @Transactional
    public LeadResponse changeStatus(UUID id, LeadStatusChangeRequest request) throws BadRequestException, UnauthorizedException {
        if (request.getFeedback() == null || request.getFeedback().isBlank()) {
            throw new BadRequestException("Feedback is required when changing lead status.");
        }

        Lead lead = leadRepository.findById(id)
                .filter(l -> !l.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Lead not found with id: " + id));

        LeadStatus newStatus = resolveNewStatus(request);
        User currentUser = getCurrentUserEntity();

        LeadStatus oldStatus = lead.getCurrentStatus();
        boolean statusChanged = (oldStatus == null || !oldStatus.getId().equals(newStatus.getId()));

        lead.setCurrentStatus(newStatus);
        Lead updated = leadRepository.save(lead);

        if (statusChanged) {
            LeadStatusHistory history = LeadStatusHistory.builder()
                    .lead(updated)
                    .previousStatus(oldStatus)
                    .newStatus(newStatus)
                    .changedByUser(currentUser)
                    .feedback(request.getFeedback())
                    .build();
            leadStatusHistoryRepository.save(history);
            log.info("Recorded status change history for lead {}: {} -> {}", updated.getLeadCode(),
                    oldStatus != null ? oldStatus.getName() : "null", newStatus.getName());
        }

        LeadFeedback feedback = LeadFeedback.builder()
                .lead(updated)
                .createdByUser(currentUser)
                .feedback(request.getFeedback())
                .statusAtTime(newStatus)
                .build();
        leadFeedbackRepository.save(feedback);

        return leadMapper.toDto(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeadStatusHistoryResponse> getStatusHistoryByLeadId(UUID leadId) throws UnauthorizedException {
        Lead lead = leadRepository.findById(leadId)
                .filter(l -> !l.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Lead not found with id: " + leadId));

        validateLeadDataScopeAccess(lead);

        List<LeadStatusHistory> histories = leadStatusHistoryRepository.findByLeadIdOrderByCreatedAtDesc(lead.getId());
        return histories.stream()
                .map(leadMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public LeadStatusHistoryPageResponse getStatusHistoryByLeadId(UUID leadId, PageRequestDTO pageRequest) throws UnauthorizedException {
        Lead lead = leadRepository.findById(leadId)
                .filter(l -> !l.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Lead not found with id: " + leadId));

        validateLeadDataScopeAccess(lead);

        String sortBy = pageRequest.getSortBy();
        if (sortBy == null || sortBy.isBlank()) {
            sortBy = "createdAt";
        }
        Sort.Direction direction = Sort.Direction.fromString(
                pageRequest.getSortDirection() != null ? pageRequest.getSortDirection() : "DESC"
        );
        Pageable pageable = PageRequest.of(pageRequest.getPage(), pageRequest.getSize(), Sort.by(direction, sortBy));

        Page<LeadStatusHistory> page = leadStatusHistoryRepository.findByLeadId(lead.getId(), pageable);
        List<LeadStatusHistoryResponse> content = page.getContent().stream()
                .map(leadMapper::toDto)
                .collect(Collectors.toList());

        return LeadStatusHistoryPageResponse.builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    private void validateLeadDataScopeAccess(Lead lead) throws UnauthorizedException {
        UserDataScope dataScope = dataScopeService.getScopeForCurrentUser();
        if (dataScope.getScopeType() == ScopeType.SYSTEM) {
            return;
        }
        if (dataScope.getScopeType() == ScopeType.SELF) {
            boolean isAssigned = lead.getAssignedTo() != null && lead.getAssignedTo().getId().equals(dataScope.getUserId());
            boolean isCreator = lead.getCreatedByUser() != null && lead.getCreatedByUser().getId().equals(dataScope.getUserId());
            if (!isAssigned && !isCreator) {
                throw new UnauthorizedException("You do not have permission to view status history for this lead.");
            }
        } else if (dataScope.getScopeType() == ScopeType.DEPARTMENT) {
            boolean isOwn = (lead.getAssignedTo() != null && lead.getAssignedTo().getId().equals(dataScope.getUserId()))
                    || (lead.getCreatedByUser() != null && lead.getCreatedByUser().getId().equals(dataScope.getUserId()));
            boolean isDeptLead = lead.getDepartment() != null && dataScope.getDepartmentIds() != null && dataScope.getDepartmentIds().contains(lead.getDepartment().getId());
            boolean isDeptUser = lead.getAssignedTo() != null && dataScope.getDepartmentUserIds() != null && dataScope.getDepartmentUserIds().contains(lead.getAssignedTo().getId());
            if (!isOwn && !isDeptLead && !isDeptUser) {
                throw new UnauthorizedException("You do not have permission to view status history for this lead outside your department scope.");
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeadSourceStatsResponse> getSourceWiseStats() {
        List<Object[]> results = leadRepository.countBySource();
        List<LeadSourceStatsResponse> stats = new ArrayList<>();
        for (Object[] row : results) {
            LeadSource source = (LeadSource) row[0];
            Long count = (Long) row[1];
            if (source != null) {
                stats.add(LeadSourceStatsResponse.builder()
                        .sourceId(source.getId())
                        .sourceName(source.getName())
                        .count(count)
                        .build());
            }
        }
        return stats;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> getStatusWiseStats() {
        List<Object[]> results = leadRepository.countByStatus();
        Map<String, Long> stats = new HashMap<>();
        for (Object[] row : results) {
            LeadStatus status = (LeadStatus) row[0];
            Long count = (Long) row[1];
            if (status != null) {
                stats.put(status.getCode(), count);
            }
        }
        return stats;
    }

    // --- Helper Methods & Specifications ---

    private User getCurrentUserEntity() throws UnauthorizedException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new UnauthorizedException("User is not authenticated");
        }
        String username = auth.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourcesNotFoundException("User not found with username: " + username));
    }

    private void validateLeadAssignmentDepartment(User user, Department department) {
        if (user.getRoles() != null && user.getRoles().stream().anyMatch(r -> RoleType.SUPER_ADMIN.name().equalsIgnoreCase(r.getName()) || RoleType.ADMIN.name().equalsIgnoreCase(r.getName()))) {
            return;
        }
        if (user.getDepartments() != null && !user.getDepartments().isEmpty()) {
            boolean matches = user.getDepartments().stream().anyMatch(d -> d.getId().equals(department.getId()));
            if (!matches) {
                log.warn("Assigned user {} is not mapped to lead department {}", user.getUsername(), department.getName());
            }
        }
    }

    private Set<LeadSource> resolveLeadSources(List<UUID> leadSourceIds) {
        if (leadSourceIds == null || leadSourceIds.isEmpty()) {
            return new HashSet<>();
        }
        Set<LeadSource> sources = new HashSet<>();
        for (UUID id : leadSourceIds) {
            LeadSource source = leadSourceRepository.findById(id)
                    .filter(s -> !s.isDeleted())
                    .orElseThrow(() -> new ResourcesNotFoundException("Lead source not found with id: " + id));
            sources.add(source);
        }
        return sources;
    }

    private Set<Course> resolveCourses(List<UUID> courseIds) {
        if (courseIds == null || courseIds.isEmpty()) {
            return new HashSet<>();
        }
        Set<Course> courses = new HashSet<>();
        for (UUID id : courseIds) {
            Course course = courseRepository.findById(id)
                    .filter(c -> !c.isDeleted())
                    .orElseThrow(() -> new ResourcesNotFoundException("Course not found with id: " + id));
            courses.add(course);
        }
        return courses;
    }

    private Specification<Lead> isNotDeleted() {
        return (root, query, cb) -> cb.equal(root.get("isDeleted"), false);
    }

    private Specification<Lead> filterByAssignedOrCreatedUser(UUID userId) {
        return (root, query, cb) -> cb.or(
                cb.equal(root.get("assignedTo").get("id"), userId),
                cb.equal(root.get("createdByUser").get("id"), userId)
        );
    }

    private Specification<Lead> filterByDepartmentScope(Set<UUID> departmentIds, Set<UUID> departmentUserIds, UUID currentUserId) {
        return (root, query, cb) -> {
            jakarta.persistence.criteria.Predicate ownData = cb.or(
                    cb.equal(root.get("assignedTo").get("id"), currentUserId),
                    cb.equal(root.get("createdByUser").get("id"), currentUserId)
            );
            if (departmentIds == null || departmentIds.isEmpty()) {
                return ownData;
            }
            jakarta.persistence.criteria.Predicate deptLead = root.get("department").get("id").in(departmentIds);
            jakarta.persistence.criteria.Predicate deptUser = root.get("assignedTo").get("id").in(departmentUserIds);
            return cb.or(ownData, deptLead, deptUser);
        };
    }

    private Specification<Lead> filterBySources(List<UUID> leadSourceIds) {
        return (root, query, cb) -> {
            query.distinct(true);
            return root.join("leadSources", jakarta.persistence.criteria.JoinType.INNER).get("id").in(leadSourceIds);
        };
    }

    private Specification<Lead> filterByInterestedCourses(List<UUID> interestedCourseIds) {
        return (root, query, cb) -> {
            query.distinct(true);
            return root.join("interestedCourses", jakarta.persistence.criteria.JoinType.INNER).get("id").in(interestedCourseIds);
        };
    }

    private Specification<Lead> filterByCourseType(UUID courseTypeId) {
        return (root, query, cb) -> {
            query.distinct(true);
            jakarta.persistence.criteria.Join<Object, Object> interestedJoin = root.join("interestedCourses", jakarta.persistence.criteria.JoinType.LEFT);
            jakarta.persistence.criteria.Join<Object, Object> registeredJoin = root.join("course", jakarta.persistence.criteria.JoinType.LEFT);
            return cb.or(
                    cb.equal(interestedJoin.join("courseType", jakarta.persistence.criteria.JoinType.LEFT).get("id"), courseTypeId),
                    cb.equal(registeredJoin.join("courseType", jakarta.persistence.criteria.JoinType.LEFT).get("id"), courseTypeId)
            );
        };
    }

    private Specification<Lead> filterByCourse(UUID courseId) {
        return (root, query, cb) -> cb.equal(root.get("course").get("id"), courseId);
    }

    private Specification<Lead> filterWithoutCourse() {
        return (root, query, cb) -> cb.isNull(root.get("course"));
    }

    private LeadStatus resolveInitialStatus(UUID statusId) {
        if (statusId != null) {
            return leadStatusRepository.findById(statusId)
                    .filter(s -> !s.isDeleted())
                    .orElseThrow(() -> new ResourcesNotFoundException("Lead status not found with id: " + statusId));
        }
        return leadStatusRepository.findByCodeIgnoreCase("RAW")
                .or(() -> leadStatusRepository.findByNameIgnoreCase("Raw"))
                .orElseGet(() -> {
                    List<LeadStatus> all = leadStatusRepository.findAll();
                    return all.stream().filter(s -> !s.isDeleted()).findFirst()
                            .orElseThrow(() -> new ResourcesNotFoundException("No active Lead Status configured in database"));
                });
    }

    private LeadStatus resolveNewStatus(LeadStatusChangeRequest request) throws BadRequestException {
        if (request.getNewStatusId() != null) {
            return leadStatusRepository.findById(request.getNewStatusId())
                    .filter(s -> !s.isDeleted())
                    .orElseThrow(() -> new ResourcesNotFoundException("Lead status not found with id: " + request.getNewStatusId()));
        }
        if (request.getStatusCode() != null && !request.getStatusCode().isBlank()) {
            return leadStatusRepository.findByCodeIgnoreCase(request.getStatusCode().trim())
                    .filter(s -> !s.isDeleted())
                    .orElseThrow(() -> new ResourcesNotFoundException("Lead status not found with code: " + request.getStatusCode()));
        }
        throw new BadRequestException("newStatusId or statusCode is required to change lead status");
    }

    private Specification<Lead> filterByStatus(UUID statusId) {
        return (root, query, cb) -> cb.equal(root.get("currentStatus").get("id"), statusId);
    }

    private Specification<Lead> filterByStatusIds(List<UUID> statusIds) {
        return (root, query, cb) -> root.get("currentStatus").get("id").in(statusIds);
    }

    private Specification<Lead> filterByBoard(UUID boardId) {
        return (root, query, cb) -> cb.equal(root.get("board").get("id"), boardId);
    }

    private Specification<Lead> filterByBoardIds(List<UUID> boardIds) {
        return (root, query, cb) -> root.get("board").get("id").in(boardIds);
    }

    private Specification<Lead> filterByGrade(UUID gradeId) {
        return (root, query, cb) -> cb.equal(root.get("grade").get("id"), gradeId);
    }

    private Specification<Lead> filterByGradeIds(List<UUID> gradeIds) {
        return (root, query, cb) -> root.get("grade").get("id").in(gradeIds);
    }

    private Specification<Lead> searchLeads(String keyword) {
        return (root, query, cb) -> {
            String searchPattern = "%" + keyword.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("fullName")), searchPattern),
                    cb.like(cb.lower(root.get("email")), searchPattern),
                    cb.like(cb.lower(root.get("phoneNumber")), searchPattern),
                    cb.like(cb.lower(root.get("leadCode")), searchPattern),
                    cb.like(cb.lower(root.get("city")), searchPattern),
                    cb.like(cb.lower(root.get("state")), searchPattern),
                    cb.like(cb.lower(root.get("country")), searchPattern),
                    cb.like(cb.lower(root.get("courseInterested")), searchPattern)
            );
        };
    }
}
