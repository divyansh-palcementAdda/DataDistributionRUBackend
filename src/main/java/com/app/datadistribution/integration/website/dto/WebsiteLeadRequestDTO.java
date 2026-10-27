package com.app.datadistribution.integration.website.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebsiteLeadRequestDTO {

    @NotBlank(message = "Source code is required (e.g. WEBSITE_FEES_INQUIRY, WEBSITE_CAMPUS_TOUR)")
    @Size(max = 50, message = "Source code must be less than 50 characters")
    private String sourceCode;

    @Size(max = 100, message = "External lead ID must be less than 100 characters")
    private String externalLeadId;

    @NotBlank(message = "Name is required")
    @Size(max = 150, message = "Name must be less than 150 characters")
    @JsonAlias({"fullName", "studentName"})
    private String name;

    @NotBlank(message = "Mobile number is required")
    @Size(max = 20, message = "Mobile number must be less than 20 characters")
    @Pattern(regexp = "^[0-9+()\\s-]{7,20}$", message = "Mobile number contains invalid characters or length")
    @JsonAlias({"phoneNumber", "phone", "contactNumber"})
    private String mobile;

    @Email(message = "Email must be a valid email address")
    @Size(max = 100, message = "Email must be less than 100 characters")
    private String email;

    @Size(max = 150, message = "Interested course must be less than 150 characters")
    @JsonAlias({"course", "program", "programInterested"})
    private String courseInterested;

    @Size(max = 2000, message = "Message must be less than 2000 characters")
    @JsonAlias({"remarks", "query", "comments"})
    private String message;

    @Size(max = 100, message = "City must be less than 100 characters")
    private String city;

    @Size(max = 100, message = "State must be less than 100 characters")
    private String state;

    @Size(max = 100, message = "Country must be less than 100 characters")
    @Builder.Default
    private String country = "India";

    @Size(max = 255, message = "Website URL must be less than 255 characters")
    @JsonAlias({"url", "pageUrl", "sourceUrl"})
    private String websiteUrl;

    private Boolean consent;

    @Size(max = 100, message = "UTM source must be less than 100 characters")
    private String utmSource;

    @Size(max = 100, message = "UTM medium must be less than 100 characters")
    private String utmMedium;

    @Size(max = 100, message = "UTM campaign must be less than 100 characters")
    private String utmCampaign;
}
