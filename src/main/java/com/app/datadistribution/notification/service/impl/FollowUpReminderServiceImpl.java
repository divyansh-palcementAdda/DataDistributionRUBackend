package com.app.datadistribution.notification.service.impl;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadFollowUp;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.notification.dto.NotificationCreateRequest;
import com.app.datadistribution.notification.entity.FollowUpReminder;
import com.app.datadistribution.notification.enums.NotificationPriority;
import com.app.datadistribution.notification.enums.NotificationType;
import com.app.datadistribution.notification.enums.ReminderStatus;
import com.app.datadistribution.notification.enums.ReminderType;
import com.app.datadistribution.notification.repository.FollowUpReminderRepository;
import com.app.datadistribution.notification.service.IFollowUpReminderService;
import com.app.datadistribution.notification.service.INotificationService;
import com.app.datadistribution.repository.LeadFollowUpRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class FollowUpReminderServiceImpl implements IFollowUpReminderService {

    private final FollowUpReminderRepository reminderRepository;
    private final LeadFollowUpRepository followUpRepository;
    private final INotificationService notificationService;

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("hh:mm a");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");
    private static final String INSTANCE_ID = "NODE-" + UUID.randomUUID().toString().substring(0, 8);

    @Override
    @Transactional
    public void scheduleRemindersForFollowUp(LeadFollowUp followUp) {
        if (followUp == null || followUp.getId() == null || followUp.getFollowUpDate() == null) {
            return;
        }

        // Only schedule if follow-up is not completed and not cancelled
        if (followUp.isCompleted() || followUp.getCancelledAt() != null) {
            return;
        }

        User assignedUser = followUp.getAssignedTo() != null ? followUp.getAssignedTo()
                : (followUp.getLead() != null ? followUp.getLead().getAssignedTo() : null);

        if (assignedUser == null || assignedUser.getId() == null) {
            log.warn("Cannot schedule reminders for follow-up {}: assigned user is null", followUp.getId());
            return;
        }

        UUID leadId = followUp.getLead() != null ? followUp.getLead().getId() : null;
        if (leadId == null) {
            return;
        }

        LocalDateTime dueTime = followUp.getFollowUpDate();
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);

        // 1. 5-Minute Reminder
        LocalDateTime fiveMinScheduled = dueTime.minusMinutes(5);
        if (fiveMinScheduled.isAfter(now)) {
            persistReminderIfNotExists(followUp.getId(), leadId, assignedUser.getId(), ReminderType.FIVE_MIN, fiveMinScheduled);
        } else {
            log.debug("Skipping 5-min reminder for follow-up {}: scheduled time is already past", followUp.getId());
        }

        // 2. 2-Minute Reminder
        LocalDateTime twoMinScheduled = dueTime.minusMinutes(2);
        if (twoMinScheduled.isAfter(now)) {
            persistReminderIfNotExists(followUp.getId(), leadId, assignedUser.getId(), ReminderType.TWO_MIN, twoMinScheduled);
        } else {
            log.debug("Skipping 2-min reminder for follow-up {}: scheduled time is already past", followUp.getId());
        }
    }

    @Override
    @Transactional
    public void rescheduleReminders(LeadFollowUp followUp) {
        if (followUp == null || followUp.getId() == null) {
            return;
        }

        // 1. Invalidate/cancel existing pending reminders
        int cancelled = reminderRepository.cancelPendingRemindersForFollowUp(followUp.getId());
        log.info("Cancelled {} old pending reminder(s) for rescheduled follow-up {}", cancelled, followUp.getId());

        // 2. Schedule fresh reminders for the new follow-up date
        scheduleRemindersForFollowUp(followUp);
    }

    @Override
    @Transactional
    public void cancelReminders(UUID followUpId) {
        if (followUpId == null) {
            return;
        }

        int cancelled = reminderRepository.cancelPendingRemindersForFollowUp(followUpId);
        log.info("Cancelled {} pending reminder(s) for follow-up {}", cancelled, followUpId);
    }

    @Override
    @Transactional
    public void reassignReminders(LeadFollowUp followUp, UUID newUserId) {
        if (followUp == null || followUp.getId() == null || newUserId == null) {
            return;
        }

        int reassigned = reminderRepository.reassignPendingReminders(followUp.getId(), newUserId);
        log.info("Reassigned {} pending reminder(s) for follow-up {} to new user {}", reassigned, followUp.getId(), newUserId);
    }

    @Override
    @Transactional
    public void dispatchDueReminders() {
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);

        // 1. Atomic claiming of due pending reminders for cluster safety
        int claimedCount = reminderRepository.claimDueReminders(now, now, INSTANCE_ID);
        if (claimedCount == 0) {
            return;
        }

        log.info("Claimed {} due reminder(s) for dispatch by {}", claimedCount, INSTANCE_ID);

        // 2. Retrieve claimed reminders
        List<FollowUpReminder> reminders = reminderRepository.findByStatusAndClaimedBy(ReminderStatus.DISPATCHING, INSTANCE_ID);

        for (FollowUpReminder reminder : reminders) {
            try {
                processSingleReminder(reminder, now);
            } catch (Exception e) {
                log.error("Error processing reminder {}: {}", reminder.getId(), e.getMessage(), e);
                reminder.setAttemptCount(reminder.getAttemptCount() + 1);
                reminder.setErrorMessage(e.getMessage());
                if (reminder.getAttemptCount() >= 3) {
                    reminder.setStatus(ReminderStatus.FAILED);
                } else {
                    reminder.setStatus(ReminderStatus.PENDING); // retry next tick
                }
                reminderRepository.save(reminder);
            }
        }
    }

    private void processSingleReminder(FollowUpReminder reminder, LocalDateTime now) {
        // Fetch current follow-up state
        LeadFollowUp followUp = followUpRepository.findById(reminder.getFollowUpId()).orElse(null);

        if (followUp == null || followUp.isDeleted() || followUp.isCompleted() || followUp.getCancelledAt() != null) {
            reminder.setStatus(ReminderStatus.CANCELLED);
            reminder.setErrorMessage("Follow-up no longer active or was deleted/completed");
            reminderRepository.save(reminder);
            return;
        }

        Lead lead = followUp.getLead();
        if (lead == null || lead.isDeleted()) {
            reminder.setStatus(ReminderStatus.CANCELLED);
            reminder.setErrorMessage("Lead was deleted");
            reminderRepository.save(reminder);
            return;
        }

        // If reminder is more than 15 minutes overdue (e.g. server downtime), expire it to avoid spamming
        if (reminder.getScheduledFor().plusMinutes(15).isBefore(now)) {
            reminder.setStatus(ReminderStatus.EXPIRED);
            reminder.setErrorMessage("Reminder expired due to system delay");
            reminderRepository.save(reminder);
            return;
        }

        // Dynamically reflect reassignment if follow-up assignee changed
        User currentAssignee = followUp.getAssignedTo() != null ? followUp.getAssignedTo()
                : (lead != null ? lead.getAssignedTo() : null);
        if (currentAssignee != null && currentAssignee.getId() != null && !currentAssignee.getId().equals(reminder.getRecipientUserId())) {
            reminder.setRecipientUserId(currentAssignee.getId());
        }

        // Build notification content
        String leadName = lead.getFullName() != null && !lead.getFullName().isBlank() ? lead.getFullName() : "Lead";
        String leadCode = lead.getLeadCode() != null ? " (" + lead.getLeadCode() + ")" : "";
        String scheduledTimeStr = followUp.getFollowUpDate().format(TIME_FORMATTER);

        String title;
        String message;
        if (reminder.getReminderType() == ReminderType.FIVE_MIN) {
            title = "Follow-up in 5 Minutes";
            message = "Your follow-up with " + leadName + leadCode + " is scheduled at " + scheduledTimeStr + " (in 5 minutes).";
        } else {
            title = "Follow-up in 2 Minutes";
            message = "Reminder: Your follow-up with " + leadName + leadCode + " is scheduled at " + scheduledTimeStr + " (in 2 minutes).";
        }

        String navigationUrl = "/leads/" + lead.getId();
        String notifIdempotency = "NOTIF_REMINDER:" + reminder.getFollowUpId() + ":" + reminder.getReminderType();

        NotificationCreateRequest notifRequest = NotificationCreateRequest.builder()
                .recipientId(reminder.getRecipientUserId())
                .title(title)
                .message(message)
                .type(NotificationType.FOLLOW_UP_REMINDER)
                .priority(reminder.getReminderType() == ReminderType.TWO_MIN ? NotificationPriority.HIGH : NotificationPriority.NORMAL)
                .relatedEntityType("FOLLOW_UP")
                .relatedEntityId(reminder.getFollowUpId().toString())
                .navigationUrl(navigationUrl)
                .idempotencyKey(notifIdempotency)
                .expiresAt(followUp.getFollowUpDate().plusHours(2))
                .build();

        notificationService.createNotification(notifRequest);

        reminder.setStatus(ReminderStatus.SENT);
        reminder.setDispatchedAt(now);
        reminderRepository.save(reminder);
        log.info("Dispatched {} reminder for follow-up {} to user {}", reminder.getReminderType(), reminder.getFollowUpId(), reminder.getRecipientUserId());
    }

    private void persistReminderIfNotExists(UUID followUpId, UUID leadId, UUID recipientUserId, ReminderType type, LocalDateTime scheduledFor) {
        String idempotencyKey = "FUR:" + followUpId + ":" + type;
        if (reminderRepository.existsByIdempotencyKey(idempotencyKey)) {
            return;
        }

        FollowUpReminder reminder = FollowUpReminder.builder()
                .followUpId(followUpId)
                .leadId(leadId)
                .recipientUserId(recipientUserId)
                .reminderType(type)
                .scheduledFor(scheduledFor)
                .status(ReminderStatus.PENDING)
                .attemptCount(0)
                .idempotencyKey(idempotencyKey)
                .build();

        reminderRepository.save(reminder);
        log.info("Scheduled {} follow-up reminder for follow-up {} at {}", type, followUpId, scheduledFor);
    }
}
