package com.app.datadistribution.notification.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.app.datadistribution.notification.service.IFollowUpReminderService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class FollowUpReminderScheduler {

    private final IFollowUpReminderService followUpReminderService;

    /**
     * Executes every 30 seconds to dispatch due follow-up reminders (5-minute and 2-minute).
     */
    @Scheduled(fixedDelayString = "${app.notification.reminder-check-interval-ms:30000}", initialDelay = 10000)
    public void runFollowUpReminderJob() {
        try {
            followUpReminderService.dispatchDueReminders();
        } catch (Exception e) {
            log.error("Exception during FollowUpReminderScheduler execution: {}", e.getMessage(), e);
        }
    }
}

