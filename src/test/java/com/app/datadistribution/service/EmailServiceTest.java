package com.app.datadistribution.service;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import com.app.datadistribution.config.EmailConfig;
import com.app.datadistribution.dto.email.EmailResponse;
import com.app.datadistribution.dto.email.SendEmailRequest;
import com.app.datadistribution.dto.email.TestEmailRequest;
import com.app.datadistribution.entity.EmailLog;
import com.app.datadistribution.enums.EmailStatus;
import com.app.datadistribution.enums.EmailType;
import com.app.datadistribution.repository.EmailLogRepository;
import com.app.datadistribution.service.impl.EmailServiceImpl;
import com.app.datadistribution.service.interfaces.IEmailTemplateService;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

        @Mock
        private EmailConfig emailConfig;

        @Mock
        private EmailLogRepository emailLogRepository;

        @Mock
        private IEmailTemplateService emailTemplateService;

        @Mock
        private JavaMailSender mailSender;

        @InjectMocks
        private EmailServiceImpl emailService;

        @BeforeEach
        void setUp() {
                ReflectionTestUtils.setField(emailService, "mailHost", "smtp.gmail.com");
                ReflectionTestUtils.setField(emailService, "mailPort", 587);
                ReflectionTestUtils.setField(emailService, "frontendUrl", "http://localhost:3000");
                ReflectionTestUtils.setField(emailService, "mailSender", mailSender);
        }

        @Test
        void testSendHtmlEmail_Success() {
                when(emailConfig.isEnabled()).thenReturn(true);
                when(emailConfig.getMaxRetries()).thenReturn(2);
                when(emailConfig.getFromEmail()).thenReturn("chancelloroffice@renaissance.ac.in");
                when(emailConfig.getFromName()).thenReturn("RU CRM");

                when(emailLogRepository.findByIdempotencyKey("KEY_123")).thenReturn(Optional.empty());

                MimeMessage mimeMessage = new MimeMessage((Session) null);
                when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
                doNothing().when(mailSender).send(any(MimeMessage.class));

                EmailLog savedLog = EmailLog.builder()
                                .recipientEmail("counselor@example.com")
                                .status(EmailStatus.SENT)
                                .build();
                savedLog.setId(UUID.randomUUID());
                when(emailLogRepository.save(any(EmailLog.class))).thenReturn(savedLog);

                EmailResponse response = emailService.sendHtmlEmail(
                                "counselor@example.com",
                                "John Counselor",
                                UUID.randomUUID(),
                                "Follow-up Scheduled",
                                "<html><body>Test</body></html>",
                                EmailType.FOLLOWUP_SCHEDULED,
                                UUID.randomUUID(),
                                "KEY_123");

                assertNotNull(response);
                assertEquals(EmailStatus.SENT, response.getStatus());
                assertEquals("counselor@example.com", response.getRecipientEmail());
                verify(mailSender).send(any(MimeMessage.class));
                verify(emailLogRepository).save(any(EmailLog.class));
        }

        @Test
        void testSendHtmlEmail_IdempotencySkipsDuplicate() {
                EmailLog existing = EmailLog.builder()
                                .recipientEmail("counselor@example.com")
                                .status(EmailStatus.SENT)
                                .build();
                existing.setId(UUID.randomUUID());

                when(emailLogRepository.findByIdempotencyKey("KEY_EXISTING")).thenReturn(Optional.of(existing));

                EmailResponse response = emailService.sendHtmlEmail(
                                "counselor@example.com",
                                "John Counselor",
                                UUID.randomUUID(),
                                "Follow-up Scheduled",
                                "<html><body>Test</body></html>",
                                EmailType.FOLLOWUP_SCHEDULED,
                                UUID.randomUUID(),
                                "KEY_EXISTING");

                assertNotNull(response);
                assertEquals(EmailStatus.SKIPPED, response.getStatus());
                verify(mailSender, never()).send(any(MimeMessage.class));
        }

        @Test
        void testSendHtmlEmail_InvalidEmailSkips() {
                EmailLog savedLog = EmailLog.builder()
                                .recipientEmail("invalid-email")
                                .status(EmailStatus.SKIPPED)
                                .build();
                savedLog.setId(UUID.randomUUID());
                when(emailLogRepository.save(any(EmailLog.class))).thenReturn(savedLog);

                EmailResponse response = emailService.sendHtmlEmail(
                                "invalid-email",
                                "John Counselor",
                                UUID.randomUUID(),
                                "Follow-up Scheduled",
                                "<html><body>Test</body></html>",
                                EmailType.FOLLOWUP_SCHEDULED,
                                UUID.randomUUID(),
                                null);

                assertNotNull(response);
                assertEquals(EmailStatus.SKIPPED, response.getStatus());
                verify(mailSender, never()).send(any(MimeMessage.class));
        }

        @Test
        void testSendCustomEmail() {
                when(emailConfig.isEnabled()).thenReturn(true);
                when(emailConfig.getMaxRetries()).thenReturn(1);
                when(emailConfig.getFromEmail()).thenReturn("chancelloroffice@renaissance.ac.in");
                when(emailConfig.getFromName()).thenReturn("RU CRM");
                when(emailTemplateService.buildGenericEmailTemplate(any(), any(), any(), any(), any()))
                                .thenReturn("<html>Custom Body</html>");

                MimeMessage mimeMessage = new MimeMessage((Session) null);
                when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
                doNothing().when(mailSender).send(any(MimeMessage.class));

                EmailLog savedLog = EmailLog.builder()
                                .recipientEmail("user@example.com")
                                .status(EmailStatus.SENT)
                                .build();
                savedLog.setId(UUID.randomUUID());
                when(emailLogRepository.save(any(EmailLog.class))).thenReturn(savedLog);

                SendEmailRequest request = SendEmailRequest.builder()
                                .recipientEmail("user@example.com")
                                .recipientName("Alice")
                                .subject("Welcome Notice")
                                .body("Hello Alice")
                                .build();

                EmailResponse response = emailService.sendCustomEmail(request);

                assertNotNull(response);
                assertEquals(EmailStatus.SENT, response.getStatus());
        }

        @Test
        void testSendTestEmail() {
                when(emailConfig.isEnabled()).thenReturn(true);
                when(emailConfig.getMaxRetries()).thenReturn(1);
                when(emailConfig.getFromEmail()).thenReturn("chancelloroffice@renaissance.ac.in");
                when(emailConfig.getFromName()).thenReturn("RU CRM");
                when(emailTemplateService.buildGenericEmailTemplate(any(), any(), any(), any(), any()))
                                .thenReturn("<html>Test Body</html>");

                MimeMessage mimeMessage = new MimeMessage((Session) null);
                when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
                doNothing().when(mailSender).send(any(MimeMessage.class));

                EmailLog savedLog = EmailLog.builder()
                                .recipientEmail("admin@example.com")
                                .status(EmailStatus.SENT)
                                .build();
                savedLog.setId(UUID.randomUUID());
                when(emailLogRepository.save(any(EmailLog.class))).thenReturn(savedLog);

                TestEmailRequest request = TestEmailRequest.builder()
                                .recipientEmail("admin@example.com")
                                .recipientName("Admin")
                                .build();

                EmailResponse response = emailService.sendTestEmail(request);

                assertNotNull(response);
                assertEquals(EmailStatus.SENT, response.getStatus());
        }
}
