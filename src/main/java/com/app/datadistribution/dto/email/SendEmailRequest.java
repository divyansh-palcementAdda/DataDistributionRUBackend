package com.app.datadistribution.dto.email;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
public class SendEmailRequest {

    @NotBlank(message = "Recipient email is required")
    @Email(message = "Invalid recipient email address")
    private String recipientEmail;

    private String recipientName;

    @NotBlank(message = "Email subject is required")
    @Size(max = 255, message = "Subject cannot exceed 255 characters")
    private String subject;

    @NotBlank(message = "Email content/body is required")
    private String body;

    private String actionUrl;

    private String actionText;
}
