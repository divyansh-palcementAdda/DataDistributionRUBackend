package com.app.datadistribution.service.interfaces;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.app.datadistribution.dto.email.EmailConfigStatusDTO;
import com.app.datadistribution.dto.email.EmailLogResponseDTO;
import com.app.datadistribution.dto.email.EmailResponse;
import com.app.datadistribution.dto.email.SendEmailRequest;
import com.app.datadistribution.dto.email.TestEmailRequest;
import com.app.datadistribution.enums.EmailStatus;
import com.app.datadistribution.enums.EmailType;

public interface IEmailService {

    EmailResponse sendHtmlEmail(
            String recipientEmail,
            String recipientName,
            UUID recipientUserId,
            String subject,
            String htmlBody,
            EmailType emailType,
            UUID relatedEntityId,
            String idempotencyKey);

    EmailResponse sendCustomEmail(SendEmailRequest request);

    EmailResponse sendTestEmail(TestEmailRequest request);

    EmailConfigStatusDTO getConfigStatus();

    Page<EmailLogResponseDTO> getEmailLogs(EmailStatus status, EmailType emailType, String search, Pageable pageable);
}
