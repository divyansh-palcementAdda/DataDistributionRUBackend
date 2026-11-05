package com.app.datadistribution.notification;

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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.app.datadistribution.entity.User;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.notification.dto.NotificationCreateRequest;
import com.app.datadistribution.notification.dto.NotificationPageResponseDTO;
import com.app.datadistribution.notification.dto.NotificationResponseDTO;
import com.app.datadistribution.notification.entity.Notification;
import com.app.datadistribution.notification.enums.NotificationPriority;
import com.app.datadistribution.notification.enums.NotificationType;
import com.app.datadistribution.notification.repository.NotificationRepository;
import com.app.datadistribution.notification.service.INotificationPreferenceService;
import com.app.datadistribution.notification.service.INotificationRealtimeService;
import com.app.datadistribution.notification.service.impl.NotificationServiceImpl;
import com.app.datadistribution.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private INotificationPreferenceService preferenceService;

    @Mock
    private INotificationRealtimeService realtimeService;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    private User recipient;

    @BeforeEach
    void setUp() {
        recipient = User.builder()
                .firstName("Rohan")
                .lastName("Verma")
                .email("rohan@renaissance.ac.in")
                .build();
        recipient.setId(UUID.randomUUID());
    }

    @Test
    @DisplayName("Create Notification: Persists notification and pushes via SSE in real time")
    void shouldCreateNotificationAndPushRealtime() {
        NotificationCreateRequest request = NotificationCreateRequest.builder()
                .recipientId(recipient.getId())
                .title("New Lead Assigned")
                .message("Lead John Doe assigned")
                .type(NotificationType.NEW_LEAD_ASSIGNED)
                .priority(NotificationPriority.NORMAL)
                .idempotencyKey("KEY-12345")
                .build();

        when(notificationRepository.existsByIdempotencyKey("KEY-12345")).thenReturn(false);
        when(preferenceService.isNotificationEnabled(recipient.getId(), NotificationType.NEW_LEAD_ASSIGNED)).thenReturn(true);
        when(userRepository.findById(recipient.getId())).thenReturn(Optional.of(recipient));

        Notification saved = Notification.builder()
                .recipient(recipient)
                .title(request.getTitle())
                .message(request.getMessage())
                .type(request.getType())
                .priority(request.getPriority())
                .idempotencyKey(request.getIdempotencyKey())
                .read(false)
                .build();
        saved.setId(UUID.randomUUID());

        when(notificationRepository.save(any(Notification.class))).thenReturn(saved);

        Notification result = notificationService.createNotification(request);

        assertNotNull(result);
        assertEquals("New Lead Assigned", result.getTitle());
        verify(notificationRepository).save(any(Notification.class));
        verify(realtimeService).pushNotification(eq(recipient.getId()), any(NotificationResponseDTO.class));
    }

    @Test
    @DisplayName("Idempotency: Rejects duplicate notifications when idempotency key exists")
    void shouldRejectDuplicateNotification() {
        NotificationCreateRequest request = NotificationCreateRequest.builder()
                .recipientId(recipient.getId())
                .title("Duplicate")
                .message("Test")
                .idempotencyKey("DUPE-KEY")
                .build();

        when(notificationRepository.existsByIdempotencyKey("DUPE-KEY")).thenReturn(true);

        Notification result = notificationService.createNotification(request);

        assertNull(result);
        verify(notificationRepository, never()).save(any());
        verify(realtimeService, never()).pushNotification(any(), any());
    }

    @Test
    @DisplayName("Preferences: Skips notification when user preference has disabled the type")
    void shouldSkipNotificationWhenDisabledInUserPreferences() {
        NotificationCreateRequest request = NotificationCreateRequest.builder()
                .recipientId(recipient.getId())
                .title("Follow-up")
                .message("Due in 5 mins")
                .type(NotificationType.FOLLOW_UP_REMINDER)
                .build();

        when(preferenceService.isNotificationEnabled(recipient.getId(), NotificationType.FOLLOW_UP_REMINDER)).thenReturn(false);

        Notification result = notificationService.createNotification(request);

        assertNull(result);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Mark As Read: Updates read status and read timestamp")
    void shouldMarkNotificationAsRead() {
        UUID notifId = UUID.randomUUID();
        Notification notif = Notification.builder()
                .recipient(recipient)
                .title("Test")
                .message("Test message")
                .read(false)
                .build();
        notif.setId(notifId);

        when(notificationRepository.findByIdAndRecipientIdAndIsDeletedFalse(notifId, recipient.getId()))
                .thenReturn(Optional.of(notif));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NotificationResponseDTO dto = notificationService.markAsRead(notifId, recipient.getId());

        assertNotNull(dto);
        assertTrue(dto.isRead());
        assertNotNull(dto.getReadAt());
    }

    @Test
    @DisplayName("User Isolation: Rejects access when trying to mark another user's notification")
    void shouldThrowExceptionWhenMarkingOtherUsersNotification() {
        UUID notifId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        when(notificationRepository.findByIdAndRecipientIdAndIsDeletedFalse(notifId, otherUserId))
                .thenReturn(Optional.empty());

        assertThrows(ResourcesNotFoundException.class, () -> notificationService.markAsRead(notifId, otherUserId));
    }

    @Test
    @DisplayName("Pagination: Retrieves paginated notifications and accurate unread count")
    void shouldReturnPaginatedNotifications() {
        Pageable pageable = PageRequest.of(0, 10);
        Notification notif = Notification.builder()
                .recipient(recipient)
                .title("Test Title")
                .message("Test Message")
                .read(false)
                .build();
        notif.setId(UUID.randomUUID());

        Page<Notification> page = new PageImpl<>(List.of(notif), pageable, 1);
        when(notificationRepository.findByRecipientIdAndIsDeletedFalse(recipient.getId(), pageable)).thenReturn(page);
        when(notificationRepository.countUnreadByRecipientId(recipient.getId())).thenReturn(1L);

        NotificationPageResponseDTO response = notificationService.getUserNotifications(recipient.getId(), false, pageable);

        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        assertEquals(1L, response.getUnreadCount());
        assertEquals(0, response.getPage());
    }
}

