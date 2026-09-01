package com.app.datadistribution.dto.email;

import java.time.LocalDateTime;
import java.util.UUID;

import com.app.datadistribution.enums.EmailStatus;
import com.app.datadistribution.enums.EmailType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailLogResponseDTO {

    private UUID id;
    private String idempotencyKey;
    private String recipientEmail;
    private String recipientName;
    private UUID recipientUserId;
    private String subject;
    private EmailType emailType;
    private UUID relatedEntityId;
    private EmailStatus status;
    private String errorMessage;
    private int retryCount;
    private LocalDateTime sentAt;
    private LocalDateTime attemptedAt;
    private LocalDateTime createdAt;
}
