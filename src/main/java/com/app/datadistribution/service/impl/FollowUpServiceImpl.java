package com.app.datadistribution.service.impl;

import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.followup.FollowUpPagedResponseDTO;
import com.app.datadistribution.dto.followup.FollowUpResponseDTO;
import com.app.datadistribution.dto.followup.FollowUpSummaryDTO;
import com.app.datadistribution.entity.LeadFollowUp;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.FollowUpStatus;
import com.app.datadistribution.enums.RoleType;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.mapper.LeadMapper;
import com.app.datadistribution.repository.LeadFollowUpRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.repository.specification.FollowUpSpecification;
import com.app.datadistribution.service.interfaces.FollowUpService;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FollowUpServiceImpl implements FollowUpService {

    private final LeadFollowUpRepository leadFollowUpRepository;
    private final UserRepository userRepository;
    private final LeadMapper leadMapper;

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
    public FollowUpPagedResponseDTO getAllFollowUps(PageRequestDTO pageRequest, LocalDate date, FollowUpStatus status, UUID userId, UUID leadId) throws UnauthorizedException {
        User currentUser = getCurrentUserEntity();
        
        Pageable pageable = createSafePageable(pageRequest);

        Specification<LeadFollowUp> spec = Specification.where(FollowUpSpecification.isNotDeleted())
                .and(FollowUpSpecification.leadIsNotDeleted());

        if (isAdmin(currentUser)) {
            if (userId != null) {
                spec = spec.and(FollowUpSpecification.hasCreatedByUserId(userId));
            }
        } else {
            // Enforce user visibility constraint: createdByUser == currentUser AND lead.assignedTo == currentUser
            spec = spec.and(FollowUpSpecification.belongsToUser(currentUser));
        }

        if (leadId != null) {
            spec = spec.and(FollowUpSpecification.hasLead(leadId));
        }
        if (status != null) {
            spec = spec.and(FollowUpSpecification.hasStatus(status));
        }
        if (date != null) {
            spec = spec.and(FollowUpSpecification.hasFollowUpDateOn(date));
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
    public FollowUpPagedResponseDTO getFollowUpsByUserId(UUID userId, PageRequestDTO pageRequest) throws UnauthorizedException {
        User currentUser = getCurrentUserEntity();

        if (!isAdmin(currentUser) && !currentUser.getId().equals(userId)) {
            throw new UnauthorizedException("You do not have permission to view other users' followups");
        }

        return getAllFollowUps(pageRequest, null, null, userId, null);
    }

    @Override
    @Transactional(readOnly = true)
    public FollowUpPagedResponseDTO getTodayFollowUps(PageRequestDTO pageRequest) throws UnauthorizedException {
        return getAllFollowUps(pageRequest, LocalDate.now(), null, null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public FollowUpPagedResponseDTO getPendingFollowUps(PageRequestDTO pageRequest) throws UnauthorizedException {
        User currentUser = getCurrentUserEntity();
        Pageable pageable = createSafePageable(pageRequest);

        Specification<LeadFollowUp> spec = Specification.where(FollowUpSpecification.isNotDeleted())
                .and(FollowUpSpecification.leadIsNotDeleted())
                .and(FollowUpSpecification.isCompleted(false));

        if (!isAdmin(currentUser)) {
            spec = spec.and(FollowUpSpecification.belongsToUser(currentUser));
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
    public FollowUpPagedResponseDTO getCompletedFollowUps(PageRequestDTO pageRequest) throws UnauthorizedException {
        User currentUser = getCurrentUserEntity();
        Pageable pageable = createSafePageable(pageRequest);

        Specification<LeadFollowUp> spec = Specification.where(FollowUpSpecification.isNotDeleted())
                .and(FollowUpSpecification.leadIsNotDeleted())
                .and(FollowUpSpecification.isCompleted(true));

        if (!isAdmin(currentUser)) {
            spec = spec.and(FollowUpSpecification.belongsToUser(currentUser));
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
    public FollowUpSummaryDTO getDashboardStats() throws UnauthorizedException {
        User currentUser = getCurrentUserEntity();

        Specification<LeadFollowUp> baseSpec = Specification.where(FollowUpSpecification.isNotDeleted())
                .and(FollowUpSpecification.leadIsNotDeleted());

        if (!isAdmin(currentUser)) {
            baseSpec = baseSpec.and(FollowUpSpecification.belongsToUser(currentUser));
        }

        Specification<LeadFollowUp> todaySpec = baseSpec.and(FollowUpSpecification.hasFollowUpDateOn(LocalDate.now()));
        Specification<LeadFollowUp> pendingSpec = baseSpec.and(FollowUpSpecification.isCompleted(false));
        Specification<LeadFollowUp> completedSpec = baseSpec.and(FollowUpSpecification.isCompleted(true));
        Specification<LeadFollowUp> overdueSpec = baseSpec.and(FollowUpSpecification.isOverdue());

        long todayCount = leadFollowUpRepository.count(todaySpec);
        long pendingCount = leadFollowUpRepository.count(pendingSpec);
        long completedCount = leadFollowUpRepository.count(completedSpec);
        long overdueCount = leadFollowUpRepository.count(overdueSpec);

        return FollowUpSummaryDTO.builder()
                .todayFollowUps(todayCount)
                .pendingFollowUps(pendingCount)
                .completedFollowUps(completedCount)
                .overdueFollowUps(overdueCount)
                .build();
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

    private boolean isAdmin(User user) {
        if (user.getRoles() == null) {
            return false;
        }
        return user.getRoles().stream()
                .anyMatch(role -> RoleType.SUPER_ADMIN.name().equalsIgnoreCase(role.getName())
                        || RoleType.ADMIN.name().equalsIgnoreCase(role.getName()));
    }
}
