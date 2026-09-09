package com.app.datadistribution.service.engine;

import com.app.datadistribution.dto.lead.UnassignedLeadDTO;
import com.app.datadistribution.dto.lead.UserDistributionSummaryDTO;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.User;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Shared Engine for Lead Distribution Allocation Algorithm.
 * Implements strict workload capacity limits (Max 30 Follow-ups, Max 40 RAW Leads)
 * and fair Round-Robin distribution across all eligible users.
 */
@Slf4j
@Component
public class LeadDistributionEngine {

    public static final int DEFAULT_MAX_DAILY_FOLLOWUPS = 30;
    public static final int DEFAULT_MAX_RAW_LEADS = 40;

    @Getter
    @Builder
    public static class EnginePlanResult {
        private final List<UserDistributionSummaryDTO> userSummaries;
        private final List<UnassignedLeadDTO> unassignedLeads;
        private final Map<UUID, List<Lead>> userAssignedLeadsMap;
        private final int totalAssigned;
        private final int totalUnassigned;
    }

    @Getter
    public static class UserCapacityContext {
        private final User user;
        private final int todayFollowupsCount;
        private final int currentRawLeadsCount;
        private final int followupCapacity;
        private final int rawCapacity;
        private final int initialTotalCapacity;
        private int remainingCapacity;
        private int assignedCount;
        private final List<UUID> assignedLeadIds;
        private final List<Lead> assignedLeads;
        private final String status;
        private final String reason;

        public UserCapacityContext(
                User user,
                int todayFollowupsCount,
                int currentRawLeadsCount,
                int maxFollowups,
                int maxRawLeads) {
            this.user = user;
            this.todayFollowupsCount = todayFollowupsCount;
            this.currentRawLeadsCount = currentRawLeadsCount;
            this.followupCapacity = Math.max(0, maxFollowups - todayFollowupsCount);
            this.rawCapacity = Math.max(0, maxRawLeads - currentRawLeadsCount);
            this.initialTotalCapacity = Math.min(this.followupCapacity, this.rawCapacity);
            this.remainingCapacity = this.initialTotalCapacity;
            this.assignedCount = 0;
            this.assignedLeadIds = new ArrayList<>();
            this.assignedLeads = new ArrayList<>();

            if (this.initialTotalCapacity > 0) {
                this.status = "ELIGIBLE";
                this.reason = "Capacity available (Followup cap: " + followupCapacity + ", RAW cap: " + rawCapacity + ")";
            } else {
                if (this.followupCapacity == 0 && this.rawCapacity == 0) {
                    this.status = "EXCEEDED_BOTH_LIMITS";
                    this.reason = "Reached both daily follow-up limit (" + todayFollowupsCount + "/" + maxFollowups + ") and RAW limit (" + currentRawLeadsCount + "/" + maxRawLeads + ")";
                } else if (this.followupCapacity == 0) {
                    this.status = "EXCEEDED_FOLLOWUP_LIMIT";
                    this.reason = "Reached daily follow-up limit (" + todayFollowupsCount + "/" + maxFollowups + ")";
                } else {
                    this.status = "EXCEEDED_RAW_LIMIT";
                    this.reason = "Reached current RAW lead limit (" + currentRawLeadsCount + "/" + maxRawLeads + ")";
                }
            }
        }

        public boolean hasCapacity() {
            return remainingCapacity > 0;
        }

        public void allocateLead(Lead lead) {
            if (remainingCapacity <= 0) {
                throw new IllegalStateException("User " + user.getId() + " has no remaining capacity.");
            }
            this.remainingCapacity--;
            this.assignedCount++;
            this.assignedLeadIds.add(lead.getId());
            this.assignedLeads.add(lead);
        }

        public UserDistributionSummaryDTO toSummaryDTO() {
            return UserDistributionSummaryDTO.builder()
                    .userId(user.getId())
                    .userName(user.getFirstName() != null ? user.getFirstName() : user.getUsername())
                    .todayFollowUpCount(todayFollowupsCount)
                    .currentRawCount(currentRawLeadsCount)
                    .followupCapacity(followupCapacity)
                    .rawCapacity(rawCapacity)
                    .finalCapacity(initialTotalCapacity)
                    .assignedCount(assignedCount)
                    .assignedLeadIds(new ArrayList<>(assignedLeadIds))
                    .status(status)
                    .reason(reason)
                    .build();
        }
    }

