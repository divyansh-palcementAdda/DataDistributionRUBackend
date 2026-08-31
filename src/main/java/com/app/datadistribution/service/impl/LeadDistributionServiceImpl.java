package com.app.datadistribution.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.datadistribution.dto.lead.LeadDistributionFilterRequest;
import com.app.datadistribution.dto.lead.LeadDistributionRequest;
import com.app.datadistribution.dto.lead.LeadDistributionResponse;
import com.app.datadistribution.dto.lead.UserDistributionSummaryDTO;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadAssignmentHistory;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.repository.LeadAssignmentHistoryRepository;
import com.app.datadistribution.repository.LeadFollowUpRepository;
import com.app.datadistribution.repository.LeadRepository;
import com.app.datadistribution.repository.UserRepository;
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

    private final LeadRepository leadRepository;
    private final UserRepository userRepository;
    private final LeadFollowUpRepository leadFollowUpRepository;
    private final LeadAssignmentHistoryRepository leadAssignmentHistoryRepository;

    @Value("${app.lead-distribution.max-daily-followups:30}")
    private int maxDailyFollowups;

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
        Specification<Lead> availableSpec = buildAvailableLeadsSpecification(request.getFilters());
        List<Lead> availableLeads = leadRepository.findAll(availableSpec, Sort.by(Sort.Direction.ASC, "createdAt", "id"));

        long totalAvailableCount = availableLeads.size();
        log.info("Lead Distribution (preview={}): Found {} available leads matching filters", isPreview, totalAvailableCount);

        List<UUID> uniqueUserIds = request.getUserIds().stream().distinct().collect(Collectors.toList());
        List<UserDistributionSummaryDTO> userSummaries = new ArrayList<>();

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = LocalDate.now().atTime(LocalTime.MAX);

        int remainingLeadPoolSize = (int) totalAvailableCount;
        int totalAssignedInBatch = 0;
        int leadPointer = 0;

        for (UUID userId : uniqueUserIds) {
            Optional<User> userOpt = userRepository.findById(userId).filter(u -> !u.isDeleted());
            if (userOpt.isEmpty()) {
                userSummaries.add(UserDistributionSummaryDTO.builder()
                        .userId(userId)
                        .userName("Unknown User")
                        .todayFollowUpCount(0)
                        .currentUnavailedLeadCount(0)
                        .remainingCapacity(0)
                        .assignedCount(0)
                        .status("SKIPPED")
                        .reason("USER_NOT_FOUND")
                        .build());
                continue;
            }

            User user = userOpt.get();
            if (!user.isActive()) {
                userSummaries.add(UserDistributionSummaryDTO.builder()
                        .userId(user.getId())
                        .userName(user.getFirstName() + " " + user.getLastName())
                        .userEmail(user.getEmail())
                        .todayFollowUpCount(0)
                        .currentUnavailedLeadCount(0)
                        .remainingCapacity(0)
                        .assignedCount(0)
                        .status("SKIPPED")
                        .reason("USER_INACTIVE")
                        .build());
                continue;
            }

            long todayFollowUps = leadFollowUpRepository.countScheduledFollowUpsForUserBetween(user.getId(), todayStart, todayEnd);
            long currentUnavailedLeads = leadRepository.countUnavailedLeadsByUserId(user.getId());

            if (todayFollowUps >= maxDailyFollowups) {
                userSummaries.add(UserDistributionSummaryDTO.builder()
                        .userId(user.getId())
                        .userName(user.getFirstName() + " " + user.getLastName())
                        .userEmail(user.getEmail())
                        .todayFollowUpCount(todayFollowUps)
                        .currentUnavailedLeadCount(currentUnavailedLeads)
                        .remainingCapacity(0)
                        .assignedCount(0)
                        .status("SKIPPED")
                        .reason("DAILY_FOLLOWUP_LIMIT_REACHED")
                        .build());
                continue;
            }

            if (currentUnavailedLeads >= request.getMaximumDataPerUser()) {
                userSummaries.add(UserDistributionSummaryDTO.builder()
                        .userId(user.getId())
                        .userName(user.getFirstName() + " " + user.getLastName())
                        .userEmail(user.getEmail())
                        .todayFollowUpCount(todayFollowUps)
                        .currentUnavailedLeadCount(currentUnavailedLeads)
                        .remainingCapacity(0)
                        .assignedCount(0)
                        .status("SKIPPED")
                        .reason("MAX_CAPACITY_REACHED")
                        .build());
                continue;
            }

            int remainingCapacity = (int) (request.getMaximumDataPerUser() - currentUnavailedLeads);
            int assignCountForUser = Math.min(remainingCapacity, remainingLeadPoolSize);

            if (assignCountForUser <= 0) {
                userSummaries.add(UserDistributionSummaryDTO.builder()
                        .userId(user.getId())
                        .userName(user.getFirstName() + " " + user.getLastName())
                        .userEmail(user.getEmail())
                        .todayFollowUpCount(todayFollowUps)
                        .currentUnavailedLeadCount(currentUnavailedLeads)
                        .remainingCapacity(remainingCapacity)
                        .assignedCount(0)
                        .status("SKIPPED")
                        .reason("NO_AVAILABLE_LEADS")
                        .build());
                continue;
            }

            if (!isPreview) {
                for (int i = 0; i < assignCountForUser; i++) {
                    Lead leadToAssign = availableLeads.get(leadPointer + i);
                    User oldUser = leadToAssign.getAssignedTo();

                    leadToAssign.setAssignedTo(user);
                    leadToAssign.setDepartment(LeadDepartmentResolver.resolveDepartmentForUser(user, leadToAssign.getDepartment()));
                    leadRepository.save(leadToAssign);

                    LeadAssignmentHistory history = LeadAssignmentHistory.builder()
                            .lead(leadToAssign)
                            .oldAssignedUser(oldUser)
                            .newAssignedUser(user)
                            .changedByUser(currentUser)
                            .remarks("Manual rule-based batch lead distribution")
                            .build();
                    leadAssignmentHistoryRepository.save(history);
                }
            }

            leadPointer += assignCountForUser;
            remainingLeadPoolSize -= assignCountForUser;
            totalAssignedInBatch += assignCountForUser;

            userSummaries.add(UserDistributionSummaryDTO.builder()
                    .userId(user.getId())
                    .userName(user.getFirstName() + " " + user.getLastName())
                    .userEmail(user.getEmail())
                    .todayFollowUpCount(todayFollowUps)
                    .currentUnavailedLeadCount(currentUnavailedLeads)
                    .remainingCapacity(remainingCapacity)
                    .assignedCount(assignCountForUser)
                    .status("SUCCESS")
                    .build());
        }

        return LeadDistributionResponse.builder()
                .totalMatchingLeads(totalAvailableCount)
                .totalAvailableLeads(totalAvailableCount)
                .totalAssigned(totalAssignedInBatch)
                .requestedMaximumPerUser(request.getMaximumDataPerUser())
                .isPreviewOnly(isPreview)
                .users(userSummaries)
                .build();
    }

    private void validateRequest(LeadDistributionRequest request) throws BadRequestException {
        if (request == null) {
            throw new BadRequestException("Distribution request payload cannot be null.");
        }
        if (request.getUserIds() == null || request.getUserIds().isEmpty()) {
            throw new BadRequestException("At least one user ID must be selected for lead distribution.");
        }
        if (request.getMaximumDataPerUser() <= 0) {
            throw new BadRequestException("Maximum data per user must be greater than zero.");
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
