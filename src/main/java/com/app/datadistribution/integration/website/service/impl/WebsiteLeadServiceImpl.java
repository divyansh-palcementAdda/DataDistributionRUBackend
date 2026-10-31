package com.app.datadistribution.integration.website.service.impl;

import com.app.datadistribution.dto.lead.LeadMatchResult;
import com.app.datadistribution.dto.lead.LeadRequest;
import com.app.datadistribution.dto.lead.LeadResponse;
import com.app.datadistribution.entity.Course;
import com.app.datadistribution.entity.Lead;
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
import com.app.datadistribution.integration.website.service.interfaces.IWebsiteLeadService;
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

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebsiteLeadServiceImpl implements IWebsiteLeadService {

    private final ILeadService leadService;
    private final LeadSourceRepository leadSourceRepository;
    private final CourseRepository courseRepository;
    private final ProgramRepository programRepository;
    private final UserRepository userRepository;
    private final WebsiteLeadSubmissionRepository submissionRepository;
    private final WebsiteIntegrationProperties properties;
    private final ILeadDeduplicationService deduplicationService;

    @Override
    @Transactional
    public WebsiteLeadResponseDTO ingestLead(WebsiteLeadRequestDTO request, String clientIp) throws BadRequestException {
        String sourceCodeStr = request.getSourceCode() != null ? request.getSourceCode().trim() : "";
        log.info("Processing website lead submission: sourceCode={}, externalLeadId={}, ip={}",
                sourceCodeStr, request.getExternalLeadId(), clientIp);

        // 1. Resolve and Validate Canonical Lead Source
        LeadSource canonicalSource = resolveCanonicalLeadSource(sourceCodeStr);

        // 2. Idempotency Check: Safe Retry Protection for externalLeadId
        String externalLeadId = request.getExternalLeadId() != null && !request.getExternalLeadId().isBlank()
                ? request.getExternalLeadId().trim()
                : null;

        if (externalLeadId != null) {
            Optional<WebsiteLeadSubmission> existing = submissionRepository
                    .findByExternalLeadIdAndSourceCode(externalLeadId, canonicalSource.getCode());

            if (existing.isPresent()) {
                WebsiteLeadSubmission prev = existing.get();
                log.info("Duplicate website submission recognized (idempotent): externalLeadId={}, sourceCode={}, leadCode={}",
                        externalLeadId, canonicalSource.getCode(), prev.getLeadCode());
                return WebsiteLeadResponseDTO.builder()
                        .externalLeadId(externalLeadId)
                        .leadId(prev.getLeadId())
                        .leadCode(prev.getLeadCode())
                        .sourceCode(canonicalSource.getCode())
                        .status("EXISTING_SUBMISSION")
                        .message("Website lead already processed (idempotent submission)")
                        .build();
            }
        }

        // 3. Resolve Academic Course/Program Catalog without guessing
        UUID resolvedCourseId = null;
        UUID resolvedProgramId = null;
        String courseInterest = request.getCourseInterested();
        if (courseInterest != null && !courseInterest.isBlank()) {
            String trimmedCourse = courseInterest.trim();
            Optional<Course> courseOpt = courseRepository.findByCourseNameIgnoreCaseAndIsDeletedFalse(trimmedCourse)
                    .or(() -> courseRepository.findByCourseCodeIgnoreCaseAndIsDeletedFalse(trimmedCourse));

            if (courseOpt.isPresent() && courseOpt.get().isActive()) {
                resolvedCourseId = courseOpt.get().getId();
            } else {
                Optional<Program> programOpt = programRepository.findByNameIgnoreCaseAndIsDeletedFalse(trimmedCourse)
                        .or(() -> programRepository.findByCodeIgnoreCaseAndIsDeletedFalse(trimmedCourse));
                if (programOpt.isPresent() && programOpt.get().isActive()) {
                    resolvedProgramId = programOpt.get().getId();
                }
            }
        }

        // 4. Resolve System User Context for Audit Logging
        User systemUser = resolveSystemUser();

        // 5. Build Structured Remarks and Source Details
        String sourceDetails = String.format("Website Form: %s | External ID: %s | URL: %s",
                canonicalSource.getName(),
                externalLeadId != null ? externalLeadId : "N/A",
                request.getWebsiteUrl() != null ? request.getWebsiteUrl() : "N/A");
        if (sourceDetails.length() > 255) {
            sourceDetails = sourceDetails.substring(0, 255);
        }

        StringBuilder remarksBuilder = new StringBuilder();
        remarksBuilder.append("[Website Lead Submission]\n");
        remarksBuilder.append("Form Source: ").append(canonicalSource.getName()).append(" (").append(canonicalSource.getCode()).append(")\n");
        if (externalLeadId != null) {
            remarksBuilder.append("External Lead ID: ").append(externalLeadId).append("\n");
        }
        if (request.getWebsiteUrl() != null && !request.getWebsiteUrl().isBlank()) {
            remarksBuilder.append("Page URL: ").append(request.getWebsiteUrl().trim()).append("\n");
        }
        if (courseInterest != null && !courseInterest.isBlank()) {
            remarksBuilder.append("Course Interest: ").append(courseInterest.trim());
            if (resolvedCourseId == null && resolvedProgramId == null) {
                remarksBuilder.append(" (No catalog match - manual review)");
            }
            remarksBuilder.append("\n");
        }
        if (request.getMessage() != null && !request.getMessage().isBlank()) {
            remarksBuilder.append("Inquiry Message: ").append(request.getMessage().trim()).append("\n");
        }
        if (request.getConsent() != null) {
            remarksBuilder.append("Consent Given: ").append(request.getConsent()).append("\n");
        }
        if (request.getUtmSource() != null || request.getUtmMedium() != null || request.getUtmCampaign() != null) {
            remarksBuilder.append("UTM: [source=").append(request.getUtmSource() != null ? request.getUtmSource() : "-")
                    .append(", medium=").append(request.getUtmMedium() != null ? request.getUtmMedium() : "-")
                    .append(", campaign=").append(request.getUtmCampaign() != null ? request.getUtmCampaign() : "-")
                    .append("]\n");
        }

        boolean isTour = canonicalSource.getCode().equalsIgnoreCase(WebsiteSourceCode.WEBSITE_CAMPUS_TOUR.getCode())
                || canonicalSource.getCode().equalsIgnoreCase(WebsiteSourceCode.WEBSITE_VIRTUAL_TOUR.getCode());

        // 6. Build Canonical LeadRequest
        LeadRequest leadRequest = LeadRequest.builder()
                .fullName(request.getName().trim())
                .phoneNumber(request.getMobile().trim())
                .email(request.getEmail() != null && !request.getEmail().isBlank() ? request.getEmail().trim() : null)
                .city(request.getCity() != null && !request.getCity().isBlank() ? request.getCity().trim() : null)
                .state(request.getState() != null && !request.getState().isBlank() ? request.getState().trim() : null)
                .country(request.getCountry() != null && !request.getCountry().isBlank() ? request.getCountry().trim() : "India")
                .leadSourceIds(List.of(canonicalSource.getId()))
                .sourceDetails(sourceDetails)
                .courseId(resolvedCourseId)
                .programId(resolvedProgramId)
                .planningToVisitUniversity(isTour)
                .remarks(remarksBuilder.toString().trim())
                .active(true)
                .build();

        // 7. Canonical Deduplication Check (Rules A through F)
        String normPhone = deduplicationService.normalizePhone(request.getMobile());
        Course resolvedCourse = resolvedCourseId != null ? courseRepository.findById(resolvedCourseId).orElse(null) : null;
        Program resolvedProgram = resolvedProgramId != null ? programRepository.findById(resolvedProgramId).orElse(null) : null;

        LeadMatchResult matchResult = deduplicationService.executeWithPhoneLock(normPhone, () ->
                deduplicationService.matchLead(
                        request.getName(),
                        request.getMobile(),
                        request.getEmail(),
                        canonicalSource,
                        resolvedCourse != null ? Set.of(resolvedCourse) : Set.of(),
                        resolvedProgram != null ? Set.of(resolvedProgram) : Set.of()
                )
        );

        if (matchResult.isMatched()) {
            Lead matchedLead = matchResult.getMatchedLead();
            log.info("Website lead matched existing lead {}: {} (status: {})",
                    matchedLead.getLeadCode(), matchedLead.getFullName(), matchResult.getStatus());

            Lead updatedLead = deduplicationService.enrichAndSaveMatchedLead(
                    matchedLead,
                    leadRequest,
                    matchResult,
                    systemUser,
                    "Lead submitted via " + canonicalSource.getName() + "."
            );

            WebsiteLeadSubmission matchSubmission = WebsiteLeadSubmission.builder()
                    .externalLeadId(externalLeadId)
                    .sourceCode(canonicalSource.getCode())
                    .leadId(updatedLead.getId())
                    .leadCode(updatedLead.getLeadCode())
                    .status("EXISTING_LEAD_MATCHED")
                    .fullName(request.getName().trim())
                    .mobile(request.getMobile().trim())
                    .email(request.getEmail() != null ? request.getEmail().trim() : null)
                    .courseInterested(courseInterest)
                    .websiteUrl(request.getWebsiteUrl())
                    .ipAddress(clientIp)
                    .requestSummary(remarksBuilder.toString().trim())
                    .build();

            try {
                submissionRepository.save(matchSubmission);
            } catch (DataIntegrityViolationException dive) {
                if (externalLeadId != null) {
                    Optional<WebsiteLeadSubmission> prev = submissionRepository
                            .findByExternalLeadIdAndSourceCode(externalLeadId, canonicalSource.getCode());
                    if (prev.isPresent()) {
                        return WebsiteLeadResponseDTO.builder()
                                .externalLeadId(externalLeadId)
                                .leadId(prev.get().getLeadId())
                                .leadCode(prev.get().getLeadCode())
                                .sourceCode(canonicalSource.getCode())
                                .status("EXISTING_SUBMISSION")
                                .message("Website lead already processed (idempotent submission)")
                                .build();
                    }
                }
                throw dive;
            }

            return WebsiteLeadResponseDTO.builder()
                    .externalLeadId(externalLeadId)
                    .leadId(updatedLead.getId())
                    .leadCode(updatedLead.getLeadCode())
                    .sourceCode(canonicalSource.getCode())
                    .status("EXISTING_LEAD_MATCHED")
                    .message("Existing lead matched and enquiry associated successfully")
                    .build();
        }

        // 8. Persist Lead via Canonical Service
        LeadResponse createdLead = leadService.createLeadFromExternal(
                leadRequest,
                systemUser,
                "Lead submitted via " + canonicalSource.getName() + "."
        );

        // 8. Record Website Lead Submission (handling concurrency race condition)
        WebsiteLeadSubmission submission = WebsiteLeadSubmission.builder()
                .externalLeadId(externalLeadId)
                .sourceCode(canonicalSource.getCode())
                .leadId(createdLead.getId())
                .leadCode(createdLead.getLeadCode())
                .status("CREATED")
                .fullName(request.getName().trim())
                .mobile(request.getMobile().trim())
                .email(request.getEmail() != null ? request.getEmail().trim() : null)
                .courseInterested(courseInterest)
                .websiteUrl(request.getWebsiteUrl())
                .ipAddress(clientIp)
                .requestSummary(remarksBuilder.toString().trim())
                .build();

        try {
            submissionRepository.save(submission);
        } catch (DataIntegrityViolationException dive) {
            // Concurrent duplicate submission occurred with identical (externalLeadId, sourceCode)
            if (externalLeadId != null) {
                Optional<WebsiteLeadSubmission> prev = submissionRepository
                        .findByExternalLeadIdAndSourceCode(externalLeadId, canonicalSource.getCode());
                if (prev.isPresent()) {
                    log.warn("Concurrent duplicate submission caught: externalLeadId={}, sourceCode={}",
                            externalLeadId, canonicalSource.getCode());
                    return WebsiteLeadResponseDTO.builder()
                            .externalLeadId(externalLeadId)
                            .leadId(prev.get().getLeadId())
                            .leadCode(prev.get().getLeadCode())
                            .sourceCode(canonicalSource.getCode())
                            .status("EXISTING_SUBMISSION")
                            .message("Website lead already processed (idempotent submission)")
                            .build();
                }
            }
            throw dive;
        }

        log.info("Successfully ingested website lead: leadCode={}, externalLeadId={}, source={}",
                createdLead.getLeadCode(), externalLeadId, canonicalSource.getCode());

        return WebsiteLeadResponseDTO.builder()
                .externalLeadId(externalLeadId)
                .leadId(createdLead.getId())
                .leadCode(createdLead.getLeadCode())
                .sourceCode(canonicalSource.getCode())
                .status("CREATED")
                .message("Website lead received successfully")
                .build();
    }

    private LeadSource resolveCanonicalLeadSource(String sourceCodeStr) throws BadRequestException {
        if (sourceCodeStr == null || sourceCodeStr.isBlank()) {
            throw new BadRequestException("Source code is required");
        }

        // Validate source code against registered WebsiteSourceCode enum or database
        Optional<WebsiteSourceCode> enumMatch = WebsiteSourceCode.fromCode(sourceCodeStr);
        String lookupCode = enumMatch.map(WebsiteSourceCode::getCode).orElse(sourceCodeStr.trim().toUpperCase());

        Optional<LeadSource> sourceOpt = leadSourceRepository.findByCodeIgnoreCaseAndIsDeletedFalse(lookupCode);
        if (sourceOpt.isEmpty()) {
            // Check by canonical name if code didn't match directly
            if (enumMatch.isPresent()) {
                sourceOpt = leadSourceRepository.findByNameIgnoreCaseAndIsDeletedFalse(enumMatch.get().getCanonicalName());
            }
        }

        if (sourceOpt.isEmpty()) {
            throw new BadRequestException("Unsupported or invalid website source code: " + sourceCodeStr);
        }

        LeadSource source = sourceOpt.get();
        if (!source.isActive()) {
            throw new BadRequestException("Website lead source is currently inactive: " + sourceCodeStr);
        }

        return source;
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
}
