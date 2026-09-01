package com.app.datadistribution.scheduler;

import java.time.LocalDateTime;
import java.util.List;
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
import com.app.datadistribution.entity.LeadFollowUp;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.EmailStatus;
import com.app.datadistribution.enums.EmailType;
import com.app.datadistribution.enums.FollowUpStatus;
import com.app.datadistribution.repository.LeadFollowUpRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.interfaces.IEmailService;
import com.app.datadistribution.service.interfaces.IEmailTemplateService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyFollowUpReminderSchedulerTest {

    @Mock
    private LeadFollowUpRepository leadFollowUpRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private IEmailService emailService;

    @Mock
    private IEmailTemplateService emailTemplateService;

    @InjectMocks
    private DailyFollowUpReminderScheduler scheduler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduler, "frontendUrl", "http://localhost:3000");
    }

    @Test
    void testExecuteDailyFollowUpReminders_SendsEmailsForAssignedCounselors() {
        UUID counselorId = UUID.randomUUID();
        User counselor = User.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john.doe@example.com")
                .build();
        counselor.setId(counselorId);

        Lead lead = Lead.builder()
                .fullName("Jane Student")
                .assignedTo(counselor)
                .build();
        lead.setId(UUID.randomUUID());

        LeadFollowUp followUp = LeadFollowUp.builder()
                .lead(lead)
                .assignedTo(counselor)
                .status(FollowUpStatus.PENDING)
                .followUpDate(LocalDateTime.now())
                .remarks("Call back about B.Tech fee structure")
                .build();
        followUp.setId(UUID.randomUUID());

        when(leadFollowUpRepository.findActiveFollowUpsForDateRangeWithDetails(any(), any()))
                .thenReturn(List.of(followUp));
        when(userRepository.findById(counselorId)).thenReturn(Optional.of(counselor));
        when(emailTemplateService.buildDailyFollowUpReminderTemplate(any(), any(), any(), any()))
                .thenReturn("<html>Daily Digest</html>");

        EmailResponse successResponse = EmailResponse.builder().status(EmailStatus.SENT).build();
        when(emailService.sendHtmlEmail(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(successResponse);

        scheduler.executeDailyFollowUpReminders();

        verify(emailService).sendHtmlEmail(
                eq("john.doe@example.com"),
                eq("John Doe"),
                eq(counselorId),
                any(),
                eq("<html>Daily Digest</html>"),
                eq(EmailType.DAILY_FOLLOWUP_REMINDER),
                eq(null),
                any());
    }

    @Test
    void testExecuteDailyFollowUpReminders_NoFollowUps_SkipsExecution() {
        when(leadFollowUpRepository.findActiveFollowUpsForDateRangeWithDetails(any(), any()))
                .thenReturn(List.of());

        scheduler.executeDailyFollowUpReminders();

        verify(emailService, never()).sendHtmlEmail(any(), any(), any(), any(), any(), any(), any(), any());
    }
}
