package com.app.datadistribution.integration.meta.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.datadistribution.dto.lead.LeadMatchResult;
import com.app.datadistribution.dto.lead.LeadRequest;
import com.app.datadistribution.dto.lead.LeadResponse;
import com.app.datadistribution.entity.Course;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadSource;
import com.app.datadistribution.entity.Program;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.RoleType;
import com.app.datadistribution.integration.meta.config.MetaProperties;
import com.app.datadistribution.integration.meta.dto.MetaLeadFieldDataDTO;
import com.app.datadistribution.integration.meta.dto.MetaLeadResponseDTO;
import com.app.datadistribution.integration.meta.dto.MetaWebhookPayloadDTO;
import com.app.datadistribution.integration.meta.entity.MetaWebhookEvent;
import com.app.datadistribution.integration.meta.entity.MetaWebhookEventStatus;
import com.app.datadistribution.integration.meta.exception.MetaGraphApiException;
import com.app.datadistribution.integration.meta.repository.MetaWebhookEventRepository;
import com.app.datadistribution.integration.meta.service.MetaCampaignCourseResolver;
import com.app.datadistribution.integration.meta.service.interfaces.IMetaGraphApiService;
import com.app.datadistribution.integration.meta.service.interfaces.IMetaLeadIngestionService;
import com.app.datadistribution.mapper.LeadMapper;
import com.app.datadistribution.repository.CourseRepository;
import com.app.datadistribution.repository.LeadSourceRepository;
import com.app.datadistribution.repository.ProgramRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.interfaces.ILeadDeduplicationService;
import com.app.datadistribution.service.interfaces.ILeadService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetaLeadIngestionServiceImpl implements IMetaLeadIngestionService {

    private final MetaProperties metaProperties;
    private final MetaWebhookEventRepository eventRepository;
    private final IMetaGraphApiService metaGraphApiService;
    private final ILeadService leadService;
    private final LeadSourceRepository leadSourceRepository;
    private final CourseRepository courseRepository;
    private final ProgramRepository programRepository;
    private final UserRepository userRepository;
    private final MetaCampaignCourseResolver metaCampaignCourseResolver;
    private final ILeadDeduplicationService deduplicationService;
    private final LeadMapper leadMapper;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$");

    @Override
    @Transactional
    public MetaWebhookEvent recordWebhookEvent(MetaWebhookPayloadDTO.Value eventValue, String rawPayload) {
        String leadgenId = eventValue.getLeadgenId();
        if (leadgenId == null || leadgenId.isBlank()) {
            throw new IllegalArgumentException("Cannot record webhook event: leadgen_id is missing");
        }

        Optional<MetaWebhookEvent> existingOpt = eventRepository.findByLeadgenId(leadgenId);
        if (existingOpt.isPresent()) {
            MetaWebhookEvent existing = existingOpt.get();
            log.info("Duplicate Meta webhook event detected for leadgen_id: {}. Current status: {}",
                    leadgenId, existing.getStatus());
            return existing;
        }

        MetaWebhookEvent event = MetaWebhookEvent.builder()
                .leadgenId(leadgenId)
                .pageId(eventValue.getPageId())
                .formId(eventValue.getFormId())
                .adId(eventValue.getAdId())
                .adgroupId(eventValue.getEffectiveAdgroupId())
                .status(MetaWebhookEventStatus.PENDING)
                .maxRetries(metaProperties.getMaxRetries())
                .retryCount(0)
                .rawPayload(rawPayload)
                .build();

        MetaWebhookEvent saved = eventRepository.save(event);
        log.info("Recorded new Meta webhook event: id={}, leadgen_id={}, form_id={}",
                saved.getId(), saved.getLeadgenId(), saved.getFormId());
        return saved;
    }

    @Override
    @Async("taskExecutor")
    public void processEventAsync(UUID eventId) {
        if (eventId == null) return;
        eventRepository.findById(eventId).ifPresent(this::processEvent);
    }

    @Override
    @Transactional
    public void processEvent(MetaWebhookEvent event) {
        if (event == null) return;

        if (event.getStatus() == MetaWebhookEventStatus.PROCESSED || event.getStatus() == MetaWebhookEventStatus.DUPLICATE) {
            log.info("Meta event {} already in state {}. Skipping processing.", event.getLeadgenId(), event.getStatus());
            return;
        }

        event.setStatus(MetaWebhookEventStatus.PROCESSING);
        eventRepository.save(event);

        try {
            // 1. Retrieve lead details from Meta Graph API
            MetaLeadResponseDTO leadDto = metaGraphApiService.retrieveLead(event.getLeadgenId());
            if (leadDto == null) {
                throw new RuntimeException("Null response received from Meta Graph API for leadgen_id: " + event.getLeadgenId());
            }

            // Retrieve campaign name: use leadDto if present, else fallback to Graph API lookup via campaignId or adId
            String effectiveCampaignId = leadDto.getCampaignId() != null ? leadDto.getCampaignId() : event.getCampaignId();
            String effectiveAdId = leadDto.getAdId() != null ? leadDto.getAdId() : event.getAdId();
            String campaignName = leadDto.getCampaignName();
            if (campaignName == null || campaignName.isBlank()) {
                campaignName = metaGraphApiService.retrieveCampaignName(effectiveCampaignId, effectiveAdId);
            }

            // Enrich event metadata
            if ((event.getAdId() == null || event.getAdId().isBlank()) && leadDto.getAdId() != null) {
                event.setAdId(leadDto.getAdId());
            }
            if ((event.getAdName() == null || event.getAdName().isBlank()) && leadDto.getAdName() != null) {
                event.setAdName(leadDto.getAdName());
            }
            if ((event.getAdgroupId() == null || event.getAdgroupId().isBlank()) && leadDto.getAdsetId() != null) {
                event.setAdgroupId(leadDto.getAdsetId());
            }
            if ((event.getAdsetName() == null || event.getAdsetName().isBlank()) && leadDto.getAdsetName() != null) {
                event.setAdsetName(leadDto.getAdsetName());
            }
            if ((event.getFormId() == null || event.getFormId().isBlank()) && leadDto.getFormId() != null) {
                event.setFormId(leadDto.getFormId());
            }
            if ((event.getCampaignId() == null || event.getCampaignId().isBlank()) && effectiveCampaignId != null) {
                event.setCampaignId(effectiveCampaignId);
            }
            if ((event.getCampaignName() == null || event.getCampaignName().isBlank()) && campaignName != null) {
                event.setCampaignName(campaignName);
            }

            // 2. Parse field_data
            Map<String, String> fieldMap = new HashMap<>();
            StringBuilder allQuestionsText = new StringBuilder();

            if (leadDto.getFieldData() != null) {
                for (MetaLeadFieldDataDTO f : leadDto.getFieldData()) {
                    if (f.getName() != null) {
                        String val = f.getFirstValue();
                        String normName = f.getName().trim().toLowerCase();
                        if (val != null && !val.isBlank()) {
                            fieldMap.put(normName, val.trim());
                            allQuestionsText.append("- ").append(f.getName()).append(": ").append(val.trim()).append("\n");
                        }
                    }
                }
            }

            // 3. Extract core fields with fallbacks
            String fullName = extractFullName(fieldMap, event.getLeadgenId());
            String phone = extractPhoneNumber(fieldMap);
            String email = extractEmail(fieldMap);
            String city = extractField(fieldMap, "city");
            String state = extractField(fieldMap, "state");

            // 4. Intelligent Academic Resolution using Campaign Name & Form field_data (Phase 2 & Phase 5)
            MetaCampaignCourseResolver.MetaCourseResolutionResult courseResolution =
                    metaCampaignCourseResolver.resolve(campaignName, leadDto.getFieldData());

            UUID resolvedCourseId = null;
            String resolvedCourseName = null;
            if (courseResolution.isSuccess()) {
                Course matchedCourse = courseResolution.getCourse();
                resolvedCourseId = matchedCourse.getId();
                resolvedCourseName = matchedCourse.getCourseName();
                log.info("Meta lead {}: resolved interested course '{}' ({}) via {}",
                        event.getLeadgenId(), resolvedCourseName, resolvedCourseId, courseResolution.getMappingMethod());
            } else {
                log.info("Meta lead {}: course resolution unresolved. Reason: {}",
                        event.getLeadgenId(), courseResolution.getMappingReason());
            }

            // Fallback for Program interest if course is unresolved and applicant specified a program name
            UUID resolvedProgramId = null;
            String courseAnswer = courseResolution.getCourseAnswer() != null
                    ? courseResolution.getCourseAnswer()
                    : extractCourseAnswer(fieldMap);

            if (resolvedCourseId == null && courseAnswer != null && !courseAnswer.isBlank()) {
                Optional<Program> programOpt = programRepository.findByNameIgnoreCaseAndIsDeletedFalse(courseAnswer)
                        .or(() -> programRepository.findByCodeIgnoreCaseAndIsDeletedFalse(courseAnswer));
                if (programOpt.isPresent() && programOpt.get().isActive()) {
                    resolvedProgramId = programOpt.get().getId();
                }
            }

            // Enrich event with audit and traceability fields (Phase 8)
            event.setCourseQuestion(courseResolution.getCourseQuestion());
            event.setCourseAnswer(courseResolution.getCourseAnswer());
            event.setResolvedCourseId(resolvedCourseId);
            event.setMappingMethod(courseResolution.getMappingMethod() != null ? courseResolution.getMappingMethod().name() : "UNRESOLVED");
            event.setMappingReason(courseResolution.getMappingReason());

            // 5. Source attribution
            List<UUID> leadSourceIds = resolveMetaLeadSource(leadDto.getPlatform());

            // 6. Structured Remarks & Source Details
            String sourceDetails = String.format("Meta Lead ID: %s | Campaign: %s | Form: %s | Ad: %s",
                    event.getLeadgenId(),
                    campaignName != null ? campaignName : (effectiveCampaignId != null ? effectiveCampaignId : "N/A"),
                    leadDto.getFormId() != null ? leadDto.getFormId() : "N/A",
                    leadDto.getAdId() != null ? leadDto.getAdId() : "N/A");

            StringBuilder remarksBuilder = new StringBuilder();
            remarksBuilder.append("[Meta Lead Ads Integration]\n");
            remarksBuilder.append("Lead ID: ").append(event.getLeadgenId()).append("\n");
            if (leadDto.getFormId() != null) remarksBuilder.append("Form ID: ").append(leadDto.getFormId()).append("\n");
            if (leadDto.getAdId() != null) remarksBuilder.append("Ad ID: ").append(leadDto.getAdId()).append("\n");
            if (leadDto.getAdName() != null) remarksBuilder.append("Ad Name: ").append(leadDto.getAdName()).append("\n");
            if (leadDto.getAdsetId() != null) remarksBuilder.append("Ad Set ID: ").append(leadDto.getAdsetId()).append("\n");
            if (leadDto.getAdsetName() != null) remarksBuilder.append("Ad Set Name: ").append(leadDto.getAdsetName()).append("\n");
            if (effectiveCampaignId != null) remarksBuilder.append("Campaign ID: ").append(effectiveCampaignId).append("\n");
            if (campaignName != null) remarksBuilder.append("Campaign Name: ").append(campaignName).append("\n");

            if (resolvedCourseName != null) {
                remarksBuilder.append("Interested Course: ").append(resolvedCourseName).append("\n");
                remarksBuilder.append("Course Mapping Method: ").append(courseResolution.getMappingMethod()).append("\n");
                remarksBuilder.append("Course Mapping Details: ").append(courseResolution.getMappingReason()).append("\n");
            } else {
                remarksBuilder.append("Interested Course: Unresolved\n");
                remarksBuilder.append("Course Mapping Details: ").append(courseResolution.getMappingReason()).append("\n");
            }

            if (courseResolution.getStatus() == MetaCampaignCourseResolver.MetaCourseResolutionResult.Status.CONFLICT) {
                remarksBuilder.append("Warning: ").append(courseResolution.getMappingReason()).append("\n");
            }

            if (leadDto.getPlatform() != null) remarksBuilder.append("Platform: ").append(leadDto.getPlatform()).append("\n");
            if (courseAnswer != null && resolvedCourseId == null && resolvedProgramId == null) {
                remarksBuilder.append("Course Inquiry: \"").append(courseAnswer).append("\" (No direct catalog match - review manually)\n");
            }
            if (allQuestionsText.length() > 0) {
                remarksBuilder.append("\nForm Responses:\n").append(allQuestionsText);
            }

            // 7. Resolve System User Context
            User systemUser = resolveSystemUser();

            // 8. Build LeadRequest
            List<UUID> interestedCourseIds = resolvedCourseId != null ? List.of(resolvedCourseId) : Collections.emptyList();
            LeadRequest leadRequest = LeadRequest.builder()
                    .fullName(fullName)
                    .phoneNumber(phone)
                    .email(email)
                    .city(city)
                    .state(state)
                    .country("India")
                    .leadSourceIds(leadSourceIds)
                    .sourceDetails(sourceDetails.length() > 255 ? sourceDetails.substring(0, 255) : sourceDetails)
                    .courseId(resolvedCourseId)
                    .interestedCourseIds(interestedCourseIds)
                    .programId(resolvedProgramId)
                    .remarks(remarksBuilder.toString().trim())
                    .active(true)
                    .build();

            // 9. Canonical Deduplication Check (Rules A through F) & Lead Persistence (Phase 6)
            String normPhone = deduplicationService != null ? deduplicationService.normalizePhone(phone) : phone;
            Course resolvedCourse = resolvedCourseId != null ? courseRepository.findById(resolvedCourseId).orElse(null) : null;
            Program resolvedProgram = resolvedProgramId != null ? programRepository.findById(resolvedProgramId).orElse(null) : null;

            LeadMatchResult matchResult = null;
            if (deduplicationService != null) {
                LeadSource primarySource = (leadSourceIds != null && !leadSourceIds.isEmpty())
                        ? leadSourceRepository.findById(leadSourceIds.get(0)).orElse(null)
                        : null;
                matchResult = deduplicationService.executeWithPhoneLock(normPhone, () ->
                        deduplicationService.matchLead(
                                fullName,
                                phone,
                                email,
                                primarySource,
                                resolvedCourse != null ? Set.of(resolvedCourse) : Collections.emptySet(),
                                resolvedProgram != null ? Set.of(resolvedProgram) : Collections.emptySet()
                        )
                );
            }

            LeadResponse leadResponse;
            if (matchResult != null && matchResult.isMatched()) {
                Lead matchedLead = matchResult.getMatchedLead();
                log.info("Meta lead matched existing lead {}: {} (match status: {})",
                        matchedLead.getLeadCode(), matchedLead.getFullName(), matchResult.getStatus());

                Lead updatedLead = deduplicationService.enrichAndSaveMatchedLead(
                        matchedLead,
                        leadRequest,
                        matchResult,
                        systemUser,
                        "Lead imported from Meta Lead Ads."
                );
                leadResponse = leadMapper.toDto(updatedLead);
            } else {
                leadResponse = leadService.createLeadFromExternal(leadRequest, systemUser);
            }

            // 10. Update Event Status
            event.setStatus(MetaWebhookEventStatus.PROCESSED);
            event.setLeadId(leadResponse.getId());
            event.setLeadCode(leadResponse.getLeadCode());
            event.setErrorMessage(null);
            event.setNextRetryAt(null);
            eventRepository.save(event);

            log.info("Successfully ingested Meta lead. leadgen_id: {}, lead_code: {}, course_id: {}",
                    event.getLeadgenId(), leadResponse.getLeadCode(), resolvedCourseId);

        } catch (Exception e) {
            log.error("Failed to process Meta webhook event for leadgen_id: {}. Attempt {} of {}",
                    event.getLeadgenId(), event.getRetryCount() + 1, event.getMaxRetries(), e);

            boolean isRetryable = true;
            String errorMsg = e.getMessage();

            if (e instanceof MetaGraphApiException) {
                MetaGraphApiException mgEx = (MetaGraphApiException) e;
                isRetryable = mgEx.isRetryable();
                errorMsg = String.format("[HTTP %d | Code %s | Subcode %s] %s",
                        mgEx.getHttpStatus(),
                        mgEx.getErrorCode() != null ? mgEx.getErrorCode() : "N/A",
                        mgEx.getErrorSubcode() != null ? mgEx.getErrorSubcode() : "N/A",
                        mgEx.getMetaMessage());
            }

            int newRetryCount = event.getRetryCount() + 1;
            event.setRetryCount(newRetryCount);
            event.setErrorMessage(errorMsg != null && errorMsg.length() > 2000
                    ? errorMsg.substring(0, 2000)
                    : errorMsg);

            if (!isRetryable || newRetryCount >= event.getMaxRetries()) {
                event.setStatus(MetaWebhookEventStatus.FAILED_PERMANENTLY);
                event.setNextRetryAt(null);
                log.warn("Meta event for leadgen_id: {} marked FAILED_PERMANENTLY (Retryable: {}, Attempt: {}/{})",
                        event.getLeadgenId(), isRetryable, newRetryCount, event.getMaxRetries());
            } else {
                event.setStatus(MetaWebhookEventStatus.FAILED);
                // Exponential backoff: 2^retryCount * 2 minutes
                long backoffMinutes = (long) Math.pow(2, newRetryCount);
                event.setNextRetryAt(LocalDateTime.now().plusMinutes(backoffMinutes));
                log.info("Meta event for leadgen_id: {} scheduled for retry in {} minutes at {} (attempt {}/{})",
                        event.getLeadgenId(), backoffMinutes, event.getNextRetryAt(), newRetryCount, event.getMaxRetries());
            }

            eventRepository.save(event);
        }
    }

    @Override
    @Transactional
    public boolean retryEvent(UUID eventId) {
        if (eventId == null) return false;
        Optional<MetaWebhookEvent> opt = eventRepository.findById(eventId);
        if (opt.isEmpty()) return false;
        MetaWebhookEvent event = opt.get();
        if (event.getStatus() == MetaWebhookEventStatus.PROCESSED || event.getStatus() == MetaWebhookEventStatus.DUPLICATE) {
            log.info("Event {} is already {}, skipping administrative retry", eventId, event.getStatus());
            return false;
        }
        event.setStatus(MetaWebhookEventStatus.PENDING);
        event.setNextRetryAt(null);
        event.setErrorMessage(null);
        eventRepository.save(event);
        processEvent(event);
        return true;
    }

    @Override
    @Transactional
    public int retryFailedEvents() {
        List<MetaWebhookEvent> failedEvents = eventRepository.findAllByStatusInAndIsDeletedFalse(
                List.of(MetaWebhookEventStatus.FAILED, MetaWebhookEventStatus.FAILED_PERMANENTLY));
        int count = 0;
        for (MetaWebhookEvent event : failedEvents) {
            if (event.getLeadId() == null && event.getStatus() != MetaWebhookEventStatus.PROCESSED) {
                event.setStatus(MetaWebhookEventStatus.PENDING);
                event.setNextRetryAt(null);
                event.setErrorMessage(null);
                eventRepository.save(event);
                processEvent(event);
                count++;
            }
        }
        log.info("Administratively reprocessed {} failed Meta webhook events", count);
        return count;
    }

    private String extractFullName(Map<String, String> fieldMap, String leadgenId) {
        if (fieldMap.containsKey("full_name")) {
            return fieldMap.get("full_name");
        }
        if (fieldMap.containsKey("name")) {
            return fieldMap.get("name");
        }
        String first = fieldMap.getOrDefault("first_name", "");
        String last = fieldMap.getOrDefault("last_name", "");
        String combined = (first + " " + last).trim();
        if (!combined.isEmpty()) {
            return combined;
        }
        return "Meta Lead " + leadgenId;
    }

    private String extractPhoneNumber(Map<String, String> fieldMap) {
        String raw = fieldMap.get("phone_number");
        if (raw == null) raw = fieldMap.get("phone");
        if (raw == null) raw = fieldMap.get("mobile");
        if (raw == null) raw = fieldMap.get("contact_number");

        if (raw == null || raw.isBlank()) {
            return "0000000000"; // Fallback to satisfy non-null validation if completely missing
        }

        // Clean digits and optional leading '+'
        String cleaned = raw.replaceAll("[^0-9+]", "");
        if (cleaned.length() > 20) {
            cleaned = cleaned.substring(0, 20);
        }
        return cleaned.isBlank() ? "0000000000" : cleaned;
    }

    private String extractEmail(Map<String, String> fieldMap) {
        String raw = fieldMap.get("email");
        if (raw == null) raw = fieldMap.get("email_address");
        if (raw != null && !raw.isBlank()) {
            String trimmed = raw.trim().toLowerCase();
            if (trimmed.length() <= 100 && EMAIL_PATTERN.matcher(trimmed).matches()) {
                return trimmed;
            }
        }
        return null;
    }

    private String extractField(Map<String, String> fieldMap, String key) {
        String val = fieldMap.get(key);
        if (val != null && !val.isBlank()) {
            String trimmed = val.trim();
            return trimmed.length() > 100 ? trimmed.substring(0, 100) : trimmed;
        }
        return null;
    }

    private String extractCourseAnswer(Map<String, String> fieldMap) {
        String[] potentialCourseKeys = {
                "what_course_are_you_interested_in?",
                "what_course_are_you_interested_in",
                "course",
                "course_interested",
                "interested_course",
                "program",
                "program_interested",
                "select_course",
                "preferred_course"
        };
        for (String k : potentialCourseKeys) {
            if (fieldMap.containsKey(k)) {
                return fieldMap.get(k);
            }
        }
        // Also check if any key contains "course" or "program"
        for (Map.Entry<String, String> entry : fieldMap.entrySet()) {
            if (entry.getKey().contains("course") || entry.getKey().contains("program")) {
                return entry.getValue();
            }
        }
        return null;
    }

    private List<UUID> resolveMetaLeadSource(String platform) {
        // Look up META_ADS (already seeded in DB)
        Optional<LeadSource> metaSourceOpt = leadSourceRepository.findByCodeIgnoreCase("META_ADS")
                .or(() -> leadSourceRepository.findByNameIgnoreCase("Meta Ads"));

        if (metaSourceOpt.isPresent()) {
            return List.of(metaSourceOpt.get().getId());
        }

        // If not found, look up any active source
        List<LeadSource> all = leadSourceRepository.findAll();
        for (LeadSource s : all) {
            if (!s.isDeleted() && s.isActive()) {
                return List.of(s.getId());
            }
        }
        return Collections.emptyList();
    }

    private User resolveSystemUser() {
        String username = metaProperties.getSystemUsername();
        if (username != null && !username.isBlank()) {
            Optional<User> uOpt = userRepository.findByUsername(username);
            if (uOpt.isPresent() && !uOpt.get().isDeleted() && uOpt.get().isActive()) {
                return uOpt.get();
            }
        }

        return userRepository.findByUsername("superadmin")
                .filter(u -> !u.isDeleted())
                .or(() -> userRepository.findAll().stream().filter(u -> !u.isDeleted()).findFirst())
                .orElse(null);
    }
}
