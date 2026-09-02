package com.app.datadistribution.service.impl;

import com.app.datadistribution.dto.lead.CancelFollowUpRequest;
import com.app.datadistribution.dto.lead.CompleteFollowUpRequest;
import com.app.datadistribution.dto.lead.LeadFollowUpRequest;
import com.app.datadistribution.dto.lead.LeadFollowUpResponse;
import com.app.datadistribution.dto.lead.RescheduleFollowUpRequest;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadFollowUp;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.FollowUpStatus;
import com.app.datadistribution.event.FollowUpCancelledEvent;
import com.app.datadistribution.event.FollowUpCompletedEvent;
import com.app.datadistribution.event.FollowUpRescheduledEvent;
import com.app.datadistribution.event.FollowUpScheduledEvent;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.datadistribution.dto.lead.FollowUpStatusUpdateRequest;
import com.app.datadistribution.dto.lead.NotConnectedFollowUpRequest;
import com.app.datadistribution.service.interfaces.ILeadStatusTransitionService;

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
    private final ILeadStatusTransitionService leadStatusTransitionService;
    private final ApplicationEventPublisher eventPublisher;

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

        // Publish event for transaction-safe email notification after commit
        if (eventPublisher != null) {
            eventPublisher.publishEvent(FollowUpScheduledEvent.builder()
                    .followUpId(saved.getId())
                    .leadId(lead.getId())
                    .assignedUserId(lead.getAssignedTo() != null ? lead.getAssignedTo().getId() : null)
                    .followUpDate(saved.getFollowUpDate())
                    .remarks(saved.getRemarks())
                    .followUpStatus(saved.getStatus() != null ? saved.getStatus().getDisplayName() : "Pending")
                    .scheduledByUserId(currentUser.getId())
                    .build());
        }

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
    public LeadFollowUpResponse rescheduleFollowUp(UUID followUpId, RescheduleFollowUpRequest request) throws UnauthorizedException, BadRequestException {
        if (followUpId == null) {
            throw new BadRequestException("Follow-up ID is required for rescheduling.");
        }
        if (request == null || request.getNewFollowUpDate() == null) {
            throw new BadRequestException("New follow-up date and time is required.");
        }
        if (request.getRemarks() == null || request.getRemarks().trim().isEmpty()) {
            throw new BadRequestException("Remarks/reason is required when rescheduling a follow-up.");
        }

        LeadFollowUp followUp = leadFollowUpRepository.findById(followUpId)
                .filter(f -> !f.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Follow-up not found with id: " + followUpId));

        Lead lead = followUp.getLead();
        if (lead != null) {
            UserDataScope dataScope = leadDataScopeService.getCurrentUserScope();
            leadDataScopeService.validateLeadWriteAccess(lead, dataScope);
        }

        if (followUp.isCompleted() || followUp.getStatus() == FollowUpStatus.COMPLETED || followUp.getStatus() == FollowUpStatus.CANCELLED) {
            throw new BadRequestException("Cannot reschedule a completed or cancelled follow-up.");
        }

        LocalDateTime previousDate = followUp.getFollowUpDate();
        LocalDateTime newDate = request.getNewFollowUpDate();

        followUp.setFollowUpDate(newDate);
        String currentRemarks = followUp.getRemarks();
        String updatedRemarks = (currentRemarks != null && !currentRemarks.isBlank())
                ? currentRemarks + " | Rescheduled: " + request.getRemarks().trim()
                : request.getRemarks().trim();
        followUp.setRemarks(updatedRemarks);

        LeadFollowUp saved = leadFollowUpRepository.save(followUp);

        if (lead != null) {
            LocalDateTime nextActiveDate = leadFollowUpRepository.findEarliestActiveFollowUpDateByLeadId(lead.getId());
            lead.setNextFollowUpDate(nextActiveDate);
            leadRepository.save(lead);
        }

        User currentUser = getCurrentUserEntity();
        log.info("Follow-up {} rescheduled from {} to {} by user {}",
                followUpId, previousDate, newDate, currentUser.getUsername());

        // Publish event for transaction-safe email notification after commit
        if (eventPublisher != null) {
            User assigned = followUp.getAssignedTo() != null ? followUp.getAssignedTo() : (lead != null ? lead.getAssignedTo() : null);
            eventPublisher.publishEvent(FollowUpRescheduledEvent.builder()
                    .followUpId(saved.getId())
                    .leadId(lead != null ? lead.getId() : null)
                    .assignedUserId(assigned != null ? assigned.getId() : null)
                    .previousFollowUpDate(previousDate)
                    .newFollowUpDate(newDate)
                    .remarks(request.getRemarks().trim())
                    .followUpStatus(saved.getStatus() != null ? saved.getStatus().getDisplayName() : "Pending")
                    .rescheduledByUserId(currentUser.getId())
                    .build());
        }

        return leadMapper.toDto(saved);
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

        LocalDateTime scheduledDate = followUp.getFollowUpDate();
        LocalDateTime completedAt = LocalDateTime.now();

        followUp.setCompleted(true);
        followUp.setCompletedAt(completedAt);
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

        User currentUser = getCurrentUserEntity();
        log.info("Follow-up {} marked completed with feedback: {}", followUpId, remarks.trim());

        // Publish event for transaction-safe email notification after commit
        if (eventPublisher != null) {
            User assigned = followUp.getAssignedTo() != null ? followUp.getAssignedTo() : (lead != null ? lead.getAssignedTo() : null);
            eventPublisher.publishEvent(FollowUpCompletedEvent.builder()
                    .followUpId(saved.getId())
                    .leadId(lead != null ? lead.getId() : null)
                    .assignedUserId(assigned != null ? assigned.getId() : null)
                    .scheduledDate(scheduledDate)
                    .completedAt(completedAt)
                    .remarks(remarks.trim())
                    .finalStatus("COMPLETED")
                    .completedByUserId(currentUser.getId())
                    .build());
        }

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

        LocalDateTime scheduledDate = followUp.getFollowUpDate();
        LocalDateTime cancelledAt = LocalDateTime.now();

        followUp.setStatus(FollowUpStatus.CANCELLED);
        followUp.setCancelledAt(cancelledAt);
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

        User currentUser = getCurrentUserEntity();
        log.info("Follow-up {} marked cancelled with feedback: {}", followUpId, remarks.trim());

        // Publish event for transaction-safe email notification after commit
        if (eventPublisher != null) {
            User assigned = followUp.getAssignedTo() != null ? followUp.getAssignedTo() : (lead != null ? lead.getAssignedTo() : null);
            eventPublisher.publishEvent(FollowUpCancelledEvent.builder()
                    .followUpId(saved.getId())
                    .leadId(lead != null ? lead.getId() : null)
                    .assignedUserId(assigned != null ? assigned.getId() : null)
                    .scheduledDate(scheduledDate)
                    .cancelledAt(cancelledAt)
                    .remarks(followUp.getRemarks())
                    .cancellationRemarks(remarks.trim())
                    .cancelledByUserId(currentUser.getId())
                    .build());
        }

        return leadMapper.toDto(saved);
    }

    @Override
    @Transactional
    public LeadFollowUpResponse markNotConnected(UUID followUpId, NotConnectedFollowUpRequest request) throws UnauthorizedException, BadRequestException {
        if (request == null || request.getRemarks() == null || request.getRemarks().trim().isEmpty()) {
            throw new BadRequestException("Remarks/feedback is required when marking a follow-up as not connected.");
        }
        return markNotConnected(followUpId, request.getRemarks());
    }

    @Override
    @Transactional
    public LeadFollowUpResponse markNotConnected(UUID followUpId, String remarks) throws UnauthorizedException, BadRequestException {
        if (followUpId == null) {
            throw new BadRequestException("Follow-up ID is required.");
        }
        if (remarks == null || remarks.trim().isEmpty()) {
            throw new BadRequestException("Remarks/feedback is required when marking a follow-up as not connected.");
        }

        LeadFollowUp followUp = leadFollowUpRepository.findById(followUpId)
                .filter(f -> !f.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Follow-up not found with id: " + followUpId));

        Lead lead = followUp.getLead();
        if (lead == null || lead.isDeleted()) {
            throw new ResourcesNotFoundException("Associated lead not found or deleted for follow-up: " + followUpId);
        }

        // Validate user data scope and lead write permissions
        UserDataScope dataScope = leadDataScopeService.getCurrentUserScope();
        leadDataScopeService.validateLeadWriteAccess(lead, dataScope);

        // State Machine validation: Only PENDING or UPCOMING can be marked NOT_CONNECTED
        if (followUp.getStatus() != FollowUpStatus.PENDING && followUp.getStatus() != FollowUpStatus.UPCOMING) {
            throw new BadRequestException("Only PENDING or UPCOMING follow-ups can be marked as not connected. Current status: " + followUp.getStatus());
        }

        if (followUp.isCompleted() || followUp.getStatus() == FollowUpStatus.COMPLETED || followUp.getStatus() == FollowUpStatus.CANCELLED || followUp.getStatus() == FollowUpStatus.NOT_CONNECTED) {
            throw new BadRequestException("Cannot change status of a closed or not connected follow-up.");
        }

        User currentUser = getCurrentUserEntity();

        // Strict Assigned User / Role Verification
        boolean isAssignedUser = (lead.getAssignedTo() != null && currentUser != null && lead.getAssignedTo().getId().equals(currentUser.getId()))
                || (followUp.getAssignedTo() != null && currentUser != null && followUp.getAssignedTo().getId().equals(currentUser.getId()));
        boolean isPrivileged = dataScope != null && (dataScope.isAdmin() || dataScope.isHod());
        if (!isAssignedUser && !isPrivileged) {
            throw new UnauthorizedException("Only the assigned user or department head can mark this follow-up as not connected.");
        }

        // 1. Update Followup
        followUp.setStatus(FollowUpStatus.NOT_CONNECTED);
        followUp.setCompleted(false);

        String currentRemarks = followUp.getRemarks();
        String updatedRemarks = (currentRemarks != null && !currentRemarks.isBlank())
                ? currentRemarks + " | Not Connected: " + remarks.trim()
                : remarks.trim();
        followUp.setRemarks(updatedRemarks);

        LeadFollowUp savedFollowUp = leadFollowUpRepository.save(followUp);

        // 2. Sync Lead Next Follow Up Date
        LocalDateTime nextActiveDate = leadFollowUpRepository.findEarliestActiveFollowUpDateByLeadId(lead.getId());
        lead.setNextFollowUpDate(nextActiveDate);

        // 3. Resolve Dynamic Lead Status for NOT_CONNECTED
        com.app.datadistribution.entity.LeadStatus notConnectedStatus = leadStatusRepository.findByCodeIgnoreCase("NOT_CONNECTED")
                .filter(s -> !s.isDeleted())
                .orElseThrow(() -> new BadRequestException("Dynamic lead status 'NOT_CONNECTED' is not configured or is inactive in the system."));

        if (!notConnectedStatus.isActive()) {
            throw new BadRequestException("Dynamic lead status 'NOT_CONNECTED' is inactive in the system.");
        }

        // 4. Canonical Lead Status Transition (atomic within this transaction, creates LeadStatusHistory)
        leadStatusTransitionService.executeStatusTransition(lead, notConnectedStatus, currentUser, remarks.trim());

        log.info("Follow-up {} marked NOT_CONNECTED and associated lead {} transitioned to NOT_CONNECTED by user {}",
                followUpId, lead.getLeadCode(), currentUser != null ? currentUser.getUsername() : "SYSTEM");

        return leadMapper.toDto(savedFollowUp);
    }

    @Override
    @Transactional
    public LeadFollowUpResponse updateFollowUpStatus(UUID followUpId, FollowUpStatusUpdateRequest request) throws UnauthorizedException, BadRequestException {
        if (request == null || request.getStatus() == null) {
            throw new BadRequestException("Target follow-up status is required.");
        }
        FollowUpStatus target = request.getStatus();
        String effectiveRemarks = request.getEffectiveRemarks();

        switch (target) {
            case COMPLETED:
                return completeFollowUp(followUpId, effectiveRemarks);
            case CANCELLED:
                return cancelFollowUp(followUpId, effectiveRemarks);
            case NOT_CONNECTED:
                return markNotConnected(followUpId, effectiveRemarks);
            default:
                throw new BadRequestException("Unsupported status update transition to " + target);
        }
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

