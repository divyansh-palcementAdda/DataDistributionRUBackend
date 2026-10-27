package com.app.datadistribution.integration.website;

import com.app.datadistribution.dto.lead.LeadRequest;
import com.app.datadistribution.dto.lead.LeadResponse;
import com.app.datadistribution.entity.Course;
import com.app.datadistribution.entity.LeadSource;
import com.app.datadistribution.entity.Program;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.integration.website.config.WebsiteIntegrationProperties;
import com.app.datadistribution.integration.website.dto.WebsiteLeadRequestDTO;
import com.app.datadistribution.integration.website.dto.WebsiteLeadResponseDTO;
import com.app.datadistribution.integration.website.dto.WebsiteSourceCode;
import com.app.datadistribution.integration.website.entity.WebsiteLeadSubmission;
import com.app.datadistribution.integration.website.repository.WebsiteLeadSubmissionRepository;
import com.app.datadistribution.integration.website.service.impl.WebsiteLeadServiceImpl;
import com.app.datadistribution.repository.CourseRepository;
import com.app.datadistribution.repository.LeadSourceRepository;
import com.app.datadistribution.repository.ProgramRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.interfaces.ILeadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebsiteLeadServiceTest {

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

        @Mock
        private WebsiteLeadSubmissionRepository submissionRepository;

        @Mock
        private WebsiteIntegrationProperties properties;

        @InjectMocks
        private WebsiteLeadServiceImpl websiteLeadService;

        private LeadSource feesInquirySource;
        private User testSuperAdmin;

        @BeforeEach
        void setUp() {
                feesInquirySource = LeadSource.builder()
                                .name("Website - Fees Inquiry")
                                .code("WEBSITE_FEES_INQUIRY")
                                .active(true)
                                .build();
                feesInquirySource.setId(UUID.randomUUID());

                testSuperAdmin = User.builder()
                                .username("superadmin")
                                .email("superadmin@example.com")
                                .build();
                testSuperAdmin.setId(UUID.randomUUID());
        }

        @Test
        @DisplayName("Should successfully ingest website lead and call canonical lead creation service")
        void shouldIngestLeadSuccessfully() throws BadRequestException {
                when(leadSourceRepository.findByCodeIgnoreCaseAndIsDeletedFalse("WEBSITE_FEES_INQUIRY"))
                                .thenReturn(Optional.of(feesInquirySource));
                when(submissionRepository.findByExternalLeadIdAndSourceCode("fees-123", "WEBSITE_FEES_INQUIRY"))
                                .thenReturn(Optional.empty());
                when(properties.getSystemUsername()).thenReturn("superadmin");
                when(userRepository.findByUsername("superadmin")).thenReturn(Optional.of(testSuperAdmin));

                UUID createdLeadId = UUID.randomUUID();
                LeadResponse mockResponse = LeadResponse.builder()
                                .id(createdLeadId)
                                .leadCode("LEAD-FEES01")
                                .fullName("Rahul Sharma")
                                .phoneNumber("9876543210")
                                .build();

                when(leadService.createLeadFromExternal(any(LeadRequest.class), eq(testSuperAdmin), anyString()))
                                .thenReturn(mockResponse);

                WebsiteLeadRequestDTO request = WebsiteLeadRequestDTO.builder()
                                .sourceCode("WEBSITE_FEES_INQUIRY")
                                .externalLeadId("fees-123")
                                .name("Rahul Sharma")
                                .mobile("9876543210")
                                .email("rahul@example.com")
                                .courseInterested("MBA")
                                .city("Indore")
                                .state("Madhya Pradesh")
                                .websiteUrl("https://example.edu/fees")
                                .consent(true)
                                .build();

                WebsiteLeadResponseDTO result = websiteLeadService.ingestLead(request, "127.0.0.1");

                assertNotNull(result);
                assertEquals("CREATED", result.getStatus());
                assertEquals(createdLeadId, result.getLeadId());
                assertEquals("LEAD-FEES01", result.getLeadCode());
                assertEquals("WEBSITE_FEES_INQUIRY", result.getSourceCode());
                assertEquals("fees-123", result.getExternalLeadId());

                // Verify canonical LeadRequest values
                ArgumentCaptor<LeadRequest> captor = ArgumentCaptor.forClass(LeadRequest.class);
                verify(leadService).createLeadFromExternal(captor.capture(), eq(testSuperAdmin),
                                eq("Lead submitted via Website - Fees Inquiry."));
                LeadRequest captured = captor.getValue();
                assertEquals("Rahul Sharma", captured.getFullName());
                assertEquals("9876543210", captured.getPhoneNumber());
                assertEquals("rahul@example.com", captured.getEmail());
                assertTrue(captured.getLeadSourceIds().contains(feesInquirySource.getId()));
                verify(submissionRepository).save(any(WebsiteLeadSubmission.class));
        }

        @Test
        @DisplayName("Idempotency: Re-submitting same externalLeadId should return EXISTING_SUBMISSION without creating duplicate lead")
        void shouldReturnExistingSubmissionOnDuplicateRetry() throws BadRequestException {
                when(leadSourceRepository.findByCodeIgnoreCaseAndIsDeletedFalse("WEBSITE_FEES_INQUIRY"))
                                .thenReturn(Optional.of(feesInquirySource));

                UUID existingLeadId = UUID.randomUUID();
                WebsiteLeadSubmission existingSubmission = WebsiteLeadSubmission.builder()
                                .externalLeadId("fees-123")
                                .sourceCode("WEBSITE_FEES_INQUIRY")
                                .leadId(existingLeadId)
                                .leadCode("LEAD-FEES01")
                                .status("CREATED")
                                .build();

                when(submissionRepository.findByExternalLeadIdAndSourceCode("fees-123", "WEBSITE_FEES_INQUIRY"))
                                .thenReturn(Optional.of(existingSubmission));

                WebsiteLeadRequestDTO request = WebsiteLeadRequestDTO.builder()
                                .sourceCode("WEBSITE_FEES_INQUIRY")
                                .externalLeadId("fees-123")
                                .name("Rahul Sharma")
                                .mobile("9876543210")
                                .build();

                WebsiteLeadResponseDTO result = websiteLeadService.ingestLead(request, "127.0.0.1");

                assertNotNull(result);
                assertEquals("EXISTING_SUBMISSION", result.getStatus());
                assertEquals(existingLeadId, result.getLeadId());
                assertEquals("LEAD-FEES01", result.getLeadCode());

                // Verify lead creation service was NOT called
                verify(leadService, never()).createLeadFromExternal(any(), any(), any());
        }

        @Test
        @DisplayName("Unsupported sourceCode should be rejected with BadRequestException")
        void shouldRejectUnsupportedSourceCode() throws BadRequestException {
                when(leadSourceRepository.findByCodeIgnoreCaseAndIsDeletedFalse("INVALID_UNKNOWN_CODE"))
                                .thenReturn(Optional.empty());

                WebsiteLeadRequestDTO request = WebsiteLeadRequestDTO.builder()
                                .sourceCode("INVALID_UNKNOWN_CODE")
                                .name("Test User")
                                .mobile("9876543210")
                                .build();

                BadRequestException ex = assertThrows(
                                BadRequestException.class,
                                () -> websiteLeadService.ingestLead(request, "127.0.0.1"));
                assertTrue(ex.getMessage().contains("Unsupported or invalid website source code"));
                verify(leadService, never()).createLeadFromExternal(any(), any(), any());
        }

        @Test
        @DisplayName("Inactive LeadSource should be rejected with BadRequestException")
        void shouldRejectInactiveSourceCode() {
                LeadSource inactiveSource = LeadSource.builder()
                                .name("Website - Newspaper")
                                .code("WEBSITE_NEWSPAPER")
                                .active(false)
                                .build();

                when(leadSourceRepository.findByCodeIgnoreCaseAndIsDeletedFalse("WEBSITE_NEWSPAPER"))
                                .thenReturn(Optional.of(inactiveSource));

                WebsiteLeadRequestDTO request = WebsiteLeadRequestDTO.builder()
                                .sourceCode("WEBSITE_NEWSPAPER")
                                .name("Test User")
                                .mobile("9876543210")
                                .build();

                BadRequestException ex = assertThrows(
                                BadRequestException.class,
                                () -> websiteLeadService.ingestLead(request, "127.0.0.1"));
                assertTrue(ex.getMessage().contains("currently inactive"));
        }

        @Test
        @DisplayName("Campus Tour should automatically set planningToVisitUniversity to true")
        void shouldSetVisitPlanningForCampusTour() throws BadRequestException {
                LeadSource campusTourSource = LeadSource.builder()
                                .name("Website - Campus Tour")
                                .code("WEBSITE_CAMPUS_TOUR")
                                .active(true)
                                .build();
                campusTourSource.setId(UUID.randomUUID());

                when(leadSourceRepository.findByCodeIgnoreCaseAndIsDeletedFalse("WEBSITE_CAMPUS_TOUR"))
                                .thenReturn(Optional.of(campusTourSource));
                when(properties.getSystemUsername()).thenReturn("superadmin");
                when(userRepository.findByUsername("superadmin")).thenReturn(Optional.of(testSuperAdmin));

                LeadResponse mockResponse = LeadResponse.builder()
                                .id(UUID.randomUUID())
                                .leadCode("LEAD-TOUR01")
                                .build();
                when(leadService.createLeadFromExternal(any(), any(), any())).thenReturn(mockResponse);

                WebsiteLeadRequestDTO request = WebsiteLeadRequestDTO.builder()
                                .sourceCode("WEBSITE_CAMPUS_TOUR")
                                .name("Anita Patel")
                                .mobile("9876543210")
                                .build();

                websiteLeadService.ingestLead(request, "127.0.0.1");

                ArgumentCaptor<LeadRequest> captor = ArgumentCaptor.forClass(LeadRequest.class);
                verify(leadService).createLeadFromExternal(captor.capture(), eq(testSuperAdmin), anyString());
                assertTrue(captor.getValue().getPlanningToVisitUniversity());
        }
}
