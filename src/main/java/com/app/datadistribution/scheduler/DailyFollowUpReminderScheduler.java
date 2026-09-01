package com.app.datadistribution.scheduler;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.app.datadistribution.entity.LeadFollowUp;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.EmailType;
import com.app.datadistribution.repository.LeadFollowUpRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.interfaces.IEmailService;
import com.app.datadistribution.service.interfaces.IEmailTemplateService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class DailyFollowUpReminderScheduler {

    private final LeadFollowUpRepository leadFollowUpRepository;
    private final UserRepository userRepository;
    private final IEmailService emailService;
    private final IEmailTemplateService emailTemplateService;

    @Value("${app.frontend.url:https://dds.areyoureporting.com}")
    private String frontendUrl;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMMM yyyy");
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    /**
     * Executes every morning at 09:30 AM IST.
     */
    @Scheduled(cron = "${app.mail.daily-reminder-cron:0 30 9 * * *}", zone = "${app.mail.daily-reminder-zone:Asia/Kolkata}")
    @Transactional(readOnly = true)
    public void executeDailyFollowUpReminders() {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        log.info("Starting Daily Follow-up Reminder Job for date: {}", today);

        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.atTime(LocalTime.MAX);

        List<LeadFollowUp> activeFollowUps = leadFollowUpRepository.findActiveFollowUpsForDateRangeWithDetails(startOfDay, endOfDay);
        if (activeFollowUps == null || activeFollowUps.isEmpty()) {
            log.info("No active follow-ups scheduled for today ({}). Daily reminder job finished.", today);
            return;
        }

        // Group follow-ups by assigned counselor
        Map<UUID, List<LeadFollowUp>> followUpsByUser = new HashMap<>();
        for (LeadFollowUp f : activeFollowUps) {
            User counselor = f.getAssignedTo() != null ? f.getAssignedTo() : (f.getLead() != null ? f.getLead().getAssignedTo() : null);
            if (counselor != null && counselor.getId() != null) {
                followUpsByUser.computeIfAbsent(counselor.getId(), k -> new ArrayList<>()).add(f);
            }
        }

        log.info("Found {} total active follow-ups across {} counselors for date {}",
                activeFollowUps.size(), followUpsByUser.size(), today);

        String formattedDate = today.format(DATE_FORMATTER);
        String dashboardUrl = frontendUrl + "/followups";
        int sentCount = 0;

        for (Map.Entry<UUID, List<LeadFollowUp>> entry : followUpsByUser.entrySet()) {
            UUID userId = entry.getKey();
            List<LeadFollowUp> userFollowUps = entry.getValue();

            if (userFollowUps.isEmpty()) {
                continue; // Do not send empty emails
            }

            User counselor = userRepository.findById(userId).filter(u -> !u.isDeleted()).orElse(null);
            if (counselor == null || counselor.getEmail() == null || counselor.getEmail().isBlank()) {
                log.warn("Counselor {} not found or has no email address. Skipping daily reminder.", userId);
                continue;
            }

            String counselorName = counselor.getFirstName() + " " + counselor.getLastName();
            String subject = "Today's Follow-ups – " + formattedDate + " (" + userFollowUps.size() + " leads)";
            String idempotencyKey = "DAILY_REMINDER:" + counselor.getId() + ":" + today;

            String htmlBody = emailTemplateService.buildDailyFollowUpReminderTemplate(
                    counselorName, formattedDate, userFollowUps, dashboardUrl);

            try {
                emailService.sendHtmlEmail(
                        counselor.getEmail(),
                        counselorName,
                        counselor.getId(),
                        subject,
                        htmlBody,
                        EmailType.DAILY_FOLLOWUP_REMINDER,
                        null,
                        idempotencyKey);
                sentCount++;
            } catch (Exception e) {
                log.error("Failed to send daily follow-up reminder to counselor {}: {}", counselor.getUsername(), e.getMessage());
            }
        }

        log.info("Daily Follow-up Reminder Job completed. Dispatched reminders for {} counselors.", sentCount);
    }
}
