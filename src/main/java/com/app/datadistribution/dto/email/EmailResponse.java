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
public class EmailResponse {

    private UUID logId;
    private String recipientEmail;
    private String subject;
    private EmailType emailType;
    private EmailStatus status;
    private String message;
    private LocalDateTime timestamp;
}
