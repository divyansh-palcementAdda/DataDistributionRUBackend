package com.app.datadistribution.integration.googleads.service.impl;

import com.app.datadistribution.dto.lead.LeadMatchResult;
import com.app.datadistribution.dto.lead.LeadRequest;
import com.app.datadistribution.dto.lead.LeadResponse;
import com.app.datadistribution.entity.Course;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadSource;
import com.app.datadistribution.entity.Program;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.integration.googleads.config.GoogleAdsIntegrationProperties;
import com.app.datadistribution.integration.googleads.dto.GoogleAdsWebhookRequest;
import com.app.datadistribution.integration.googleads.entity.GoogleAdsLeadEvent;
import com.app.datadistribution.integration.googleads.repository.GoogleAdsLeadEventRepository;
import com.app.datadistribution.integration.googleads.security.GoogleAdsWebhookValidator;
import com.app.datadistribution.integration.googleads.service.GoogleAdsLeadMapper;
import com.app.datadistribution.integration.googleads.service.interfaces.IGoogleAdsWebhookService;
import com.app.datadistribution.repository.CourseRepository;
import com.app.datadistribution.repository.LeadSourceRepository;
import com.app.datadistribution.repository.ProgramRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.interfaces.ILeadDeduplicationService;
import com.app.datadistribution.service.interfaces.ILeadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleAdsWebhookServiceImpl implements IGoogleAdsWebhookService {

    private static final String PROVIDER_NAME = "GOOGLE_ADS";
    private static final String CANONICAL_SOURCE_CODE = "GOOGLE_ADS";
    private static final String CANONICAL_SOURCE_NAME = "Google Ads";
    private static final Pattern SECRET_MASK_PATTERN = Pattern.compile("(\"google_key\"\\s*:\\s*\")[^\"]*(\")");

    private final GoogleAdsIntegrationProperties properties;
    private final GoogleAdsWebhookValidator validator;
    private final GoogleAdsLeadEventRepository eventRepository;
    private final GoogleAdsLeadMapper leadMapper;
    private final ILeadService leadService;
    private final ILeadDeduplicationService deduplicationService;
    private final LeadSourceRepository leadSourceRepository;
    private final CourseRepository courseRepository;
    private final ProgramRepository programRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void processWebhook(GoogleAdsWebhookRequest request, String rawPayload) throws BadRequestException {
        if (request == null) {
            throw new BadRequestException("Google Ads webhook request payload cannot be empty");
        }

        // 1. Authenticate secret webhook key (constant-time comparison, fail-closed)
        validator.validate(request.getGoogleKey());

        // 2. Validate mandatory lead_id
        String externalLeadId = request.getLeadId() != null ? request.getLeadId().trim() : null;
        if (externalLeadId == null || externalLeadId.isBlank()) {
            log.warn("Google Ads webhook rejected: missing lead_id");
            throw new BadRequestException("Missing lead_id in Google Ads webhook payload");
        }

        // Sanitize raw payload (mask secret so it is never stored in DB or logs)
        String sanitizedPayload = maskSecret(rawPayload);

        // 3. Handle Test Submissions (is_test = true)
        if (request.isTestSubmission()) {
            handleTestSubmission(request, externalLeadId, sanitizedPayload);
            return;
        }

        // 4. Production Submission: Idempotency Check
        Optional<GoogleAdsLeadEvent> existingOpt = eventRepository.findByProviderAndExternalLeadId(PROVIDER_NAME, externalLeadId);
        if (existingOpt.isPresent()) {
            GoogleAdsLeadEvent existing = existingOpt.get();
            log.info("Duplicate Google Ads lead event detected (idempotent submission): externalLeadId={}, status={}",
                    externalLeadId, existing.getProcessingStatus());
            return; // Acknowledged cleanly with HTTP 200 without creating duplicate leads
        }

        // 5. Parse lead form data
        GoogleAdsLeadMapper.ParsedGoogleLead parsed = leadMapper.parsePayload(request);
        if (parsed.getPhoneNumber() == null || parsed.getPhoneNumber().isBlank()) {
            log.warn("Google Ads webhook rejected: missing phone number for externalLeadId={}", externalLeadId);
            throw new BadRequestException("Missing required PHONE_NUMBER in Google Ads lead form submission");
        }

        // 6. Resolve Canonical Lead Source (dynamic, independent from WEBSITE_GOOGLE_ADS)
        LeadSource canonicalSource = resolveCanonicalLeadSource();

        // 7. Resolve Academic Catalog (Course / Program) without guessing
        UUID resolvedCourseId = null;
        UUID resolvedProgramId = null;
        if (parsed.getEducationCourse() != null && !parsed.getEducationCourse().isBlank()) {
            String courseText = parsed.getEducationCourse().trim();
            Optional<Course> courseOpt = courseRepository.findByCourseNameIgnoreCaseAndIsDeletedFalse(courseText)
                    .or(() -> courseRepository.findByCourseCodeIgnoreCaseAndIsDeletedFalse(courseText));
            if (courseOpt.isPresent() && courseOpt.get().isActive()) {
                resolvedCourseId = courseOpt.get().getId();
            } else {
                Optional<Program> programOpt = programRepository.findByNameIgnoreCaseAndIsDeletedFalse(courseText)
                        .or(() -> programRepository.findByCodeIgnoreCaseAndIsDeletedFalse(courseText));
                if (programOpt.isPresent() && programOpt.get().isActive()) {
                    resolvedProgramId = programOpt.get().getId();
                }
            }
        }

        // 8. Resolve System User Context for Audit Logging
        User systemUser = resolveSystemUser();

        // 9. Build Canonical LeadRequest
        LeadRequest leadRequest = leadMapper.buildLeadRequest(parsed, canonicalSource, resolvedCourseId, resolvedProgramId);

        // 10. Canonical Lead Deduplication Check (Rules A through F)
        String normPhone = deduplicationService.normalizePhone(parsed.getPhoneNumber());
        Course resolvedCourse = resolvedCourseId != null ? courseRepository.findById(resolvedCourseId).orElse(null) : null;
        Program resolvedProgram = resolvedProgramId != null ? programRepository.findById(resolvedProgramId).orElse(null) : null;

        LeadMatchResult matchResult = deduplicationService.executeWithPhoneLock(normPhone, () ->
                deduplicationService.matchLead(
                        parsed.getFullName(),
                        parsed.getPhoneNumber(),
                        parsed.getEmail(),
                        canonicalSource,
                        resolvedCourse != null ? Set.of(resolvedCourse) : Set.of(),
                        resolvedProgram != null ? Set.of(resolvedProgram) : Set.of()
                )
        );

        UUID finalLeadId;
        String finalLeadCode;
        String outcomeStatus;

        if (matchResult.isMatched()) {
            Lead matchedLead = matchResult.getMatchedLead();
            log.info("Google Ads lead matched existing lead {}: {} (match status: {})",
                    matchedLead.getLeadCode(), matchedLead.getFullName(), matchResult.getStatus());

            Lead updatedLead = deduplicationService.enrichAndSaveMatchedLead(
                    matchedLead,
                    leadRequest,
                    matchResult,
                    systemUser,
                    "Lead submitted via Google Ads Lead Form."
            );

            finalLeadId = updatedLead.getId();
            finalLeadCode = updatedLead.getLeadCode();
            outcomeStatus = "DUPLICATE_ENRICHED";
        } else {
            LeadResponse createdLead = leadService.createLeadFromExternal(
                    leadRequest,
                    systemUser,
                    "Lead submitted via Google Ads Lead Form."
            );

            finalLeadId = createdLead.getId();
            finalLeadCode = createdLead.getLeadCode();
            outcomeStatus = "PROCESSED";
        }

        // 11. Durably Record Google Ads Integration Event
        GoogleAdsLeadEvent event = GoogleAdsLeadEvent.builder()
                .provider(PROVIDER_NAME)
                .externalLeadId(externalLeadId)
                .formId(request.getFormId())
                .campaignId(request.getCampaignId())
                .adgroupId(request.getAdgroupId())
                .creativeId(request.getCreativeId())
                .gclId(request.getGclId())
                .leadSource(request.getLeadSource())
                .submissionTime(request.getLeadSubmitTime())
                .isTest(false)
                .processingStatus(outcomeStatus)
                .rawPayload(sanitizedPayload)
                .linkedLeadId(finalLeadId)
                .leadCode(finalLeadCode)
                .fullName(parsed.getFullName())
                .mobile(parsed.getPhoneNumber())
                .email(parsed.getEmail())
                .courseInterested(parsed.getEducationCourse())
                .build();

        try {
            eventRepository.save(event);
            log.info("Successfully ingested Google Ads lead: externalLeadId={}, status={}, leadCode={}",
                    externalLeadId, outcomeStatus, finalLeadCode);
        } catch (DataIntegrityViolationException dive) {
            // Concurrent race condition handling: if another thread saved the same externalLeadId
            Optional<GoogleAdsLeadEvent> concurrent = eventRepository.findByProviderAndExternalLeadId(PROVIDER_NAME, externalLeadId);
            if (concurrent.isPresent()) {
                log.warn("Concurrent duplicate Google Ads submission resolved: externalLeadId={}", externalLeadId);
                return;
            }
            throw dive;
        }
    }

    private void handleTestSubmission(GoogleAdsWebhookRequest request, String externalLeadId, String sanitizedPayload) {
        log.info("Received Google Ads TEST submission: externalLeadId={}, formId={}, campaignId={}",
                externalLeadId, request.getFormId(), request.getCampaignId());

        Optional<GoogleAdsLeadEvent> existingOpt = eventRepository.findByProviderAndExternalLeadId(PROVIDER_NAME, externalLeadId);
        if (existingOpt.isPresent()) {
            log.info("Duplicate Google Ads test event already recorded: externalLeadId={}", externalLeadId);
            return;
        }

        GoogleAdsLeadMapper.ParsedGoogleLead parsed = leadMapper.parsePayload(request);

        GoogleAdsLeadEvent testEvent = GoogleAdsLeadEvent.builder()
                .provider(PROVIDER_NAME)
                .externalLeadId(externalLeadId)
                .formId(request.getFormId())
                .campaignId(request.getCampaignId())
                .adgroupId(request.getAdgroupId())
                .creativeId(request.getCreativeId())
                .gclId(request.getGclId())
                .leadSource(request.getLeadSource())
                .submissionTime(request.getLeadSubmitTime())
                .isTest(true)
                .processingStatus("TEST_RECORDED")
                .rawPayload(sanitizedPayload)
                .fullName(parsed.getFullName())
                .mobile(parsed.getPhoneNumber())
                .email(parsed.getEmail())
                .courseInterested(parsed.getEducationCourse())
                .build();

        try {
            eventRepository.save(testEvent);
            log.info("Recorded Google Ads test submission event: externalLeadId={}", externalLeadId);
        } catch (DataIntegrityViolationException dive) {
            log.warn("Concurrent Google Ads test event already saved: externalLeadId={}", externalLeadId);
        }
    }

    private LeadSource resolveCanonicalLeadSource() {
        return leadSourceRepository.findByCodeIgnoreCaseAndIsDeletedFalse(CANONICAL_SOURCE_CODE)
                .or(() -> leadSourceRepository.findByNameIgnoreCaseAndIsDeletedFalse(CANONICAL_SOURCE_NAME))
                .or(() -> leadSourceRepository.findByCodeIgnoreCase(CANONICAL_SOURCE_CODE))
                .orElseGet(() -> {
                    log.info("Seeding missing LeadSource: {} ({})", CANONICAL_SOURCE_NAME, CANONICAL_SOURCE_CODE);
                    LeadSource newSource = LeadSource.builder()
                            .name(CANONICAL_SOURCE_NAME)
                            .code(CANONICAL_SOURCE_CODE)
                            .description("Paid search marketing campaigns on Google")
                            .active(true)
                            .build();
                    return leadSourceRepository.save(newSource);
                });
    }

    private User resolveSystemUser() {
        String configuredUsername = properties.getSystemUsername();
        if (configuredUsername != null && !configuredUsername.isBlank()) {
            Optional<User> userOpt = userRepository.findByUsername(configuredUsername.trim())
                    .filter(u -> !u.isDeleted());
            if (userOpt.isPresent()) {
                return userOpt.get();
            }
        }

        return userRepository.findByUsername("superadmin")
                .filter(u -> !u.isDeleted())
                .or(() -> userRepository.findAll().stream().filter(u -> !u.isDeleted()).findFirst())
                .orElse(null);
    }

    private String maskSecret(String rawPayload) {
        if (rawPayload == null || rawPayload.isBlank()) {
            return null;
        }
        return SECRET_MASK_PATTERN.matcher(rawPayload).replaceAll("$1[REDACTED]$2");
    }
}

