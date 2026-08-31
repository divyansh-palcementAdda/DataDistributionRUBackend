package com.app.datadistribution.service.impl;

import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.followup.FollowUpPagedResponseDTO;
import com.app.datadistribution.dto.followup.FollowUpResponseDTO;
import com.app.datadistribution.dto.followup.FollowUpSummaryDTO;
import com.app.datadistribution.entity.LeadFollowUp;
import com.app.datadistribution.enums.FollowUpStatus;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.mapper.LeadMapper;
import com.app.datadistribution.repository.LeadFollowUpRepository;
import com.app.datadistribution.repository.LeadStatusRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.repository.specification.FollowUpSpecification;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.service.dto.UserDataScope.ScopeType;
import com.app.datadistribution.service.interfaces.FollowUpService;
import com.app.datadistribution.service.interfaces.IUserDataScopeService;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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
public class FollowUpServiceImpl implements FollowUpService {

    private final LeadFollowUpRepository leadFollowUpRepository;
    private final LeadStatusRepository leadStatusRepository;
    private final UserRepository userRepository;
    private final IUserDataScopeService dataScopeService;
    private final LeadMapper leadMapper;
    private final jakarta.persistence.EntityManager entityManager;

    private static final Set<String> ALLOWED_SORT_FIELDS = getAllowedSortFields();

    private static Set<String> getAllowedSortFields() {
        Set<String> allowedFields = new HashSet<>();
        Class<?> current = LeadFollowUp.class;
        while (current != null && current != Object.class) {
            for (java.lang.reflect.Field field : current.getDeclaredFields()) {
                allowedFields.add(field.getName());
            }
            current = current.getSuperclass();
        }
        allowedFields.remove("lead");
        allowedFields.remove("createdByUser");
        return allowedFields;
    }

    private Pageable createSafePageable(PageRequestDTO pageRequest) {
        String sortBy = pageRequest.getSortBy();
        Sort.Direction direction;
        try {
            direction = Sort.Direction.fromString(pageRequest.getSortDirection());
        } catch (Exception e) {
            direction = Sort.Direction.ASC;
        }

        if ("date".equalsIgnoreCase(sortBy)) {
            sortBy = "followUpDate";
        }

        if (sortBy == null || !ALLOWED_SORT_FIELDS.contains(sortBy)) {
            sortBy = "createdAt";
            direction = Sort.Direction.DESC;
        }

        return PageRequest.of(pageRequest.getPage(), pageRequest.getSize(), Sort.by(direction, sortBy));
    }

    @Override
    @Transactional(readOnly = true)
    public FollowUpPagedResponseDTO getAllFollowUps(PageRequestDTO pageRequest, LocalDate date, FollowUpStatus status, UUID userId, UUID leadId, UUID leadStatusId, List<UUID> leadStatusIds) throws UnauthorizedException, BadRequestException {
        UserDataScope dataScope = dataScopeService.getScopeForCurrentUser();
        Pageable pageable = createSafePageable(pageRequest);

        Specification<LeadFollowUp> spec = Specification.where(FollowUpSpecification.isNotDeleted())
                .and(FollowUpSpecification.leadIsNotDeleted());

        if (dataScope.isAdmin() || dataScope.isSystemScope()) {
            if (userId != null) {
                spec = spec.and((root, query, cb) -> cb.or(
                        cb.equal(root.get("createdByUser").get("id"), userId),
                        cb.equal(root.get("assignedTo").get("id"), userId),
                        cb.equal(root.get("lead").get("assignedTo").get("id"), userId)
                ));
            }
        } else if (dataScope.isHod() || dataScope.isDepartmentScope()) {
            spec = spec.and((root, query, cb) -> {
                jakarta.persistence.criteria.Predicate ownFollowUp = cb.or(
                        cb.equal(root.get("createdByUser").get("id"), dataScope.getUserId()),
                        cb.equal(root.get("assignedTo").get("id"), dataScope.getUserId()),
                        cb.equal(root.get("lead").get("assignedTo").get("id"), dataScope.getUserId())
                );
                if (dataScope.getDepartmentIds() != null && !dataScope.getDepartmentIds().isEmpty()) {
                    jakarta.persistence.criteria.Predicate deptLead = root.get("lead").get("department").get("id").in(dataScope.getDepartmentIds());
                    jakarta.persistence.criteria.Predicate deptUser = (dataScope.getDepartmentUserIds() != null && !dataScope.getDepartmentUserIds().isEmpty())
                            ? cb.or(
                                    root.get("lead").get("assignedTo").get("id").in(dataScope.getDepartmentUserIds()),
                                    root.get("createdByUser").get("id").in(dataScope.getDepartmentUserIds()),
                                    root.get("assignedTo").get("id").in(dataScope.getDepartmentUserIds())
                              )
                            : cb.disjunction();
                    return cb.or(ownFollowUp, deptLead, deptUser);
                }
                return ownFollowUp;
            });

            if (userId != null) {
                if (dataScope.getUserId().equals(userId) || (dataScope.getDepartmentUserIds() != null && dataScope.getDepartmentUserIds().contains(userId))) {
                    spec = spec.and((root, query, cb) -> cb.or(
                            cb.equal(root.get("createdByUser").get("id"), userId),
                            cb.equal(root.get("assignedTo").get("id"), userId),
                            cb.equal(root.get("lead").get("assignedTo").get("id"), userId)
                    ));
                } else {
                    spec = spec.and((root, query, cb) -> cb.disjunction());
                }
            }
        } else {
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.equal(root.get("createdByUser").get("id"), dataScope.getUserId()),
                    cb.equal(root.get("assignedTo").get("id"), dataScope.getUserId()),
                    cb.equal(root.get("lead").get("assignedTo").get("id"), dataScope.getUserId())
            ));

