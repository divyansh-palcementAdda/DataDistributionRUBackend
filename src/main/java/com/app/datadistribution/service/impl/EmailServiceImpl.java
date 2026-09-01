package com.app.datadistribution.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.app.datadistribution.config.EmailConfig;
import com.app.datadistribution.dto.email.EmailConfigStatusDTO;
import com.app.datadistribution.dto.email.EmailLogResponseDTO;
import com.app.datadistribution.dto.email.EmailResponse;
import com.app.datadistribution.dto.email.SendEmailRequest;
import com.app.datadistribution.dto.email.TestEmailRequest;
import com.app.datadistribution.entity.EmailLog;
import com.app.datadistribution.enums.EmailStatus;
import com.app.datadistribution.enums.EmailType;
import com.app.datadistribution.repository.EmailLogRepository;
import com.app.datadistribution.service.interfaces.IEmailService;
import com.app.datadistribution.service.interfaces.IEmailTemplateService;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements IEmailService {

    private final EmailConfig emailConfig;
    private final EmailLogRepository emailLogRepository;
    private final IEmailTemplateService emailTemplateService;

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.host:smtp.gmail.com}")
    private String mailHost;

    @Value("${spring.mail.port:587}")
    private int mailPort;

    @Value("${app.frontend.url:https://dds.areyoureporting.com}")
    private String frontendUrl;

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$");

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EmailResponse sendHtmlEmail(
            String recipientEmail,
            String recipientName,
            UUID recipientUserId,
            String subject,
            String htmlBody,
            EmailType emailType,
            UUID relatedEntityId,
            String idempotencyKey) {

        LocalDateTime now = LocalDateTime.now();

        // 1. Idempotency Check
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<EmailLog> existingLog = emailLogRepository.findByIdempotencyKey(idempotencyKey);
            if (existingLog.isPresent()) {
                EmailLog logEntry = existingLog.get();
                log.info("Email skipped by idempotency key: {} | Status: {}", idempotencyKey, logEntry.getStatus());
                return EmailResponse.builder()
                        .logId(logEntry.getId())
                        .recipientEmail(recipientEmail)
                        .subject(subject)
                        .emailType(emailType)
                        .status(EmailStatus.SKIPPED)
                        .message("Email already processed with idempotency key: " + idempotencyKey)
                        .timestamp(now)
                        .build();
            }
        }

        // 2. Validate Recipient Email Address
        if (recipientEmail == null || recipientEmail.isBlank() || !EMAIL_PATTERN.matcher(recipientEmail.trim()).matches()) {
            log.warn("Invalid recipient email address: '{}'. Email sending skipped for subject: '{}'", recipientEmail, subject);
            EmailLog logEntry = createAndSaveLog(idempotencyKey, recipientEmail != null ? recipientEmail : "UNKNOWN",
                    recipientName, recipientUserId, subject, emailType, relatedEntityId,
                    EmailStatus.SKIPPED, "Invalid or missing email address", 0, null, now);

            return EmailResponse.builder()
                    .logId(logEntry.getId())
                    .recipientEmail(recipientEmail)
                    .subject(subject)
                    .emailType(emailType)
                    .status(EmailStatus.SKIPPED)
                    .message("Invalid email address: " + recipientEmail)
                    .timestamp(now)
                    .build();
        }

        String sanitizedEmail = recipientEmail.trim();

        // 3. Check if Email Sending is Enabled
        if (!emailConfig.isEnabled()) {
            log.info("Email service is disabled by configuration. Email to '{}' with subject '{}' recorded as SKIPPED.", sanitizedEmail, subject);
            EmailLog logEntry = createAndSaveLog(idempotencyKey, sanitizedEmail, recipientName, recipientUserId,
                    subject, emailType, relatedEntityId, EmailStatus.SKIPPED, "Email service disabled in configuration", 0, null, now);

            return EmailResponse.builder()
                    .logId(logEntry.getId())
                    .recipientEmail(sanitizedEmail)
                    .subject(subject)
                    .emailType(emailType)
                    .status(EmailStatus.SKIPPED)
                    .message("Email service is currently disabled")
                    .timestamp(now)
                    .build();
        }

        // 4. Attempt Sending via JavaMailSender with Retries
        int maxRetries = Math.max(1, emailConfig.getMaxRetries());
        Exception lastException = null;
        boolean sentSuccessfully = false;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                if (mailSender == null) {
                    throw new IllegalStateException("JavaMailSender bean is not configured on this server");
                }

                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(
                        message,
                        MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                        StandardCharsets.UTF_8.name());

                String fromEmail = emailConfig.getFromEmail();
                String fromName = emailConfig.getFromName();

                helper.setFrom(new InternetAddress(fromEmail, fromName, StandardCharsets.UTF_8.name()));
                helper.setTo(sanitizedEmail);
                helper.setSubject(subject);
                helper.setText(htmlBody, true);

                mailSender.send(message);
                sentSuccessfully = true;
                log.info("EMAIL SENT SUCCESSFULLY | Recipient: {} | Type: {} | Subject: '{}' | Attempt: {}",
                        sanitizedEmail, emailType, subject, attempt);
                break;

            } catch (Exception e) {
                lastException = e;
                log.warn("Failed to send email to {} (Attempt {}/{}): {}", sanitizedEmail, attempt, maxRetries, e.getMessage());
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(500L * attempt); // Linear backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        // 5. Persist Log Entry
        EmailStatus finalStatus = sentSuccessfully ? EmailStatus.SENT : EmailStatus.FAILED;
        String errorMessage = sentSuccessfully ? null : (lastException != null ? lastException.getMessage() : "Unknown error");
        LocalDateTime sentTime = sentSuccessfully ? LocalDateTime.now() : null;

        EmailLog logEntry = createAndSaveLog(idempotencyKey, sanitizedEmail, recipientName, recipientUserId,
                subject, emailType, relatedEntityId, finalStatus, errorMessage, maxRetries, sentTime, now);

        return EmailResponse.builder()
                .logId(logEntry.getId())
                .recipientEmail(sanitizedEmail)
                .subject(subject)
                .emailType(emailType)
                .status(finalStatus)
                .message(sentSuccessfully ? "Email sent successfully" : "Email sending failed: " + errorMessage)
                .timestamp(now)
                .build();
    }

    @Override
    public EmailResponse sendCustomEmail(SendEmailRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("SendEmailRequest cannot be null");
        }

        String htmlBody = emailTemplateService.buildGenericEmailTemplate(
                request.getRecipientName(),
                request.getSubject(),
                request.getBody(),
                request.getActionText(),
                request.getActionUrl());

        return sendHtmlEmail(
                request.getRecipientEmail(),
                request.getRecipientName(),
                null,
                request.getSubject(),
                htmlBody,
                EmailType.CUSTOM_EMAIL,
                null,
                null);
    }

    @Override
    public EmailResponse sendTestEmail(TestEmailRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("TestEmailRequest cannot be null");
        }

        String testSubject = "Test Email – Renaissance University CRM Notification Service";
        String testContent = "<p>This is a test email sent from the CRM Email Notification System to verify SMTP provider connectivity and HTML template rendering.</p>"
                           + "<p style=\"color:#059669; font-weight:600;\">✓ SMTP Connection: Active</p>"
                           + "<p style=\"color:#059669; font-weight:600;\">✓ Template Engine: Active</p>"
                           + "<p style=\"color:#059669; font-weight:600;\">✓ Asynchronous Dispatch: Active</p>";

        String htmlBody = emailTemplateService.buildGenericEmailTemplate(
                request.getRecipientName() != null ? request.getRecipientName() : "Admin",
                "CRM Email Connectivity Test",
                testContent,
                "Open CRM Dashboard",
                frontendUrl);

        return sendHtmlEmail(
                request.getRecipientEmail(),
                request.getRecipientName(),
                null,
                testSubject,
                htmlBody,
                EmailType.TEST_EMAIL,
                null,
                null);
    }

    @Override
    @Transactional(readOnly = true)
    public EmailConfigStatusDTO getConfigStatus() {
        return EmailConfigStatusDTO.builder()
                .enabled(emailConfig.isEnabled())
                .host(mailHost)
                .port(mailPort)
                .fromEmail(emailConfig.getFromEmail())
                .fromName(emailConfig.getFromName())
                .async(emailConfig.isAsync())
                .maxRetries(emailConfig.getMaxRetries())
                .dailyReminderCron(emailConfig.getDailyReminderCron())
                .dailyReminderZone(emailConfig.getDailyReminderZone())
                .frontendUrl(frontendUrl)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EmailLogResponseDTO> getEmailLogs(EmailStatus status, EmailType emailType, String search, Pageable pageable) {
        return emailLogRepository.searchLogs(status, emailType, search, pageable)
                .map(this::toDto);
    }

    private EmailLog createAndSaveLog(
            String idempotencyKey,
            String recipientEmail,
            String recipientName,
            UUID recipientUserId,
            String subject,
            EmailType emailType,
            UUID relatedEntityId,
            EmailStatus status,
            String errorMessage,
            int retryCount,
            LocalDateTime sentAt,
            LocalDateTime attemptedAt) {

        EmailLog logEntry = EmailLog.builder()
                .idempotencyKey(idempotencyKey)
                .recipientEmail(recipientEmail)
                .recipientName(recipientName)
                .recipientUserId(recipientUserId)
                .subject(subject)
                .emailType(emailType)
                .relatedEntityId(relatedEntityId)
                .status(status)
                .errorMessage(errorMessage)
                .retryCount(retryCount)
                .sentAt(sentAt)
                .attemptedAt(attemptedAt)
                .build();

        try {
            return emailLogRepository.save(logEntry);
        } catch (Exception e) {
            log.error("Failed to persist EmailLog entry: {}", e.getMessage());
            return logEntry;
        }
    }

    private EmailLogResponseDTO toDto(EmailLog entity) {
        return EmailLogResponseDTO.builder()
                .id(entity.getId())
                .idempotencyKey(entity.getIdempotencyKey())
                .recipientEmail(entity.getRecipientEmail())
                .recipientName(entity.getRecipientName())
                .recipientUserId(entity.getRecipientUserId())
                .subject(entity.getSubject())
                .emailType(entity.getEmailType())
                .relatedEntityId(entity.getRelatedEntityId())
                .status(entity.getStatus())
                .errorMessage(entity.getErrorMessage())
                .retryCount(entity.getRetryCount())
                .sentAt(entity.getSentAt())
                .attemptedAt(entity.getAttemptedAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
