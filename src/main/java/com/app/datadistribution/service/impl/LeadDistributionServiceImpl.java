package com.app.datadistribution.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.datadistribution.dto.lead.LeadDistributionFilterRequest;
import com.app.datadistribution.dto.lead.LeadDistributionRequest;
import com.app.datadistribution.dto.lead.LeadDistributionResponse;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadAssignmentHistory;
import com.app.datadistribution.entity.LeadFollowUp;
import com.app.datadistribution.entity.LeadStatus;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.event.LeadAllocatedEvent;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.repository.LeadAssignmentHistoryRepository;
import com.app.datadistribution.repository.LeadFollowUpRepository;
import com.app.datadistribution.repository.LeadRepository;
import com.app.datadistribution.repository.LeadStatusRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.engine.LeadDistributionEngine;
import com.app.datadistribution.service.engine.LeadDistributionEngine.EnginePlanResult;
import com.app.datadistribution.service.interfaces.ILeadDistributionService;
import com.app.datadistribution.service.util.LeadDepartmentResolver;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeadDistributionServiceImpl implements ILeadDistributionService {

    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

    private final LeadRepository leadRepository;
    private final UserRepository userRepository;
    private final LeadFollowUpRepository leadFollowUpRepository;
    private final LeadAssignmentHistoryRepository leadAssignmentHistoryRepository;
    private final LeadStatusRepository leadStatusRepository;
    private final LeadDistributionEngine leadDistributionEngine;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    public LeadDistributionResponse previewDistribution(LeadDistributionRequest request)
            throws BadRequestException, UnauthorizedException {
        return processDistribution(request, true);
    }

    @Override
    @Transactional
    public LeadDistributionResponse distributeLeads(LeadDistributionRequest request)
            throws BadRequestException, UnauthorizedException {
        return processDistribution(request, false);
    }

    private LeadDistributionResponse processDistribution(LeadDistributionRequest request, boolean isPreview)
            throws BadRequestException, UnauthorizedException {
        validateRequest(request);

        User currentUser = getCurrentUserEntity();

        // 1. Resolve Candidate Leads
        List<Lead> candidateLeads = resolveCandidateLeads(request);
        log.info("Lead Distribution (preview={}): Found {} candidate leads for distribution", isPreview, candidateLeads.size());

        // 2. Resolve Target Users
        List<UUID> uniqueUserIds = request.getUserIds().stream().distinct().toList();
        List<User> targetUsers = userRepository.findAllById(uniqueUserIds).stream()
                .filter(u -> !u.isDeleted() && u.isActive())
                .toList();

        if (targetUsers.isEmpty()) {
            throw new BadRequestException("No active target users found for lead distribution.");
        }

        // 3. Resolve Dynamic RAW Status ID
        UUID rawStatusId = leadStatusRepository.findByCodeIgnoreCase("RAW")
                .map(LeadStatus::getId)
                .or(() -> leadStatusRepository.findByNameIgnoreCase("Raw").map(LeadStatus::getId))
                .orElse(null);

        // 4. Batch Query Target User Capacities (Asia/Kolkata)
        LocalDate today = LocalDate.now(IST_ZONE);
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime todayEnd = today.atTime(LocalTime.MAX);

        List<UUID> activeUserIds = targetUsers.stream().map(User::getId).toList();

        Map<UUID, Long> todayFollowUpsMap = new HashMap<>();
        List<Object[]> followUpResults = leadFollowUpRepository.countActiveTodayFollowUpsGroupedByUserIds(activeUserIds, todayStart, todayEnd);
        for (Object[] row : followUpResults) {
            if (row[0] != null && row[1] != null) {
                todayFollowUpsMap.put((UUID) row[0], ((Number) row[1]).longValue());
            }
        }

        Map<UUID, Long> rawLeadsMap = new HashMap<>();
        if (rawStatusId != null) {
            List<Object[]> rawResults = leadRepository.countCurrentRawLeadsGroupedByUserIds(activeUserIds, rawStatusId);
            for (Object[] row : rawResults) {
                if (row[0] != null && row[1] != null) {
                    rawLeadsMap.put((UUID) row[0], ((Number) row[1]).longValue());
                }
            }
        }

        // 5. Execute Allocation via Shared LeadDistributionEngine
        Integer effectiveMaxLimit = request.resolveEffectiveMaxLeads();
        EnginePlanResult planResult = leadDistributionEngine.planDistribution(
                candidateLeads,
                targetUsers,
                todayFollowUpsMap,
                rawLeadsMap,
                effectiveMaxLimit);

        // 6. If Actual Distribution (!isPreview), persist assignments & publish events
        if (!isPreview) {
            String batchId = UUID.randomUUID().toString();
            Map<UUID, User> userMap = targetUsers.stream().collect(Collectors.toMap(User::getId, Function.identity()));

            for (Map.Entry<UUID, List<Lead>> entry : planResult.getUserAssignedLeadsMap().entrySet()) {
                UUID targetUserId = entry.getKey();
                List<Lead> assignedLeads = entry.getValue();
                User targetUser = userMap.get(targetUserId);

                if (targetUser != null && !assignedLeads.isEmpty()) {
                    for (Lead lead : assignedLeads) {
                        User oldAssignedUser = lead.getAssignedTo();

                        lead.setAssignedTo(targetUser);
                        lead.setDepartment(LeadDepartmentResolver.resolveDepartmentForUser(targetUser, lead.getDepartment()));
                        leadRepository.save(lead);

                        // Save assignment history
                        LeadAssignmentHistory history = LeadAssignmentHistory.builder()
                                .lead(lead)
                                .oldAssignedUser(oldAssignedUser)
                                .newAssignedUser(targetUser)
                                .changedByUser(currentUser)
                                .remarks("Fair round-robin lead distribution (Batch: " + batchId + ")")
                                .build();
                        leadAssignmentHistoryRepository.save(history);

                        // Reassign active pending/upcoming follow-ups to the new user
                        List<LeadFollowUp> activeFollowUps = leadFollowUpRepository.findActiveFollowUpsByLeadId(lead.getId());
                        for (LeadFollowUp f : activeFollowUps) {
                            f.setAssignedTo(targetUser);
                            leadFollowUpRepository.save(f);
                        }
                    }

                    // Publish allocation event for notifications/analytics
                    if (eventPublisher != null) {
                        String deptName = (targetUser.getDepartments() != null && !targetUser.getDepartments().isEmpty())
                                ? targetUser.getDepartments().iterator().next().getName()
                                : "General Department";

                        eventPublisher.publishEvent(LeadAllocatedEvent.builder()
                                .targetUserId(targetUser.getId())
                                .allocatedByUserId(currentUser != null ? currentUser.getId() : null)
                                .allocatedCount(assignedLeads.size())
                                .departmentName(deptName)
                                .allocationTime(LocalDateTime.now())
                                .batchId(batchId)
                                .build());
                    }
                }
            }
        }

        long totalSelectedCount = candidateLeads.size();
        long totalDistributable = effectiveMaxLimit != null ? Math.min(totalSelectedCount, effectiveMaxLimit) : totalSelectedCount;

        return LeadDistributionResponse.builder()
                .totalSelectedLeads(totalSelectedCount)
                .totalDistributableLeads(totalDistributable)
                .totalMatchingLeads(totalSelectedCount)
                .totalAvailableLeads(totalDistributable)
                .totalAssigned(planResult.getTotalAssigned())
                .totalUnassigned(planResult.getTotalUnassigned())
                .requestedMaximumNumber(effectiveMaxLimit)
                .requestedMaximumPerUser(request.getMaximumDataPerUser())
                .isPreviewOnly(isPreview)
                .users(planResult.getUserSummaries())
                .unassignedLeads(planResult.getUnassignedLeads())
                .build();
    }

    private List<Lead> resolveCandidateLeads(LeadDistributionRequest request) {
        if (request.getLeadIds() != null && !request.getLeadIds().isEmpty()) {
            List<UUID> requestedIds = request.getLeadIds();
            Map<UUID, Lead> leadMap = leadRepository.findAllById(requestedIds).stream()
                    .filter(l -> !l.isDeleted())
                    .collect(Collectors.toMap(Lead::getId, Function.identity()));

            List<Lead> orderedLeads = new ArrayList<>();
            for (UUID id : requestedIds) {
                Lead lead = leadMap.get(id);
                if (lead != null) {
                    orderedLeads.add(lead);
                }
            }
            return orderedLeads;
        }

        Specification<Lead> spec = buildAvailableLeadsSpecification(request.getFilters());
        return leadRepository.findAll(spec, Sort.by(Sort.Direction.ASC, "createdAt", "id"));
    }

    private void validateRequest(LeadDistributionRequest request) throws BadRequestException {
        if (request == null) {
            throw new BadRequestException("Distribution request payload cannot be null.");
        }
        if (request.getUserIds() == null || request.getUserIds().isEmpty()) {
            throw new BadRequestException("At least one user ID must be selected for lead distribution.");
        }
        if ((request.getLeadIds() == null || request.getLeadIds().isEmpty()) && request.getFilters() == null) {
            throw new BadRequestException("Either specific lead IDs or filter criteria must be provided.");
        }
    }

    private Specification<Lead> buildAvailableLeadsSpecification(LeadDistributionFilterRequest filters) {
        Specification<Lead> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("isDeleted"), false),
                cb.isNull(root.get("assignedTo"))
        );

        if (filters == null) {
            return spec;
        }

        if (filters.getCourseTypeIds() != null && !filters.getCourseTypeIds().isEmpty()) {
            spec = spec.and(filterByCourseTypeIds(filters.getCourseTypeIds()));
        } else if (filters.getCourseTypeId() != null) {
            spec = spec.and(filterByCourseTypeIds(List.of(filters.getCourseTypeId())));
        }

        if (filters.getCourseIds() != null && !filters.getCourseIds().isEmpty()) {
            spec = spec.and((root, query, cb) -> root.get("course").get("id").in(filters.getCourseIds()));
        } else if (filters.getCourseId() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("course").get("id"), filters.getCourseId()));
        }

        if (filters.getGradeIds() != null && !filters.getGradeIds().isEmpty()) {
            spec = spec.and((root, query, cb) -> root.get("grade").get("id").in(filters.getGradeIds()));
        } else if (filters.getGradeId() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("grade").get("id"), filters.getGradeId()));
        }

        if (filters.getBoardIds() != null && !filters.getBoardIds().isEmpty()) {
            spec = spec.and((root, query, cb) -> root.get("board").get("id").in(filters.getBoardIds()));
        } else if (filters.getBoardId() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("board").get("id"), filters.getBoardId()));
        }

        if (filters.getLeadSourceIds() != null && !filters.getLeadSourceIds().isEmpty()) {
            spec = spec.and((root, query, cb) -> {
                query.distinct(true);
                return root.join("leadSources", JoinType.INNER).get("id").in(filters.getLeadSourceIds());
            });
        } else if (filters.getLeadSourceId() != null) {
            spec = spec.and((root, query, cb) -> {
                query.distinct(true);
                return root.join("leadSources", JoinType.INNER).get("id").in(List.of(filters.getLeadSourceId()));
            });
        }

        if (filters.getLeadStatusIds() != null && !filters.getLeadStatusIds().isEmpty()) {
            spec = spec.and((root, query, cb) -> root.get("currentStatus").get("id").in(filters.getLeadStatusIds()));
        } else if (filters.getStatusId() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("currentStatus").get("id"), filters.getStatusId()));
        }

        if (filters.getDepartmentId() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("department").get("id"), filters.getDepartmentId()));
        }

        if (filters.getCreatedDateStart() != null) {
            LocalDateTime start = filters.getCreatedDateStart().atStartOfDay();
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), start));
        }

        if (filters.getCreatedDateEnd() != null) {
            LocalDateTime end = filters.getCreatedDateEnd().atTime(LocalTime.MAX);
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), end));
        }

        return spec;
    }

    private Specification<Lead> filterByCourseTypeIds(List<UUID> courseTypeIds) {
        return (root, query, cb) -> {
            query.distinct(true);
            Join<Object, Object> interestedJoin = root.join("interestedCourses", JoinType.LEFT);
            Join<Object, Object> registeredJoin = root.join("course", JoinType.LEFT);
            return cb.or(
                    interestedJoin.join("courseType", JoinType.LEFT).get("id").in(courseTypeIds),
                    registeredJoin.join("courseType", JoinType.LEFT).get("id").in(courseTypeIds)
            );
        };
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
}

