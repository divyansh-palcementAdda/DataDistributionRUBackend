package com.app.datadistribution.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.datadistribution.dto.lead.LeadActionEnforcementDTO;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadFollowUp;
import com.app.datadistribution.entity.LeadStatus;
import com.app.datadistribution.entity.Role;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.FollowUpStatus;
import com.app.datadistribution.enums.RoleType;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.repository.LeadFollowUpRepository;
import com.app.datadistribution.repository.LeadRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.service.interfaces.ILeadActionEnforcementService;
import com.app.datadistribution.service.interfaces.ILeadDataScopeService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeadActionEnforcementServiceImpl implements ILeadActionEnforcementService {

    private final LeadRepository leadRepository;
    private final LeadFollowUpRepository leadFollowUpRepository;
    private final UserRepository userRepository;
    private final ILeadDataScopeService leadDataScopeService;

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");
    private static final List<String> DEFAULT_ALLOWED_ACTIONS = List.of(
            "REGISTER",
            "FOLLOW_UP",
            "NOT_CONNECTED",
            "BAD"
    );

    @Override
    @Transactional(readOnly = true)
    public LeadActionEnforcementDTO checkActionEnforcement(UUID leadId, UUID userId)
            throws UnauthorizedException, BadRequestException {
        if (leadId == null) {
            throw new BadRequestException("Lead ID is required.");
        }

        Lead lead = leadRepository.findById(leadId)
                .filter(l -> !l.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Lead not found with id: " + leadId));

        User user = null;
        if (userId != null) {
            user = userRepository.findById(userId)
                    .filter(u -> !u.isDeleted())
                    .orElse(null);
        }
        if (user == null) {
            user = getCurrentUserEntity();
        }

        return checkActionEnforcement(lead, user);
    }

    @Override
    @Transactional(readOnly = true)
    public LeadActionEnforcementDTO checkActionEnforcement(UUID leadId)
            throws UnauthorizedException, BadRequestException {
        return checkActionEnforcement(leadId, null);
    }

    @Override
    @Transactional(readOnly = true)
    public LeadActionEnforcementDTO checkActionEnforcement(Lead lead, User currentUser) {
        if (lead == null || lead.isDeleted()) {
            return unconstrained(lead != null ? lead.getId() : null);
        }

        if (currentUser == null) {
            try {
                currentUser = getCurrentUserEntity();
            } catch (Exception e) {
                return unconstrained(lead.getId());
            }
        }

        // 1. Bypass for Admin and Super Admin users
        if (isAdminUser(currentUser)) {
            return unconstrained(lead.getId());
        }

        // Also check lead data scope if available
        try {
            UserDataScope dataScope = leadDataScopeService.getCurrentUserScope();
            if (dataScope != null && dataScope.isAdmin()) {
                return unconstrained(lead.getId());
            }
        } catch (Exception ignored) {
        }

        // 2. Lead must have an assigned user, and current user MUST be the assigned user
        if (lead.getAssignedTo() == null) {
            return unconstrained(lead.getId());
        }

        UUID assignedUserId = lead.getAssignedTo().getId();
        if (!assignedUserId.equals(currentUser.getId())) {
            // HOD or other counselors viewing this lead are not restricted
            return unconstrained(lead.getId());
        }

        // 3. Check Qualifying Lead Statuses
        LeadStatus currentStatus = lead.getCurrentStatus();

        // Action A: REGISTERED
        if (isRegisteredStatus(currentStatus)) {
            return unconstrained(lead.getId());
        }

        // Action D: BAD
        if (isBadStatus(currentStatus)) {
            return unconstrained(lead.getId());
        }

        // Action C: NOT_CONNECTED (and any valid not-connected sub-status)
        if (isNotConnectedStatus(currentStatus)) {
            return unconstrained(lead.getId());
        }

        // Action E: NOT_INTERESTED
        if (isNotInterestedStatus(currentStatus)) {
            return unconstrained(lead.getId());
        }

        // 4. Check Follow-Up Qualification (Action B)
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        LocalDateTime startOfToday = today.atStartOfDay();
        LocalDateTime startOfTomorrow = today.plusDays(1).atStartOfDay();

        List<LeadFollowUp> followUps = leadFollowUpRepository.findByLeadIdOrderByFollowUpDateDesc(lead.getId());

        // Check for today's follow-up completion:
        // Find latest completed follow-up for today (whether today's pending or an upcoming follow-up completed manually today)
        Optional<LeadFollowUp> latestCompletedToday = followUps.stream()
                .filter(f -> !f.isDeleted() && f.isCompleted() && f.getCompletedAt() != null
                        && f.getCompletedAt().toLocalDate().isEqual(today))
                .max((a, b) -> a.getCompletedAt().compareTo(b.getCompletedAt()));

        if (latestCompletedToday.isPresent()) {
            LocalDateTime completedAt = latestCompletedToday.get().getCompletedAt();
            // A follow-up was completed today. Completing follow-up does NOT automatically unlock the lead (Requirement 6).
            // It only unlocks if the counselor *also* scheduled a new uncompleted follow-up created after that completion!
            boolean hasNewFollowUpScheduledAfterCompletion = followUps.stream().anyMatch(f ->
                    !f.isDeleted() &&
                    !f.isCompleted() &&
                    f.getCreatedAt() != null &&
                    f.getCreatedAt().isAfter(completedAt)
            );

            if (hasNewFollowUpScheduledAfterCompletion) {
                return unconstrained(lead.getId());
            }

            // Lead still requires action!
            return restricted(lead.getId(), "MANDATORY_LEAD_ACTION",
                    "Follow-up completed. Please update the lead status before leaving this lead.");
        }

        // Check if there is an active upcoming follow-up scheduled for future (tomorrow or later)
        boolean hasFutureFollowUp = followUps.stream().anyMatch(f ->
                !f.isDeleted() &&
                !f.isCompleted() &&
                f.getStatus() == FollowUpStatus.UPCOMING &&
                f.getFollowUpDate() != null &&
                !f.getFollowUpDate().isBefore(startOfTomorrow)
        );

        if (hasFutureFollowUp) {
            return unconstrained(lead.getId());
        }

        // Check if a follow-up was newly created TODAY during this session and is still active (not yet completed)
        boolean hasNewlyCreatedFollowUpToday = followUps.stream().anyMatch(f ->
                !f.isDeleted() &&
                !f.isCompleted() &&
                f.getCreatedAt() != null &&
                !f.getCreatedAt().isBefore(startOfToday)
        );

        if (hasNewlyCreatedFollowUpToday) {
            return unconstrained(lead.getId());
        }

        // 5. Unresolved Mandatory Lead Action
        return restricted(lead.getId(), "MANDATORY_LEAD_ACTION",
                "Please update this lead before leaving this page.");
    }

    @Override
    public boolean isLeadActionRequired(UUID leadId, UUID userId) {
        try {
            LeadActionEnforcementDTO enforcement = checkActionEnforcement(leadId, userId);
            return enforcement.isRestricted();
        } catch (Exception e) {
            log.error("Failed to check lead action enforcement for lead {} and user {}", leadId, userId, e);
            return false;
        }
    }

    @Override
    public boolean isQualifyingStatus(LeadStatus status) {
        return isRegisteredStatus(status) || isBadStatus(status) || isNotConnectedStatus(status) || isNotInterestedStatus(status);
    }

    @Override
    public boolean isRegisteredStatus(LeadStatus status) {
        if (status == null) return false;
        String code = status.getCode() != null ? status.getCode().trim().toUpperCase(Locale.ROOT) : "";
        String name = status.getName() != null ? status.getName().trim().toUpperCase(Locale.ROOT) : "";
        return "REGISTERED".equals(code) || "REGISTER".equals(code)
                || "REGISTERED".equals(name) || "REGISTER".equals(name);
    }

    @Override
    public boolean isBadStatus(LeadStatus status) {
        if (status == null) return false;
        String code = status.getCode() != null ? status.getCode().trim().toUpperCase(Locale.ROOT) : "";
        String name = status.getName() != null ? status.getName().trim().toUpperCase(Locale.ROOT) : "";
        return "BAD".equals(code) || "BAD".equals(name);
    }

    @Override
    public boolean isNotInterestedStatus(LeadStatus status) {
        if (status == null) return false;
        String code = status.getCode() != null ? status.getCode().trim().toUpperCase(Locale.ROOT) : "";
        String name = status.getName() != null ? status.getName().trim().toUpperCase(Locale.ROOT) : "";
        return "NOT_INTERESTED".equals(code) || "NOT INTERESTED".equals(name)
                || code.contains("NOT_INTERESTED") || name.contains("NOT INTERESTED");
    }

    @Override
    public boolean isNotConnectedStatus(LeadStatus status) {
        if (status == null) return false;

        Set<LeadStatus> visited = new HashSet<>();
        LeadStatus current = status;

        while (current != null && !visited.contains(current)) {
            visited.add(current);

            String code = current.getCode() != null ? current.getCode().trim().toUpperCase(Locale.ROOT) : "";
            String name = current.getName() != null ? current.getName().trim().toUpperCase(Locale.ROOT) : "";

            if (matchesNotConnectedPattern(code) || matchesNotConnectedPattern(name)) {
                return true;
            }

            current = current.getParentStatus();
        }

        return false;
    }

    private boolean matchesNotConnectedPattern(String text) {
        if (text == null || text.isBlank()) return false;
        return text.equals("NOT_CONNECTED")
                || text.equals("NOT CONNECTED")
                || text.startsWith("NOT_CONNECTED")
                || text.startsWith("NOT CONNECTED")
                || text.contains("CALL_NOT_CONNECTED")
                || text.contains("CALL NOT CONNECTED")
                || text.contains("NOT_REACHABLE")
                || text.contains("NOT REACHABLE")
                || text.contains("SWITCHED_OFF")
                || text.contains("SWITCHED OFF")
                || text.contains("BUSY")
                || text.contains("FINALLY_NOT_CONNECTED")
                || text.contains("FINALLY NOT CONNECTED");
    }

    private boolean isAdminUser(User user) {
        if (user == null || user.getRoles() == null) return false;
        for (Role r : user.getRoles()) {
            if (r == null || r.getName() == null) continue;
            String roleName = r.getName().toUpperCase(Locale.ROOT);
            if (roleName.contains("ADMIN") || RoleType.ADMIN.name().equals(roleName) || RoleType.SUPER_ADMIN.name().equals(roleName)) {
                return true;
            }
        }
        return false;
    }

    private LeadActionEnforcementDTO unconstrained(UUID leadId) {
        return LeadActionEnforcementDTO.builder()
                .leadId(leadId)
                .restricted(false)
                .reason(null)
                .allowedActions(Collections.emptyList())
                .message(null)
                .build();
    }

    private LeadActionEnforcementDTO restricted(UUID leadId, String reason, String message) {
        return LeadActionEnforcementDTO.builder()
                .leadId(leadId)
                .restricted(true)
                .reason(reason)
                .allowedActions(new ArrayList<>(DEFAULT_ALLOWED_ACTIONS))
                .message(message)
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
}
