package com.app.datadistribution.notification;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.app.datadistribution.entity.LeadFollowUp;
import com.app.datadistribution.event.FollowUpCancelledEvent;
import com.app.datadistribution.event.FollowUpCompletedEvent;
import com.app.datadistribution.event.FollowUpRescheduledEvent;
import com.app.datadistribution.event.FollowUpScheduledEvent;
import com.app.datadistribution.event.LeadAllocatedEvent;
import com.app.datadistribution.event.LeadReassignedEvent;
import com.app.datadistribution.notification.dto.NotificationCreateRequest;
import com.app.datadistribution.notification.enums.NotificationType;
import com.app.datadistribution.notification.listener.NotificationEventListener;
import com.app.datadistribution.notification.service.IFollowUpReminderService;
import com.app.datadistribution.notification.service.INotificationService;
import com.app.datadistribution.repository.LeadFollowUpRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock
    private INotificationService notificationService;

    @Mock
    private IFollowUpReminderService followUpReminderService;

    @Mock
    private LeadFollowUpRepository followUpRepository;

    @InjectMocks
    private NotificationEventListener eventListener;

    private UUID userId;
    private UUID followUpId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        followUpId = UUID.randomUUID();
    }

    @Test
    @DisplayName("LeadAllocatedEvent: Creates in-app notification for allocated user")
    void shouldCreateNotificationOnLeadAllocated() {
        LeadAllocatedEvent event = LeadAllocatedEvent.builder()
                .targetUserId(userId)
                .allocatedCount(5)
                .departmentName("School of Engineering")
                .batchId("BATCH-123")
                .build();

        eventListener.onLeadAllocated(event);

        ArgumentCaptor<NotificationCreateRequest> captor = ArgumentCaptor.forClass(NotificationCreateRequest.class);
        verify(notificationService).createNotification(captor.capture());

        NotificationCreateRequest req = captor.getValue();
        assertEquals(userId, req.getRecipientId());
        assertEquals(NotificationType.NEW_LEAD_ASSIGNED, req.getType());
        assertTrue(req.getMessage().contains("5 new leads"));
        assertTrue(req.getMessage().contains("School of Engineering"));
    }

    @Test
    @DisplayName("LeadReassignedEvent: Creates in-app notification with source user info")
    void shouldCreateNotificationOnLeadReassigned() {
        UUID sourceUserId = UUID.randomUUID();
        LeadReassignedEvent event = LeadReassignedEvent.builder()
                .targetUserId(userId)
                .sourceUserId(sourceUserId)
                .sourceUserName("Rahul Sharma")
                .reassignedCount(1)
                .departmentName("Social Science")
                .batchId("BATCH-456")
                .build();

        eventListener.onLeadReassigned(event);

        ArgumentCaptor<NotificationCreateRequest> captor = ArgumentCaptor.forClass(NotificationCreateRequest.class);
        verify(notificationService).createNotification(captor.capture());

        NotificationCreateRequest req = captor.getValue();
        assertEquals(userId, req.getRecipientId());
        assertEquals(NotificationType.LEAD_REASSIGNED, req.getType());
        assertTrue(req.getMessage().contains("Rahul Sharma"));
    }

    @Test
    @DisplayName("LeadReassignedEvent: Skips notification when target user is same as source user")
    void shouldSkipNotificationWhenTargetAndSourceUsersAreIdentical() {
        LeadReassignedEvent event = LeadReassignedEvent.builder()
                .targetUserId(userId)
                .sourceUserId(userId)
                .reassignedCount(2)
                .build();

        eventListener.onLeadReassigned(event);

        verify(notificationService, never()).createNotification(any());
    }

    @Test
    @DisplayName("FollowUpScheduledEvent: Delegates to reminder service")
    void shouldDelegateOnFollowUpScheduled() {
        LeadFollowUp followUp = LeadFollowUp.builder().build();
        followUp.setId(followUpId);

        when(followUpRepository.findById(followUpId)).thenReturn(Optional.of(followUp));

        FollowUpScheduledEvent event = FollowUpScheduledEvent.builder()
                .followUpId(followUpId)
                .build();

        eventListener.onFollowUpScheduled(event);

        verify(followUpReminderService).scheduleRemindersForFollowUp(followUp);
    }

    @Test
    @DisplayName("FollowUpCompletedEvent: Cancels pending reminders")
    void shouldCancelRemindersOnFollowUpCompleted() {
        FollowUpCompletedEvent event = FollowUpCompletedEvent.builder()
                .followUpId(followUpId)
                .build();

        eventListener.onFollowUpCompleted(event);

        verify(followUpReminderService).cancelReminders(followUpId);
    }
}

