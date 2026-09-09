package com.app.datadistribution.scheduler;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.app.datadistribution.repository.LeadFollowUpRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Scheduled job that automatically transitions follow-up statuses based on business date:
 * 1. UPCOMING -> PENDING when scheduled date has arrived (today or earlier).
 * 2. PENDING -> MISSED when scheduled date is in the past (before today's start of day).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FollowUpTransitionScheduler {

    private final LeadFollowUpRepository leadFollowUpRepository;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    /**
     * Executes shortly after midnight every day (00:01 AM IST) and every hour to ensure consistency.
     */
    @Scheduled(cron = "${app.followup.transition-cron:0 1 * * * *}", zone = "Asia/Kolkata")
    @Transactional
    public void transitionFollowUpStatuses() {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.atTime(LocalTime.MAX);

        try {
            int upcomingToPendingCount = leadFollowUpRepository.transitionUpcomingToPendingForDate(endOfDay);
            if (upcomingToPendingCount > 0) {
                log.info("Transitioned {} UPCOMING follow-up(s) to PENDING for date <= {}", upcomingToPendingCount, today);
            }

            int pendingToMissedCount = leadFollowUpRepository.transitionPendingToMissedForDate(startOfDay);
            if (pendingToMissedCount > 0) {
                log.info("Transitioned {} PENDING follow-up(s) to MISSED for date < {}", pendingToMissedCount, today);
            }
        } catch (Exception e) {
            log.error("Failed to automatically transition follow-up statuses", e);
        }
    }
}
