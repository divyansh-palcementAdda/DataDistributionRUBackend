package com.app.datadistribution.service.impl;

import com.app.datadistribution.dto.lead.CancelFollowUpRequest;
import com.app.datadistribution.dto.lead.CompleteFollowUpRequest;
import com.app.datadistribution.dto.lead.LeadFollowUpRequest;
import com.app.datadistribution.dto.lead.LeadFollowUpResponse;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadFollowUp;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.FollowUpStatus;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.mapper.LeadMapper;
import com.app.datadistribution.repository.LeadFollowUpRepository;
import com.app.datadistribution.repository.LeadRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.service.interfaces.ILeadDataScopeService;
import com.app.datadistribution.service.interfaces.ILeadFollowUpService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeadFollowUpServiceImpl implements ILeadFollowUpService {

    private final LeadFollowUpRepository leadFollowUpRepository;
    private final LeadRepository leadRepository;
    private final com.app.datadistribution.repository.LeadStatusRepository leadStatusRepository;
    private final UserRepository userRepository;
    private final LeadMapper leadMapper;
    private final ILeadDataScopeService leadDataScopeService;

    @Override
    @Transactional
    public LeadFollowUpResponse createFollowUp(LeadFollowUpRequest request) throws UnauthorizedException, BadRequestException {
        if (request == null || request.getLeadId() == null) {
            throw new BadRequestException("Lead ID is required to schedule a follow-up.");
        }
        return createFollowUp(request.getLeadId(), request);
    }

    @Override
    @Transactional
    public LeadFollowUpResponse createFollowUp(UUID leadId, LeadFollowUpRequest request) throws UnauthorizedException, BadRequestException {
        if (leadId == null) {
            throw new BadRequestException("Lead ID is required to schedule a follow-up.");
        }
        if (request == null) {
            throw new BadRequestException("Follow-up request body is required.");
        }
        if (request.getRemarks() == null || request.getRemarks().trim().isEmpty()) {
            throw new BadRequestException("Remarks/feedback is required while scheduling a follow-up.");
        }
        if (request.getFollowUpDate() == null) {
            throw new BadRequestException("Follow-up date is required.");
        }

        // 1. Concurrency-safe pessimistic lock on Lead
        Lead lead = leadRepository.findByIdForUpdate(leadId)
                .orElseGet(() -> leadRepository.findById(leadId)
                        .filter(l -> !l.isDeleted())
                        .orElseThrow(() -> new ResourcesNotFoundException("Lead not found with id: " + leadId)));

        if (lead.isDeleted()) {
            throw new ResourcesNotFoundException("Lead not found with id: " + leadId);
        }

        // 2. Lead data scoping & write access
        UserDataScope dataScope = leadDataScopeService.getCurrentUserScope();
        leadDataScopeService.validateLeadWriteAccess(lead, dataScope);

        // 3. Strict Assigned User Validation (Only assigned user can schedule)
        User currentUser = getCurrentUserEntity();
        if (lead.getAssignedTo() == null || currentUser == null || !lead.getAssignedTo().getId().equals(currentUser.getId())) {
            throw new BadRequestException("Only the currently assigned user can schedule a follow-up for this lead.");
        }

        // 4. Single Active Follow-up Invariant (PENDING or UPCOMING)
        boolean hasActiveFollowUp = leadFollowUpRepository.existsActiveFollowUpByLeadId(leadId);
        if (hasActiveFollowUp) {
            throw new BadRequestException("A follow-up is already active for this lead. Please complete or cancel the existing follow-up before scheduling a new one.");
        }

        // 5. Dynamic Lead Status Follow-Up classification validation
        if (request.getLeadStatusId() != null) {
            com.app.datadistribution.entity.LeadStatus status = leadStatusRepository.findById(request.getLeadStatusId())
                    .filter(s -> !s.isDeleted())
                    .orElseThrow(() -> new ResourcesNotFoundException("Lead status not found with id: " + request.getLeadStatusId()));
            if (!status.isActive()) {
                throw new BadRequestException("Selected lead status is inactive.");
            }
            if (!status.isFollowUpStatus()) {
                throw new BadRequestException("Selected lead status '" + status.getName() + "' is not configured as a follow-up status.");
            }
            lead.setLeadStatus(status);
        }

        FollowUpStatus initialStatus = (request.getStatus() == FollowUpStatus.UPCOMING)
                ? FollowUpStatus.UPCOMING
                : FollowUpStatus.PENDING;

        LeadFollowUp followUp = LeadFollowUp.builder()
                .lead(lead)
                .followUpDate(request.getFollowUpDate())
                .remarks(request.getRemarks().trim())
                .status(initialStatus)
                .completed(false)
                .createdByUser(currentUser)
                .assignedTo(lead.getAssignedTo())
                .build();

        lead.setNextFollowUpDate(request.getFollowUpDate());
        leadRepository.save(lead);

        LeadFollowUp saved = leadFollowUpRepository.save(followUp);
        log.info("Follow-up {} scheduled for lead {} on {} by user {}",
                saved.getId(), lead.getLeadCode(), saved.getFollowUpDate(), currentUser.getUsername());

        return leadMapper.toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeadFollowUpResponse> getFollowUpsByLeadId(UUID leadId) throws UnauthorizedException {
        if (leadId == null) {
            return List.of();
        }
        Lead lead = leadRepository.findById(leadId)
                .filter(l -> !l.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Lead not found with id: " + leadId));

        try {
            UserDataScope dataScope = leadDataScopeService.getCurrentUserScope();
            leadDataScopeService.validateLeadReadAccess(lead, dataScope);
        } catch (BadRequestException e) {
            throw new UnauthorizedException("Cannot resolve user data scope: " + e.getMessage());
        }

        return leadFollowUpRepository.findByLeadIdOrderByFollowUpDateDesc(leadId).stream()
                .map(leadMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public LeadFollowUpResponse completeFollowUp(UUID followUpId, CompleteFollowUpRequest request) throws UnauthorizedException, BadRequestException {
        if (request == null || request.getRemarks() == null || request.getRemarks().trim().isEmpty()) {
            throw new BadRequestException("Remarks/feedback is required when marking a follow-up as completed.");
        }
        return completeFollowUp(followUpId, request.getRemarks());
    }

    @Override
    @Transactional
    public LeadFollowUpResponse completeFollowUp(UUID followUpId, String remarks) throws UnauthorizedException, BadRequestException {
        if (remarks == null || remarks.trim().isEmpty()) {
            throw new BadRequestException("Remarks/feedback is required when marking a follow-up as completed.");
        }

        LeadFollowUp followUp = leadFollowUpRepository.findById(followUpId)
                .filter(f -> !f.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Follow-up not found with id: " + followUpId));

        Lead lead = followUp.getLead();
        if (lead != null) {
            UserDataScope dataScope = leadDataScopeService.getCurrentUserScope();
            leadDataScopeService.validateLeadWriteAccess(lead, dataScope);
        }

        // State Machine validation
        if (followUp.getStatus() != FollowUpStatus.PENDING && followUp.getStatus() != FollowUpStatus.UPCOMING) {
            throw new BadRequestException("Only PENDING or UPCOMING follow-ups can be marked as completed. Current status: " + followUp.getStatus());
        }

        followUp.setCompleted(true);
        followUp.setCompletedAt(LocalDateTime.now());
        followUp.setStatus(FollowUpStatus.COMPLETED);

        String currentRemarks = followUp.getRemarks();
        String updatedRemarks = (currentRemarks != null && !currentRemarks.isBlank())
                ? currentRemarks + " | Completion Feedback: " + remarks.trim()
                : remarks.trim();
        followUp.setRemarks(updatedRemarks);

        LeadFollowUp saved = leadFollowUpRepository.save(followUp);

        // Sync lead next follow up date
        if (lead != null) {
            LocalDateTime nextActiveDate = leadFollowUpRepository.findEarliestActiveFollowUpDateByLeadId(lead.getId());
            lead.setNextFollowUpDate(nextActiveDate);
            leadRepository.save(lead);
        }

        log.info("Follow-up {} marked completed with feedback: {}", followUpId, remarks.trim());
        return leadMapper.toDto(saved);
    }

    @Override
    @Transactional
    public LeadFollowUpResponse cancelFollowUp(UUID followUpId, CancelFollowUpRequest request) throws UnauthorizedException, BadRequestException {
        if (request == null || request.getRemarks() == null || request.getRemarks().trim().isEmpty()) {
            throw new BadRequestException("Remarks/feedback is required when cancelling a follow-up.");
        }
        return cancelFollowUp(followUpId, request.getRemarks());
    }

    @Override
    @Transactional
    public LeadFollowUpResponse cancelFollowUp(UUID followUpId, String remarks) throws UnauthorizedException, BadRequestException {
        if (remarks == null || remarks.trim().isEmpty()) {
            throw new BadRequestException("Remarks/feedback is required when cancelling a follow-up.");
        }

        LeadFollowUp followUp = leadFollowUpRepository.findById(followUpId)
                .filter(f -> !f.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Follow-up not found with id: " + followUpId));

        Lead lead = followUp.getLead();
        if (lead != null) {
            UserDataScope dataScope = leadDataScopeService.getCurrentUserScope();
            leadDataScopeService.validateLeadWriteAccess(lead, dataScope);
        }

        // State Machine validation
        if (followUp.getStatus() != FollowUpStatus.PENDING && followUp.getStatus() != FollowUpStatus.UPCOMING) {
            throw new BadRequestException("Only PENDING or UPCOMING follow-ups can be cancelled. Current status: " + followUp.getStatus());
        }

        followUp.setStatus(FollowUpStatus.CANCELLED);
        followUp.setCancelledAt(LocalDateTime.now());
        followUp.setCancellationRemarks(remarks.trim());

        String currentRemarks = followUp.getRemarks();
        String updatedRemarks = (currentRemarks != null && !currentRemarks.isBlank())
                ? currentRemarks + " | Cancellation Reason: " + remarks.trim()
                : remarks.trim();
        followUp.setRemarks(updatedRemarks);

        LeadFollowUp saved = leadFollowUpRepository.save(followUp);

        // Sync lead next follow up date
        if (lead != null) {
            LocalDateTime nextActiveDate = leadFollowUpRepository.findEarliestActiveFollowUpDateByLeadId(lead.getId());
            lead.setNextFollowUpDate(nextActiveDate);
            leadRepository.save(lead);
        }

        log.info("Follow-up {} marked cancelled with feedback: {}", followUpId, remarks.trim());
        return leadMapper.toDto(saved);
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
