package com.app.datadistribution.service.impl;

import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.feedback.FeedbackPagedResponseDTO;
import com.app.datadistribution.dto.feedback.FeedbackResponseDTO;
import com.app.datadistribution.dto.feedback.FeedbackSummaryDTO;
import com.app.datadistribution.entity.LeadFeedback;
import com.app.datadistribution.entity.LeadStatus;
import com.app.datadistribution.entity.LeadStatusSentiment;
import com.app.datadistribution.enums.SentimentCategory;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.mapper.LeadMapper;
import com.app.datadistribution.repository.LeadFeedbackRepository;
import com.app.datadistribution.repository.LeadStatusRepository;
import com.app.datadistribution.repository.LeadStatusSentimentRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.repository.specification.FeedbackSpecification;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.service.interfaces.FeedbackService;
import com.app.datadistribution.service.interfaces.IUserDataScopeService;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl implements FeedbackService {

    private final LeadFeedbackRepository leadFeedbackRepository;
    private final UserRepository userRepository;
    private final LeadStatusSentimentRepository leadStatusSentimentRepository;
    private final LeadStatusRepository leadStatusRepository;
    private final IUserDataScopeService dataScopeService;
    private final LeadMapper leadMapper;

    @Override
    @Transactional(readOnly = true)
    public FeedbackPagedResponseDTO getAllFeedbacks(PageRequestDTO pageRequest, UUID userId, UUID leadId) throws UnauthorizedException {
        UserDataScope dataScope = dataScopeService.getScopeForCurrentUser();

        Sort.Direction direction = Sort.Direction.fromString(pageRequest.getSortDirection());
        Pageable pageable = PageRequest.of(pageRequest.getPage(), pageRequest.getSize(), Sort.by(direction, pageRequest.getSortBy()));

        Specification<LeadFeedback> spec = Specification.where(FeedbackSpecification.isNotDeleted())
                .and(FeedbackSpecification.leadIsNotDeleted());

        if (dataScope.isAdmin()) {
            if (userId != null) {
                spec = spec.and(FeedbackSpecification.hasCreatedByUserId(userId));
            }
        } else if (dataScope.isHod()) {
            spec = spec.and((root, query, cb) -> {
                jakarta.persistence.criteria.Predicate ownFeedback = cb.equal(root.get("createdByUser").get("id"), dataScope.getUserId());
                if (dataScope.getDepartmentIds() != null && !dataScope.getDepartmentIds().isEmpty()) {
                    jakarta.persistence.criteria.Predicate deptLead = root.get("lead").get("department").get("id").in(dataScope.getDepartmentIds());
                    jakarta.persistence.criteria.Predicate deptUser = root.get("lead").get("assignedTo").get("id").in(dataScope.getDepartmentUserIds());
                    return cb.or(ownFeedback, deptLead, deptUser);
                }
                return ownFeedback;
            });
        } else {
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.equal(root.get("createdByUser").get("id"), dataScope.getUserId()),
                    cb.equal(root.get("lead").get("assignedTo").get("id"), dataScope.getUserId())
            ));
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
        UserDataScope dataScope = dataScopeService.getScopeForCurrentUser();

        if (!dataScope.isAdmin() && !dataScope.getUserId().equals(userId) && !dataScope.getDepartmentUserIds().contains(userId)) {
            throw new UnauthorizedException("You do not have permission to view other users' feedbacks");
        }

        return getAllFeedbacks(pageRequest, userId, null);
    }

    @Override
    @Transactional(readOnly = true)
    public FeedbackSummaryDTO getDashboardStats() throws UnauthorizedException {
        UserDataScope dataScope = dataScopeService.getScopeForCurrentUser();

        Specification<LeadFeedback> baseSpec = Specification.where(FeedbackSpecification.isNotDeleted())
                .and(FeedbackSpecification.leadIsNotDeleted());

        if (dataScope.getScopeType() == UserDataScope.ScopeType.SELF) {
            baseSpec = baseSpec.and((root, query, cb) -> cb.or(
                    cb.equal(root.get("createdByUser").get("id"), dataScope.getUserId()),
                    cb.equal(root.get("lead").get("assignedTo").get("id"), dataScope.getUserId())
            ));
        } else if (dataScope.getScopeType() == UserDataScope.ScopeType.DEPARTMENT) {
            baseSpec = baseSpec.and((root, query, cb) -> {
                jakarta.persistence.criteria.Predicate own = cb.equal(root.get("createdByUser").get("id"), dataScope.getUserId());
                if (dataScope.getDepartmentIds() != null && !dataScope.getDepartmentIds().isEmpty()) {
                    return cb.or(own, root.get("lead").get("department").get("id").in(dataScope.getDepartmentIds()));
                }
                return own;
            });
        }

        long total = leadFeedbackRepository.count(baseSpec);
        long today = leadFeedbackRepository.count(baseSpec.and(FeedbackSpecification.createdToday()));

        List<LeadStatusSentiment> positiveSentiments = leadStatusSentimentRepository.findBySentimentCategory(SentimentCategory.POSITIVE);
        List<LeadStatus> positiveStatuses = positiveSentiments.stream().map(LeadStatusSentiment::getLeadStatus).collect(Collectors.toList());
        long positiveCount = leadFeedbackRepository.count(baseSpec.and(FeedbackSpecification.hasStatusAtTimeIn(positiveStatuses)));

        List<LeadStatusSentiment> negativeSentiments = leadStatusSentimentRepository.findBySentimentCategory(SentimentCategory.NEGATIVE);
        List<LeadStatus> negativeStatuses = negativeSentiments.stream().map(LeadStatusSentiment::getLeadStatus).collect(Collectors.toList());
        long negativeCount = leadFeedbackRepository.count(baseSpec.and(FeedbackSpecification.hasStatusAtTimeIn(negativeStatuses)));

        return FeedbackSummaryDTO.builder()
                .totalFeedbacks(total)
                .todayFeedbacks(today)
                .positiveFeedbacks(positiveCount)
                .negativeFeedbacks(negativeCount)
                .build();
    }
}
