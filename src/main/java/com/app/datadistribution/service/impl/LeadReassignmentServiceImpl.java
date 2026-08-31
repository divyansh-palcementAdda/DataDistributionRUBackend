package com.app.datadistribution.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.lead.LeadResponse;
import com.app.datadistribution.dto.reassign.FollowUpReassignItemDTO;
import com.app.datadistribution.dto.reassign.FollowUpReassignRequest;
import com.app.datadistribution.dto.reassign.FollowUpReassignResponse;
import com.app.datadistribution.dto.reassign.FollowUpReassignableDTO;
import com.app.datadistribution.dto.reassign.LeadReassignItemDTO;
import com.app.datadistribution.dto.reassign.LeadReassignRequest;
import com.app.datadistribution.dto.reassign.LeadReassignResponse;
import com.app.datadistribution.dto.reassign.ReassignablePageResponseDTO;
import com.app.datadistribution.entity.FollowUpAssignmentHistory;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadAssignmentHistory;
import com.app.datadistribution.entity.LeadFollowUp;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.FollowUpStatus;
import com.app.datadistribution.enums.PermissionType;
import com.app.datadistribution.enums.RoleType;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.mapper.LeadMapper;
import com.app.datadistribution.repository.FollowUpAssignmentHistoryRepository;
import com.app.datadistribution.repository.LeadAssignmentHistoryRepository;
import com.app.datadistribution.repository.LeadFollowUpRepository;
import com.app.datadistribution.repository.LeadRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.service.dto.UserDataScope.ScopeType;
import com.app.datadistribution.service.interfaces.IActivityLogService;
import com.app.datadistribution.service.interfaces.ILeadReassignmentService;
import com.app.datadistribution.service.interfaces.IUserDataScopeService;
import com.app.datadistribution.service.util.LeadDepartmentResolver;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeadReassignmentServiceImpl implements ILeadReassignmentService {

    private final UserRepository userRepository;
    private final LeadRepository leadRepository;
    private final LeadFollowUpRepository leadFollowUpRepository;
    private final LeadAssignmentHistoryRepository leadAssignmentHistoryRepository;
    private final FollowUpAssignmentHistoryRepository followUpAssignmentHistoryRepository;
    private final LeadMapper leadMapper;
    private final IActivityLogService activityLogService;
    private final IUserDataScopeService dataScopeService;
    private final EntityManager entityManager;

    @Value("${app.lead-distribution.max-daily-followups:30}")
    private int maxDailyFollowUps;

    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

    private User getCurrentUser() throws UnauthorizedException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new UnauthorizedException("User is not authenticated");
        }
        String username = authentication.getName();
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isPresent()) {
            return userOpt.get();
        }
        return userRepository.findByEmail(username)
                .orElseThrow(() -> new UnauthorizedException("Authenticated user not found: " + username));
    }

    private boolean isUserInScope(User targetUser, User currentUser, UserDataScope scope) {
        if (scope.getScopeType() == ScopeType.SYSTEM) {
            return true;
        }
        if (scope.getScopeType() == ScopeType.SELF) {
            return targetUser.getId().equals(currentUser.getId());
        }
        if (scope.getScopeType() == ScopeType.DEPARTMENT) {
            if (scope.getDepartmentUserIds() != null && scope.getDepartmentUserIds().contains(targetUser.getId())) {
                return true;
            }
            if (currentUser.getDepartments() != null && targetUser.getDepartments() != null) {
                Set<UUID> userDeptIds = currentUser.getDepartments().stream().map(com.app.datadistribution.entity.Department::getId).collect(Collectors.toSet());
                return targetUser.getDepartments().stream().anyMatch(d -> userDeptIds.contains(d.getId()));
            }
        }
        return false;
    }

    @Override
    @Transactional(readOnly = true)
    public ReassignablePageResponseDTO<FollowUpReassignableDTO> getReassignableFollowUps(
            UUID responsibleUserId,
            LocalDate scheduledDate,
            LocalDate fromDate,
            LocalDate toDate,
            FollowUpStatus status,
            PageRequestDTO pageRequest) throws UnauthorizedException, BadRequestException, ResourcesNotFoundException {

        User currentUser = getCurrentUser();
        UserDataScope scope = dataScopeService.getScopeForCurrentUser();

        User sourceUser = userRepository.findById(responsibleUserId)
                .orElseThrow(() -> new ResourcesNotFoundException("Responsible user not found with ID: " + responsibleUserId));

        if (!isUserInScope(sourceUser, currentUser, scope)) {
            throw new UnauthorizedException("You do not have data-scope permission to view follow-ups for this user");
        }

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<LeadFollowUp> cq = cb.createQuery(LeadFollowUp.class);
        Root<LeadFollowUp> root = cq.from(LeadFollowUp.class);
        Join<LeadFollowUp, Lead> leadJoin = root.join("lead", JoinType.INNER);

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(root.get("isDeleted"), false));

        // Responsible user condition: (assignedTo.id = responsibleUserId OR (assignedTo IS NULL AND lead.assignedTo.id = responsibleUserId))
        Predicate assignedToPredicate = cb.equal(root.get("assignedTo").get("id"), responsibleUserId);
        Predicate leadAssignedToPredicate = cb.and(
                cb.isNull(root.get("assignedTo")),
                cb.equal(leadJoin.get("assignedTo").get("id"), responsibleUserId)
        );
        predicates.add(cb.or(assignedToPredicate, leadAssignedToPredicate));

        if (status != null) {
            predicates.add(cb.equal(root.get("status"), status));
        }

        if (scheduledDate != null) {
            LocalDateTime startOfDay = scheduledDate.atStartOfDay();
            LocalDateTime endOfDay = scheduledDate.atTime(23, 59, 59);
            predicates.add(cb.between(root.get("followUpDate"), startOfDay, endOfDay));
        } else {
            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("followUpDate"), fromDate.atStartOfDay()));
            }
            if (toDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("followUpDate"), toDate.atTime(23, 59, 59)));
            }
        }

        cq.where(predicates.toArray(new Predicate[0]));
        cq.orderBy(cb.asc(root.get("followUpDate")));

        TypedQuery<LeadFollowUp> query = entityManager.createQuery(cq);
        int page = pageRequest.getPage();
        int size = pageRequest.getSize();
        query.setFirstResult(page * size);
        query.setMaxResults(size);

        List<LeadFollowUp> followUps = query.getResultList();

        // Total count query
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<LeadFollowUp> countRoot = countQuery.from(LeadFollowUp.class);
        Join<LeadFollowUp, Lead> countLeadJoin = countRoot.join("lead", JoinType.INNER);
        List<Predicate> countPredicates = new ArrayList<>();
        countPredicates.add(cb.equal(countRoot.get("isDeleted"), false));
        Predicate countAssignedToPredicate = cb.equal(countRoot.get("assignedTo").get("id"), responsibleUserId);
        Predicate countLeadAssignedToPredicate = cb.and(
                cb.isNull(countRoot.get("assignedTo")),
                cb.equal(countLeadJoin.get("assignedTo").get("id"), responsibleUserId)
        );
        countPredicates.add(cb.or(countAssignedToPredicate, countLeadAssignedToPredicate));

        if (status != null) {
            countPredicates.add(cb.equal(countRoot.get("status"), status));
        }
        if (scheduledDate != null) {
            countPredicates.add(cb.between(countRoot.get("followUpDate"), scheduledDate.atStartOfDay(), scheduledDate.atTime(23, 59, 59)));
        } else {
            if (fromDate != null) {
                countPredicates.add(cb.greaterThanOrEqualTo(countRoot.get("followUpDate"), fromDate.atStartOfDay()));
            }
            if (toDate != null) {
                countPredicates.add(cb.lessThanOrEqualTo(countRoot.get("followUpDate"), toDate.atTime(23, 59, 59)));
            }
        }
        countQuery.select(cb.count(countRoot)).where(countPredicates.toArray(new Predicate[0]));
        long totalElements = entityManager.createQuery(countQuery).getSingleResult();

        List<FollowUpReassignableDTO> dtos = followUps.stream().map(f -> {
            User respUser = f.getAssignedTo() != null ? f.getAssignedTo() : f.getLead().getAssignedTo();
            User creatorUser = f.getCreatedByUser() != null ? f.getCreatedByUser() : (f.getCreatedBy() != null ? userRepository.findByUsername(f.getCreatedBy()).orElse(null) : null);

            return FollowUpReassignableDTO.builder()
                    .followUpId(f.getId())
                    .leadId(f.getLead().getId())
                    .studentName(f.getLead().getFullName())
                    .studentPhone(f.getLead().getPhoneNumber())
                    .studentEmail(f.getLead().getEmail())
                    .followUpDate(f.getFollowUpDate())
                    .status(f.getStatus())
                    .completed(f.isCompleted())
                    .remarks(f.getRemarks())
                    .currentResponsibleUserId(respUser != null ? respUser.getId() : null)
                    .currentResponsibleUserName(respUser != null ? (respUser.getFirstName() + " " + respUser.getLastName()).trim() : null)
                    .originalCreatorUserId(creatorUser != null ? creatorUser.getId() : null)
                    .originalCreatorUserName(creatorUser != null ? (creatorUser.getFirstName() + " " + creatorUser.getLastName()).trim() : null)
                    .build();
        }).collect(Collectors.toList());

        int totalPages = (int) Math.ceil((double) totalElements / size);

        return ReassignablePageResponseDTO.<FollowUpReassignableDTO>builder()
                .content(dtos)
                .pageNumber(page)
                .pageSize(size)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .last(page >= totalPages - 1)
                .build();
    }

    @Override
    @Transactional
    public FollowUpReassignResponse reassignFollowUps(FollowUpReassignRequest request)
            throws UnauthorizedException, BadRequestException, ResourcesNotFoundException {

        User currentUser = getCurrentUser();
        UserDataScope scope = dataScopeService.getScopeForCurrentUser();

        User sourceUser = userRepository.findById(request.getSourceUserId())
                .orElseThrow(() -> new ResourcesNotFoundException("Source user not found with ID: " + request.getSourceUserId()));

        if (!isUserInScope(sourceUser, currentUser, scope)) {
            throw new UnauthorizedException("You do not have permission to reassign follow-ups for source user: " + sourceUser.getUsername());
        }

        ZonedDateTime nowIST = ZonedDateTime.now(IST_ZONE);
        LocalDate targetDate = request.getScheduledDate() != null ? request.getScheduledDate() : nowIST.toLocalDate();
        LocalDateTime startOfDay = targetDate.atStartOfDay();
        LocalDateTime endOfDay = targetDate.atTime(23, 59, 59);

        Set<UUID> processedFollowUpIds = new HashSet<>();
        List<FollowUpReassignResponse.UserFollowUpDistributionSummary> summaries = new ArrayList<>();
        int totalReassignedCount = 0;

        for (FollowUpReassignItemDTO item : request.getAssignments()) {
            User targetUser = userRepository.findById(item.getTargetUserId())
                    .orElseThrow(() -> new ResourcesNotFoundException("Target user not found with ID: " + item.getTargetUserId()));

            if (!targetUser.isActive() || targetUser.isDeleted()) {
                throw new BadRequestException("Target user is inactive or deleted: " + targetUser.getUsername());
            }

            if (targetUser.getId().equals(sourceUser.getId())) {
                throw new BadRequestException("Target user cannot be identical to source user: " + sourceUser.getUsername());
            }

            if (!isUserInScope(targetUser, currentUser, scope)) {
                throw new UnauthorizedException("Target user is outside your department data-scope: " + targetUser.getUsername());
            }

            List<LeadFollowUp> followUpsToReassign = new ArrayList<>();

            if (item.getFollowUpIds() != null && !item.getFollowUpIds().isEmpty()) {
                for (UUID fId : item.getFollowUpIds()) {
                    if (processedFollowUpIds.contains(fId)) {
                        throw new BadRequestException("Duplicate follow-up ID in reassignment request: " + fId);
                    }
                    LeadFollowUp f = leadFollowUpRepository.findById(fId)
                            .orElseThrow(() -> new ResourcesNotFoundException("Follow-up not found with ID: " + fId));

                    User effectiveRespUser = f.getAssignedTo() != null ? f.getAssignedTo() : f.getLead().getAssignedTo();
                    if (effectiveRespUser == null || !effectiveRespUser.getId().equals(sourceUser.getId())) {
                        throw new BadRequestException("Follow-up " + fId + " does not belong to source user: " + sourceUser.getUsername());
                    }

                    followUpsToReassign.add(f);
                    processedFollowUpIds.add(fId);
                }
            } else if (item.getCount() != null && item.getCount() > 0) {
                // Fetch eligible unassigned follow-ups deterministically for count distribution
                CriteriaBuilder cb = entityManager.getCriteriaBuilder();
                CriteriaQuery<LeadFollowUp> cq = cb.createQuery(LeadFollowUp.class);
                Root<LeadFollowUp> root = cq.from(LeadFollowUp.class);
                Join<LeadFollowUp, Lead> leadJoin = root.join("lead", JoinType.INNER);

                List<Predicate> predicates = new ArrayList<>();
                predicates.add(cb.equal(root.get("isDeleted"), false));
                predicates.add(cb.equal(root.get("completed"), false));

                Predicate assignedToPredicate = cb.equal(root.get("assignedTo").get("id"), sourceUser.getId());
                Predicate leadAssignedToPredicate = cb.and(
                        cb.isNull(root.get("assignedTo")),
                        cb.equal(leadJoin.get("assignedTo").get("id"), sourceUser.getId())
                );
                predicates.add(cb.or(assignedToPredicate, leadAssignedToPredicate));

                if (request.getScheduledDate() != null) {
                    predicates.add(cb.between(root.get("followUpDate"), startOfDay, endOfDay));
                }
                if (!processedFollowUpIds.isEmpty()) {
                    predicates.add(cb.not(root.get("id").in(processedFollowUpIds)));
                }

                cq.where(predicates.toArray(new Predicate[0]));
                cq.orderBy(cb.asc(root.get("followUpDate")));

                List<LeadFollowUp> available = entityManager.createQuery(cq).setMaxResults(item.getCount()).getResultList();
                if (available.size() < item.getCount()) {
                    throw new BadRequestException("Not enough reassignable follow-ups available for source user "
                            + sourceUser.getUsername() + ". Requested: " + item.getCount() + ", Found: " + available.size());
                }

                for (LeadFollowUp f : available) {
                    followUpsToReassign.add(f);
                    processedFollowUpIds.add(f.getId());
                }
            } else {
                throw new BadRequestException("Each assignment item must specify either followUpIds or count");
            }

            // Workload validation for target user today
            long currentTargetWorkload = leadFollowUpRepository.countScheduledFollowUpsForUserBetween(targetUser.getId(), startOfDay, endOfDay);
            long newWorkload = currentTargetWorkload + followUpsToReassign.size();

            if (newWorkload > maxDailyFollowUps) {
                boolean hasOverridePermission = currentUser.getRoles() != null && currentUser.getRoles().stream()
                        .flatMap(r -> r.getPermissions() != null ? r.getPermissions().stream() : java.util.stream.Stream.empty())
                        .anyMatch(p -> PermissionType.FOLLOW_UP_REASSIGN_OVERRIDE_WORKLOAD.name().equals(p.getName())
                                || PermissionType.LEAD_REASSIGN.name().equals(p.getName()));

                boolean isAdmin = currentUser.getRoles() != null && currentUser.getRoles().stream()
                        .anyMatch(r -> RoleType.SUPER_ADMIN.name().equals(r.getName()) || RoleType.ADMIN.name().equals(r.getName()));

                if (!request.isAllowWorkloadOverride() || (!hasOverridePermission && !isAdmin)) {
                    throw new BadRequestException("Assigning " + followUpsToReassign.size() + " follow-ups to " + targetUser.getUsername()
                            + " exceeds maximum daily workload (" + maxDailyFollowUps + "). Current: " + currentTargetWorkload
                            + ". Require explicit workload override permission and flag.");
                }
            }

            // Apply reassignment
            String transferType = (item.getFollowUpIds() != null && !item.getFollowUpIds().isEmpty()) ? "MANUAL_TRANSFER" : "BULK_DISTRIBUTION";
            String reason = request.getReason() != null ? request.getReason() : "Reassigned by Admin";

            for (LeadFollowUp f : followUpsToReassign) {
                User oldRespUser = f.getAssignedTo() != null ? f.getAssignedTo() : f.getLead().getAssignedTo();
                f.setAssignedTo(targetUser);
                leadFollowUpRepository.save(f);

                FollowUpAssignmentHistory history = FollowUpAssignmentHistory.builder()
                        .followUp(f)
                        .lead(f.getLead())
                        .oldResponsibleUser(oldRespUser)
                        .newResponsibleUser(targetUser)
                        .changedByUser(currentUser)
                        .reason(reason)
                        .transferType(transferType)
                        .build();
                followUpAssignmentHistoryRepository.save(history);
            }

            summaries.add(FollowUpReassignResponse.UserFollowUpDistributionSummary.builder()
                    .targetUserId(targetUser.getId())
                    .targetUserName((targetUser.getFirstName() + " " + targetUser.getLastName()).trim())
                    .count(followUpsToReassign.size())
                    .build());

            totalReassignedCount += followUpsToReassign.size();
        }

        activityLogService.logActivity(
                com.app.datadistribution.enums.ActivityType.FOLLOWUP_REASSIGNED,
                "Reassigned " + totalReassignedCount + " follow-ups from user " + sourceUser.getUsername() + " to " + summaries.size() + " target users. Reason: " + request.getReason(),
                currentUser.getUsername()
        );

        return FollowUpReassignResponse.builder()
                .message("Follow-ups reassigned successfully")
                .sourceUserId(sourceUser.getId())
                .sourceUserName((sourceUser.getFirstName() + " " + sourceUser.getLastName()).trim())
                .totalRequested(processedFollowUpIds.size())
                .totalReassigned(totalReassignedCount)
                .assignments(summaries)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ReassignablePageResponseDTO<LeadResponse> getReassignableLeads(
            UUID assignedUserId,
            UUID courseTypeId,
            UUID gradeId,
            UUID boardId,
            UUID leadSourceId,
            UUID statusId,
            UUID departmentId,
            PageRequestDTO pageRequest) throws UnauthorizedException, BadRequestException, ResourcesNotFoundException {

        User currentUser = getCurrentUser();
        UserDataScope scope = dataScopeService.getScopeForCurrentUser();

        User sourceUser = userRepository.findById(assignedUserId)
                .orElseThrow(() -> new ResourcesNotFoundException("Assigned user not found with ID: " + assignedUserId));

        if (!isUserInScope(sourceUser, currentUser, scope)) {
            throw new UnauthorizedException("You do not have data-scope permission to view leads for this user");
        }

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Lead> cq = cb.createQuery(Lead.class);
        Root<Lead> root = cq.from(Lead.class);

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(root.get("isDeleted"), false));
        predicates.add(cb.equal(root.get("assignedTo").get("id"), assignedUserId));

        if (courseTypeId != null) predicates.add(cb.equal(root.get("courseType").get("id"), courseTypeId));
        if (gradeId != null) predicates.add(cb.equal(root.get("grade").get("id"), gradeId));
        if (boardId != null) predicates.add(cb.equal(root.get("board").get("id"), boardId));
        if (leadSourceId != null) predicates.add(cb.equal(root.get("leadSource").get("id"), leadSourceId));
        if (statusId != null) predicates.add(cb.equal(root.get("status").get("id"), statusId));
        if (departmentId != null) predicates.add(cb.equal(root.get("department").get("id"), departmentId));

        if (pageRequest.getSearch() != null && !pageRequest.getSearch().trim().isEmpty()) {
            String term = "%" + pageRequest.getSearch().trim().toLowerCase() + "%";
            predicates.add(cb.or(
                    cb.like(cb.lower(root.get("fullName")), term),
                    cb.like(cb.lower(root.get("phoneNumber")), term),
                    cb.like(cb.lower(root.get("email")), term)
            ));
        }

        cq.where(predicates.toArray(new Predicate[0]));
        cq.orderBy(cb.desc(root.get("createdAt")));

        TypedQuery<Lead> query = entityManager.createQuery(cq);
        int page = pageRequest.getPage();
        int size = pageRequest.getSize();
        query.setFirstResult(page * size);
        query.setMaxResults(size);

        List<Lead> leads = query.getResultList();

        // Count query
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<Lead> countRoot = countQuery.from(Lead.class);
        List<Predicate> countPreds = new ArrayList<>();
        countPreds.add(cb.equal(countRoot.get("isDeleted"), false));
        countPreds.add(cb.equal(countRoot.get("assignedTo").get("id"), assignedUserId));
        if (courseTypeId != null) countPreds.add(cb.equal(countRoot.get("courseType").get("id"), courseTypeId));
        if (gradeId != null) countPreds.add(cb.equal(countRoot.get("grade").get("id"), gradeId));
        if (boardId != null) countPreds.add(cb.equal(countRoot.get("board").get("id"), boardId));
        if (leadSourceId != null) countPreds.add(cb.equal(countRoot.get("leadSource").get("id"), leadSourceId));
        if (statusId != null) countPreds.add(cb.equal(countRoot.get("status").get("id"), statusId));
        if (departmentId != null) countPreds.add(cb.equal(countRoot.get("department").get("id"), departmentId));
        if (pageRequest.getSearch() != null && !pageRequest.getSearch().trim().isEmpty()) {
            String term = "%" + pageRequest.getSearch().trim().toLowerCase() + "%";
            countPreds.add(cb.or(
                    cb.like(cb.lower(countRoot.get("fullName")), term),
                    cb.like(cb.lower(countRoot.get("phoneNumber")), term),
                    cb.like(cb.lower(countRoot.get("email")), term)
            ));
        }
        countQuery.select(cb.count(countRoot)).where(countPreds.toArray(new Predicate[0]));
        long totalElements = entityManager.createQuery(countQuery).getSingleResult();

        List<LeadResponse> dtos = leads.stream().map(leadMapper::toDto).collect(Collectors.toList());
        int totalPages = (int) Math.ceil((double) totalElements / size);

        return ReassignablePageResponseDTO.<LeadResponse>builder()
                .content(dtos)
                .pageNumber(page)
                .pageSize(size)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .last(page >= totalPages - 1)
                .build();
    }

    @Override
    @Transactional
    public LeadReassignResponse reassignLeads(LeadReassignRequest request)
            throws UnauthorizedException, BadRequestException, ResourcesNotFoundException {

        User currentUser = getCurrentUser();
        UserDataScope scope = dataScopeService.getScopeForCurrentUser();

        User sourceUser = userRepository.findById(request.getSourceUserId())
                .orElseThrow(() -> new ResourcesNotFoundException("Source user not found with ID: " + request.getSourceUserId()));

        if (!isUserInScope(sourceUser, currentUser, scope)) {
            throw new UnauthorizedException("You do not have permission to reassign leads for source user: " + sourceUser.getUsername());
        }

        Set<UUID> processedLeadIds = new HashSet<>();
        List<LeadReassignResponse.UserLeadDistributionSummary> summaries = new ArrayList<>();
        int totalReassignedLeads = 0;
        int totalReassignedFollowUps = 0;

        ZonedDateTime nowIST = ZonedDateTime.now(IST_ZONE);
        LocalDateTime startOfDayIST = nowIST.toLocalDate().atStartOfDay();

        for (LeadReassignItemDTO item : request.getAssignments()) {
            User targetUser = userRepository.findById(item.getTargetUserId())
                    .orElseThrow(() -> new ResourcesNotFoundException("Target user not found with ID: " + item.getTargetUserId()));

            if (!targetUser.isActive() || targetUser.isDeleted()) {
                throw new BadRequestException("Target user is inactive or deleted: " + targetUser.getUsername());
            }

            if (targetUser.getId().equals(sourceUser.getId())) {
                throw new BadRequestException("Target user cannot be identical to source user: " + sourceUser.getUsername());
            }

            if (!isUserInScope(targetUser, currentUser, scope)) {
                throw new UnauthorizedException("Target user is outside your department data-scope: " + targetUser.getUsername());
            }

            List<Lead> leadsToReassign = new ArrayList<>();

            if (item.getLeadIds() != null && !item.getLeadIds().isEmpty()) {
                for (UUID lId : item.getLeadIds()) {
                    if (processedLeadIds.contains(lId)) {
                        throw new BadRequestException("Duplicate lead ID in reassignment request: " + lId);
                    }
                    Lead lead = leadRepository.findById(lId)
                            .orElseThrow(() -> new ResourcesNotFoundException("Lead not found with ID: " + lId));

                    if (lead.getAssignedTo() == null || !lead.getAssignedTo().getId().equals(sourceUser.getId())) {
                        throw new BadRequestException("Lead " + lId + " is not assigned to source user: " + sourceUser.getUsername());
                    }

                    leadsToReassign.add(lead);
                    processedLeadIds.add(lId);
                }
            } else if (item.getCount() != null && item.getCount() > 0) {
                CriteriaBuilder cb = entityManager.getCriteriaBuilder();
                CriteriaQuery<Lead> cq = cb.createQuery(Lead.class);
                Root<Lead> root = cq.from(Lead.class);

                List<Predicate> predicates = new ArrayList<>();
                predicates.add(cb.equal(root.get("isDeleted"), false));
                predicates.add(cb.equal(root.get("assignedTo").get("id"), sourceUser.getId()));
                if (!processedLeadIds.isEmpty()) {
                    predicates.add(cb.not(root.get("id").in(processedLeadIds)));
                }

                cq.where(predicates.toArray(new Predicate[0]));
                cq.orderBy(cb.desc(root.get("createdAt")));

                List<Lead> availableLeads = entityManager.createQuery(cq).setMaxResults(item.getCount()).getResultList();
                if (availableLeads.size() < item.getCount()) {
                    throw new BadRequestException("Not enough reassignable leads available for source user "
                            + sourceUser.getUsername() + ". Requested: " + item.getCount() + ", Found: " + availableLeads.size());
                }

                for (Lead l : availableLeads) {
                    leadsToReassign.add(l);
                    processedLeadIds.add(l.getId());
                }
            } else {
                throw new BadRequestException("Each assignment item must specify either leadIds or count");
            }

            String reason = request.getReason() != null ? request.getReason() : "Lead reassigned by Admin";
            int pendingFollowUpCountForUser = 0;

            for (Lead lead : leadsToReassign) {
                User oldAssignedUser = lead.getAssignedTo();
                lead.setAssignedTo(targetUser);
                lead.setDepartment(LeadDepartmentResolver.resolveDepartmentForUser(targetUser, lead.getDepartment()));
                leadRepository.save(lead);

                // Save Lead Assignment History
                LeadAssignmentHistory history = LeadAssignmentHistory.builder()
                        .lead(lead)
                        .oldAssignedUser(oldAssignedUser)
                        .newAssignedUser(targetUser)
                        .changedByUser(currentUser)
                        .remarks(reason)
                        .build();
                leadAssignmentHistoryRepository.save(history);

                // Reassign related pending uncompleted follow-ups if requested
                if (request.isReassignRelatedPendingFollowUps()) {
                    List<LeadFollowUp> pendingFollowUps = leadFollowUpRepository.findPendingUncompletedFollowUpsByLeadIds(
                            List.of(lead.getId()), startOfDayIST);

                    for (LeadFollowUp f : pendingFollowUps) {
                        User oldRespUser = f.getAssignedTo() != null ? f.getAssignedTo() : oldAssignedUser;
                        f.setAssignedTo(targetUser);
                        leadFollowUpRepository.save(f);

                        FollowUpAssignmentHistory fHistory = FollowUpAssignmentHistory.builder()
                                .followUp(f)
                                .lead(lead)
                                .oldResponsibleUser(oldRespUser)
                                .newResponsibleUser(targetUser)
                                .changedByUser(currentUser)
                                .reason("Cascade reassignment with Lead #" + lead.getId() + ". " + reason)
                                .transferType("LEAD_CASCADE")
                                .build();
                        followUpAssignmentHistoryRepository.save(fHistory);
                        pendingFollowUpCountForUser++;
                    }
                }
            }

            summaries.add(LeadReassignResponse.UserLeadDistributionSummary.builder()
                    .targetUserId(targetUser.getId())
                    .targetUserName((targetUser.getFirstName() + " " + targetUser.getLastName()).trim())
                    .count(leadsToReassign.size())
                    .pendingFollowUpsCount(pendingFollowUpCountForUser)
                    .build());

            totalReassignedLeads += leadsToReassign.size();
            totalReassignedFollowUps += pendingFollowUpCountForUser;
        }

        activityLogService.logActivity(
                com.app.datadistribution.enums.ActivityType.LEAD_REASSIGNED,
                "Reassigned " + totalReassignedLeads + " leads (and " + totalReassignedFollowUps + " pending follow-ups) from user "
                        + sourceUser.getUsername() + " to " + summaries.size() + " target users. Reason: " + request.getReason(),
                currentUser.getUsername()
        );

        return LeadReassignResponse.builder()
                .message("Leads reassigned successfully")
                .sourceUserId(sourceUser.getId())
                .sourceUserName((sourceUser.getFirstName() + " " + sourceUser.getLastName()).trim())
                .totalRequested(processedLeadIds.size())
                .totalReassigned(totalReassignedLeads)
                .totalPendingFollowUpsReassigned(totalReassignedFollowUps)
                .assignments(summaries)
                .build();
    }
}
