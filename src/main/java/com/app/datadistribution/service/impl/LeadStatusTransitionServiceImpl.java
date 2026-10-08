package com.app.datadistribution.service.impl;

import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadStatus;
import com.app.datadistribution.entity.LeadStatusHistory;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.entity.LeadFollowUp;
import com.app.datadistribution.enums.FollowUpStatus;
import com.app.datadistribution.repository.LeadFollowUpRepository;
import com.app.datadistribution.repository.LeadRepository;
import com.app.datadistribution.repository.LeadStatusHistoryRepository;
import com.app.datadistribution.repository.LeadStatusRepository;
import com.app.datadistribution.service.interfaces.ILeadStatusTransitionService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeadStatusTransitionServiceImpl implements ILeadStatusTransitionService {

    private final LeadRepository leadRepository;
    private final LeadStatusRepository leadStatusRepository;
    private final LeadStatusHistoryRepository leadStatusHistoryRepository;
    private final LeadFollowUpRepository leadFollowUpRepository;

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    @Override
    public boolean isRegisteredStatus(LeadStatus status) {
        if (status == null) return false;
        String code = status.getCode() != null ? status.getCode().trim().toUpperCase(Locale.ROOT) : "";
        String name = status.getName() != null ? status.getName().trim().toUpperCase(Locale.ROOT) : "";
        return "REGISTERED".equals(code) || "REGISTER".equals(code)
                || "REGISTERED".equals(name) || "REGISTER".equals(name)
                || code.contains("REGISTERED") || name.contains("REGISTERED");
    }

    @Override
    @Transactional
    public Lead executeStatusTransition(Lead lead, LeadStatus targetStatus, User currentUser, String feedbackOrRemarks)
            throws BadRequestException {

        if (lead == null || lead.isDeleted()) {
            throw new BadRequestException("Lead must exist and not be deleted.");
        }
        if (targetStatus == null || targetStatus.isDeleted()) {
            throw new BadRequestException("Target lead status is required and must exist.");
        }
        if (!targetStatus.isActive()) {
            throw new BadRequestException("Cannot assign inactive lead status: " + targetStatus.getName());
        }

        LeadStatus currentStatus = lead.getCurrentStatus();
        boolean statusChanged = (currentStatus == null || !currentStatus.getId().equals(targetStatus.getId()));

        if (!statusChanged) {
            log.info("No status transition for lead {}. Current status is already {}.",
                    lead.getLeadCode(), currentStatus != null ? currentStatus.getName() : "null");
            // Defensive: ensure active follow-ups are closed if already registered
            if (isRegisteredStatus(currentStatus)) {
                completeActiveFollowUpsForRegisteredLead(lead);
            }
            return lead;
        }

        // Defensive check: If the lead's current status is already REGISTERED, it is terminal for calling/status workflows.
        if (isRegisteredStatus(currentStatus) && !isRegisteredStatus(targetStatus)) {
            throw new BadRequestException("Lead is already registered. Calling workflow is terminated and status cannot be changed to " + targetStatus.getName() + ".");
        }

        boolean isAssignedUser = (lead.getAssignedTo() != null
                && currentUser != null
                && lead.getAssignedTo().getId().equals(currentUser.getId()));

        // Validate sequential branches and daily limits for assigned user
        if (isAssignedUser) {
            validateTransitionAndLimits(lead, currentStatus, targetStatus, currentUser);
        }

        // Resolve dynamic chronological path from currentStatus to targetStatus
        List<LeadStatus> statusPath = isAssignedUser ? resolveStatusPath(currentStatus, targetStatus) : List.of(targetStatus);

        // Load existing status IDs in lead's history for idempotency
        List<LeadStatusHistory> existingHistories = lead.getId() != null
                ? leadStatusHistoryRepository.findByLeadId(lead.getId())
                : Collections.emptyList();

        Set<UUID> historicalStatusIds = new HashSet<>();
        for (LeadStatusHistory h : existingHistories) {
            if (h.getNewStatus() != null) {
                historicalStatusIds.add(h.getNewStatus().getId());
            }
        }
        if (currentStatus != null) {
            historicalStatusIds.add(currentStatus.getId());
        }

        LeadStatus lastStatus = currentStatus;
        LocalDateTime now = LocalDateTime.now();

        // Process path chronologically
        for (int i = 0; i < statusPath.size(); i++) {
            LeadStatus stepStatus = statusPath.get(i);
            boolean isFinalTarget = (i == statusPath.size() - 1);

            // If it's an intermediate step and already present in history, skip to avoid duplicate records
            if (!isFinalTarget && historicalStatusIds.contains(stepStatus.getId())) {
                lastStatus = stepStatus;
                continue;
            }

            String stepFeedback;
            String transitionType;

            if (isFinalTarget) {
                stepFeedback = (feedbackOrRemarks != null && !feedbackOrRemarks.isBlank())
                        ? feedbackOrRemarks
                        : "Lead status updated to " + stepStatus.getName();
                transitionType = isAssignedUser ? "USER" : (currentUser != null ? "ADMIN_OVERRIDE" : "SYSTEM");
            } else {
                stepFeedback = "Automatically progressed through hierarchy to " + stepStatus.getName();
                transitionType = "AUTO_PARENT";
            }

            LeadStatusHistory history = LeadStatusHistory.builder()
                    .lead(lead)
                    .previousStatus(lastStatus)
                    .newStatus(stepStatus)
                    .changedByUser(currentUser)
                    .feedback(stepFeedback)
                    .transitionType(transitionType)
                    .build();

            LeadStatusHistory savedHistory = leadStatusHistoryRepository.save(history);
            if (lead.getStatusHistories() != null) {
                lead.getStatusHistories().add(savedHistory);
            }
            historicalStatusIds.add(stepStatus.getId());
            lastStatus = stepStatus;

            log.info("Recorded status history for lead {}: {} -> {} (type: {}, changed by: {})",
                    lead.getLeadCode(),
                    history.getPreviousStatus() != null ? history.getPreviousStatus().getName() : "null",
                    stepStatus.getName(),
                    transitionType,
                    currentUser != null ? currentUser.getUsername() : "SYSTEM");
        }

        // Update current status and last contacted timestamp
        lead.setCurrentStatus(targetStatus);
        lead.setLastContactedAt(now);
        Lead savedLead = leadRepository.save(lead);

        // Canonical registration side-effects: complete all PENDING and UPCOMING follow-ups atomically
        if (isRegisteredStatus(targetStatus)) {
            completeActiveFollowUpsForRegisteredLead(savedLead);
        }

        return savedLead;
    }

    @Override
    public List<LeadStatus> resolveStatusPath(LeadStatus currentStatus, LeadStatus targetStatus) {
        if (targetStatus == null) {
            return Collections.emptyList();
        }
        if (currentStatus != null && currentStatus.getId().equals(targetStatus.getId())) {
            return Collections.emptyList();
        }

        // Trace upwards from targetStatus to root or currentStatus
        List<LeadStatus> targetAncestorChain = new ArrayList<>();
        LeadStatus curr = targetStatus;
        while (curr != null) {
            targetAncestorChain.add(curr);
            if (currentStatus != null && curr.getId().equals(currentStatus.getId())) {
                break;
            }
            curr = curr.getParentStatus();
        }
        Collections.reverse(targetAncestorChain);

        // If currentStatus was an ancestor, targetAncestorChain is [currentStatus, child1, ..., targetStatus]
        if (!targetAncestorChain.isEmpty() && currentStatus != null && targetAncestorChain.get(0).getId().equals(currentStatus.getId())) {
            // Return intermediate and target (excluding currentStatus itself)
            return targetAncestorChain.subList(1, targetAncestorChain.size());
        }

        // If targetStatus does not have currentStatus as ancestor (e.g. branch switch or root transition),
        // we return full target ancestor chain (excluding any parent that matches currentStatus)
        List<LeadStatus> path = new ArrayList<>();
        for (LeadStatus s : targetAncestorChain) {
            if (currentStatus == null || !s.getId().equals(currentStatus.getId())) {
                path.add(s);
            }
        }

        return path.isEmpty() ? List.of(targetStatus) : path;
    }

    @Override
    public void validateTransitionAndLimits(Lead lead, LeadStatus currentStatus, LeadStatus targetStatus, User currentUser)
            throws BadRequestException {

        // 1. Sequential Branch Enforcement
        if (targetStatus.isSequential()) {
            LeadStatus requiredParent = targetStatus.getParentStatus();
            if (requiredParent != null && requiredParent.isSequential()) {
                // If currentStatus is already inside the sequential sequence, ensure step-by-step progression
                if (currentStatus != null && currentStatus.isSequential()) {
                    if (!currentStatus.getId().equals(requiredParent.getId())) {
                        throw new BadRequestException("Sequential progression required: You cannot skip steps. Next expected status after "
                                + currentStatus.getName() + " is " + getExpectedNextSequentialStatusName(currentStatus)
                                + ", but requested " + targetStatus.getName());
                    }
                }
            }
        }

        // 2. Daily Rate Limiting for Rate-Limited Branches (e.g. NOT_CONNECTED)
        Integer attemptLimit = targetStatus.getDailyAttemptLimit();
        if (attemptLimit != null && attemptLimit > 0 && lead != null && lead.getId() != null && currentUser != null) {
            LocalDate today = LocalDate.now(BUSINESS_ZONE);
            LocalDateTime startOfDay = today.atStartOfDay();
            LocalDateTime endOfDay = today.plusDays(1).atStartOfDay();

            List<LeadStatusHistory> todayHistories = leadStatusHistoryRepository.findByLeadId(lead.getId());

            long todayAttempts = todayHistories.stream()
                    .filter(h -> h.getCreatedAt() != null
                            && !h.getCreatedAt().isBefore(startOfDay)
                            && h.getCreatedAt().isBefore(endOfDay))
                    .filter(h -> h.getChangedByUser() != null && h.getChangedByUser().getId().equals(currentUser.getId()))
                    .filter(h -> "USER".equals(h.getTransitionType()) || h.getTransitionType() == null)
                    .filter(h -> h.getNewStatus() != null && Objects.equals(h.getNewStatus().getDailyAttemptLimit(), attemptLimit))
                    .count();

            if (todayAttempts >= attemptLimit) {
                log.warn("Daily attempt limit ({}) reached for lead {} by user {} on date {}",
                        attemptLimit, lead.getLeadCode(), currentUser.getUsername(), today);
                throw new BadRequestException("Maximum " + attemptLimit
                        + " not-connected attempts are allowed for this lead per day. Please try again tomorrow.");
            }
        }
    }

    private String getExpectedNextSequentialStatusName(LeadStatus currentStatus) {
        if (currentStatus == null) return "initial stage";
        return "next stage in " + currentStatus.getName();
    }

    private void completeActiveFollowUpsForRegisteredLead(Lead lead) {
        if (lead == null || lead.getId() == null || leadFollowUpRepository == null) {
            return;
        }
        List<LeadFollowUp> activeFollowUps = leadFollowUpRepository.findActiveFollowUpsByLeadId(lead.getId());
        if (activeFollowUps != null && !activeFollowUps.isEmpty()) {
            LocalDateTime completionTime = LocalDateTime.now();
            List<LeadFollowUp> toSave = new ArrayList<>();
            for (LeadFollowUp f : activeFollowUps) {
                if (!f.isCompleted() && (f.getStatus() == FollowUpStatus.PENDING || f.getStatus() == FollowUpStatus.UPCOMING)) {
                    f.setCompleted(true);
                    f.setCompletedAt(completionTime);
                    f.setStatus(FollowUpStatus.COMPLETED);
                    String currentRemarks = f.getRemarks();
                    String autoNote = "Auto-completed: Lead transitioned to REGISTERED";
                    f.setRemarks((currentRemarks != null && !currentRemarks.isBlank())
                            ? currentRemarks + " | " + autoNote
                            : autoNote);
                    toSave.add(f);
                }
            }
            if (!toSave.isEmpty()) {
                leadFollowUpRepository.saveAll(toSave);
                log.info("Auto-completed {} active follow-up(s) for registered lead {}", toSave.size(), lead.getLeadCode());
            }
        }
        lead.setNextFollowUpDate(null);
        leadRepository.save(lead);
    }
}