    /**
     * Executes allocation calculation deterministically across candidate leads and target users.
     */
    public EnginePlanResult planDistribution(
            List<Lead> candidateLeads,
            List<User> targetUsers,
            Map<UUID, Long> todayFollowUpsMap,
            Map<UUID, Long> rawLeadsMap,
            Integer maximumNumber) {

        if (candidateLeads == null || candidateLeads.isEmpty()) {
            return EnginePlanResult.builder()
                    .userSummaries(buildEmptyUserSummaries(targetUsers, todayFollowUpsMap, rawLeadsMap))
                    .unassignedLeads(Collections.emptyList())
                    .userAssignedLeadsMap(Collections.emptyMap())
                    .totalAssigned(0)
                    .totalUnassigned(0)
                    .build();
        }

        // Limit candidates if maximumNumber is specified
        List<Lead> leadsToDistribute = candidateLeads;
        List<Lead> skippedDueToMaxLimit = Collections.emptyList();
        if (maximumNumber != null && maximumNumber > 0 && maximumNumber < candidateLeads.size()) {
            leadsToDistribute = candidateLeads.subList(0, maximumNumber);
            skippedDueToMaxLimit = candidateLeads.subList(maximumNumber, candidateLeads.size());
        }

        // Build User Capacity Contexts
        List<UserCapacityContext> contexts = new ArrayList<>();
        for (User user : targetUsers) {
            int followups = todayFollowUpsMap.getOrDefault(user.getId(), 0L).intValue();
            int rawLeads = rawLeadsMap.getOrDefault(user.getId(), 0L).intValue();
            contexts.add(new UserCapacityContext(
                    user,
                    followups,
                    rawLeads,
                    DEFAULT_MAX_DAILY_FOLLOWUPS,
                    DEFAULT_MAX_RAW_LEADS));
        }

        List<UnassignedLeadDTO> unassignedLeads = new ArrayList<>();
        Map<UUID, List<Lead>> userAssignedLeadsMap = new LinkedHashMap<>();
        for (User user : targetUsers) {
            userAssignedLeadsMap.put(user.getId(), new ArrayList<>());
        }

        // Filter eligible users
        List<UserCapacityContext> eligibleUsers = contexts.stream()
                .filter(UserCapacityContext::hasCapacity)
                .toList();

        if (eligibleUsers.isEmpty()) {
            log.warn("No eligible users with capacity found for lead distribution.");
            for (Lead lead : leadsToDistribute) {
                unassignedLeads.add(UnassignedLeadDTO.builder()
                        .leadId(lead.getId())
                        .leadCode(lead.getLeadCode())
                        .leadFullName(lead.getFullName())
                        .reason("No target users have available capacity (Daily Follow-ups >= 30 or RAW Leads >= 40)")
                        .build());
            }
        } else {
            // Round-robin index across eligible users
            int userIndex = 0;
            int totalEligible = eligibleUsers.size();

            for (Lead lead : leadsToDistribute) {
                // Find next user with capacity
                UserCapacityContext assignedUser = null;
                int attempts = 0;

                while (attempts < totalEligible) {
                    UserCapacityContext candidate = eligibleUsers.get(userIndex % totalEligible);
                    userIndex++;
                    attempts++;

                    if (candidate.hasCapacity()) {
                        // Check if candidate is current owner; if other users with capacity exist, skip candidate to prefer real reassignment
                        UUID currentOwnerId = lead.getAssignedTo() != null ? lead.getAssignedTo().getId() : null;
                        if (currentOwnerId != null && Objects.equals(currentOwnerId, candidate.getUser().getId())) {
                            boolean anyOtherHasCapacity = eligibleUsers.stream()
                                    .anyMatch(u -> !Objects.equals(u.getUser().getId(), currentOwnerId) && u.hasCapacity());
                            if (anyOtherHasCapacity) {
                                continue; // Skip to next candidate in round-robin
                            }
                        }

                        assignedUser = candidate;
                        break;
                    }
                }

                if (assignedUser != null) {
                    assignedUser.allocateLead(lead);
                    userAssignedLeadsMap.get(assignedUser.getUser().getId()).add(lead);
                } else {
                    // All users are at maximum capacity
                    unassignedLeads.add(UnassignedLeadDTO.builder()
                            .leadId(lead.getId())
                            .leadCode(lead.getLeadCode())
                            .leadFullName(lead.getFullName())
                            .reason("All eligible users have reached their maximum capacity limits")
                            .build());
                }
            }
        }

        // Also record leads skipped because of maximumNumber
        for (Lead lead : skippedDueToMaxLimit) {
            unassignedLeads.add(UnassignedLeadDTO.builder()
                    .leadId(lead.getId())
                    .leadCode(lead.getLeadCode())
                    .leadFullName(lead.getFullName())
                    .reason("Skipped due to requested maximum number limit (" + maximumNumber + ")")
                    .build());
        }

        List<UserDistributionSummaryDTO> summaries = contexts.stream()
                .map(UserCapacityContext::toSummaryDTO)
                .toList();

        int totalAssigned = summaries.stream().mapToInt(UserDistributionSummaryDTO::getAssignedCount).sum();
        int totalUnassigned = unassignedLeads.size();

        return EnginePlanResult.builder()
                .userSummaries(summaries)
                .unassignedLeads(unassignedLeads)
                .userAssignedLeadsMap(userAssignedLeadsMap)
                .totalAssigned(totalAssigned)
                .totalUnassigned(totalUnassigned)
                .build();
    }

    private List<UserDistributionSummaryDTO> buildEmptyUserSummaries(
            List<User> targetUsers,
            Map<UUID, Long> todayFollowUpsMap,
            Map<UUID, Long> rawLeadsMap) {
        List<UserDistributionSummaryDTO> summaries = new ArrayList<>();
        if (targetUsers == null) return summaries;
        for (User user : targetUsers) {
            int followups = todayFollowUpsMap != null ? todayFollowUpsMap.getOrDefault(user.getId(), 0L).intValue() : 0;
            int rawLeads = rawLeadsMap != null ? rawLeadsMap.getOrDefault(user.getId(), 0L).intValue() : 0;
            UserCapacityContext ctx = new UserCapacityContext(
                    user,
                    followups,
                    rawLeads,
                    DEFAULT_MAX_DAILY_FOLLOWUPS,
                    DEFAULT_MAX_RAW_LEADS);
            summaries.add(ctx.toSummaryDTO());
        }
        return summaries;
    }
}
