package com.app.datadistribution.service;

import com.app.datadistribution.dto.lead.LeadDuplicateDiagnosticDTO;
import com.app.datadistribution.dto.lead.LeadMatchResult;
import com.app.datadistribution.dto.lead.LeadRequest;
import com.app.datadistribution.entity.Course;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadSource;
import com.app.datadistribution.entity.LeadStatus;
import com.app.datadistribution.entity.LeadStatusHistory;
import com.app.datadistribution.entity.Program;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.repository.LeadRepository;
import com.app.datadistribution.repository.LeadStatusHistoryRepository;
import com.app.datadistribution.service.impl.LeadDeduplicationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeadDeduplicationServiceTest {

    @Mock
    private LeadRepository leadRepository;

    @Mock
    private LeadStatusHistoryRepository leadStatusHistoryRepository;

    @InjectMocks
    private LeadDeduplicationServiceImpl deduplicationService;

    private LeadSource websiteFeesSource;
    private LeadSource websiteCourseSource;
    private Course bbaCourse;
    private Course mbaCourse;
    private Program bbaProgram;
    private Program mbaProgram;
    private User testUser;
    private Lead existingLead;

    @BeforeEach
    void setUp() {
        websiteFeesSource = LeadSource.builder().name("Website - Fees Inquiry").code("WEBSITE_FEES_INQUIRY").build();
        websiteFeesSource.setId(UUID.randomUUID());

        websiteCourseSource = LeadSource.builder().name("Website - Course Enquiry").code("WEBSITE_COURSE_ENQUIRY").build();
        websiteCourseSource.setId(UUID.randomUUID());

        bbaCourse = Course.builder().courseName("BBA").courseCode("BBA01").build();
        bbaCourse.setId(UUID.randomUUID());

        mbaCourse = Course.builder().courseName("MBA").courseCode("MBA01").build();
        mbaCourse.setId(UUID.randomUUID());

        bbaProgram = Program.builder().name("BBA Program").code("PROG_BBA").build();
        bbaProgram.setId(UUID.randomUUID());

        mbaProgram = Program.builder().name("MBA Program").code("PROG_MBA").build();
        mbaProgram.setId(UUID.randomUUID());

        testUser = User.builder().username("testuser").email("test@example.com").build();
        testUser.setId(UUID.randomUUID());

        existingLead = Lead.builder()
                .leadCode("LEAD-EXISTING1")
                .fullName("Akshat Gupta")
                .phoneNumber("9876543210")
                .email("akshat@example.com")
                .leadSources(new HashSet<>(Set.of(websiteFeesSource)))
                .interestedCourses(new HashSet<>(Set.of(bbaCourse)))
                .course(bbaCourse)
                .programs(new HashSet<>(Set.of(bbaProgram)))
                .program(bbaProgram)
                .currentStatus(LeadStatus.builder().name("Raw").build())
                .statusHistories(new ArrayList<>())
                .active(true)
                .build();
        existingLead.setId(UUID.randomUUID());
        existingLead.setCreatedAt(LocalDateTime.now().minusDays(2));
        existingLead.setUpdatedAt(LocalDateTime.now().minusDays(1));
    }

    @Test
    @DisplayName("Normalization: Name, phone with country code / leading zero, and email")
    void testNormalizations() {
        assertEquals("akshat gupta", deduplicationService.normalizeName("  Akshat   Gupta  "));
        assertEquals("9876543210", deduplicationService.normalizePhone("+91-98765 43210"));
        assertEquals("9876543210", deduplicationService.normalizePhone("09876543210"));
        assertEquals("9876543210", deduplicationService.normalizePhone("919876543210"));
        assertEquals("akshat@example.com", deduplicationService.normalizeEmail("  AKSHAT@EXAMPLE.COM  "));
        assertEquals("", deduplicationService.normalizeEmail(null));
        assertEquals("", deduplicationService.normalizePhone(null));
        assertEquals("", deduplicationService.normalizeName(null));
    }

    @Test
    @DisplayName("Rule A: Same Name, Mobile, Source and Email -> Existing lead reused")
    void testRuleA_SameNameMobileSourceEmail_ReusesLead() {
        when(leadRepository.findCandidateLeadsByPhone("9876543210", "9876543210"))
                .thenReturn(List.of(existingLead));

        LeadMatchResult result = deduplicationService.matchLead(
                "Akshat Gupta",
                "9876543210",
                "akshat@example.com",
                websiteFeesSource,
                Set.of(bbaCourse),
                Set.of(bbaProgram)
        );

        assertTrue(result.isMatched());
        assertEquals(LeadMatchResult.MatchStatus.EXACT_MATCH, result.getStatus());
        assertEquals(existingLead.getId(), result.getMatchedLead().getId());
        assertTrue(result.getNewCoursesToAdd().isEmpty());
        assertTrue(result.getNewSourcesToAdd().isEmpty());
        assertFalse(result.isEmailEnriched());
    }

    @Test
    @DisplayName("Rule B: Same Name, Mobile and Source, but Different Non-Empty Emails -> Separate lead, no match")
    void testRuleB_SameNameMobileSource_DifferentEmails_NoMatch() {
        when(leadRepository.findCandidateLeadsByPhone("9876543210", "9876543210"))
                .thenReturn(List.of(existingLead));

        LeadMatchResult result = deduplicationService.matchLead(
                "Akshat Gupta",
                "9876543210",
                "akshat2@example.com",
                websiteFeesSource,
                Set.of(bbaCourse),
                Set.of(bbaProgram)
        );

        assertFalse(result.isMatched());
        assertEquals(LeadMatchResult.MatchStatus.NO_MATCH, result.getStatus());
        assertTrue(result.getMatchReason().contains("Rule B"));
    }

    @Test
    @DisplayName("Rule C: Same Name and Mobile, Existing Email Missing -> Existing lead reused and email enriched")
    void testRuleC_ExistingEmailMissing_ReusesAndEnriches() {
        existingLead.setEmail(null);
        when(leadRepository.findCandidateLeadsByPhone("9876543210", "9876543210"))
                .thenReturn(List.of(existingLead));

        LeadMatchResult result = deduplicationService.matchLead(
                "Akshat Gupta",
                "9876543210",
                "akshat@example.com",
                websiteFeesSource,
                Set.of(bbaCourse),
                Set.of(bbaProgram)
        );

        assertTrue(result.isMatched());
        assertTrue(result.isEmailEnriched());
        assertEquals("akshat@example.com", result.getEmailToEnrich());
        assertEquals(LeadMatchResult.MatchStatus.ENRICHED_MATCH, result.getStatus());

        // Perform safe enrichment
        when(leadRepository.save(any(Lead.class))).thenAnswer(i -> i.getArgument(0));
        Lead enriched = deduplicationService.enrichAndSaveMatchedLead(
                result.getMatchedLead(),
                LeadRequest.builder().fullName("Akshat Gupta").phoneNumber("9876543210").email("akshat@example.com").build(),
                result,
                testUser,
                "Website submission"
        );

        assertEquals("akshat@example.com", enriched.getEmail());
        verify(leadStatusHistoryRepository).save(any(LeadStatusHistory.class));
    }

    @Test
    @DisplayName("Rule C: Incoming Email Missing, Existing Email Present -> Reused, never overwrites existing email")
    void testRuleC_IncomingEmailMissing_NeverOverwritesExistingEmail() {
        when(leadRepository.findCandidateLeadsByPhone("9876543210", "9876543210"))
                .thenReturn(List.of(existingLead));

        LeadMatchResult result = deduplicationService.matchLead(
                "Akshat Gupta",
                "9876543210",
                null, // Missing incoming email
                websiteFeesSource,
                Set.of(bbaCourse),
                Set.of(bbaProgram)
        );

        assertTrue(result.isMatched());
        assertFalse(result.isEmailEnriched());

        when(leadRepository.save(any(Lead.class))).thenAnswer(i -> i.getArgument(0));
        Lead enriched = deduplicationService.enrichAndSaveMatchedLead(
                result.getMatchedLead(),
                LeadRequest.builder().fullName("Akshat Gupta").phoneNumber("9876543210").email(null).build(),
                result,
                testUser,
                "Website submission"
        );

        assertEquals("akshat@example.com", enriched.getEmail(), "Existing populated email must not be overwritten by null");
    }

    @Test
    @DisplayName("Rule D: Same Mobile, Different Names -> Separate leads, no accidental merge")
    void testRuleD_SameMobile_DifferentNames_NoMatch() {
        when(leadRepository.findCandidateLeadsByPhone("9876543210", "9876543210"))
                .thenReturn(List.of(existingLead));

        LeadMatchResult result = deduplicationService.matchLead(
                "Vikram Sharma", // Different person sharing phone number
                "9876543210",
                "vikram@example.com",
                websiteFeesSource,
                Set.of(bbaCourse),
                Set.of(bbaProgram)
        );

        assertFalse(result.isMatched(), "Different names sharing phone number must not be merged");
        assertEquals(LeadMatchResult.MatchStatus.NO_MATCH, result.getStatus());
        assertTrue(result.getMatchReason().contains("Rule D"));
    }

    @Test
    @DisplayName("Rule E: Same Person, Different Sources -> One logical lead with both sources preserved")
    void testRuleE_DifferentSources_BothPreserved() {
        when(leadRepository.findCandidateLeadsByPhone("9876543210", "9876543210"))
                .thenReturn(List.of(existingLead));

        LeadMatchResult result = deduplicationService.matchLead(
                "Akshat Gupta",
                "9876543210",
                "akshat@example.com",
                websiteCourseSource, // Different source
                Set.of(bbaCourse),
                Set.of(bbaProgram)
        );

        assertTrue(result.isMatched());
        assertEquals(1, result.getNewSourcesToAdd().size());
        assertTrue(result.getNewSourcesToAdd().contains(websiteCourseSource));

        when(leadRepository.save(any(Lead.class))).thenAnswer(i -> i.getArgument(0));
        Lead enriched = deduplicationService.enrichAndSaveMatchedLead(
                result.getMatchedLead(),
                LeadRequest.builder().fullName("Akshat Gupta").phoneNumber("9876543210").email("akshat@example.com").build(),
                result,
                testUser,
                "Website course enquiry"
        );

        assertEquals(2, enriched.getLeadSources().size());
        assertTrue(enriched.getLeadSources().contains(websiteFeesSource));
        assertTrue(enriched.getLeadSources().contains(websiteCourseSource));
        assertTrue(enriched.isMultiSource());
    }

    @Test
    @DisplayName("Rule F: Same Person, Different Courses -> One logical lead with both courses preserved")
    void testRuleF_DifferentCourses_BothPreserved() {
        when(leadRepository.findCandidateLeadsByPhone("9876543210", "9876543210"))
                .thenReturn(List.of(existingLead));

        LeadMatchResult result = deduplicationService.matchLead(
                "Akshat Gupta",
                "9876543210",
                "akshat@example.com",
                websiteFeesSource,
                Set.of(mbaCourse), // Different course
                Set.of(mbaProgram)
        );

        assertTrue(result.isMatched());
        assertEquals(1, result.getNewCoursesToAdd().size());
        assertTrue(result.getNewCoursesToAdd().contains(mbaCourse));

        when(leadRepository.save(any(Lead.class))).thenAnswer(i -> i.getArgument(0));
        Lead enriched = deduplicationService.enrichAndSaveMatchedLead(
                result.getMatchedLead(),
                LeadRequest.builder().fullName("Akshat Gupta").phoneNumber("9876543210").email("akshat@example.com").build(),
                result,
                testUser,
                "Website MBA enquiry"
        );

        assertEquals(2, enriched.getInterestedCourses().size());
        assertTrue(enriched.getInterestedCourses().contains(bbaCourse));
        assertTrue(enriched.getInterestedCourses().contains(mbaCourse));
    }

    @Test
    @DisplayName("Idempotency: Same course and source submitted twice -> No duplicate associations created")
    void testIdempotency_SameCourseAndSourceTwice_NoDuplicateAssociations() {
        when(leadRepository.findCandidateLeadsByPhone("9876543210", "9876543210"))
                .thenReturn(List.of(existingLead));

        // Attempt to associate bbaCourse and websiteFeesSource again
        LeadMatchResult result = deduplicationService.matchLead(
                "Akshat Gupta",
                "9876543210",
                "akshat@example.com",
                websiteFeesSource,
                Set.of(bbaCourse),
                Set.of(bbaProgram)
        );

        assertTrue(result.getNewCoursesToAdd().isEmpty());
        assertTrue(result.getNewSourcesToAdd().isEmpty());

        when(leadRepository.save(any(Lead.class))).thenAnswer(i -> i.getArgument(0));
        Lead enriched = deduplicationService.enrichAndSaveMatchedLead(
                result.getMatchedLead(),
                LeadRequest.builder().fullName("Akshat Gupta").phoneNumber("9876543210").email("akshat@example.com").build(),
                result,
                testUser,
                "Resubmitted fees enquiry"
        );

        assertEquals(1, enriched.getLeadSources().size());
        assertEquals(1, enriched.getInterestedCourses().size());
    }

    @Test
    @DisplayName("Historical Ambiguity Handling: Multiple existing matching records -> Selects most recent active lead safely")
    void testHistoricalAmbiguity_MultipleMatches_SelectsMostRecentSafely() {
        Lead olderDuplicate = Lead.builder()
                .leadCode("LEAD-OLDER")
                .fullName("Akshat Gupta")
                .phoneNumber("9876543210")
                .email("akshat@example.com")
                .leadSources(new HashSet<>(Set.of(websiteFeesSource)))
                .interestedCourses(new HashSet<>(Set.of(bbaCourse)))
                .active(true)
                .build();
        olderDuplicate.setId(UUID.randomUUID());
        olderDuplicate.setCreatedAt(LocalDateTime.now().minusDays(10));
        olderDuplicate.setUpdatedAt(LocalDateTime.now().minusDays(5));

        when(leadRepository.findCandidateLeadsByPhone("9876543210", "9876543210"))
                .thenReturn(List.of(olderDuplicate, existingLead));

        LeadMatchResult result = deduplicationService.matchLead(
                "Akshat Gupta",
                "9876543210",
                "akshat@example.com",
                websiteFeesSource,
                Set.of(bbaCourse),
                Set.of(bbaProgram)
        );

        assertTrue(result.isMatched());
        assertTrue(result.isAmbiguous());
        assertEquals(existingLead.getId(), result.getMatchedLead().getId(), "Must select most recently active lead");
    }

    @Test
    @DisplayName("Concurrency: Phone lock executes critical section safely under concurrent requests")
    void testConcurrencySafety_PhoneLock() throws InterruptedException {
        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger counter = new AtomicInteger(0);
        AtomicInteger concurrencyViolations = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    deduplicationService.executeWithPhoneLock("9876543210", () -> {
                        int current = counter.incrementAndGet();
                        if (current > 1) {
                            concurrencyViolations.incrementAndGet();
                        }
                        try {
                            Thread.sleep(15);
                        } catch (InterruptedException ignored) {
                        }
                        counter.decrementAndGet();
                        return null;
                    });
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertEquals(0, concurrencyViolations.get(), "Lock per normalized phone should prevent concurrent entry");
    }

    @Test
    @DisplayName("Historical Diagnostic Tool: Categorizes duplicate clusters correctly without mutating data")
    void testDuplicateDiagnostics_IdentifiesClusters() {
        Lead duplicate1 = Lead.builder()
                .leadCode("LEAD-DUP1")
                .fullName("Akshat Gupta")
                .phoneNumber("9876543210")
                .email("akshat@example.com")
                .leadSources(new HashSet<>(Set.of(websiteFeesSource)))
                .active(true)
                .build();
        duplicate1.setId(UUID.randomUUID());

        Lead duplicate2 = Lead.builder()
                .leadCode("LEAD-DUP2")
                .fullName("Akshat Gupta")
                .phoneNumber("+919876543210")
                .email("akshat@example.com")
                .leadSources(new HashSet<>(Set.of(websiteFeesSource)))
                .active(true)
                .build();
        duplicate2.setId(UUID.randomUUID());

        when(leadRepository.findAll()).thenReturn(List.of(duplicate1, duplicate2));

        List<LeadDuplicateDiagnosticDTO> diagnostics = deduplicationService.generateDuplicateDiagnostics();

        assertNotNull(diagnostics);
        assertFalse(diagnostics.isEmpty());
        LeadDuplicateDiagnosticDTO diag = diagnostics.stream()
                .filter(d -> "9876543210".equals(d.getNormalizedPhone()))
                .findFirst()
                .orElse(null);

        assertNotNull(diag);
        assertEquals("EXACT_DUPLICATE", diag.getMatchClassification());
        assertEquals(2, diag.getCandidateCount());
    }
}

