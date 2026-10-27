package com.app.datadistribution.integration.meta;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.app.datadistribution.dto.lead.LeadRequest;
import com.app.datadistribution.dto.lead.LeadResponse;
import com.app.datadistribution.entity.Course;
import com.app.datadistribution.entity.LeadSource;
import com.app.datadistribution.entity.Program;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.integration.meta.config.MetaProperties;
import com.app.datadistribution.integration.meta.dto.MetaLeadFieldDataDTO;
import com.app.datadistribution.integration.meta.dto.MetaLeadResponseDTO;
import com.app.datadistribution.integration.meta.dto.MetaWebhookPayloadDTO;
import com.app.datadistribution.integration.meta.entity.MetaWebhookEvent;
import com.app.datadistribution.integration.meta.entity.MetaWebhookEventStatus;
import com.app.datadistribution.integration.meta.exception.MetaGraphApiException;
import com.app.datadistribution.integration.meta.repository.MetaWebhookEventRepository;
import com.app.datadistribution.integration.meta.service.impl.MetaLeadIngestionServiceImpl;
import com.app.datadistribution.integration.meta.service.interfaces.IMetaGraphApiService;
import com.app.datadistribution.repository.CourseRepository;
import com.app.datadistribution.repository.LeadSourceRepository;
import com.app.datadistribution.repository.ProgramRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.interfaces.ILeadService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetaLeadIngestionServiceTest {

    @Mock
    private MetaProperties metaProperties;

    @Mock
    private MetaWebhookEventRepository eventRepository;

    @Mock
    private IMetaGraphApiService metaGraphApiService;

    @Mock
    private ILeadService leadService;

    @Mock
    private LeadSourceRepository leadSourceRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private ProgramRepository programRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private MetaLeadIngestionServiceImpl ingestionService;

    private User systemUser;
    private LeadSource metaSource;

    @BeforeEach
    void setUp() {
        systemUser = User.builder()
                .username("superadmin")
                .email("superadmin@datadistribution.com")
                .build();
        systemUser.setId(UUID.randomUUID());

        metaSource = LeadSource.builder()
                .name("Meta Ads")
                .code("META_ADS")
                .build();
        metaSource.setId(UUID.randomUUID());
    }

    @Test
    @DisplayName("Deduplication: Should return existing event without re-saving when leadgen_id exists")
    void shouldReturnExistingEventWhenDuplicate() {
        MetaWebhookPayloadDTO.Value val = new MetaWebhookPayloadDTO.Value();
        val.setLeadgenId("100200300");

        MetaWebhookEvent existingEvent = MetaWebhookEvent.builder()
                .leadgenId("100200300")
                .status(MetaWebhookEventStatus.PROCESSED)
                .build();

        when(eventRepository.findByLeadgenId("100200300")).thenReturn(Optional.of(existingEvent));

        MetaWebhookEvent result = ingestionService.recordWebhookEvent(val, "{}");

        assertEquals(existingEvent, result);
        verify(eventRepository, never()).save(any(MetaWebhookEvent.class));
    }

    @Test
    @DisplayName("Lead Ingestion: Should retrieve lead, map fields, and create lead in DDS")
    void shouldProcessEventSuccessfully() throws BadRequestException {
        MetaWebhookEvent event = MetaWebhookEvent.builder()
                .leadgenId("100200300")
                .formId("form_abc")
                .adId("ad_xyz")
                .status(MetaWebhookEventStatus.PENDING)
                .maxRetries(5)
                .retryCount(0)
                .build();

        MetaLeadResponseDTO leadDto = MetaLeadResponseDTO.builder()
                .id("100200300")
                .formId("form_abc")
                .adId("ad_xyz")
                .adsetId("adset_123")
                .campaignId("campaign_456")
                .platform("fb")
                .fieldData(List.of(
                        new MetaLeadFieldDataDTO("full_name", List.of("Aarav Sharma")),
                        new MetaLeadFieldDataDTO("phone_number", List.of("+91 9876543210")),
                        new MetaLeadFieldDataDTO("email", List.of("aarav.sharma@example.com")),
                        new MetaLeadFieldDataDTO("city", List.of("Indore")),
                        new MetaLeadFieldDataDTO("state", List.of("Madhya Pradesh")),
                        new MetaLeadFieldDataDTO("what_course_are_you_interested_in?", List.of("B.Tech Computer Science"))
                ))
                .build();

        Course matchedCourse = Course.builder()
                .courseName("B.Tech Computer Science")
                .courseCode("BT_CS")
                .status(com.app.datadistribution.enums.Status.ACTIVE)
                .build();
        matchedCourse.setId(UUID.randomUUID());

        when(metaGraphApiService.retrieveLead("100200300")).thenReturn(leadDto);
        when(courseRepository.findByCourseNameIgnoreCaseAndIsDeletedFalse("B.Tech Computer Science"))
                .thenReturn(Optional.of(matchedCourse));
        when(leadSourceRepository.findByCodeIgnoreCase("META_ADS")).thenReturn(Optional.of(metaSource));
        when(metaProperties.getSystemUsername()).thenReturn("superadmin");
        when(userRepository.findByUsername("superadmin")).thenReturn(Optional.of(systemUser));

        UUID createdLeadId = UUID.randomUUID();
        LeadResponse createdLeadResponse = LeadResponse.builder()
                .id(createdLeadId)
                .leadCode("LEAD-TEST1234")
                .build();
        when(leadService.createLeadFromExternal(any(LeadRequest.class), eq(systemUser)))
                .thenReturn(createdLeadResponse);

        ingestionService.processEvent(event);

        ArgumentCaptor<LeadRequest> requestCaptor = ArgumentCaptor.forClass(LeadRequest.class);
        verify(leadService).createLeadFromExternal(requestCaptor.capture(), eq(systemUser));

        LeadRequest captured = requestCaptor.getValue();
        assertEquals("Aarav Sharma", captured.getFullName());
        assertEquals("+919876543210", captured.getPhoneNumber());
        assertEquals("aarav.sharma@example.com", captured.getEmail());
        assertEquals("Indore", captured.getCity());
        assertEquals("Madhya Pradesh", captured.getState());
        assertEquals(matchedCourse.getId(), captured.getCourseId());
        assertTrue(captured.getLeadSourceIds().contains(metaSource.getId()));
        assertTrue(captured.getRemarks().contains("[Meta Lead Ads Integration]"));
        assertTrue(captured.getRemarks().contains("Ad Set ID: adset_123"));
        assertTrue(captured.getRemarks().contains("Campaign ID: campaign_456"));

        assertEquals(MetaWebhookEventStatus.PROCESSED, event.getStatus());
        assertEquals(createdLeadId, event.getLeadId());
        assertEquals("LEAD-TEST1234", event.getLeadCode());
        assertEquals("adset_123", event.getAdgroupId()); // Enriched from leadDto
        assertNull(event.getErrorMessage());
        assertNull(event.getNextRetryAt());
    }

    @Test
    @DisplayName("Permanent Failure: Non-retryable error (e.g. Code 100 invalid field) immediately transitions to FAILED_PERMANENTLY")
    void shouldTransitionImmediatelyToFailedPermanentlyOnNonRetryableError() {
        MetaWebhookEvent event = MetaWebhookEvent.builder()
                .leadgenId("fail_code_100")
                .status(MetaWebhookEventStatus.PENDING)
                .maxRetries(5)
                .retryCount(0)
                .build();

        MetaGraphApiException nonRetryableEx = new MetaGraphApiException(
                400, 100, null, "OAuthException",
                "(#100) Tried accessing nonexisting field (adgroup_id)", false, null
        );

        when(metaGraphApiService.retrieveLead("fail_code_100")).thenThrow(nonRetryableEx);

        ingestionService.processEvent(event);

        assertEquals(MetaWebhookEventStatus.FAILED_PERMANENTLY, event.getStatus());
        assertEquals(1, event.getRetryCount());
        assertNull(event.getNextRetryAt(), "Non-retryable errors must not schedule a next retry");
        assertNotNull(event.getErrorMessage());
        assertTrue(event.getErrorMessage().contains("Code 100"));
        assertTrue(event.getErrorMessage().contains("Tried accessing nonexisting field"));
    }

    @Test
    @DisplayName("Retry and Exponential Backoff: Transient retryable failure schedules next retry")
    void shouldScheduleRetryOnTransientFailure() {
        MetaWebhookEvent event = MetaWebhookEvent.builder()
                .leadgenId("fail_transient_123")
                .status(MetaWebhookEventStatus.PENDING)
                .maxRetries(5)
                .retryCount(0)
                .build();

        MetaGraphApiException retryableEx = new MetaGraphApiException(
                504, null, null, "NetworkTimeout",
                "Network timeout connecting to Meta Graph API", true, null
        );

        when(metaGraphApiService.retrieveLead("fail_transient_123")).thenThrow(retryableEx);

        ingestionService.processEvent(event);

        assertEquals(MetaWebhookEventStatus.FAILED, event.getStatus());
        assertEquals(1, event.getRetryCount());
        assertNotNull(event.getNextRetryAt(), "Retryable errors must schedule a next retry");
        assertTrue(event.getErrorMessage().contains("Network timeout"));
    }

    @Test
    @DisplayName("Max Retries: Should mark FAILED_PERMANENTLY when retryCount reaches maxRetries")
    void shouldMarkFailedPermanentlyWhenMaxRetriesExceeded() {
        MetaWebhookEvent event = MetaWebhookEvent.builder()
                .leadgenId("fail_permanent")
                .status(MetaWebhookEventStatus.FAILED)
                .maxRetries(3)
                .retryCount(2)
                .build();

        MetaGraphApiException retryableEx = new MetaGraphApiException(
                500, null, null, "ServerError",
                "Meta Graph API temporary server failure", true, null
        );

        when(metaGraphApiService.retrieveLead("fail_permanent")).thenThrow(retryableEx);

        ingestionService.processEvent(event);

        assertEquals(MetaWebhookEventStatus.FAILED_PERMANENTLY, event.getStatus());
        assertEquals(3, event.getRetryCount());
        assertNull(event.getNextRetryAt());
    }

    @Test
    @DisplayName("Administrative Retry: retryEvent should reset status to PENDING and reprocess event")
    void shouldAdministrativelyRetrySingleEvent() throws BadRequestException {
        UUID eventId = UUID.randomUUID();
        MetaWebhookEvent event = MetaWebhookEvent.builder()
                .leadgenId("retry_leadgen_1")
                .status(MetaWebhookEventStatus.FAILED_PERMANENTLY)
                .errorMessage("Old error")
                .maxRetries(5)
                .retryCount(1)
                .build();
        event.setId(eventId);

        MetaLeadResponseDTO leadDto = MetaLeadResponseDTO.builder()
                .id("retry_leadgen_1")
                .fieldData(List.of(new MetaLeadFieldDataDTO("full_name", List.of("Admin Retried"))))
                .build();

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(metaGraphApiService.retrieveLead("retry_leadgen_1")).thenReturn(leadDto);
        when(leadSourceRepository.findByCodeIgnoreCase("META_ADS")).thenReturn(Optional.of(metaSource));
        when(metaProperties.getSystemUsername()).thenReturn("superadmin");
        when(userRepository.findByUsername("superadmin")).thenReturn(Optional.of(systemUser));

        when(leadService.createLeadFromExternal(any(LeadRequest.class), eq(systemUser)))
                .thenReturn(LeadResponse.builder().id(UUID.randomUUID()).leadCode("LEAD-RETRY-01").build());

        boolean result = ingestionService.retryEvent(eventId);

        assertTrue(result);
        assertEquals(MetaWebhookEventStatus.PROCESSED, event.getStatus());
        assertEquals("LEAD-RETRY-01", event.getLeadCode());
        assertNull(event.getErrorMessage());
    }

    @Test
    @DisplayName("Administrative Retry: retryEvent should not reprocess if already PROCESSED")
    void shouldNotRetryAlreadyProcessedEvent() {
        UUID eventId = UUID.randomUUID();
        MetaWebhookEvent event = MetaWebhookEvent.builder()
                .leadgenId("already_done")
                .status(MetaWebhookEventStatus.PROCESSED)
                .leadId(UUID.randomUUID())
                .leadCode("LEAD-EXISTING")
                .build();
        event.setId(eventId);

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        boolean result = ingestionService.retryEvent(eventId);

        assertFalse(result, "Already processed events must not be retried");
        verify(metaGraphApiService, never()).retrieveLead(any());
    }

    @Test
    @DisplayName("Administrative Bulk Retry: retryFailedEvents should reprocess all unlinked failed events")
    void shouldAdministrativelyRetryAllFailedEvents() throws BadRequestException {
        MetaWebhookEvent event1 = MetaWebhookEvent.builder()
                .leadgenId("failed_1")
                .status(MetaWebhookEventStatus.FAILED_PERMANENTLY)
                .maxRetries(5)
                .retryCount(1)
                .build();

        MetaWebhookEvent event2 = MetaWebhookEvent.builder()
                .leadgenId("already_processed_in_list")
                .status(MetaWebhookEventStatus.FAILED)
                .leadId(UUID.randomUUID()) // already has lead
                .build();

        when(eventRepository.findAllByStatusInAndIsDeletedFalse(any())).thenReturn(List.of(event1, event2));

        MetaLeadResponseDTO leadDto = MetaLeadResponseDTO.builder()
                .id("failed_1")
                .fieldData(List.of(new MetaLeadFieldDataDTO("full_name", List.of("Lead One"))))
                .build();

        when(metaGraphApiService.retrieveLead("failed_1")).thenReturn(leadDto);
        when(leadSourceRepository.findByCodeIgnoreCase("META_ADS")).thenReturn(Optional.of(metaSource));
        when(metaProperties.getSystemUsername()).thenReturn("superadmin");
        when(userRepository.findByUsername("superadmin")).thenReturn(Optional.of(systemUser));
        when(leadService.createLeadFromExternal(any(), eq(systemUser)))
                .thenReturn(LeadResponse.builder().id(UUID.randomUUID()).leadCode("LEAD-BULK-1").build());

        int count = ingestionService.retryFailedEvents();

        assertEquals(1, count);
        assertEquals(MetaWebhookEventStatus.PROCESSED, event1.getStatus());
        assertEquals("LEAD-BULK-1", event1.getLeadCode());
    }

    @Test
    @DisplayName("Academic Resolution: Should not guess course when not matched, preserving inquiry in remarks")
    void shouldNotGuessCourseWhenNotFoundInCatalog() throws BadRequestException {
        MetaWebhookEvent event = MetaWebhookEvent.builder()
                .leadgenId("888777")
                .status(MetaWebhookEventStatus.PENDING)
                .maxRetries(5)
                .retryCount(0)
                .build();

        MetaLeadResponseDTO leadDto = MetaLeadResponseDTO.builder()
                .id("888777")
                .fieldData(List.of(
                        new MetaLeadFieldDataDTO("full_name", List.of("Priya Verma")),
                        new MetaLeadFieldDataDTO("phone_number", List.of("9876500000")),
                        new MetaLeadFieldDataDTO("course", List.of("Unknown Exotic Subject"))
                ))
                .build();

        when(metaGraphApiService.retrieveLead("888777")).thenReturn(leadDto);
        when(courseRepository.findByCourseNameIgnoreCaseAndIsDeletedFalse("Unknown Exotic Subject"))
                .thenReturn(Optional.empty());
        when(courseRepository.findByCourseCodeIgnoreCaseAndIsDeletedFalse("Unknown Exotic Subject"))
                .thenReturn(Optional.empty());
        when(programRepository.findByNameIgnoreCaseAndIsDeletedFalse("Unknown Exotic Subject"))
                .thenReturn(Optional.empty());
        when(programRepository.findByCodeIgnoreCaseAndIsDeletedFalse("Unknown Exotic Subject"))
                .thenReturn(Optional.empty());
        when(leadSourceRepository.findByCodeIgnoreCase("META_ADS")).thenReturn(Optional.of(metaSource));
        when(metaProperties.getSystemUsername()).thenReturn("superadmin");
        when(userRepository.findByUsername("superadmin")).thenReturn(Optional.of(systemUser));

        when(leadService.createLeadFromExternal(any(LeadRequest.class), eq(systemUser)))
                .thenReturn(LeadResponse.builder().id(UUID.randomUUID()).leadCode("LEAD-PRIYA").build());

        ingestionService.processEvent(event);

        ArgumentCaptor<LeadRequest> requestCaptor = ArgumentCaptor.forClass(LeadRequest.class);
        verify(leadService).createLeadFromExternal(requestCaptor.capture(), eq(systemUser));

        LeadRequest captured = requestCaptor.getValue();
        assertNull(captured.getCourseId());
        assertNull(captured.getProgramId());
        assertTrue(captured.getRemarks().contains("Course Inquiry: \"Unknown Exotic Subject\" (No direct catalog match - review manually)"));
    }
}