            if (userId != null && !userId.equals(dataScope.getUserId())) {
                spec = spec.and((root, query, cb) -> cb.disjunction());
            }
        }

        List<UUID> statusIdsToFilter = leadStatusIds;
        if ((statusIdsToFilter == null || statusIdsToFilter.isEmpty()) && leadStatusId != null) {
            statusIdsToFilter = List.of(leadStatusId);
        }

        if (statusIdsToFilter != null && !statusIdsToFilter.isEmpty()) {
            for (UUID id : statusIdsToFilter) {
                if (id != null) {
                    leadStatusRepository.findById(id)
                            .filter(s -> !s.isDeleted())
                            .orElseThrow(() -> new ResourcesNotFoundException("Lead status not found with id: " + id));
                }
            }
            if (statusIdsToFilter.size() == 1) {
                spec = spec.and(FollowUpSpecification.hasLeadStatusId(statusIdsToFilter.get(0)));
            } else {
                spec = spec.and(FollowUpSpecification.hasLeadStatusIds(statusIdsToFilter));
            }
        }

        if (date != null) {
            spec = spec.and(FollowUpSpecification.hasFollowUpDateOn(date));
        }
        if (status != null) {
            spec = spec.and(FollowUpSpecification.hasStatus(status));
        }
        if (leadId != null) {
            spec = spec.and(FollowUpSpecification.hasLead(leadId));
        }
        if (pageRequest.getSearch() != null && !pageRequest.getSearch().isBlank()) {
            spec = spec.and(FollowUpSpecification.search(pageRequest.getSearch()));
        }

        Page<LeadFollowUp> page = leadFollowUpRepository.findAll(spec, pageable);
        List<FollowUpResponseDTO> content = page.getContent().stream()
                .map(leadMapper::toFollowUpResponseDto)
                .collect(Collectors.toList());

        return FollowUpPagedResponseDTO.builder()
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
    public FollowUpPagedResponseDTO getFollowUpsByUserId(UUID userId, PageRequestDTO pageRequest) throws UnauthorizedException, BadRequestException {
        UserDataScope dataScope = dataScopeService.getScopeForCurrentUser();
        if (!dataScope.isAdmin() && !dataScope.isSystemScope()
                && !dataScope.getUserId().equals(userId)
                && (dataScope.getDepartmentUserIds() == null || !dataScope.getDepartmentUserIds().contains(userId))) {
            throw new UnauthorizedException("You do not have permission to view this user's follow-ups");
        }
        return getAllFollowUps(pageRequest, null, null, userId, null, null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public FollowUpPagedResponseDTO getTodayFollowUps(PageRequestDTO pageRequest) throws UnauthorizedException, BadRequestException {
        return getAllFollowUps(pageRequest, LocalDate.now(), null, null, null, null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public FollowUpPagedResponseDTO getPendingFollowUps(PageRequestDTO pageRequest) throws UnauthorizedException, BadRequestException {
        return getAllFollowUps(pageRequest, null, FollowUpStatus.PENDING, null, null, null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public FollowUpPagedResponseDTO getCompletedFollowUps(PageRequestDTO pageRequest) throws UnauthorizedException, BadRequestException {
        return getAllFollowUps(pageRequest, null, FollowUpStatus.COMPLETED, null, null, null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public FollowUpSummaryDTO getDashboardStats() throws UnauthorizedException, BadRequestException {
        UserDataScope dataScope = dataScopeService.getScopeForCurrentUser();

        Specification<LeadFollowUp> baseSpec = Specification.where(FollowUpSpecification.isNotDeleted())
                .and(FollowUpSpecification.leadIsNotDeleted());

        if (dataScope.isSelfScope()) {
            baseSpec = baseSpec.and((root, query, cb) -> cb.or(
                    cb.equal(root.get("createdByUser").get("id"), dataScope.getUserId()),
                    cb.equal(root.get("assignedTo").get("id"), dataScope.getUserId()),
                    cb.equal(root.get("lead").get("assignedTo").get("id"), dataScope.getUserId())
            ));
        } else if (dataScope.isDepartmentScope()) {
            baseSpec = baseSpec.and((root, query, cb) -> {
                jakarta.persistence.criteria.Predicate own = cb.or(
                        cb.equal(root.get("createdByUser").get("id"), dataScope.getUserId()),
                        cb.equal(root.get("assignedTo").get("id"), dataScope.getUserId()),
                        cb.equal(root.get("lead").get("assignedTo").get("id"), dataScope.getUserId())
                );
                if (dataScope.getDepartmentIds() != null && !dataScope.getDepartmentIds().isEmpty()) {
                    jakarta.persistence.criteria.Predicate deptLead = root.get("lead").get("department").get("id").in(dataScope.getDepartmentIds());
                    jakarta.persistence.criteria.Predicate deptUser = (dataScope.getDepartmentUserIds() != null && !dataScope.getDepartmentUserIds().isEmpty())
                            ? cb.or(
                                    root.get("lead").get("assignedTo").get("id").in(dataScope.getDepartmentUserIds()),
                                    root.get("createdByUser").get("id").in(dataScope.getDepartmentUserIds()),
                                    root.get("assignedTo").get("id").in(dataScope.getDepartmentUserIds())
                              )
                            : cb.disjunction();
                    return cb.or(own, deptLead, deptUser);
                }
                return own;
            });
        }

        long pending = leadFollowUpRepository.count(baseSpec.and(FollowUpSpecification.isCompleted(false)));
        long completed = leadFollowUpRepository.count(baseSpec.and(FollowUpSpecification.isCompleted(true)));
        long overdue = leadFollowUpRepository.count(baseSpec.and(FollowUpSpecification.isOverdue()));
        long today = leadFollowUpRepository.count(baseSpec.and(FollowUpSpecification.hasFollowUpDateOn(LocalDate.now())));

        return FollowUpSummaryDTO.builder()
                .todayFollowUps(today)
                .pendingFollowUps(pending)
                .completedFollowUps(completed)
                .overdueFollowUps(overdue)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<com.app.datadistribution.dto.followup.FollowUpStatusCountDTO> getFollowUpStatusCounts() throws UnauthorizedException, BadRequestException {
        UserDataScope dataScope = dataScopeService.getScopeForCurrentUser();

        List<com.app.datadistribution.entity.LeadStatus> followUpStatuses =
                leadStatusRepository.findByActiveTrueAndIsFollowUpStatusTrueAndIsDeletedFalseOrderByDisplayOrderAsc();

        if (followUpStatuses.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        List<UUID> statusIds = followUpStatuses.stream()
                .map(com.app.datadistribution.entity.LeadStatus::getId)
                .collect(Collectors.toList());

        jakarta.persistence.criteria.CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        jakarta.persistence.criteria.CriteriaQuery<Object[]> cq = cb.createQuery(Object[].class);
        jakarta.persistence.criteria.Root<com.app.datadistribution.entity.Lead> leadRoot = cq.from(com.app.datadistribution.entity.Lead.class);

        List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
        predicates.add(cb.isFalse(leadRoot.get("isDeleted")));
        predicates.add(leadRoot.get("currentStatus").get("id").in(statusIds));

        if (dataScope.isSelfScope()) {
            predicates.add(cb.equal(leadRoot.get("assignedTo").get("id"), dataScope.getUserId()));
        } else if (dataScope.isDepartmentScope()) {
            List<jakarta.persistence.criteria.Predicate> deptPreds = new java.util.ArrayList<>();
            if (dataScope.getDepartmentIds() != null && !dataScope.getDepartmentIds().isEmpty()) {
                deptPreds.add(leadRoot.get("department").get("id").in(dataScope.getDepartmentIds()));
            }
            if (dataScope.getDepartmentUserIds() != null && !dataScope.getDepartmentUserIds().isEmpty()) {
                deptPreds.add(leadRoot.get("assignedTo").get("id").in(dataScope.getDepartmentUserIds()));
            }
            deptPreds.add(cb.equal(leadRoot.get("assignedTo").get("id"), dataScope.getUserId()));
            predicates.add(cb.or(deptPreds.toArray(new jakarta.persistence.criteria.Predicate[0])));
        }

        cq.multiselect(leadRoot.get("currentStatus").get("id"), cb.count(leadRoot));
        cq.where(cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0])));
        cq.groupBy(leadRoot.get("currentStatus").get("id"));

        List<Object[]> rows = entityManager.createQuery(cq).getResultList();
        java.util.Map<UUID, Long> countByStatusId = new java.util.HashMap<>();
        for (Object[] row : rows) {
            countByStatusId.put((UUID) row[0], ((Number) row[1]).longValue());
        }

        return followUpStatuses.stream()
                .map(s -> com.app.datadistribution.dto.followup.FollowUpStatusCountDTO.builder()
                        .statusId(s.getId())
                        .statusName(s.getName())
                        .statusCode(s.getCode())
                        .count(countByStatusId.getOrDefault(s.getId(), 0L))
                        .build())
                .collect(Collectors.toList());
    }
}
