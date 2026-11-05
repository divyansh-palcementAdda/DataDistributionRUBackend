package com.app.datadistribution.notification.listener;

import java.util.UUID;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.app.datadistribution.entity.LeadFollowUp;
import com.app.datadistribution.event.FollowUpCancelledEvent;
import com.app.datadistribution.event.FollowUpCompletedEvent;
import com.app.datadistribution.event.FollowUpRescheduledEvent;
import com.app.datadistribution.event.FollowUpScheduledEvent;
import com.app.datadistribution.event.LeadAllocatedEvent;
import com.app.datadistribution.event.LeadReassignedEvent;
import com.app.datadistribution.notification.dto.NotificationCreateRequest;
import com.app.datadistribution.notification.enums.NotificationPriority;
import com.app.datadistribution.notification.enums.NotificationType;
import com.app.datadistribution.notification.service.IFollowUpReminderService;
import com.app.datadistribution.notification.service.INotificationService;
import com.app.datadistribution.repository.LeadFollowUpRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final INotificationService notificationService;
    private final IFollowUpReminderService followUpReminderService;
    private final LeadFollowUpRepository followUpRepository;

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFollowUpScheduled(FollowUpScheduledEvent event) {
        log.info("NotificationEventListener: processing FollowUpScheduledEvent for follow-up {}", event.getFollowUpId());
        try {
            if (event.getFollowUpId() == null) return;
            LeadFollowUp followUp = followUpRepository.findById(event.getFollowUpId()).orElse(null);
            if (followUp != null) {
                followUpReminderService.scheduleRemindersForFollowUp(followUp);
            }
        } catch (Exception e) {
            log.error("Failed to schedule reminders for FollowUpScheduledEvent {}: {}", event.getFollowUpId(), e.getMessage(), e);
        }
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFollowUpRescheduled(FollowUpRescheduledEvent event) {
        log.info("NotificationEventListener: processing FollowUpRescheduledEvent for follow-up {}", event.getFollowUpId());
        try {
            if (event.getFollowUpId() == null) return;
            LeadFollowUp followUp = followUpRepository.findById(event.getFollowUpId()).orElse(null);
            if (followUp != null) {
                followUpReminderService.rescheduleReminders(followUp);
            }
        } catch (Exception e) {
            log.error("Failed to reschedule reminders for FollowUpRescheduledEvent {}: {}", event.getFollowUpId(), e.getMessage(), e);
        }
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFollowUpCompleted(FollowUpCompletedEvent event) {
        log.info("NotificationEventListener: processing FollowUpCompletedEvent for follow-up {}", event.getFollowUpId());
        try {
            if (event.getFollowUpId() == null) return;
            followUpReminderService.cancelReminders(event.getFollowUpId());
        } catch (Exception e) {
            log.error("Failed to cancel reminders for FollowUpCompletedEvent {}: {}", event.getFollowUpId(), e.getMessage(), e);
        }
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFollowUpCancelled(FollowUpCancelledEvent event) {
        log.info("NotificationEventListener: processing FollowUpCancelledEvent for follow-up {}", event.getFollowUpId());
        try {
            if (event.getFollowUpId() == null) return;
            followUpReminderService.cancelReminders(event.getFollowUpId());
        } catch (Exception e) {
            log.error("Failed to cancel reminders for FollowUpCancelledEvent {}: {}", event.getFollowUpId(), e.getMessage(), e);
        }
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLeadAllocated(LeadAllocatedEvent event) {
        log.info("NotificationEventListener: processing LeadAllocatedEvent for user {}", event.getTargetUserId());
        try {
            if (event.getTargetUserId() == null || event.getAllocatedCount() <= 0) return;

            String title = event.getAllocatedCount() == 1 ? "New Lead Assigned" : "New Leads Allocated";
            String message = event.getAllocatedCount() == 1
                    ? "A new lead has been assigned to you. Open Lead Management to review it."
                    : event.getAllocatedCount() + " new leads have been allocated to you in " + (event.getDepartmentName() != null ? event.getDepartmentName() : "your department") + ".";

            String batch = event.getBatchId() != null ? event.getBatchId() : UUID.randomUUID().toString();
            String idempotencyKey = "INAPP_LEAD_ALLOC:" + batch + ":" + event.getTargetUserId();

            NotificationCreateRequest request = NotificationCreateRequest.builder()
                    .recipientId(event.getTargetUserId())
                    .title(title)
                    .message(message)
                    .type(NotificationType.NEW_LEAD_ASSIGNED)
                    .priority(NotificationPriority.NORMAL)
                    .relatedEntityType("LEAD")
                    .navigationUrl("/leads")
                    .idempotencyKey(idempotencyKey)
                    .metadata("{\"allocatedCount\":" + event.getAllocatedCount() + "}")
                    .build();

            notificationService.createNotification(request);
        } catch (Exception e) {
            log.error("Failed to create in-app notification for LeadAllocatedEvent: {}", e.getMessage(), e);
        }
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLeadReassigned(LeadReassignedEvent event) {
        log.info("NotificationEventListener: processing LeadReassignedEvent for target user {}", event.getTargetUserId());
        try {
            if (event.getTargetUserId() == null || event.getReassignedCount() <= 0) return;

            // Do not notify if target user is same as source user
            if (event.getSourceUserId() != null && event.getTargetUserId().equals(event.getSourceUserId())) {
                return;
            }

            String source = event.getSourceUserName() != null ? event.getSourceUserName() : "previous owner";
            String title = event.getReassignedCount() == 1 ? "Lead Reassigned to You" : "Leads Reassigned to You";
            String message = (event.getReassignedCount() == 1 ? "A lead has" : event.getReassignedCount() + " leads have")
                    + " been reassigned to you from " + source + ". Open Lead Management to review.";

            String batch = event.getBatchId() != null ? event.getBatchId() : UUID.randomUUID().toString();
            String idempotencyKey = "INAPP_LEAD_REASSIGN:" + batch + ":" + event.getTargetUserId();

            NotificationCreateRequest request = NotificationCreateRequest.builder()
                    .recipientId(event.getTargetUserId())
                    .title(title)
                    .message(message)
                    .type(NotificationType.LEAD_REASSIGNED)
                    .priority(NotificationPriority.NORMAL)
                    .relatedEntityType("LEAD")
                    .navigationUrl("/leads")
                    .idempotencyKey(idempotencyKey)
                    .metadata("{\"reassignedCount\":" + event.getReassignedCount() + "}")
                    .build();

            notificationService.createNotification(request);
        } catch (Exception e) {
            log.error("Failed to create in-app notification for LeadReassignedEvent: {}", e.getMessage(), e);
        }
    }
}

