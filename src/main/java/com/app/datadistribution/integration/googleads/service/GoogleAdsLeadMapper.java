package com.app.datadistribution.integration.googleads.service;

import com.app.datadistribution.dto.lead.LeadRequest;
import com.app.datadistribution.entity.LeadSource;
import com.app.datadistribution.integration.googleads.dto.GoogleAdsWebhookRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@Slf4j
public class GoogleAdsLeadMapper {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    public ParsedGoogleLead parsePayload(GoogleAdsWebhookRequest request) {
        Map<String, String> columnMap = new LinkedHashMap<>();
        Map<String, String> additionalFields = new LinkedHashMap<>();

        if (request.getUserColumnData() != null) {
            for (GoogleAdsWebhookRequest.UserColumnData col : request.getUserColumnData()) {
                if (col == null) continue;
                String colId = col.getColumnId() != null ? col.getColumnId().trim().toUpperCase() : "";
                String val = col.getStringValue() != null ? col.getStringValue().trim() : "";
                if (!colId.isEmpty() && !val.isEmpty()) {
                    columnMap.put(colId, val);
                }
            }
        }

        // 1. Extract Full Name with First + Last Name fallback
        String fullName = columnMap.get("FULL_NAME");
        if (fullName == null || fullName.isBlank()) {
            String firstName = columnMap.getOrDefault("FIRST_NAME", "").trim();
            String lastName = columnMap.getOrDefault("LAST_NAME", "").trim();
            if (!firstName.isEmpty() || !lastName.isEmpty()) {
                fullName = (firstName + " " + lastName).trim();
            }
        }
        if (fullName == null || fullName.isBlank()) {
            fullName = "Google Ads Lead " + (request.getLeadId() != null ? request.getLeadId() : "");
        }

        // 2. Extract Phone Number
        String phone = columnMap.get("PHONE_NUMBER");
        if (phone != null) {
            phone = phone.trim();
        }

        // 3. Extract Email
        String email = columnMap.get("EMAIL");
        if (email != null) {
            email = email.trim().toLowerCase();
            if (email.length() > 100 || !EMAIL_PATTERN.matcher(email).matches()) {
                email = null;
            }
        }

        // 4. Extract Location
        String city = columnMap.get("CITY");
        String state = columnMap.get("REGION"); // Google Ads uses REGION for State/Province
        String country = columnMap.getOrDefault("COUNTRY", "India");

        // 5. Extract Academic Preferences
        String educationCourse = columnMap.get("EDUCATION_COURSE");
        String educationProgram = columnMap.get("EDUCATION_PROGRAM");

        // Collect all non-standard questions for remarks
        for (Map.Entry<String, String> entry : columnMap.entrySet()) {
            String k = entry.getKey();
            if (!k.equals("FULL_NAME") && !k.equals("FIRST_NAME") && !k.equals("LAST_NAME")
                    && !k.equals("EMAIL") && !k.equals("PHONE_NUMBER") && !k.equals("CITY")
                    && !k.equals("REGION") && !k.equals("COUNTRY") && !k.equals("EDUCATION_COURSE")
                    && !k.equals("EDUCATION_PROGRAM")) {
                additionalFields.put(k, entry.getValue());
            }
        }

        return ParsedGoogleLead.builder()
                .fullName(fullName)
                .phoneNumber(phone)
                .email(email)
                .city(city)
                .state(state)
                .country(country != null && !country.isBlank() ? country : "India")
                .educationCourse(educationCourse)
                .educationProgram(educationProgram)
                .additionalFields(additionalFields)
                .externalLeadId(request.getLeadId())
                .formId(request.getFormId())
                .campaignId(request.getCampaignId())
                .adgroupId(request.getAdgroupId())
                .creativeId(request.getCreativeId())
                .gclId(request.getGclId())
                .submissionTime(request.getLeadSubmitTime())
                .leadSource(request.getLeadSource())
                .build();
    }

    public LeadRequest buildLeadRequest(
            ParsedGoogleLead parsed,
            LeadSource canonicalSource,
            UUID resolvedCourseId,
            UUID resolvedProgramId) {

        String externalId = parsed.getExternalLeadId() != null ? parsed.getExternalLeadId() : "N/A";
        String campaignId = parsed.getCampaignId() != null ? parsed.getCampaignId() : "N/A";
        String formId = parsed.getFormId() != null ? parsed.getFormId() : "N/A";

        String sourceDetails = String.format("Google Ads | Lead ID: %s | Campaign: %s | Form: %s",
                externalId, campaignId, formId);
        if (sourceDetails.length() > 255) {
            sourceDetails = sourceDetails.substring(0, 255);
        }

        StringBuilder remarks = new StringBuilder();
        remarks.append("[Google Ads Lead Form Integration]\n");
        remarks.append("Lead ID: ").append(externalId).append("\n");
        if (parsed.getCampaignId() != null) remarks.append("Campaign ID: ").append(parsed.getCampaignId()).append("\n");
        if (parsed.getFormId() != null) remarks.append("Form ID: ").append(parsed.getFormId()).append("\n");
        if (parsed.getAdgroupId() != null) remarks.append("Ad Group ID: ").append(parsed.getAdgroupId()).append("\n");
        if (parsed.getCreativeId() != null) remarks.append("Creative ID: ").append(parsed.getCreativeId()).append("\n");
        if (parsed.getGclId() != null) remarks.append("GCL ID: ").append(parsed.getGclId()).append("\n");
        if (parsed.getLeadSource() != null) remarks.append("Google Lead Source: ").append(parsed.getLeadSource()).append("\n");
        if (parsed.getSubmissionTime() != null) remarks.append("Submission Time: ").append(parsed.getSubmissionTime()).append("\n");

        if (parsed.getEducationCourse() != null && !parsed.getEducationCourse().isBlank()) {
            remarks.append("Course Inquiry: ").append(parsed.getEducationCourse().trim());
            if (resolvedCourseId == null && resolvedProgramId == null) {
                remarks.append(" (No direct catalog match - manual review)");
            }
            remarks.append("\n");
        }
        if (parsed.getEducationProgram() != null && !parsed.getEducationProgram().isBlank()) {
            remarks.append("Program Inquiry: ").append(parsed.getEducationProgram().trim()).append("\n");
        }

        if (parsed.getAdditionalFields() != null && !parsed.getAdditionalFields().isEmpty()) {
            remarks.append("\nAdditional Form Fields:\n");
            for (Map.Entry<String, String> entry : parsed.getAdditionalFields().entrySet()) {
                remarks.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
        }

        return LeadRequest.builder()
                .fullName(parsed.getFullName())
                .phoneNumber(parsed.getPhoneNumber())
                .email(parsed.getEmail())
                .city(parsed.getCity())
                .state(parsed.getState())
                .country(parsed.getCountry())
                .leadSourceIds(List.of(canonicalSource.getId()))
                .sourceDetails(sourceDetails)
                .courseId(resolvedCourseId)
                .programId(resolvedProgramId)
                .remarks(remarks.toString().trim())
                .active(true)
                .build();
    }

    @lombok.Value
    @lombok.Builder
    public static class ParsedGoogleLead {
        String fullName;
        String phoneNumber;
        String email;
        String city;
        String state;
        String country;
        String educationCourse;
        String educationProgram;
        Map<String, String> additionalFields;
        String externalLeadId;
        String formId;
        String campaignId;
        String adgroupId;
        String creativeId;
        String gclId;
        String submissionTime;
        String leadSource;
    }
}

