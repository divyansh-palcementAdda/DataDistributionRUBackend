package com.app.datadistribution.service.impl;

import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.lead.*;
import com.app.datadistribution.entity.*;
import com.app.datadistribution.enums.LeadStatus;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.mapper.LeadMapper;
import com.app.datadistribution.repository.*;
import com.app.datadistribution.service.interfaces.ILeadService;
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
    private final UserRepository userRepository;
    private final LeadStatusHistoryRepository leadStatusHistoryRepository;
    private final LeadFeedbackRepository leadFeedbackRepository;
    private final LeadMapper leadMapper;

    @Override
    @Transactional
    public LeadResponse create(LeadRequest request) throws BadRequestException, UnauthorizedException {
        User currentUser = getCurrentUserEntity();
        
        LeadSource source = leadSourceRepository.findById(request.getSourceId())
                .filter(s -> !s.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Lead source not found with id: " + request.getSourceId()));

        User assignedTo = null;
        if (request.getAssignedToUserId() != null) {
            assignedTo = userRepository.findById(request.getAssignedToUserId())
                    .filter(u -> !u.isDeleted())
                    .orElseThrow(() -> new ResourcesNotFoundException("User not found with id: " + request.getAssignedToUserId()));
        }

        String leadCode = request.getLeadCode();
        if (leadCode == null || leadCode.isBlank()) {
            leadCode = "LEAD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } else if (leadRepository.existsByLeadCode(leadCode)) {
            throw new BadRequestException("Lead code already exists: " + leadCode);
        }

        Lead lead = leadMapper.toEntity(request);
        lead.setLeadCode(leadCode);
        lead.setSource(source);
        lead.setAssignedTo(assignedTo);
        lead.setCreatedByUser(currentUser);
        lead.setCurrentStatus(LeadStatus.RAW);
        lead.setActive(true);

        Lead saved = leadRepository.save(lead);
        log.info("Created lead: {} ({})", saved.getFullName(), saved.getLeadCode());

        // Create initial status history trail
        LeadStatusHistory initialHistory = LeadStatusHistory.builder()
                .lead(saved)
                .oldStatus(null)
                .newStatus(LeadStatus.RAW)
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

        LeadSource source = leadSourceRepository.findById(request.getSourceId())
                .filter(s -> !s.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Lead source not found with id: " + request.getSourceId()));

        User assignedTo = null;
        if (request.getAssignedToUserId() != null) {
            assignedTo = userRepository.findById(request.getAssignedToUserId())
                    .filter(u -> !u.isDeleted())
                    .orElseThrow(() -> new ResourcesNotFoundException("User not found with id: " + request.getAssignedToUserId()));
        }

        lead.setFullName(request.getFullName());
        lead.setPhoneNumber(request.getPhoneNumber());
        lead.setAlternatePhoneNumber(request.getAlternatePhoneNumber());
        lead.setEmail(request.getEmail());
        lead.setCity(request.getCity());
        lead.setState(request.getState());
        lead.setCountry(request.getCountry());
        lead.setSource(source);
        lead.setSourceDetails(request.getSourceDetails());
        lead.setCourseInterested(request.getCourseInterested());
        lead.setRemarks(request.getRemarks());
        lead.setAssignedTo(assignedTo);
        lead.setActive(request.isActive());
        if (request.getNextFollowUpDate() != null) {
            lead.setNextFollowUpDate(request.getNextFollowUpDate());
        }

        Lead updated = leadRepository.save(lead);
        log.info("Updated lead: {}", updated.getLeadCode());
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
    public LeadPageResponse getAllLeads(PageRequestDTO pageRequest, UUID sourceId) {
        Sort.Direction direction = Sort.Direction.fromString(pageRequest.getSortDirection());
        Pageable pageable = PageRequest.of(pageRequest.getPage(), pageRequest.getSize(), Sort.by(direction, pageRequest.getSortBy()));

        Specification<Lead> spec = Specification.where(isNotDeleted());
        if (sourceId != null) {
            spec = spec.and(filterBySource(sourceId));
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

        User currentUser = getCurrentUserEntity();
        LeadStatus oldStatus = lead.getCurrentStatus();
        LeadStatus newStatus = request.getNewStatus();

        if (oldStatus == newStatus) {
            return leadMapper.toDto(lead);
        }

        // 1. Save LeadStatusHistory
        LeadStatusHistory statusHistory = LeadStatusHistory.builder()
                .lead(lead)
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .changedByUser(currentUser)
                .feedback(request.getFeedback())
                .build();
        leadStatusHistoryRepository.save(statusHistory);

        // 2. Save LeadFeedback
        LeadFeedback feedback = LeadFeedback.builder()
                .lead(lead)
                .feedback(request.getFeedback())
                .statusAtTime(newStatus)
                .createdByUser(currentUser)
                .build();
        leadFeedbackRepository.save(feedback);

        // 3. Update Lead
        lead.setCurrentStatus(newStatus);
        lead.setLastContactedAt(LocalDateTime.now());
        Lead saved = leadRepository.save(lead);

        log.info("Lead status changed for {}: {} -> {}", lead.getLeadCode(), oldStatus, newStatus);
        return leadMapper.toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeadStatusHistoryResponse> getStatusHistoryByLeadId(UUID leadId) {
        if (!leadRepository.existsById(leadId)) {
            throw new ResourcesNotFoundException("Lead not found with id: " + leadId);
        }
        return leadStatusHistoryRepository.findByLeadIdOrderByCreatedAtDesc(leadId).stream()
                .map(leadMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeadSourceStatsResponse> getSourceWiseStats() {
        List<Object[]> results = leadRepository.countBySource();
        long totalLeads = results.stream().mapToLong(r -> (Long) r[1]).sum();

        List<LeadSourceStatsResponse> stats = new ArrayList<>();
        for (Object[] result : results) {
            LeadSource source = (LeadSource) result[0];
            long count = (Long) result[1];
            double percentage = totalLeads > 0 ? (count * 100.0) / totalLeads : 0.0;

            stats.add(LeadSourceStatsResponse.builder()
                    .sourceId(source != null ? source.getId() : null)
                    .sourceName(source != null ? source.getName() : "Unknown")
                    .count(count)
                    .percentage(percentage)
                    .build());
        }
        return stats;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> getStatusWiseStats() {
        List<Object[]> results = leadRepository.countByStatus();
        Map<String, Long> stats = new HashMap<>();
        for (Object[] result : results) {
            LeadStatus status = (LeadStatus) result[0];
            long count = (Long) result[1];
            stats.put(status.name(), count);
        }
        return stats;
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

    private Specification<Lead> isNotDeleted() {
        return (root, query, cb) -> cb.equal(root.get("isDeleted"), false);
    }

    private Specification<Lead> filterBySource(UUID sourceId) {
        return (root, query, cb) -> cb.equal(root.get("source").get("id"), sourceId);
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
