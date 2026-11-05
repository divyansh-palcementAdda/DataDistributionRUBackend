package com.app.datadistribution.notification;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.app.datadistribution.entity.Role;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.notification.dto.AdminNotificationResultDTO;
import com.app.datadistribution.notification.dto.NotificationCreateRequest;
import com.app.datadistribution.notification.dto.NotificationResponseDTO;
import com.app.datadistribution.notification.dto.SendAdminNotificationRequest;
import com.app.datadistribution.notification.entity.Notification;
import com.app.datadistribution.notification.enums.NotificationPriority;
import com.app.datadistribution.notification.enums.NotificationType;
import com.app.datadistribution.notification.service.INotificationService;
import com.app.datadistribution.notification.service.impl.NotificationAdminServiceImpl;
import com.app.datadistribution.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationAdminServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private INotificationService notificationService;

    @InjectMocks
    private NotificationAdminServiceImpl adminService;

    private User admin;
    private User counselor;

    @BeforeEach
    void setUp() {
        admin = User.builder()
                .firstName("Admin")
                .lastName("User")
                .email("admin@renaissance.ac.in")
                .build();
        admin.setId(UUID.randomUUID());
        admin.setActive(true);

        Role counselorRole = Role.builder().name("COUNSELOR").build();
        counselor = User.builder()
                .firstName("Pooja")
                .lastName("Patel")
                .email("pooja@renaissance.ac.in")
                .roles(Set.of(counselorRole))
                .build();
        counselor.setId(UUID.randomUUID());
        counselor.setActive(true);
    }

    @Test
    @DisplayName("Admin Broadcast: Sends manual notification to ALL active users")
    void shouldSendNotificationToAllActiveUsers() {
        when(userRepository.findAll()).thenReturn(List.of(admin, counselor));
        when(notificationService.createNotification(any(NotificationCreateRequest.class)))
                .thenReturn(Notification.builder().build());

        SendAdminNotificationRequest request = SendAdminNotificationRequest.builder()
                .title("All Hands Meeting")
                .message("Team meeting at 4 PM today")
                .targetType("ALL")
                .type(NotificationType.ADMIN_ANNOUNCEMENT)
                .priority(NotificationPriority.NORMAL)
                .build();

        AdminNotificationResultDTO result = adminService.sendManualNotification(request, admin);

        assertNotNull(result);
        assertEquals(2, result.getTotalTargeted());
        assertEquals(2, result.getSuccessCount());
        verify(notificationService, times(2)).createNotification(any(NotificationCreateRequest.class));
    }

    @Test
    @DisplayName("Admin Broadcast: Sends notification targeted by ROLE")
    void shouldSendNotificationTargetedByRole() {
        when(userRepository.findAll()).thenReturn(List.of(admin, counselor));
        when(notificationService.createNotification(any(NotificationCreateRequest.class)))
                .thenReturn(Notification.builder().build());

        SendAdminNotificationRequest request = SendAdminNotificationRequest.builder()
                .title("Counselor Briefing")
                .message("Updated admission guidelines")
                .targetType("ROLE")
                .roleName("COUNSELOR")
                .build();

        AdminNotificationResultDTO result = adminService.sendManualNotification(request, admin);

        assertNotNull(result);
        assertEquals(1, result.getTotalTargeted());
        assertEquals(1, result.getSuccessCount());
        verify(notificationService, times(1)).createNotification(any(NotificationCreateRequest.class));
    }

    @Test
    @DisplayName("Admin Test Notification: Dispatches test alert to admin's own account")
    void shouldSendTestNotificationToAdmin() {
        Notification testNotif = Notification.builder()
                .recipient(admin)
                .title("Test Notification")
                .type(NotificationType.TEST)
                .build();
        testNotif.setId(UUID.randomUUID());

        when(notificationService.createNotification(any(NotificationCreateRequest.class))).thenReturn(testNotif);
        when(notificationService.toDto(testNotif)).thenReturn(NotificationResponseDTO.builder()
                .id(testNotif.getId())
                .title("Test Notification")
                .type(NotificationType.TEST)
                .build());

        NotificationResponseDTO response = adminService.sendTestNotification(admin);

        assertNotNull(response);
        assertEquals("Test Notification", response.getTitle());
        assertEquals(NotificationType.TEST, response.getType());
        verify(notificationService).createNotification(any(NotificationCreateRequest.class));
    }
}

