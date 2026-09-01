package com.app.datadistribution.dto.email;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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
public class TestEmailRequest {

    @NotBlank(message = "Recipient email is required")
    @Email(message = "Invalid recipient email address")
    private String recipientEmail;

    private String recipientName;
}
