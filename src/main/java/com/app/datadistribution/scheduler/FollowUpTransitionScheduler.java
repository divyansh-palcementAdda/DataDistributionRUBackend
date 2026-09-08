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
 * Scheduled job that automatically transitions UPCOMING follow-ups
 * whose scheduled date has arrived (today or earlier) to PENDING.
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
    public void transitionUpcomingFollowUpsToPending() {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        LocalDateTime endOfDay = today.atTime(LocalTime.MAX);

        try {
            int updatedCount = leadFollowUpRepository.transitionUpcomingToPendingForDate(endOfDay);
            if (updatedCount > 0) {
                log.info("Transitioned {} UPCOMING follow-up(s) to PENDING for date <= {}", updatedCount, today);
            }
        } catch (Exception e) {
            log.error("Failed to automatically transition UPCOMING follow-ups to PENDING", e);
        }
    }
}
