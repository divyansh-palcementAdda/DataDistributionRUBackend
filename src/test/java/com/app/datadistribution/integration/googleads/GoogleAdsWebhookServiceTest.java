package com.app.datadistribution.integration.googleads;

import com.app.datadistribution.dto.lead.LeadMatchResult;
import com.app.datadistribution.dto.lead.LeadRequest;
import com.app.datadistribution.dto.lead.LeadResponse;
import com.app.datadistribution.entity.Course;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadSource;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.integration.googleads.config.GoogleAdsIntegrationProperties;
import com.app.datadistribution.integration.googleads.dto.GoogleAdsWebhookRequest;
import com.app.datadistribution.integration.googleads.entity.GoogleAdsLeadEvent;
import com.app.datadistribution.integration.googleads.repository.GoogleAdsLeadEventRepository;
import com.app.datadistribution.integration.googleads.security.GoogleAdsWebhookValidator;
import com.app.datadistribution.integration.googleads.service.GoogleAdsLeadMapper;
import com.app.datadistribution.integration.googleads.service.impl.GoogleAdsWebhookServiceImpl;
import com.app.datadistribution.repository.CourseRepository;
import com.app.datadistribution.repository.LeadSourceRepository;
import com.app.datadistribution.repository.ProgramRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.interfaces.ILeadDeduplicationService;
import com.app.datadistribution.service.interfaces.ILeadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleAdsWebhookServiceTest {

        @Mock
        private GoogleAdsIntegrationProperties properties;

        @Mock
        private GoogleAdsWebhookValidator validator;

        @Mock
        private GoogleAdsLeadEventRepository eventRepository;

        @Spy
        private GoogleAdsLeadMapper leadMapper = new GoogleAdsLeadMapper();

        @Mock
        private ILeadService leadService;

        @Mock
        private ILeadDeduplicationService deduplicationService;

        @Mock
        private LeadSourceRepository leadSourceRepository;

        @Mock
        private CourseRepository courseRepository;

        @Mock
        private ProgramRepository programRepository;

        @Mock
        private UserRepository userRepository;

        @InjectMocks
        private GoogleAdsWebhookServiceImpl webhookService;

        private LeadSource googleAdsSource;
        private User superadminUser;

        @BeforeEach
        void setUp() {
                googleAdsSource = LeadSource.builder()
                                .name("Google Ads")
                                .code("GOOGLE_ADS")
                                .active(true)
                                .build();
                googleAdsSource.setId(UUID.randomUUID());

                superadminUser = User.builder()
                                .username("superadmin")
                                .build();
                superadminUser.setId(UUID.randomUUID());
        }

        @Test
        @DisplayName("Should successfully ingest fresh Google Ads production lead")
        void shouldIngestFreshProductionLead() throws Exception {
                GoogleAdsWebhookRequest request = GoogleAdsWebhookRequest.builder()
                                .leadId("LEAD_1001")
                                .googleKey("valid_key")
                                .campaignId("CAMP_1")
                                .formId("FORM_1")
                                .isTest(false)
                                .userColumnData(List.of(
                                                new GoogleAdsWebhookRequest.UserColumnData("FULL_NAME", "Vikram Singh",
                                                                null),
                                                new GoogleAdsWebhookRequest.UserColumnData("PHONE_NUMBER",
                                                                "+919876543210", null),
                                                new GoogleAdsWebhookRequest.UserColumnData("EMAIL",
                                                                "vikram@example.com", null),
                                                new GoogleAdsWebhookRequest.UserColumnData("EDUCATION_COURSE", "BBA",
                                                                null)))
                                .build();

                doNothing().when(validator).validate(anyString());
                when(eventRepository.findByProviderAndExternalLeadId("GOOGLE_ADS", "LEAD_1001"))
                                .thenReturn(Optional.empty());
                when(leadSourceRepository.findByCodeIgnoreCaseAndIsDeletedFalse("GOOGLE_ADS"))
                                .thenReturn(Optional.of(googleAdsSource));

                Course bbaCourse = Course.builder().courseName("BBA").courseCode("BBA")
                                .status(com.app.datadistribution.enums.Status.ACTIVE).build();
                bbaCourse.setId(UUID.randomUUID());
                when(courseRepository.findByCourseNameIgnoreCaseAndIsDeletedFalse("BBA"))
                                .thenReturn(Optional.of(bbaCourse));

                when(userRepository.findByUsername("superadmin")).thenReturn(Optional.of(superadminUser));
                when(deduplicationService.normalizePhone("+919876543210")).thenReturn("9876543210");
                when(deduplicationService.executeWithPhoneLock(eq("9876543210"), any()))
                                .thenReturn(LeadMatchResult.noMatch());

                UUID createdLeadId = UUID.randomUUID();
                LeadResponse createdLeadResponse = LeadResponse.builder()
                                .id(createdLeadId)
                                .leadCode("LEAD-VK1001")
                                .build();
                when(leadService.createLeadFromExternal(any(LeadRequest.class), eq(superadminUser), anyString()))
                                .thenReturn(createdLeadResponse);

                String rawPayload = "{\"lead_id\":\"LEAD_1001\",\"google_key\":\"valid_key\"}";

                assertDoesNotThrow(() -> webhookService.processWebhook(request, rawPayload));

                verify(leadService, times(1)).createLeadFromExternal(any(LeadRequest.class), eq(superadminUser),
                                anyString());

                ArgumentCaptor<GoogleAdsLeadEvent> eventCaptor = ArgumentCaptor.forClass(GoogleAdsLeadEvent.class);
                verify(eventRepository).save(eventCaptor.capture());
                GoogleAdsLeadEvent savedEvent = eventCaptor.getValue();
                assertEquals("LEAD_1001", savedEvent.getExternalLeadId());
                assertEquals("PROCESSED", savedEvent.getProcessingStatus());
                assertEquals(createdLeadId, savedEvent.getLinkedLeadId());
                assertEquals("LEAD-VK1001", savedEvent.getLeadCode());
                assertFalse(savedEvent.isTest());
        }

        @Test
        @DisplayName("Should enrich existing lead when deduplication matches existing record")
        void shouldEnrichExistingLeadOnDuplicateMatch() throws Exception {
                GoogleAdsWebhookRequest request = GoogleAdsWebhookRequest.builder()
                                .leadId("LEAD_1002")
                                .googleKey("valid_key")
                                .isTest(false)
                                .userColumnData(List.of(
                                                new GoogleAdsWebhookRequest.UserColumnData("FULL_NAME", "Vikram Singh",
                                                                null),
                                                new GoogleAdsWebhookRequest.UserColumnData("PHONE_NUMBER",
                                                                "+919876543210", null)))
                                .build();

                doNothing().when(validator).validate(anyString());
                when(eventRepository.findByProviderAndExternalLeadId("GOOGLE_ADS", "LEAD_1002"))
                                .thenReturn(Optional.empty());
                when(leadSourceRepository.findByCodeIgnoreCaseAndIsDeletedFalse("GOOGLE_ADS"))
                                .thenReturn(Optional.of(googleAdsSource));
                when(userRepository.findByUsername("superadmin")).thenReturn(Optional.of(superadminUser));
                when(deduplicationService.normalizePhone("+919876543210")).thenReturn("9876543210");

                Lead existingLead = Lead.builder()
                                .fullName("Vikram Singh")
                                .phoneNumber("+919876543210")
                                .leadCode("LEAD-EXISTING-1")
                                .build();
                existingLead.setId(UUID.randomUUID());

                LeadMatchResult matchResult = LeadMatchResult.builder()
                                .status(LeadMatchResult.MatchStatus.EXACT_MATCH)
                                .matchedLead(existingLead)
                                .build();
                when(deduplicationService.executeWithPhoneLock(eq("9876543210"), any())).thenReturn(matchResult);

                when(deduplicationService.enrichAndSaveMatchedLead(eq(existingLead), any(), eq(matchResult),
                                eq(superadminUser), anyString()))
                                .thenReturn(existingLead);

                webhookService.processWebhook(request, "{}");

                // Should NOT create new lead via leadService
                verify(leadService, never()).createLeadFromExternal(any(), any(), anyString());
                // Should enrich existing lead
                verify(deduplicationService, times(1)).enrichAndSaveMatchedLead(eq(existingLead), any(),
                                eq(matchResult), eq(superadminUser), anyString());

                ArgumentCaptor<GoogleAdsLeadEvent> eventCaptor = ArgumentCaptor.forClass(GoogleAdsLeadEvent.class);
                verify(eventRepository).save(eventCaptor.capture());
                assertEquals("DUPLICATE_ENRICHED", eventCaptor.getValue().getProcessingStatus());
                assertEquals(existingLead.getId(), eventCaptor.getValue().getLinkedLeadId());
        }

        @Test
        @DisplayName("Should handle duplicate submission idempotently without creating another lead")
        void shouldHandleDuplicateSubmissionIdempotently() throws Exception {
                GoogleAdsWebhookRequest request = GoogleAdsWebhookRequest.builder()
                                .leadId("LEAD_ALREADY_EXISTS")
                                .googleKey("valid_key")
                                .isTest(false)
                                .build();

                doNothing().when(validator).validate(anyString());

                GoogleAdsLeadEvent existingEvent = GoogleAdsLeadEvent.builder()
                                .externalLeadId("LEAD_ALREADY_EXISTS")
                                .processingStatus("PROCESSED")
                                .build();
                when(eventRepository.findByProviderAndExternalLeadId("GOOGLE_ADS", "LEAD_ALREADY_EXISTS"))
                                .thenReturn(Optional.of(existingEvent));

                assertDoesNotThrow(() -> webhookService.processWebhook(request, "{}"));

                // Must not touch lead creation or deduplication
                verify(leadService, never()).createLeadFromExternal(any(), any(), anyString());
                verify(deduplicationService, never()).matchLead(any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should handle test submission by recording event without creating production lead")
        void shouldHandleTestSubmissionCorrectly() throws Exception {
                GoogleAdsWebhookRequest request = GoogleAdsWebhookRequest.builder()
                                .leadId("TEST_LEAD_999")
                                .googleKey("valid_key")
                                .isTest(true)
                                .formId("FORM_TEST")
                                .campaignId("CAMP_TEST")
                                .userColumnData(List.of(
                                                new GoogleAdsWebhookRequest.UserColumnData("FULL_NAME", "Test User",
                                                                null),
                                                new GoogleAdsWebhookRequest.UserColumnData("PHONE_NUMBER",
                                                                "+919999999999", null)))
                                .build();

                doNothing().when(validator).validate(anyString());
                when(eventRepository.findByProviderAndExternalLeadId("GOOGLE_ADS", "TEST_LEAD_999"))
                                .thenReturn(Optional.empty());

                assertDoesNotThrow(() -> webhookService.processWebhook(request, "{\"google_key\":\"valid_key\"}"));

                // Must not touch lead creation
                verify(leadService, never()).createLeadFromExternal(any(), any(), anyString());
                verify(deduplicationService, never()).matchLead(any(), any(), any(), any(), any(), any());

                ArgumentCaptor<GoogleAdsLeadEvent> eventCaptor = ArgumentCaptor.forClass(GoogleAdsLeadEvent.class);
                verify(eventRepository).save(eventCaptor.capture());
                GoogleAdsLeadEvent event = eventCaptor.getValue();
                assertEquals("TEST_LEAD_999", event.getExternalLeadId());
                assertTrue(event.isTest());
                assertEquals("TEST_RECORDED", event.getProcessingStatus());
        }

        @Test
        @DisplayName("Should throw BadRequestException when lead_id is missing")
        void shouldThrowWhenLeadIdMissing() {
                GoogleAdsWebhookRequest request = GoogleAdsWebhookRequest.builder()
                                .leadId(null)
                                .googleKey("valid_key")
                                .build();

                doNothing().when(validator).validate(anyString());

                assertThrows(BadRequestException.class, () -> webhookService.processWebhook(request, "{}"));
        }

        @Test
        @DisplayName("Should throw BadRequestException when PHONE_NUMBER is missing for production lead")
        void shouldThrowWhenPhoneMissing() {
                GoogleAdsWebhookRequest request = GoogleAdsWebhookRequest.builder()
                                .leadId("LEAD_NO_PHONE")
                                .googleKey("valid_key")
                                .isTest(false)
                                .userColumnData(List.of(
                                                new GoogleAdsWebhookRequest.UserColumnData("FULL_NAME", "No Phone User",
                                                                null)))
                                .build();

                doNothing().when(validator).validate(anyString());
                when(eventRepository.findByProviderAndExternalLeadId("GOOGLE_ADS", "LEAD_NO_PHONE"))
                                .thenReturn(Optional.empty());

                assertThrows(BadRequestException.class, () -> webhookService.processWebhook(request, "{}"));
        }

        @Test
        @DisplayName("Should successfully persist Google Ads lead with 224+ char hex lead_id")
        void shouldSuccessfullyProcessLongGoogleAdsLeadId_224Chars() throws BadRequestException {
                String longLeadId = "01466bbee417a0957dd509adf15301e10f3f020d3e751b59a7d2102258c98d28d8ff9147e527157b6cde9c4eb23e7783c6346b65ee752396ce29f7fe383f28bc1cec6b231a0b88293e4ed528a04854f3105ecf45851cfff21e88efc82c614a086f918fb04dd39ae17306a717a0020099";

                GoogleAdsWebhookRequest request = GoogleAdsWebhookRequest.builder()
                                .leadId(longLeadId)
                                .googleKey("valid_key")
                                .formId("form_456")
                                .campaignId("camp_789")
                                .adgroupId("adgroup_101")
                                .creativeId("creative_202")
                                .gclId("gclid_test_long_hash_value")
                                .leadSource("google_search")
                                .leadSubmitTime("2026-11-05 14:00:12")
                                .isTest(false)
                                .userColumnData(List.of(
                                                new GoogleAdsWebhookRequest.UserColumnData("FULL_NAME", "Real Applicant", null),
                                                new GoogleAdsWebhookRequest.UserColumnData("PHONE_NUMBER", "+919876543210", null),
                                                new GoogleAdsWebhookRequest.UserColumnData("EMAIL", "applicant@gmail.com", null)
                                ))
                                .build();

                doNothing().when(validator).validate(anyString());
                when(eventRepository.findByProviderAndExternalLeadId("GOOGLE_ADS", longLeadId))
                                .thenReturn(Optional.empty());

                LeadSource leadSource = LeadSource.builder().name("Google Ads").code("GOOGLE_ADS").build();
                leadSource.setId(UUID.randomUUID());
                when(leadSourceRepository.findByCodeIgnoreCaseAndIsDeletedFalse("GOOGLE_ADS"))
                                .thenReturn(Optional.of(leadSource));

                when(deduplicationService.normalizePhone("+919876543210")).thenReturn("9876543210");
                when(deduplicationService.executeWithPhoneLock(eq("9876543210"), any()))
                                .thenReturn(LeadMatchResult.noMatch());
                when(userRepository.findByUsername("superadmin")).thenReturn(Optional.of(superadminUser));

                UUID createdId = UUID.randomUUID();
                LeadResponse createdResp = LeadResponse.builder().id(createdId).leadCode("LEAD-41167AF1").build();
                when(leadService.createLeadFromExternal(any(LeadRequest.class), any(), anyString()))
                                .thenReturn(createdResp);

                webhookService.processWebhook(request, "{\"lead_id\":\"" + longLeadId + "\",\"google_key\":\"valid_key\"}");

                ArgumentCaptor<GoogleAdsLeadEvent> eventCaptor = ArgumentCaptor.forClass(GoogleAdsLeadEvent.class);
                verify(eventRepository).save(eventCaptor.capture());
                GoogleAdsLeadEvent saved = eventCaptor.getValue();

                assertEquals(longLeadId, saved.getExternalLeadId());
                assertEquals(224, saved.getExternalLeadId().length());
                assertEquals("PROCESSED", saved.getProcessingStatus());
                assertEquals("LEAD-41167AF1", saved.getLeadCode());
                assertEquals("Real Applicant", saved.getFullName());
                assertEquals("+919876543210", saved.getMobile());
                assertEquals("applicant@gmail.com", saved.getEmail());
        }
}