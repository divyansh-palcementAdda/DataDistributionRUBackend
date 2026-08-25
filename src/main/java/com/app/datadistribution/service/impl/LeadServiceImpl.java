package com.app.datadistribution.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.lead.LeadAvailedResponse;
import com.app.datadistribution.dto.lead.LeadPageResponse;
import com.app.datadistribution.dto.lead.LeadRequest;
import com.app.datadistribution.dto.lead.LeadResponse;
import com.app.datadistribution.dto.lead.LeadSourceStatsResponse;
import com.app.datadistribution.dto.lead.LeadStatusChangeRequest;
import com.app.datadistribution.dto.lead.LeadStatusHistoryPageResponse;
import com.app.datadistribution.dto.lead.LeadStatusHistoryResponse;
import com.app.datadistribution.entity.Board;
import com.app.datadistribution.entity.Course;
import com.app.datadistribution.entity.Department;
import com.app.datadistribution.entity.Grade;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadAssignmentHistory;
import com.app.datadistribution.entity.LeadAvailed;
import com.app.datadistribution.entity.LeadFeedback;
import com.app.datadistribution.entity.LeadSource;
import com.app.datadistribution.entity.LeadStatus;
import com.app.datadistribution.entity.LeadStatusHistory;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.RoleType;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.mapper.LeadMapper;
import com.app.datadistribution.mapper.UserMapper;
import com.app.datadistribution.repository.BoardRepository;
import com.app.datadistribution.repository.CourseRepository;
import com.app.datadistribution.repository.DepartmentRepository;
import com.app.datadistribution.repository.GradeRepository;
import com.app.datadistribution.repository.LeadAssignmentHistoryRepository;
import com.app.datadistribution.repository.LeadAvailedRepository;
import com.app.datadistribution.repository.LeadFeedbackRepository;
import com.app.datadistribution.repository.LeadRepository;
import com.app.datadistribution.repository.LeadSourceRepository;
import com.app.datadistribution.repository.LeadStatusHistoryRepository;
import com.app.datadistribution.repository.LeadStatusRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.service.interfaces.ILeadDataScopeService;
import com.app.datadistribution.service.interfaces.ILeadService;
import com.app.datadistribution.service.interfaces.IUserDataScopeService;

