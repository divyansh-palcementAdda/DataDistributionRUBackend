package com.app.datadistribution.service.impl;

import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.feedback.FeedbackPagedResponseDTO;
import com.app.datadistribution.dto.feedback.FeedbackResponseDTO;
import com.app.datadistribution.dto.feedback.FeedbackSummaryDTO;
import com.app.datadistribution.entity.LeadFeedback;
import com.app.datadistribution.entity.LeadStatusSentiment;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.LeadStatus;
import com.app.datadistribution.enums.RoleType;
import com.app.datadistribution.enums.SentimentCategory;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.mapper.LeadMapper;
import com.app.datadistribution.repository.LeadFeedbackRepository;
import com.app.datadistribution.repository.LeadStatusSentimentRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.repository.specification.FeedbackSpecification;
import com.app.datadistribution.service.interfaces.FeedbackService;
import java.util.List;
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
public class FeedbackServiceImpl implements FeedbackService {

    private final LeadFeedbackRepository leadFeedbackRepository;
    private final UserRepository userRepository;
    private final LeadStatusSentimentRepository leadStatusSentimentRepository;
    private final LeadMapper leadMapper;

    @Override
    @Transactional(readOnly = true)
    public FeedbackPagedResponseDTO getAllFeedbacks(PageRequestDTO pageRequest, UUID userId, UUID leadId) throws UnauthorizedException {
        User currentUser = getCurrentUserEntity();

        Sort.Direction direction = Sort.Direction.fromString(pageRequest.getSortDirection());
        Pageable pageable = PageRequest.of(pageRequest.getPage(), pageRequest.getSize(), Sort.by(direction, pageRequest.getSortBy()));

        Specification<LeadFeedback> spec = Specification.where(FeedbackSpecification.isNotDeleted())
                .and(FeedbackSpecification.leadIsNotDeleted());

        if (isAdmin(currentUser)) {
            if (userId != null) {
                spec = spec.and(FeedbackSpecification.hasCreatedByUserId(userId));
            }
        } else {
            // Enforce visibility check: feedback.lead.assignedTo == currentUser
            spec = spec.and(FeedbackSpecification.leadAssignedTo(currentUser));
        }

        if (leadId != null) {
            spec = spec.and(FeedbackSpecification.hasLead(leadId));
        }
        if (pageRequest.getSearch() != null && !pageRequest.getSearch().isBlank()) {
            spec = spec.and(FeedbackSpecification.search(pageRequest.getSearch()));
        }

        Page<LeadFeedback> page = leadFeedbackRepository.findAll(spec, pageable);
        List<FeedbackResponseDTO> content = page.getContent().stream()
                .map(leadMapper::toFeedbackResponseDto)
                .collect(Collectors.toList());

        return FeedbackPagedResponseDTO.builder()
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
    public FeedbackPagedResponseDTO getFeedbacksByUserId(UUID userId, PageRequestDTO pageRequest) throws UnauthorizedException {
        User currentUser = getCurrentUserEntity();

        if (!isAdmin(currentUser) && !currentUser.getId().equals(userId)) {
            throw new UnauthorizedException("You do not have permission to view other users' feedbacks");
        }

        return getAllFeedbacks(pageRequest, userId, null);
    }

    @Override
    @Transactional(readOnly = true)
    public FeedbackSummaryDTO getDashboardStats() throws UnauthorizedException {
        User currentUser = getCurrentUserEntity();

        Specification<LeadFeedback> baseSpec = Specification.where(FeedbackSpecification.isNotDeleted())
                .and(FeedbackSpecification.leadIsNotDeleted());

        if (!isAdmin(currentUser)) {
            baseSpec = baseSpec.and(FeedbackSpecification.leadAssignedTo(currentUser));
        }

        // Fetch dynamic status lists mapped to POSITIVE and NEGATIVE sentiments from Database
        List<LeadStatus> positiveStatuses = leadStatusSentimentRepository.findBySentimentCategory(SentimentCategory.POSITIVE)
                .stream().map(LeadStatusSentiment::getLeadStatus).collect(Collectors.toList());

        List<LeadStatus> negativeStatuses = leadStatusSentimentRepository.findBySentimentCategory(SentimentCategory.NEGATIVE)
                .stream().map(LeadStatusSentiment::getLeadStatus).collect(Collectors.toList());

        Specification<LeadFeedback> todaySpec = baseSpec.and(FeedbackSpecification.createdToday());
        Specification<LeadFeedback> positiveSpec = baseSpec.and(FeedbackSpecification.hasStatusAtTimeIn(positiveStatuses));
        Specification<LeadFeedback> negativeSpec = baseSpec.and(FeedbackSpecification.hasStatusAtTimeIn(negativeStatuses));

        long totalCount = leadFeedbackRepository.count(baseSpec);
        long todayCount = leadFeedbackRepository.count(todaySpec);
        long positiveCount = leadFeedbackRepository.count(positiveSpec);
        long negativeCount = leadFeedbackRepository.count(negativeSpec);

        return FeedbackSummaryDTO.builder()
                .totalFeedbacks(totalCount)
                .todayFeedbacks(todayCount)
                .positiveFeedbacks(positiveCount)
                .negativeFeedbacks(negativeCount)
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
