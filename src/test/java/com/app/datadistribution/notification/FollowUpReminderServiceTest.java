package com.app.datadistribution.notification;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
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

import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadFollowUp;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.notification.dto.NotificationCreateRequest;
import com.app.datadistribution.notification.entity.FollowUpReminder;
import com.app.datadistribution.notification.enums.NotificationType;
import com.app.datadistribution.notification.enums.ReminderStatus;
import com.app.datadistribution.notification.enums.ReminderType;
import com.app.datadistribution.notification.repository.FollowUpReminderRepository;
import com.app.datadistribution.notification.service.INotificationService;
import com.app.datadistribution.notification.service.impl.FollowUpReminderServiceImpl;
import com.app.datadistribution.repository.LeadFollowUpRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FollowUpReminderServiceTest {

    @Mock
    private FollowUpReminderRepository reminderRepository;

    @Mock
    private LeadFollowUpRepository followUpRepository;

    @Mock
    private INotificationService notificationService;

    @InjectMocks
    private FollowUpReminderServiceImpl reminderService;

    private User counselor;
    private Lead lead;
    private LeadFollowUp followUp;
    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    @BeforeEach
    void setUp() {
        counselor = User.builder()
                .firstName("Aditi")
                .lastName("Sharma")
                .email("aditi@renaissance.ac.in")
                .build();
        counselor.setId(UUID.randomUUID());

        lead = Lead.builder()
                .fullName("Rahul Verma")
                .phoneNumber("9876543210")
                .leadCode("LEAD-TEST-001")
                .assignedTo(counselor)
                .build();
        lead.setId(UUID.randomUUID());

        followUp = LeadFollowUp.builder()
                .lead(lead)
                .assignedTo(counselor)
                .followUpDate(LocalDateTime.now(ZONE).plusMinutes(10))
                .completed(false)
                .build();
        followUp.setId(UUID.randomUUID());
    }

    @Test
    @DisplayName("Schedule Reminders: Creates 5-min and 2-min reminders when follow-up is scheduled 10 mins away")
    void shouldScheduleBothRemindersWhenSufficientTimeAvailable() {
        when(reminderRepository.existsByIdempotencyKey(anyString())).thenReturn(false);

        reminderService.scheduleRemindersForFollowUp(followUp);

        ArgumentCaptor<FollowUpReminder> captor = ArgumentCaptor.forClass(FollowUpReminder.class);
        verify(reminderRepository, times(2)).save(captor.capture());

        List<FollowUpReminder> savedReminders = captor.getAllValues();
        assertEquals(2, savedReminders.size());

        assertTrue(savedReminders.stream().anyMatch(r -> r.getReminderType() == ReminderType.FIVE_MIN));
        assertTrue(savedReminders.stream().anyMatch(r -> r.getReminderType() == ReminderType.TWO_MIN));
        assertEquals(counselor.getId(), savedReminders.get(0).getRecipientUserId());
    }

    @Test
    @DisplayName("Schedule Reminders: Only schedules 2-min reminder when follow-up is 3 minutes away")
    void shouldOnlyScheduleTwoMinReminderWhenLessThanFiveMinutesRemain() {
        followUp.setFollowUpDate(LocalDateTime.now(ZONE).plusMinutes(3));
        when(reminderRepository.existsByIdempotencyKey(anyString())).thenReturn(false);

        reminderService.scheduleRemindersForFollowUp(followUp);

        ArgumentCaptor<FollowUpReminder> captor = ArgumentCaptor.forClass(FollowUpReminder.class);
        verify(reminderRepository, times(1)).save(captor.capture());

        FollowUpReminder saved = captor.getValue();
        assertEquals(ReminderType.TWO_MIN, saved.getReminderType());
    }

    @Test
    @DisplayName("Schedule Reminders: Does not schedule reminders when follow-up is already overdue")
    void shouldNotScheduleRemindersWhenFollowUpIsOverdue() {
        followUp.setFollowUpDate(LocalDateTime.now(ZONE).minusMinutes(5));

        reminderService.scheduleRemindersForFollowUp(followUp);

        verify(reminderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Reschedule Reminders: Cancels old pending reminders and schedules new ones")
    void shouldCancelOldAndScheduleNewWhenRescheduled() {
        when(reminderRepository.cancelPendingRemindersForFollowUp(followUp.getId())).thenReturn(2);
        when(reminderRepository.existsByIdempotencyKey(anyString())).thenReturn(false);

        reminderService.rescheduleReminders(followUp);

        verify(reminderRepository).cancelPendingRemindersForFollowUp(followUp.getId());
        verify(reminderRepository, times(2)).save(any(FollowUpReminder.class));
    }

    @Test
    @DisplayName("Complete Follow-up: Cancels remaining pending reminders")
    void shouldCancelPendingRemindersWhenFollowUpCompleted() {
        reminderService.cancelReminders(followUp.getId());
        verify(reminderRepository).cancelPendingRemindersForFollowUp(followUp.getId());
    }

    @Test
    @DisplayName("Reassign Reminders: Updates recipient on pending reminders")
    void shouldReassignPendingRemindersWhenFollowUpReassigned() {
        UUID newUserId = UUID.randomUUID();
        reminderService.reassignReminders(followUp, newUserId);
        verify(reminderRepository).reassignPendingReminders(followUp.getId(), newUserId);
    }

    @Test
    @DisplayName("Dispatch Due Reminders: Claims due reminders and creates in-app notification")
    void shouldClaimAndDispatchDueReminders() {
        LocalDateTime now = LocalDateTime.now(ZONE);
        FollowUpReminder reminder = FollowUpReminder.builder()
                .followUpId(followUp.getId())
                .leadId(lead.getId())
                .recipientUserId(counselor.getId())
                .reminderType(ReminderType.FIVE_MIN)
                .scheduledFor(now.minusSeconds(10))
                .status(ReminderStatus.DISPATCHING)
                .build();
        reminder.setId(UUID.randomUUID());

        when(reminderRepository.claimDueReminders(any(), any(), anyString())).thenReturn(1);
        when(reminderRepository.findByStatusAndClaimedBy(eq(ReminderStatus.DISPATCHING), anyString()))
                .thenReturn(List.of(reminder));
        when(followUpRepository.findById(followUp.getId())).thenReturn(Optional.of(followUp));

        reminderService.dispatchDueReminders();

        ArgumentCaptor<NotificationCreateRequest> notifCaptor = ArgumentCaptor.forClass(NotificationCreateRequest.class);
        verify(notificationService).createNotification(notifCaptor.capture());

        NotificationCreateRequest createdNotif = notifCaptor.getValue();
        assertEquals(counselor.getId(), createdNotif.getRecipientId());
        assertEquals(NotificationType.FOLLOW_UP_REMINDER, createdNotif.getType());
        assertTrue(createdNotif.getTitle().contains("5 Minutes"));
        assertTrue(createdNotif.getMessage().contains("Rahul Verma"));

        assertEquals(ReminderStatus.SENT, reminder.getStatus());
        assertNotNull(reminder.getDispatchedAt());
    }

    @Test
    @DisplayName("Idempotency: Skips already existing reminder keys")
    void shouldNotDuplicateReminderWhenKeyAlreadyExists() {
        when(reminderRepository.existsByIdempotencyKey(anyString())).thenReturn(true);

        reminderService.scheduleRemindersForFollowUp(followUp);

        verify(reminderRepository, never()).save(any());
    }
}