import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
    private final LeadAvailedRepository leadAvailedRepository;
    private final LeadAssignmentHistoryRepository leadAssignmentHistoryRepository;
    private final CourseRepository courseRepository;
    private final IUserDataScopeService dataScopeService;
    private final ILeadDataScopeService leadDataScopeService;
    private final LeadMapper leadMapper;
    private final jakarta.persistence.EntityManager entityManager;

    private static final Set<String> ALLOWED_LEAD_SORT_FIELDS = Set.of(
            "id", "leadCode", "fullName", "phoneNumber", "email", "city", "state", "country",
            "currentStatus", "createdAt", "updatedAt", "lastContactedAt", "nextFollowUpDate"
    );

    @Override
    @Transactional
    public LeadResponse create(LeadRequest request) throws BadRequestException, UnauthorizedException {
        User currentUser = getCurrentUserEntity();
        UserDataScope dataScope = leadDataScopeService.getCurrentUserScope();
        
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

        if (dataScope.isSelfScope() && request.getAssignedToUserId() != null && !request.getAssignedToUserId().equals(currentUser.getId())) {
            throw new BadRequestException("Counselors can only assign leads to themselves or leave unassigned.");
        }
        if (dataScope.isDepartmentScope()) {
            if (department != null && dataScope.getDepartmentIds() != null && !dataScope.getDepartmentIds().contains(department.getId())) {
                throw new BadRequestException("HOD can only create leads within their assigned department(s).");
            }
            if (assignedTo != null && dataScope.getDepartmentUserIds() != null && !dataScope.getDepartmentUserIds().contains(assignedTo.getId())) {
                throw new BadRequestException("HOD can only assign leads to members of their assigned department(s).");
            }
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
        LeadStatusHistory savedHistory = leadStatusHistoryRepository.save(initialHistory);
        if (saved.getStatusHistories() != null) {
            saved.getStatusHistories().add(savedHistory);
        }

        return leadMapper.toDto(saved);
    }

    @Override
    @Transactional
    public LeadResponse update(UUID id, LeadRequest request) throws BadRequestException, UnauthorizedException {
        Lead lead = leadRepository.findById(id)
                .filter(l -> !l.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Lead not found with id: " + id));

        UserDataScope dataScope = leadDataScopeService.getCurrentUserScope();
        leadDataScopeService.validateLeadWriteAccess(lead, dataScope);

        if (dataScope.isSelfScope() && request.getAssignedToUserId() != null && !request.getAssignedToUserId().equals(dataScope.getUserId())) {
            throw new BadRequestException("Counselors can only assign leads to themselves or leave unassigned.");
        }
        if (dataScope.isDepartmentScope()) {
            if (request.getDepartmentId() != null && dataScope.getDepartmentIds() != null && !dataScope.getDepartmentIds().contains(request.getDepartmentId())) {
                throw new BadRequestException("HOD cannot assign lead to department outside their scope.");
            }
            if (request.getAssignedToUserId() != null && dataScope.getDepartmentUserIds() != null && !dataScope.getDepartmentUserIds().contains(request.getAssignedToUserId())) {
                throw new BadRequestException("HOD can only assign leads to members of their assigned department(s).");
            }
        }

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
            if (!newStatus.isActive()) {
                throw new BadRequestException("Cannot assign inactive lead status: " + newStatus.getName());
            }
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

        Lead updated;
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

            updated = changeLeadStatusInternal(lead, newStatus, currentUser, remark);
        } else {
            updated = leadRepository.save(lead);
        }
        log.info("Updated lead: {}", updated.getLeadCode());

        return leadMapper.toDto(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public LeadResponse getById(UUID id) throws UnauthorizedException, BadRequestException {
        Lead lead = leadRepository.findById(id)
                .filter(l -> !l.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Lead not found with id: " + id));
        UserDataScope dataScope = leadDataScopeService.getCurrentUserScope();
        leadDataScopeService.validateLeadReadAccess(lead, dataScope);

        LeadResponse dto = leadMapper.toDto(lead);
        if (lead.getAssignedTo() != null) {
            leadAvailedRepository.findByLeadIdAndAvailedByUserIdAndIsDeletedFalse(lead.getId(), lead.getAssignedTo().getId())
                    .ifPresent(la -> {
                        UserMapper userMapper = org.mapstruct.factory.Mappers.getMapper(UserMapper.class);
                        dto.setAvailed(true);
                        dto.setAvailedAt(la.getAvailedAt());
                        dto.setAvailedBy(userMapper.toDto(la.getAvailedByUser()));
                    });
        }
        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public LeadPageResponse getAllLeads(PageRequestDTO pageRequest, List<UUID> leadSourceIds, UUID courseId, List<UUID> interestedCourseIds, UUID registeredCourseId, UUID courseTypeId, Boolean withoutCourse, UUID statusId, List<UUID> statusIds, UUID boardId, List<UUID> boardIds, UUID gradeId, List<UUID> gradeIds) throws UnauthorizedException, BadRequestException {
        return getAllLeads(pageRequest, leadSourceIds, courseId, interestedCourseIds, registeredCourseId, courseTypeId, null, withoutCourse, statusId, statusIds, boardId, boardIds, gradeId, gradeIds, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public LeadPageResponse getAllLeads(PageRequestDTO pageRequest, List<UUID> leadSourceIds, UUID courseId, List<UUID> interestedCourseIds, UUID registeredCourseId, UUID courseTypeId, Boolean withoutCourse, UUID statusId, List<UUID> statusIds, UUID boardId, List<UUID> boardIds, UUID gradeId, List<UUID> gradeIds, Boolean availed) throws UnauthorizedException, BadRequestException {
        return getAllLeads(pageRequest, leadSourceIds, courseId, interestedCourseIds, registeredCourseId, courseTypeId, null, withoutCourse, statusId, statusIds, boardId, boardIds, gradeId, gradeIds, null, null, null, availed, null, null, null, null, null, null, null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public LeadPageResponse getAllLeads(
            PageRequestDTO pageRequest,
            List<UUID> leadSourceIds,
            UUID courseId,
            List<UUID> interestedCourseIds,
            UUID registeredCourseId,
            UUID courseTypeId,
            List<UUID> courseTypeIds,
            Boolean withoutCourse,
            UUID statusId,
            List<UUID> statusIds,
            UUID boardId,
            List<UUID> boardIds,
            UUID gradeId,
            List<UUID> gradeIds,
            List<UUID> departmentIds,
            List<UUID> assignedUserIds,
            Boolean allotted,
            Boolean availed,
            UUID availedByUserId,
            List<UUID> availedByUserIds,
            LocalDate availedFrom,
            LocalDate availedTo,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate updatedFrom,
            LocalDate updatedTo
    ) throws UnauthorizedException, BadRequestException {
        UserDataScope dataScope = leadDataScopeService.getCurrentUserScope();

        String sortBy = pageRequest.getSortBy();
        if (sortBy == null || !ALLOWED_LEAD_SORT_FIELDS.contains(sortBy)) {
            sortBy = "createdAt";
        }
        Sort.Direction direction = Sort.Direction.fromString(
                pageRequest.getSortDirection() != null ? pageRequest.getSortDirection() : "ASC"
        );
        Pageable pageable = PageRequest.of(pageRequest.getPage(), pageRequest.getSize(), Sort.by(direction, sortBy));

        Specification<Lead> spec = leadDataScopeService.getLeadScopeSpecification(dataScope);

        if (leadSourceIds != null && !leadSourceIds.isEmpty()) {
            spec = andSpec(spec, filterBySources(leadSourceIds));
        }
        if (courseId != null) {
            spec = andSpec(spec, filterByCourse(courseId));
        }
        if (interestedCourseIds != null && !interestedCourseIds.isEmpty()) {
            spec = andSpec(spec, filterByInterestedCourses(interestedCourseIds));
        }
        if (registeredCourseId != null) {
            spec = andSpec(spec, filterByCourse(registeredCourseId));
        }
        if (courseTypeId != null) {
            spec = andSpec(spec, filterByCourseType(courseTypeId));
        } else if (courseTypeIds != null && !courseTypeIds.isEmpty()) {
            spec = andSpec(spec, filterByCourseTypeIds(courseTypeIds));
        }
        if (Boolean.TRUE.equals(withoutCourse)) {
            spec = andSpec(spec, filterWithoutCourse());
        }
        if (statusId != null) {
            spec = andSpec(spec, filterByStatus(statusId));
        } else if (statusIds != null && !statusIds.isEmpty()) {
            spec = andSpec(spec, filterByStatusIds(statusIds));
        }
        if (boardId != null) {
            spec = andSpec(spec, filterByBoard(boardId));
        } else if (boardIds != null && !boardIds.isEmpty()) {
            spec = andSpec(spec, filterByBoardIds(boardIds));
        }
        if (gradeId != null) {
            spec = andSpec(spec, filterByGrade(gradeId));
        } else if (gradeIds != null && !gradeIds.isEmpty()) {
            spec = andSpec(spec, filterByGradeIds(gradeIds));
        }
        if (departmentIds != null && !departmentIds.isEmpty()) {
            spec = andSpec(spec, filterByDepartmentIds(departmentIds));
        }
        if (assignedUserIds != null && !assignedUserIds.isEmpty()) {
            spec = andSpec(spec, filterByAssignedUserIds(assignedUserIds));
        }
        if (allotted != null) {
            spec = andSpec(spec, filterByAllotted(allotted));
        }
        if (availed != null || availedByUserId != null || (availedByUserIds != null && !availedByUserIds.isEmpty()) || availedFrom != null || availedTo != null) {
            spec = andSpec(spec, filterByAvailedDetails(availed, availedByUserId, availedByUserIds, availedFrom, availedTo));
        }
        if (startDate != null || endDate != null) {
            spec = andSpec(spec, filterByCreatedDateRange(startDate, endDate));
        }
        if (updatedFrom != null || updatedTo != null) {
            spec = andSpec(spec, filterByUpdatedDateRange(updatedFrom, updatedTo));
        }
        if (pageRequest.getSearch() != null && !pageRequest.getSearch().isBlank()) {
            spec = andSpec(spec, searchLeads(pageRequest.getSearch()));
        }

        Page<Lead> page = leadRepository.findAll(spec, pageable);
        List<Lead> leads = page.getContent();

        // Batch resolve availed records for loaded leads to avoid N+1 queries
        List<UUID> leadIds = leads.stream().map(Lead::getId).collect(Collectors.toList());
        Map<UUID, Map<UUID, LeadAvailed>> availedByLeadAndUser = new HashMap<>();
        if (!leadIds.isEmpty()) {
            List<LeadAvailed> availedList = leadAvailedRepository.findByLeadIdInAndIsDeletedFalse(leadIds);
            for (LeadAvailed la : availedList) {
                if (la.getLead() != null && la.getAvailedByUser() != null) {
                    availedByLeadAndUser
                            .computeIfAbsent(la.getLead().getId(), k -> new HashMap<>())
                            .put(la.getAvailedByUser().getId(), la);
                }
            }
        }

        UserMapper userMapper = org.mapstruct.factory.Mappers.getMapper(UserMapper.class);
        List<LeadResponse> content = leads.stream()
                .map(lead -> {
                    LeadResponse leadDto = leadMapper.toDto(lead);
                    if (lead.getAssignedTo() != null) {
                        Map<UUID, LeadAvailed> userMap = availedByLeadAndUser.get(lead.getId());
                        if (userMap != null && userMap.containsKey(lead.getAssignedTo().getId())) {
                            LeadAvailed la = userMap.get(lead.getAssignedTo().getId());
                            leadDto.setAvailed(true);
                            leadDto.setAvailedAt(la.getAvailedAt());
                            leadDto.setAvailedBy(userMapper.toDto(la.getAvailedByUser()));
                        }
                    }
                    return leadDto;
                })
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
    public LeadResponse addInterestedCourses(UUID leadId, List<UUID> courseIds) throws UnauthorizedException, BadRequestException {
        Lead lead = leadRepository.findById(leadId)
                .filter(l -> !l.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Lead not found with id: " + leadId));

        UserDataScope dataScope = leadDataScopeService.getCurrentUserScope();
        leadDataScopeService.validateLeadWriteAccess(lead, dataScope);

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
    public LeadResponse removeInterestedCourse(UUID leadId, UUID courseId) throws UnauthorizedException, BadRequestException {
        Lead lead = leadRepository.findById(leadId)
                .filter(l -> !l.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Lead not found with id: " + leadId));

        UserDataScope dataScope = leadDataScopeService.getCurrentUserScope();
        leadDataScopeService.validateLeadWriteAccess(lead, dataScope);

        if (lead.getInterestedCourses() != null) {
            lead.getInterestedCourses().removeIf(c -> c.getId().equals(courseId));
        }

        Lead updated = leadRepository.save(lead);
        log.info("Removed interested course {} from lead {}", courseId, lead.getLeadCode());
        return leadMapper.toDto(updated);
    }

    @Override
    @Transactional
    public LeadResponse registerCourse(UUID leadId, UUID courseId) throws UnauthorizedException, BadRequestException {
        Lead lead = leadRepository.findById(leadId)
                .filter(l -> !l.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Lead not found with id: " + leadId));

        UserDataScope dataScope = leadDataScopeService.getCurrentUserScope();
        leadDataScopeService.validateLeadWriteAccess(lead, dataScope);

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
    public void deleteLead(UUID id) throws UnauthorizedException, BadRequestException {
        Lead lead = leadRepository.findById(id)
                .filter(l -> !l.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Lead not found with id: " + id));

        UserDataScope dataScope = leadDataScopeService.getCurrentUserScope();
        leadDataScopeService.validateLeadWriteAccess(lead, dataScope);

        lead.setDeleted(true);
        leadRepository.save(lead);
        log.info("Soft deleted lead: {}", lead.getLeadCode());
    }

    /**
     * Centralized status change method to be used by all status modification workflows.
     * Ensures:
     * 1. Old status is captured before mutation.
     * 2. New status is validated (not null, not deleted, active).
     * 3. No duplicate history is created if status didn't actually change.
     * 4. A new immutable LeadStatusHistory record is created with previousStatus and newStatus.
     * 5. The bidirectional relationship with Lead.statusHistories is synchronized.
     * 6. Both Lead and LeadStatusHistory are persisted within the same transaction.
     */
    @Transactional
    public Lead changeLeadStatusInternal(Lead lead, LeadStatus newStatus, User currentUser, String feedbackOrRemarks) throws BadRequestException {
        if (lead == null || lead.isDeleted()) {
            throw new BadRequestException("Lead must exist and not be deleted.");
        }
        if (newStatus == null || newStatus.isDeleted()) {
            throw new BadRequestException("New lead status is required and must exist.");
        }
        if (!newStatus.isActive()) {
            throw new BadRequestException("Cannot assign inactive lead status: " + newStatus.getName());
        }

        LeadStatus oldStatus = lead.getCurrentStatus();
        boolean statusChanged = (oldStatus == null || !oldStatus.getId().equals(newStatus.getId()));

        if (!statusChanged) {
            log.info("No status transition for lead {}. Current status is already {}.",
                    lead.getLeadCode(), oldStatus != null ? oldStatus.getName() : "null");
            return lead;
        }

        lead.setCurrentStatus(newStatus);
        Lead updatedLead = leadRepository.save(lead);

        LeadStatusHistory history = LeadStatusHistory.builder()
                .lead(updatedLead)
                .previousStatus(oldStatus)
                .newStatus(newStatus)
                .changedByUser(currentUser)
                .feedback(feedbackOrRemarks != null && !feedbackOrRemarks.isBlank() ? feedbackOrRemarks : "Lead status updated.")
                .build();

        LeadStatusHistory savedHistory = leadStatusHistoryRepository.save(history);

        if (updatedLead.getStatusHistories() != null) {
            updatedLead.getStatusHistories().add(savedHistory);
        }

        log.info("Recorded status change history for lead {}: {} -> {} (changed by: {})",
                updatedLead.getLeadCode(),
                oldStatus != null ? oldStatus.getName() : "null",
                newStatus.getName(),
                currentUser != null ? currentUser.getUsername() : "SYSTEM");

        return updatedLead;
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

        UserDataScope dataScope = leadDataScopeService.getCurrentUserScope();
        leadDataScopeService.validateLeadWriteAccess(lead, dataScope);

        LeadStatus newStatus = resolveNewStatus(request);
        User currentUser = getCurrentUserEntity();

        LeadStatus oldStatus = lead.getCurrentStatus();
        boolean statusChanged = (oldStatus == null || !oldStatus.getId().equals(newStatus.getId()));

        Lead updated;
        if (statusChanged) {
            updated = changeLeadStatusInternal(lead, newStatus, currentUser, request.getFeedback());
        } else {
            updated = lead;
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
    @Transactional
    public LeadAvailedResponse markLeadAsAvailed(UUID leadId) throws UnauthorizedException, BadRequestException {
        User currentUser = getCurrentUserEntity();

        Lead lead = leadRepository.findById(leadId)
                .filter(l -> !l.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Lead not found with id: " + leadId));

        if (lead.getAssignedTo() == null) {
            throw new BadRequestException("Lead must be assigned before it can be marked as availed.");
        }

        if (!lead.getAssignedTo().getId().equals(currentUser.getId())) {
            throw new UnauthorizedException("Only the currently assigned user can mark this lead as availed.");
        }

        // Check if already availed for this lead and current assigned user
        Optional<LeadAvailed> existing = leadAvailedRepository.findByLeadIdAndAvailedByUserIdAndIsDeletedFalse(lead.getId(), currentUser.getId());
        if (existing.isPresent()) {
            log.info("Lead {} is already marked as availed by user {}", lead.getLeadCode(), currentUser.getUsername());
            return leadMapper.toDto(existing.get());
        }

        // Find latest assignment history for this lead if available
        LeadAssignmentHistory currentAssignment = null;
        List<LeadAssignmentHistory> histories = leadAssignmentHistoryRepository.findByLeadIdOrderByCreatedAtDesc(lead.getId());
        if (histories != null && !histories.isEmpty()) {
            currentAssignment = histories.get(0);
        }

        LeadAvailed leadAvailed = LeadAvailed.builder()
                .lead(lead)
                .availedByUser(currentUser)
                .assignmentHistory(currentAssignment)
                .availedAt(LocalDateTime.now())
                .build();

        LeadAvailed saved = leadAvailedRepository.save(leadAvailed);

        if (lead.getAvailedRecords() != null) {
            lead.getAvailedRecords().add(saved);
        }

        log.info("Lead {} marked as availed by assigned user {} at {}",
                lead.getLeadCode(), currentUser.getUsername(), saved.getAvailedAt());

        return leadMapper.toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeadStatusHistoryResponse> getStatusHistoryByLeadId(UUID leadId) throws UnauthorizedException, BadRequestException {
        Lead lead = leadRepository.findById(leadId)
                .filter(l -> !l.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Lead not found with id: " + leadId));

        UserDataScope dataScope = leadDataScopeService.getCurrentUserScope();
        leadDataScopeService.validateLeadReadAccess(lead, dataScope);

        List<LeadStatusHistory> histories = leadStatusHistoryRepository.findByLeadIdOrderByCreatedAtDescIdDesc(lead.getId());
        return histories.stream()
                .map(leadMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public LeadStatusHistoryPageResponse getStatusHistoryByLeadId(UUID leadId, PageRequestDTO pageRequest) throws UnauthorizedException, BadRequestException {
        Lead lead = leadRepository.findById(leadId)
                .filter(l -> !l.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Lead not found with id: " + leadId));

        UserDataScope dataScope = leadDataScopeService.getCurrentUserScope();
        leadDataScopeService.validateLeadReadAccess(lead, dataScope);

        String sortBy = pageRequest.getSortBy();
        if (sortBy == null || sortBy.isBlank() || "changedAt".equalsIgnoreCase(sortBy)) {
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

    @Override
    @Transactional(readOnly = true)
    public List<LeadSourceStatsResponse> getSourceWiseStats() throws UnauthorizedException, BadRequestException {
        UserDataScope dataScope = leadDataScopeService.getCurrentUserScope();
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<Lead> root = query.from(Lead.class);
        jakarta.persistence.criteria.SetJoin<Lead, LeadSource> sourceJoin = root.joinSet("leadSources", jakarta.persistence.criteria.JoinType.INNER);

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(leadDataScopeService.buildScopePredicate(cb, root, dataScope));
        predicates.add(cb.equal(sourceJoin.get("isDeleted"), false));

        query.select(cb.tuple(sourceJoin.get("id").alias("id"), sourceJoin.get("name").alias("name"), cb.countDistinct(root.get("id")).alias("count")));
        query.where(predicates.toArray(new Predicate[0]));
        query.groupBy(sourceJoin.get("id"), sourceJoin.get("name"));

        List<Tuple> tuples = entityManager.createQuery(query).getResultList();
        List<LeadSourceStatsResponse> stats = new ArrayList<>();
        for (Tuple t : tuples) {
            UUID sourceId = t.get("id", UUID.class);
            String sourceName = t.get("name", String.class);
            Long count = t.get("count", Long.class);
            stats.add(LeadSourceStatsResponse.builder()
                    .sourceId(sourceId)
                    .sourceName(sourceName)
                    .count(count)
                    .build());
        }
        return stats;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> getStatusWiseStats() throws UnauthorizedException, BadRequestException {
        UserDataScope dataScope = leadDataScopeService.getCurrentUserScope();
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<Lead> root = query.from(Lead.class);
        jakarta.persistence.criteria.Join<Lead, LeadStatus> statusJoin = root.join("currentStatus", jakarta.persistence.criteria.JoinType.INNER);

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(leadDataScopeService.buildScopePredicate(cb, root, dataScope));
        predicates.add(cb.equal(statusJoin.get("isDeleted"), false));

        query.select(cb.tuple(statusJoin.get("code").alias("code"), cb.countDistinct(root.get("id")).alias("count")));
        query.where(predicates.toArray(new Predicate[0]));
        query.groupBy(statusJoin.get("code"));

        List<Tuple> tuples = entityManager.createQuery(query).getResultList();
        Map<String, Long> stats = new HashMap<>();
        for (Tuple t : tuples) {
            String code = t.get("code", String.class);
            Long count = t.get("count", Long.class);
            if (code != null) {
                stats.put(code, count);
            }
        }
        return stats;
    }

    // --- Helper Methods & Specifications ---

    private Specification<Lead> andSpec(Specification<Lead> current, Specification<Lead> next) {
        if (current == null) return next;
        if (next == null) return current;
        Specification<Lead> combined = current.and(next);
        return combined != null ? combined : current;
    }

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
                    .filter(s -> !s.isDeleted() && s.isActive())
                    .orElseThrow(() -> new ResourcesNotFoundException("Active Lead status not found with id: " + statusId));
        }
        return leadStatusRepository.findByCodeIgnoreCase("RAW")
                .filter(s -> !s.isDeleted() && s.isActive())
                .or(() -> leadStatusRepository.findByNameIgnoreCase("Raw").filter(s -> !s.isDeleted() && s.isActive()))
                .orElseGet(() -> {
                    List<LeadStatus> all = leadStatusRepository.findAll();
                    return all.stream().filter(s -> !s.isDeleted() && s.isActive()).findFirst()
                            .orElseThrow(() -> new ResourcesNotFoundException("No active Lead Status configured in database"));
                });
    }

    private LeadStatus resolveNewStatus(LeadStatusChangeRequest request) throws BadRequestException {
        if (request.getNewStatusId() != null) {
            LeadStatus status = leadStatusRepository.findById(request.getNewStatusId())
                    .filter(s -> !s.isDeleted())
                    .orElseThrow(() -> new ResourcesNotFoundException("Lead status not found with id: " + request.getNewStatusId()));
            if (!status.isActive()) {
                throw new BadRequestException("Cannot assign inactive lead status: " + status.getName());
            }
            return status;
        }
        if (request.getStatusCode() != null && !request.getStatusCode().isBlank()) {
            LeadStatus status = leadStatusRepository.findByCodeIgnoreCase(request.getStatusCode().trim())
                    .filter(s -> !s.isDeleted())
                    .orElseThrow(() -> new ResourcesNotFoundException("Lead status not found with code: " + request.getStatusCode()));
            if (!status.isActive()) {
                throw new BadRequestException("Cannot assign inactive lead status: " + status.getName());
            }
            return status;
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

    private Specification<Lead> filterByCourseTypeIds(List<UUID> courseTypeIds) {
        return (root, query, cb) -> {
            query.distinct(true);
            jakarta.persistence.criteria.Join<Object, Object> interestedJoin = root.join("interestedCourses", jakarta.persistence.criteria.JoinType.LEFT);
            jakarta.persistence.criteria.Join<Object, Object> registeredJoin = root.join("course", jakarta.persistence.criteria.JoinType.LEFT);
            return cb.or(
                    interestedJoin.join("courseType", jakarta.persistence.criteria.JoinType.LEFT).get("id").in(courseTypeIds),
                    registeredJoin.join("courseType", jakarta.persistence.criteria.JoinType.LEFT).get("id").in(courseTypeIds)
            );
        };
    }

    private Specification<Lead> filterByDepartmentIds(List<UUID> departmentIds) {
        return (root, query, cb) -> root.get("department").get("id").in(departmentIds);
    }

    private Specification<Lead> filterByAssignedUserIds(List<UUID> assignedUserIds) {
        return (root, query, cb) -> root.get("assignedTo").get("id").in(assignedUserIds);
    }

    private Specification<Lead> filterByAllotted(Boolean allotted) {
        return (root, query, cb) -> {
            if (Boolean.TRUE.equals(allotted)) {
                return cb.isNotNull(root.get("assignedTo"));
            } else if (Boolean.FALSE.equals(allotted)) {
                return cb.isNull(root.get("assignedTo"));
            }
            return cb.conjunction();
        };
    }

    private Specification<Lead> filterByCreatedDateRange(LocalDate startDate, LocalDate endDate) {
        return (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            if (startDate != null) {
                preds.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDate.atStartOfDay()));
            }
            if (endDate != null) {
                preds.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDate.atTime(LocalTime.MAX)));
            }
            return cb.and(preds.toArray(new Predicate[0]));
        };
    }

    private Specification<Lead> filterByUpdatedDateRange(LocalDate updatedFrom, LocalDate updatedTo) {
        return (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            if (updatedFrom != null) {
                preds.add(cb.greaterThanOrEqualTo(root.get("updatedAt"), updatedFrom.atStartOfDay()));
            }
            if (updatedTo != null) {
                preds.add(cb.lessThanOrEqualTo(root.get("updatedAt"), updatedTo.atTime(LocalTime.MAX)));
            }
            return cb.and(preds.toArray(new Predicate[0]));
        };
    }

    private Specification<Lead> filterByAvailedDetails(Boolean isAvailed, UUID availedByUserId, List<UUID> availedByUserIds, LocalDate availedFrom, LocalDate availedTo) {
        return (root, query, cb) -> {
            jakarta.persistence.criteria.Subquery<UUID> subquery = query.subquery(UUID.class);
            jakarta.persistence.criteria.Root<LeadAvailed> availedRoot = subquery.from(LeadAvailed.class);
            subquery.select(availedRoot.get("lead").get("id"));

            List<Predicate> subqueryPreds = new ArrayList<>();
            subqueryPreds.add(cb.equal(availedRoot.get("lead"), root));
            subqueryPreds.add(cb.equal(availedRoot.get("availedByUser"), root.get("assignedTo")));
            subqueryPreds.add(cb.equal(availedRoot.get("isDeleted"), false));

            if (availedByUserId != null) {
                subqueryPreds.add(cb.equal(availedRoot.get("availedByUser").get("id"), availedByUserId));
            }
            if (availedByUserIds != null && !availedByUserIds.isEmpty()) {
                subqueryPreds.add(availedRoot.get("availedByUser").get("id").in(availedByUserIds));
            }
            if (availedFrom != null) {
                subqueryPreds.add(cb.greaterThanOrEqualTo(availedRoot.get("availedAt"), availedFrom.atStartOfDay()));
            }
            if (availedTo != null) {
                subqueryPreds.add(cb.lessThanOrEqualTo(availedRoot.get("availedAt"), availedTo.atTime(LocalTime.MAX)));
            }
            subquery.where(subqueryPreds.toArray(new Predicate[0]));

            if (Boolean.FALSE.equals(isAvailed)) {
                return cb.not(cb.exists(subquery));
            } else {
                return cb.and(cb.isNotNull(root.get("assignedTo")), cb.exists(subquery));
            }
        };
    }

    private Specification<Lead> filterByAvailed(Boolean availed) {
        return (root, query, cb) -> {
            jakarta.persistence.criteria.Subquery<UUID> subquery = query.subquery(UUID.class);
            jakarta.persistence.criteria.Root<LeadAvailed> availedRoot = subquery.from(LeadAvailed.class);
            subquery.select(availedRoot.get("lead").get("id"));
            subquery.where(
                    cb.equal(availedRoot.get("lead"), root),
                    cb.equal(availedRoot.get("availedByUser"), root.get("assignedTo")),
                    cb.equal(availedRoot.get("isDeleted"), false)
            );

            if (Boolean.TRUE.equals(availed)) {
                return cb.and(
                        cb.isNotNull(root.get("assignedTo")),
                        cb.exists(subquery)
                );
            } else {
                return cb.not(cb.exists(subquery));
            }
        };
    }
}
