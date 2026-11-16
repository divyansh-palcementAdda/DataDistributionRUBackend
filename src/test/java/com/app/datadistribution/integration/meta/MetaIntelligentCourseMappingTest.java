package com.app.datadistribution.integration.meta;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import com.app.datadistribution.dto.lead.LeadMatchResult;
import com.app.datadistribution.dto.lead.LeadRequest;
import com.app.datadistribution.dto.lead.LeadResponse;
import com.app.datadistribution.entity.Course;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadSource;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.Status;
import com.app.datadistribution.integration.meta.config.MetaProperties;
import com.app.datadistribution.integration.meta.dto.MetaLeadFieldDataDTO;
import com.app.datadistribution.integration.meta.dto.MetaLeadResponseDTO;
import com.app.datadistribution.integration.meta.entity.MetaWebhookEvent;
import com.app.datadistribution.integration.meta.entity.MetaWebhookEventStatus;
import com.app.datadistribution.integration.meta.repository.MetaWebhookEventRepository;
import com.app.datadistribution.integration.meta.service.MetaCampaignCourseResolver;
import com.app.datadistribution.integration.meta.service.MetaCampaignCourseResolver.MetaCourseResolutionResult;
import com.app.datadistribution.integration.meta.service.impl.MetaLeadIngestionServiceImpl;
import com.app.datadistribution.integration.meta.service.interfaces.IMetaGraphApiService;
import com.app.datadistribution.mapper.LeadMapper;
import com.app.datadistribution.repository.CourseRepository;
import com.app.datadistribution.repository.LeadSourceRepository;
import com.app.datadistribution.repository.ProgramRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.interfaces.ILeadDeduplicationService;
import com.app.datadistribution.service.interfaces.ILeadService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MetaIntelligentCourseMappingTest {

    @Mock
    private CourseRepository courseRepository;

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
    private ProgramRepository programRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ILeadDeduplicationService deduplicationService;

    @Mock
    private LeadMapper leadMapper;

    private MetaCampaignCourseResolver resolver;
    private MetaLeadIngestionServiceImpl ingestionService;

    private Course mbaCourse;
    private Course mscFoodCourse;
    private Course maPsyCourse;
    private Course bscCsCourse;
    private Course mfmCourse;
    private Course mDesignCourse;
    private Course bmltCourse;
    private Course dmltCourse;
    private Course dxrtCourse;

    private User systemUser;
    private LeadSource metaSource;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        mbaCourse = Course.builder().courseName("MBA Core").courseCode("MBA_CORE").status(Status.ACTIVE).build();
        mbaCourse.setId(UUID.randomUUID());

        mscFoodCourse = Course.builder().courseName("M.Sc Food Technology").courseCode("MSC_FOOD").status(Status.ACTIVE).build();
        mscFoodCourse.setId(UUID.randomUUID());

        maPsyCourse = Course.builder().courseName("MA Psychology").courseCode("MA_PSY").status(Status.ACTIVE).build();
        maPsyCourse.setId(UUID.randomUUID());

        bscCsCourse = Course.builder().courseName("B.Sc Computer Science").courseCode("BSC_CS").status(Status.ACTIVE).build();
        bscCsCourse.setId(UUID.randomUUID());

        mfmCourse = Course.builder().courseName("Master of Fashion Management").courseCode("MFM").status(Status.ACTIVE).build();
        mfmCourse.setId(UUID.randomUUID());

        mDesignCourse = Course.builder().courseName("Master of Design").courseCode("M_DES").status(Status.ACTIVE).build();
        mDesignCourse.setId(UUID.randomUUID());

        bmltCourse = Course.builder().courseName("Bachelor in Medical Laboratory Technology").courseCode("BMLT").status(Status.ACTIVE).build();
        bmltCourse.setId(UUID.randomUUID());

        dmltCourse = Course.builder().courseName("Diploma in Medical Laboratory Technology").courseCode("DMLT").status(Status.ACTIVE).build();
        dmltCourse.setId(UUID.randomUUID());

        dxrtCourse = Course.builder().courseName("Diploma in X-Ray Technology").courseCode("DXRT").status(Status.ACTIVE).build();
        dxrtCourse.setId(UUID.randomUUID());

        resolver = new MetaCampaignCourseResolver(courseRepository);
        ingestionService = new MetaLeadIngestionServiceImpl(
                metaProperties,
                eventRepository,
                metaGraphApiService,
                leadService,
                leadSourceRepository,
                courseRepository,
                programRepository,
                userRepository,
                resolver,
                deduplicationService,
                leadMapper
        );

        systemUser = User.builder().username("superadmin").email("superadmin@datadistribution.com").build();
        systemUser.setId(UUID.randomUUID());

        metaSource = LeadSource.builder().name("Meta Ads").code("META_ADS").build();
        metaSource.setId(UUID.randomUUID());

        lenient().when(courseRepository.findByStatusAndIsDeletedFalseOrderByCourseNameAsc(Status.ACTIVE))
                .thenReturn(List.of(
                        mbaCourse, mscFoodCourse, maPsyCourse, bscCsCourse,
                        mfmCourse, mDesignCourse, bmltCourse, dmltCourse, dxrtCourse
                ));

        lenient().when(metaProperties.getSystemUsername()).thenReturn("superadmin");
        lenient().when(userRepository.findByUsername("superadmin")).thenReturn(Optional.of(systemUser));
        lenient().when(leadSourceRepository.findByCodeIgnoreCase("META_ADS")).thenReturn(Optional.of(metaSource));
        lenient().when(courseRepository.findById(any())).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return List.of(mbaCourse, mscFoodCourse, maPsyCourse, bscCsCourse, mfmCourse, mDesignCourse, bmltCourse, dmltCourse, dxrtCourse)
                    .stream().filter(c -> c.getId().equals(id)).findFirst();
        });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // =========================================================================
    // SECTION 1: Single-course Campaign Tests (Phase 2 & Phase 10)
    // =========================================================================

    @Test
    @DisplayName("Single-course: RU_LG_MSc_Food_12-11-26 maps to M.Sc Food Technology")
    void testSingleCourseCampaign_MScFood() {
        MetaCourseResolutionResult result = resolver.resolve("RU_LG_MSc_Food_12-11-26", Collections.emptyList());
        assertTrue(result.isSuccess());
        assertEquals(mscFoodCourse.getId(), result.getCourse().getId());
        assertEquals(MetaCourseResolutionResult.ResolutionMethod.CAMPAIGN_NAME, result.getMappingMethod());
        assertFalse(result.isMultiCourseCampaign());
    }

    @Test
    @DisplayName("Single-course: RU_LG_MBA_CORE_12-11-26 maps to MBA Core")
    void testSingleCourseCampaign_MbaCore() {
        MetaCourseResolutionResult result = resolver.resolve("RU_LG_MBA_CORE_12-11-26", Collections.emptyList());
        assertTrue(result.isSuccess());
        assertEquals(mbaCourse.getId(), result.getCourse().getId());
        assertEquals(MetaCourseResolutionResult.ResolutionMethod.CAMPAIGN_NAME, result.getMappingMethod());
    }

    @Test
    @DisplayName("Single-course: RU_LG_MA_Psy_21-10-2026 maps to MA Psychology")
    void testSingleCourseCampaign_MaPsy() {
        MetaCourseResolutionResult result = resolver.resolve("RU_LG_MA_Psy_21-10-2026", Collections.emptyList());
        assertTrue(result.isSuccess());
        assertEquals(maPsyCourse.getId(), result.getCourse().getId());
    }

    @Test
    @DisplayName("Single-course: RU_BA_BSC_CS_12-10-2026 maps to B.Sc Computer Science")
    void testSingleCourseCampaign_BscCs() {
        MetaCourseResolutionResult result = resolver.resolve("RU_BA_BSC_CS_12-10-2026", Collections.emptyList());
        assertTrue(result.isSuccess());
        assertEquals(bscCsCourse.getId(), result.getCourse().getId());
    }

    @Test
    @DisplayName("Campaign prefixes and date suffixes do not affect single-course resolution")
    void testCampaignPrefixAndDateSuffixInsensitivity() {
        // Various date formats
        assertTrue(resolver.resolve("RU_LG_MBA_CORE_12-11-26", null).isSuccess());
        assertTrue(resolver.resolve("RU_BA_MBA_CORE 14 NOV", null).isSuccess());
        assertTrue(resolver.resolve("MBA_CORE_21-10-2026", null).isSuccess());
        assertTrue(resolver.resolve("MBA Core Admission 2027", null).isSuccess());
        assertTrue(resolver.resolve("MBA Core Lead Generation", null).isSuccess());
    }

    // =========================================================================
    // SECTION 2: Multi-course Campaign Tests (Phase 2 & Phase 10)
    // =========================================================================

    @Test
    @DisplayName("Multi-course: BMLT/DMLT/DXRT campaign with DMLT answer maps to DMLT")
    void testMultiCourseCampaign_WithDmltAnswer() {
        List<MetaLeadFieldDataDTO> fieldData = List.of(
                new MetaLeadFieldDataDTO("full_name", List.of("Rajesh Kumar")),
                new MetaLeadFieldDataDTO("phone_number", List.of("+919876543210")),
                new MetaLeadFieldDataDTO("Which program are you interested in?", List.of("DMLT"))
        );

        MetaCourseResolutionResult result = resolver.resolve("RU_LG_BMLT/DMLT/DXRT 14 NOV", fieldData);
        assertTrue(result.isSuccess());
        assertTrue(result.isMultiCourseCampaign());
        assertEquals(dmltCourse.getId(), result.getCourse().getId());
        assertEquals(MetaCourseResolutionResult.ResolutionMethod.FORM_ANSWER, result.getMappingMethod());
    }

    @Test
    @DisplayName("Multi-course: BMLT/DMLT/DXRT campaign with BMLT answer maps to BMLT")
    void testMultiCourseCampaign_WithBmltAnswer() {
        List<MetaLeadFieldDataDTO> fieldData = List.of(
                new MetaLeadFieldDataDTO("which_program_are_you_interested_in?", List.of("BMLT"))
        );

        MetaCourseResolutionResult result = resolver.resolve("RU_LG_BMLT/DMLT/DXRT 14 NOV", fieldData);
        assertTrue(result.isSuccess());
        assertEquals(bmltCourse.getId(), result.getCourse().getId());
        assertEquals(MetaCourseResolutionResult.ResolutionMethod.FORM_ANSWER, result.getMappingMethod());
    }

    @Test
    @DisplayName("Multi-course: BMLT/DMLT/DXRT campaign with DXRT answer maps to DXRT")
    void testMultiCourseCampaign_WithDxrtAnswer() {
        List<MetaLeadFieldDataDTO> fieldData = List.of(
                new MetaLeadFieldDataDTO("select your preferred course", List.of("DXRT"))
        );

        MetaCourseResolutionResult result = resolver.resolve("RU_LG_BMLT/DMLT/DXRT 14 NOV", fieldData);
        assertTrue(result.isSuccess());
        assertEquals(dxrtCourse.getId(), result.getCourse().getId());
        assertEquals(MetaCourseResolutionResult.ResolutionMethod.FORM_ANSWER, result.getMappingMethod());
    }

    @Test
    @DisplayName("Multi-course: Missing answer leaves course UNRESOLVED without picking random course")
    void testMultiCourseCampaign_MissingAnswer() {
        // Multi-course campaign, but no course question answered
        List<MetaLeadFieldDataDTO> fieldData = List.of(
                new MetaLeadFieldDataDTO("full_name", List.of("Anita Roy")),
                new MetaLeadFieldDataDTO("phone_number", List.of("9876543211"))
        );

        MetaCourseResolutionResult result = resolver.resolve("RU_LG_BMLT/DMLT/DXRT 14 NOV", fieldData);
        assertFalse(result.isSuccess());
        assertNull(result.getCourse());
        assertTrue(result.isMultiCourseCampaign());
        assertEquals(MetaCourseResolutionResult.ResolutionMethod.UNRESOLVED, result.getMappingMethod());
        assertEquals(MetaCourseResolutionResult.Status.MULTI_COURSE_MISSING_ANSWER, result.getStatus());
        assertTrue(result.getMappingReason().contains("Multi-course campaign"));
        assertTrue(result.getMappingReason().contains("answer is missing"));
    }

    @Test
    @DisplayName("Multi-course: Invalid/Unknown answer remains UNRESOLVED")
    void testMultiCourseCampaign_InvalidAnswer() {
        List<MetaLeadFieldDataDTO> fieldData = List.of(
                new MetaLeadFieldDataDTO("Which program are you interested in?", List.of("Aeronautical Engineering"))
        );

        MetaCourseResolutionResult result = resolver.resolve("RU_LG_BMLT/DMLT/DXRT 14 NOV", fieldData);
        assertFalse(result.isSuccess());
        assertNull(result.getCourse());
        assertEquals(MetaCourseResolutionResult.ResolutionMethod.UNRESOLVED, result.getMappingMethod());
        assertTrue(result.getMappingReason().contains("not found in Course Master"));
    }

    @Test
    @DisplayName("Multi-course: MFM/M Design campaign parses both courses")
    void testMultiCourseCampaign_MfmAndMDesign() {
        List<Course> candidates = resolver.extractCampaignCourseCandidates(
                "RU_BA_MFM/M Design_12-10-2026",
                List.of(mfmCourse, mDesignCourse, mbaCourse)
        );
        assertEquals(2, candidates.size());
        assertTrue(candidates.contains(mfmCourse));
        assertTrue(candidates.contains(mDesignCourse));
    }

    // =========================================================================
    // SECTION 3: Conflict Handling (Phase 2 & Phase 5)
    // =========================================================================

    @Test
    @DisplayName("Conflict: Single-course campaign indicated MBA Core but applicant selected B.Sc CS -> Prioritize Form")
    void testSingleCourseCampaign_ConflictResolution() {
        List<MetaLeadFieldDataDTO> fieldData = List.of(
                new MetaLeadFieldDataDTO("which course are you interested in", List.of("B.Sc Computer Science"))
        );

        MetaCourseResolutionResult result = resolver.resolve("RU_LG_MBA_CORE_12-11-26", fieldData);
        assertTrue(result.isSuccess());
        assertEquals(MetaCourseResolutionResult.Status.CONFLICT, result.getStatus());
        assertEquals(bscCsCourse.getId(), result.getCourse().getId());
        assertEquals(MetaCourseResolutionResult.ResolutionMethod.FORM_ANSWER, result.getMappingMethod());
        assertTrue(result.getMappingReason().contains("Conflict detected"));
        assertTrue(result.getMappingReason().contains("Prioritizing applicant form choice"));
    }

    // =========================================================================
    // SECTION 4: Ingestion Pipeline & Existing Lead Handling (Phase 6 & Phase 10)
    // =========================================================================

    @Test
    @DisplayName("Ingestion: New lead creates lead with resolved course and stores audit fields in event")
    void testNewLeadIngestion_SetsCourseAndEventAudit() throws Exception {
        MetaWebhookEvent event = MetaWebhookEvent.builder()
                .leadgenId("lead_new_123")
                .status(MetaWebhookEventStatus.PENDING)
                .maxRetries(5)
                .build();

        MetaLeadResponseDTO leadDto = MetaLeadResponseDTO.builder()
                .id("lead_new_123")
                .campaignName("RU_LG_BMLT/DMLT/DXRT 14 NOV")
                .fieldData(List.of(
                        new MetaLeadFieldDataDTO("full_name", List.of("Kavita Jain")),
                        new MetaLeadFieldDataDTO("phone_number", List.of("+919988776655")),
                        new MetaLeadFieldDataDTO("Which program are you interested in?", List.of("DMLT"))
                ))
                .build();

        when(metaGraphApiService.retrieveLead("lead_new_123")).thenReturn(leadDto);
        when(deduplicationService.normalizePhone("+919988776655")).thenReturn("+919988776655");
        when(deduplicationService.executeWithPhoneLock(anyString(), any())).thenAnswer(inv -> inv.getArgument(1, java.util.function.Supplier.class).get());
        when(deduplicationService.matchLead(any(), any(), any(), any(), any(), any()))
                .thenReturn(LeadMatchResult.builder().status(LeadMatchResult.MatchStatus.NO_MATCH).build());

        UUID generatedLeadId = UUID.randomUUID();
        when(leadService.createLeadFromExternal(any(LeadRequest.class), eq(systemUser)))
                .thenReturn(LeadResponse.builder().id(generatedLeadId).leadCode("LEAD-KAVITA").build());

        ingestionService.processEvent(event);

        // Verify lead creation call
        ArgumentCaptor<LeadRequest> captor = ArgumentCaptor.forClass(LeadRequest.class);
        verify(leadService).createLeadFromExternal(captor.capture(), eq(systemUser));
        LeadRequest captured = captor.getValue();

        assertEquals(dmltCourse.getId(), captured.getCourseId());
        assertEquals(List.of(dmltCourse.getId()), captured.getInterestedCourseIds());
        assertTrue(captured.getRemarks().contains("Interested Course: Diploma in Medical Laboratory Technology"));
        assertTrue(captured.getRemarks().contains("Course Mapping Method: FORM_ANSWER"));

        // Verify event audit fields
        assertEquals(MetaWebhookEventStatus.PROCESSED, event.getStatus());
        assertEquals(generatedLeadId, event.getLeadId());
        assertEquals("LEAD-KAVITA", event.getLeadCode());
        assertEquals("Which program are you interested in?", event.getCourseQuestion());
        assertEquals("DMLT", event.getCourseAnswer());
        assertEquals(dmltCourse.getId(), event.getResolvedCourseId());
        assertEquals("FORM_ANSWER", event.getMappingMethod());
        assertNotNull(event.getMappingReason());
    }

    @Test
    @DisplayName("Existing Lead Handling: Reuses existing lead, preserves counselor, does not duplicate")
    void testExistingLeadPreservedAndEnriched() throws Exception {
        MetaWebhookEvent event = MetaWebhookEvent.builder()
                .leadgenId("lead_existing_999")
                .status(MetaWebhookEventStatus.PENDING)
                .maxRetries(5)
                .build();

        MetaLeadResponseDTO leadDto = MetaLeadResponseDTO.builder()
                .id("lead_existing_999")
                .campaignName("RU_LG_MBA_CORE_12-11-26")
                .fieldData(List.of(
                        new MetaLeadFieldDataDTO("full_name", List.of("Sameer Verma")),
                        new MetaLeadFieldDataDTO("phone_number", List.of("+919111222333"))
                ))
                .build();

        when(metaGraphApiService.retrieveLead("lead_existing_999")).thenReturn(leadDto);
        when(deduplicationService.normalizePhone("+919111222333")).thenReturn("+919111222333");
        when(deduplicationService.executeWithPhoneLock(anyString(), any())).thenAnswer(inv -> inv.getArgument(1, java.util.function.Supplier.class).get());

        // Existing lead owned by counselor_1 with existing status and code
        User counselor = User.builder().username("counselor_1").build();
        counselor.setId(UUID.randomUUID());

        Lead existingLead = Lead.builder()
                .leadCode("LEAD-EXISTING-01")
                .fullName("Sameer Verma")
                .phoneNumber("+919111222333")
                .assignedTo(counselor)
                .course(null) // empty course initially
                .build();
        existingLead.setId(UUID.randomUUID());

        LeadMatchResult matchResult = LeadMatchResult.builder()
                .status(LeadMatchResult.MatchStatus.EXACT_MATCH)
                .matchedLead(existingLead)
                .newCoursesToAdd(Set.of(mbaCourse))
                .build();

        when(deduplicationService.matchLead(any(), any(), any(), any(), any(), any())).thenReturn(matchResult);

        // enrichAndSaveMatchedLead populates missing course and preserves counselor
        Lead enrichedLead = Lead.builder()
                .leadCode(existingLead.getLeadCode())
                .fullName(existingLead.getFullName())
                .phoneNumber(existingLead.getPhoneNumber())
                .assignedTo(counselor)
                .course(mbaCourse)
                .build();
        enrichedLead.setId(existingLead.getId());

        when(deduplicationService.enrichAndSaveMatchedLead(eq(existingLead), any(), eq(matchResult), eq(systemUser), anyString()))
                .thenReturn(enrichedLead);

        when(leadMapper.toDto(enrichedLead)).thenReturn(LeadResponse.builder()
                .id(enrichedLead.getId())
                .leadCode(enrichedLead.getLeadCode())
                .build());

        ingestionService.processEvent(event);

        // Verify createLeadFromExternal was NEVER called (no duplicate created!)
        verify(leadService, never()).createLeadFromExternal(any(), any());
        verify(deduplicationService).enrichAndSaveMatchedLead(eq(existingLead), any(), eq(matchResult), eq(systemUser), anyString());

        // Verify event was marked PROCESSED referencing the existing lead
        assertEquals(MetaWebhookEventStatus.PROCESSED, event.getStatus());
        assertEquals(existingLead.getId(), event.getLeadId());
        assertEquals("LEAD-EXISTING-01", event.getLeadCode());
        assertEquals(mbaCourse.getId(), event.getResolvedCourseId());
        assertEquals("CAMPAIGN_NAME", event.getMappingMethod());
    }
}
