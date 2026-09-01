package com.app.datadistribution.listener;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.app.datadistribution.dto.email.EmailResponse;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadStatus;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.EmailStatus;
import com.app.datadistribution.enums.EmailType;
import com.app.datadistribution.event.FollowUpCancelledEvent;
import com.app.datadistribution.event.FollowUpCompletedEvent;
import com.app.datadistribution.event.FollowUpRescheduledEvent;
import com.app.datadistribution.event.FollowUpScheduledEvent;
import com.app.datadistribution.event.LeadAllocatedEvent;
import com.app.datadistribution.event.LeadReassignedEvent;
import com.app.datadistribution.repository.LeadFollowUpRepository;
import com.app.datadistribution.repository.LeadRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.interfaces.IEmailService;
import com.app.datadistribution.service.interfaces.IEmailTemplateService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailNotificationListenerTest {

    @Mock
    private IEmailService emailService;

    @Mock
    private IEmailTemplateService emailTemplateService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private LeadRepository leadRepository;

    @Mock
    private LeadFollowUpRepository leadFollowUpRepository;

    @InjectMocks
    private EmailNotificationListener listener;

    private User counselor;
    private Lead lead;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(listener, "frontendUrl", "http://localhost:3000");

        counselor = User.builder()
                .firstName("John")
                .lastName("Counselor")
                .email("counselor@example.com")
                .build();
        counselor.setId(UUID.randomUUID());

        lead = Lead.builder()
                .fullName("Alice Student")
                .phoneNumber("9876543210")
                .email("alice@student.com")
                .leadCode("LD-2026-001")
                .currentStatus(LeadStatus.builder().name("FOLLOW_UP").build())
                .build();
        lead.setId(UUID.randomUUID());
    }

    @Test
    void testOnFollowUpScheduled() {
        FollowUpScheduledEvent event = FollowUpScheduledEvent.builder()
                .followUpId(UUID.randomUUID())
                .leadId(lead.getId())
                .assignedUserId(counselor.getId())
                .followUpDate(LocalDateTime.now().plusDays(1))
                .remarks("First callback")
                .followUpStatus("Pending")
                .build();

        when(userRepository.findById(counselor.getId())).thenReturn(Optional.of(counselor));
        when(leadRepository.findById(lead.getId())).thenReturn(Optional.of(lead));
        when(emailTemplateService.buildFollowUpScheduledTemplate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn("<html>Scheduled Body</html>");

        listener.onFollowUpScheduled(event);

        verify(emailService).sendHtmlEmail(
                eq("counselor@example.com"),
                eq("John Counselor"),
                eq(counselor.getId()),
                any(),
                eq("<html>Scheduled Body</html>"),
                eq(EmailType.FOLLOWUP_SCHEDULED),
                eq(event.getFollowUpId()),
                eq("FOLLOWUP_SCHEDULED:" + event.getFollowUpId()));
    }

    @Test
    void testOnFollowUpRescheduled() {
        FollowUpRescheduledEvent event = FollowUpRescheduledEvent.builder()
                .followUpId(UUID.randomUUID())
                .leadId(lead.getId())
                .assignedUserId(counselor.getId())
                .previousFollowUpDate(LocalDateTime.now())
                .newFollowUpDate(LocalDateTime.now().plusDays(2))
                .remarks("Rescheduled on request")
                .followUpStatus("Pending")
                .build();

        when(userRepository.findById(counselor.getId())).thenReturn(Optional.of(counselor));
        when(leadRepository.findById(lead.getId())).thenReturn(Optional.of(lead));
        when(emailTemplateService.buildFollowUpRescheduledTemplate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn("<html>Rescheduled Body</html>");

        listener.onFollowUpRescheduled(event);

        verify(emailService).sendHtmlEmail(
                eq("counselor@example.com"),
                eq("John Counselor"),
                eq(counselor.getId()),
                any(),
                eq("<html>Rescheduled Body</html>"),
                eq(EmailType.FOLLOWUP_RESCHEDULED),
                eq(event.getFollowUpId()),
                any());
    }

    @Test
    void testOnFollowUpCompleted() {
        FollowUpCompletedEvent event = FollowUpCompletedEvent.builder()
                .followUpId(UUID.randomUUID())
                .leadId(lead.getId())
                .assignedUserId(counselor.getId())
                .scheduledDate(LocalDateTime.now().minusHours(1))
                .completedAt(LocalDateTime.now())
                .remarks("Candidate visited campus")
                .finalStatus("COMPLETED")
                .build();

        when(userRepository.findById(counselor.getId())).thenReturn(Optional.of(counselor));
        when(leadRepository.findById(lead.getId())).thenReturn(Optional.of(lead));
        when(emailTemplateService.buildFollowUpCompletedTemplate(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn("<html>Completed Body</html>");

        listener.onFollowUpCompleted(event);

        verify(emailService).sendHtmlEmail(
                eq("counselor@example.com"),
                eq("John Counselor"),
                eq(counselor.getId()),
                any(),
                eq("<html>Completed Body</html>"),
                eq(EmailType.FOLLOWUP_COMPLETED),
                eq(event.getFollowUpId()),
                eq("FOLLOWUP_COMPLETED:" + event.getFollowUpId()));
    }

    @Test
    void testOnFollowUpCancelled() {
        FollowUpCancelledEvent event = FollowUpCancelledEvent.builder()
                .followUpId(UUID.randomUUID())
                .leadId(lead.getId())
                .assignedUserId(counselor.getId())
                .scheduledDate(LocalDateTime.now())
                .cancelledAt(LocalDateTime.now())
                .cancellationRemarks("Invalid phone number")
                .build();

        when(userRepository.findById(counselor.getId())).thenReturn(Optional.of(counselor));
        when(leadRepository.findById(lead.getId())).thenReturn(Optional.of(lead));
        when(emailTemplateService.buildFollowUpCancelledTemplate(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn("<html>Cancelled Body</html>");

        listener.onFollowUpCancelled(event);

        verify(emailService).sendHtmlEmail(
                eq("counselor@example.com"),
                eq("John Counselor"),
                eq(counselor.getId()),
                any(),
                eq("<html>Cancelled Body</html>"),
                eq(EmailType.FOLLOWUP_CANCELLED),
                eq(event.getFollowUpId()),
                eq("FOLLOWUP_CANCELLED:" + event.getFollowUpId()));
    }

    @Test
    void testOnLeadAllocated() {
        LeadAllocatedEvent event = LeadAllocatedEvent.builder()
                .targetUserId(counselor.getId())
                .allocatedCount(25)
                .departmentName("Admissions")
                .allocationTime(LocalDateTime.now())
                .batchId("BATCH-123")
                .build();

        when(userRepository.findById(counselor.getId())).thenReturn(Optional.of(counselor));
        when(emailTemplateService.buildDataAllocatedTemplate(any(), eq(25), any(), any(), any(), any()))
                .thenReturn("<html>Allocated Body</html>");

        listener.onLeadAllocated(event);

        verify(emailService).sendHtmlEmail(
                eq("counselor@example.com"),
                eq("John Counselor"),
                eq(counselor.getId()),
                any(),
                eq("<html>Allocated Body</html>"),
                eq(EmailType.DATA_ALLOCATED),
                eq(null),
                eq("LEAD_ALLOCATION:BATCH-123:" + counselor.getId()));
    }

    @Test
    void testOnLeadReassigned() {
        UUID sourceUserId = UUID.randomUUID();
        LeadReassignedEvent event = LeadReassignedEvent.builder()
                .targetUserId(counselor.getId())
                .sourceUserId(sourceUserId)
                .sourceUserName("Old Counselor")
                .reassignedCount(15)
                .departmentName("Admissions")
                .reason("Rebalance")
                .reassignmentTime(LocalDateTime.now())
                .batchId("BATCH-456")
                .build();

        when(userRepository.findById(counselor.getId())).thenReturn(Optional.of(counselor));
        when(emailTemplateService.buildDataReassignedTemplate(any(), eq(15), eq("Old Counselor"), any(), any(), any(), any(), any()))
                .thenReturn("<html>Reassigned Body</html>");

        listener.onLeadReassigned(event);

        verify(emailService).sendHtmlEmail(
                eq("counselor@example.com"),
                eq("John Counselor"),
                eq(counselor.getId()),
                any(),
                eq("<html>Reassigned Body</html>"),
                eq(EmailType.DATA_REASSIGNED),
                eq(null),
                eq("LEAD_REASSIGNMENT:BATCH-456:" + counselor.getId()));
    }
}
