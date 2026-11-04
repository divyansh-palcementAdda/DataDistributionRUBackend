package com.app.datadistribution.integration.googleads;

import com.app.datadistribution.dto.lead.LeadRequest;
import com.app.datadistribution.entity.LeadSource;
import com.app.datadistribution.integration.googleads.dto.GoogleAdsWebhookRequest;
import com.app.datadistribution.integration.googleads.service.GoogleAdsLeadMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoogleAdsLeadMapperTest {

    private GoogleAdsLeadMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new GoogleAdsLeadMapper();
    }

    @Test
    @DisplayName("Should parse full valid payload with all standard columns")
    void shouldParseFullValidPayload() {
        GoogleAdsWebhookRequest request = GoogleAdsWebhookRequest.builder()
                .leadId("LEAD_GOOGLE_98765")
                .formId("FORM_123")
                .campaignId("CAMP_456")
                .adgroupId("ADG_789")
                .creativeId("CR_101")
                .gclId("GCL_CLICK_ID")
                .leadSource("SEARCH")
                .leadSubmitTime("2026-11-03T10:30:00Z")
                .userColumnData(List.of(
                        new GoogleAdsWebhookRequest.UserColumnData("FULL_NAME", "Aarav Mehta", null),
                        new GoogleAdsWebhookRequest.UserColumnData("EMAIL", "aarav@example.com", null),
                        new GoogleAdsWebhookRequest.UserColumnData("PHONE_NUMBER", "+919876543210", null),
                        new GoogleAdsWebhookRequest.UserColumnData("CITY", "Indore", null),
                        new GoogleAdsWebhookRequest.UserColumnData("REGION", "Madhya Pradesh", null),
                        new GoogleAdsWebhookRequest.UserColumnData("COUNTRY", "India", null),
                        new GoogleAdsWebhookRequest.UserColumnData("EDUCATION_COURSE", "B.Tech Computer Science", null),
                        new GoogleAdsWebhookRequest.UserColumnData("CUSTOM_QUESTION_1", "Weekend Batch", null)
                ))
                .build();

        GoogleAdsLeadMapper.ParsedGoogleLead parsed = mapper.parsePayload(request);

        assertEquals("Aarav Mehta", parsed.getFullName());
        assertEquals("aarav@example.com", parsed.getEmail());
        assertEquals("+919876543210", parsed.getPhoneNumber());
        assertEquals("Indore", parsed.getCity());
        assertEquals("Madhya Pradesh", parsed.getState());
        assertEquals("India", parsed.getCountry());
        assertEquals("B.Tech Computer Science", parsed.getEducationCourse());
        assertEquals("LEAD_GOOGLE_98765", parsed.getExternalLeadId());
        assertEquals("FORM_123", parsed.getFormId());
        assertEquals("CAMP_456", parsed.getCampaignId());
        assertTrue(parsed.getAdditionalFields().containsKey("CUSTOM_QUESTION_1"));
        assertEquals("Weekend Batch", parsed.getAdditionalFields().get("CUSTOM_QUESTION_1"));

        LeadSource source = LeadSource.builder()
                .name("Google Ads")
                .code("GOOGLE_ADS")
                .build();
        source.setId(UUID.randomUUID());

        UUID courseId = UUID.randomUUID();
        LeadRequest leadRequest = mapper.buildLeadRequest(parsed, source, courseId, null);

        assertEquals("Aarav Mehta", leadRequest.getFullName());
        assertEquals("+919876543210", leadRequest.getPhoneNumber());
        assertEquals("aarav@example.com", leadRequest.getEmail());
        assertEquals("Indore", leadRequest.getCity());
        assertEquals("Madhya Pradesh", leadRequest.getState());
        assertEquals(courseId, leadRequest.getCourseId());
        assertNotNull(leadRequest.getRemarks());
        assertTrue(leadRequest.getRemarks().contains("LEAD_GOOGLE_98765"));
        assertTrue(leadRequest.getRemarks().contains("CAMP_456"));
        assertTrue(leadRequest.getRemarks().contains("CUSTOM_QUESTION_1"));
    }

    @Test
    @DisplayName("Should fallback to FIRST_NAME + LAST_NAME when FULL_NAME is missing")
    void shouldFallbackToFirstAndLastName() {
        GoogleAdsWebhookRequest request = GoogleAdsWebhookRequest.builder()
                .leadId("LEAD_GOOGLE_111")
                .userColumnData(List.of(
                        new GoogleAdsWebhookRequest.UserColumnData("FIRST_NAME", "Priya", null),
                        new GoogleAdsWebhookRequest.UserColumnData("LAST_NAME", "Sharma", null),
                        new GoogleAdsWebhookRequest.UserColumnData("PHONE_NUMBER", "+919123456789", null)
                ))
                .build();

        GoogleAdsLeadMapper.ParsedGoogleLead parsed = mapper.parsePayload(request);
        assertEquals("Priya Sharma", parsed.getFullName());
        assertEquals("+919123456789", parsed.getPhoneNumber());
        assertNull(parsed.getEmail());
    }

    @Test
    @DisplayName("Should handle missing optional fields safely")
    void shouldHandleMissingOptionalFields() {
        GoogleAdsWebhookRequest request = GoogleAdsWebhookRequest.builder()
                .leadId("LEAD_GOOGLE_222")
                .userColumnData(List.of(
                        new GoogleAdsWebhookRequest.UserColumnData("PHONE_NUMBER", "+919999888877", null)
                ))
                .build();

        GoogleAdsLeadMapper.ParsedGoogleLead parsed = mapper.parsePayload(request);
        assertEquals("Google Ads Lead LEAD_GOOGLE_222", parsed.getFullName());
        assertEquals("+919999888877", parsed.getPhoneNumber());
        assertNull(parsed.getEmail());
        assertNull(parsed.getCity());
        assertNull(parsed.getState());
        assertEquals("India", parsed.getCountry());
        assertNull(parsed.getEducationCourse());
    }
}

