package com.app.datadistribution.integration.meta.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.datadistribution.dto.lead.LeadRequest;
import com.app.datadistribution.dto.lead.LeadResponse;
import com.app.datadistribution.entity.Course;
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
import com.app.datadistribution.integration.meta.service.interfaces.IMetaGraphApiService;
import com.app.datadistribution.integration.meta.service.interfaces.IMetaLeadIngestionService;
import com.app.datadistribution.repository.CourseRepository;
import com.app.datadistribution.repository.LeadSourceRepository;
import com.app.datadistribution.repository.ProgramRepository;
import com.app.datadistribution.repository.UserRepository;
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

            // Enrich event metadata if missing from initial webhook payload
            if ((event.getAdId() == null || event.getAdId().isBlank()) && leadDto.getAdId() != null) {
                event.setAdId(leadDto.getAdId());
            }
            if ((event.getAdgroupId() == null || event.getAdgroupId().isBlank()) && leadDto.getAdsetId() != null) {
                event.setAdgroupId(leadDto.getAdsetId());
            }
            if ((event.getFormId() == null || event.getFormId().isBlank()) && leadDto.getFormId() != null) {
                event.setFormId(leadDto.getFormId());
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

            // 4. Academic resolution without guessing
            UUID resolvedCourseId = null;
            UUID resolvedProgramId = null;
            String courseAnswer = extractCourseAnswer(fieldMap);
            if (courseAnswer != null && !courseAnswer.isBlank()) {
                // Exact / case-insensitive search against active Courses
                Optional<Course> courseOpt = courseRepository.findByCourseNameIgnoreCaseAndIsDeletedFalse(courseAnswer)
                        .or(() -> courseRepository.findByCourseCodeIgnoreCaseAndIsDeletedFalse(courseAnswer));
                if (courseOpt.isPresent() && courseOpt.get().isActive()) {
                    resolvedCourseId = courseOpt.get().getId();
                } else {
                    // Exact / case-insensitive search against active Programs
                    Optional<Program> programOpt = programRepository.findByNameIgnoreCaseAndIsDeletedFalse(courseAnswer)
                            .or(() -> programRepository.findByCodeIgnoreCaseAndIsDeletedFalse(courseAnswer));
                    if (programOpt.isPresent() && programOpt.get().isActive()) {
                        resolvedProgramId = programOpt.get().getId();
                    }
                }
            }

            // 5. Source attribution
            List<UUID> leadSourceIds = resolveMetaLeadSource(leadDto.getPlatform());

            // 6. Structured Remarks & Source Details
            String sourceDetails = String.format("Meta Lead ID: %s | Form: %s | Ad: %s",
                    event.getLeadgenId(),
                    leadDto.getFormId() != null ? leadDto.getFormId() : "N/A",
                    leadDto.getAdId() != null ? leadDto.getAdId() : "N/A");

            StringBuilder remarksBuilder = new StringBuilder();
            remarksBuilder.append("[Meta Lead Ads Integration]\n");
            remarksBuilder.append("Lead ID: ").append(event.getLeadgenId()).append("\n");
            if (leadDto.getFormId() != null) remarksBuilder.append("Form ID: ").append(leadDto.getFormId()).append("\n");
            if (leadDto.getAdId() != null) remarksBuilder.append("Ad ID: ").append(leadDto.getAdId()).append("\n");
            if (leadDto.getAdsetId() != null) remarksBuilder.append("Ad Set ID: ").append(leadDto.getAdsetId()).append("\n");
            if (leadDto.getCampaignId() != null) remarksBuilder.append("Campaign ID: ").append(leadDto.getCampaignId()).append("\n");
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
                    .programId(resolvedProgramId)
                    .remarks(remarksBuilder.toString().trim())
                    .active(true)
                    .build();

            // 9. Save Lead using existing business workflow
            LeadResponse leadResponse = leadService.createLeadFromExternal(leadRequest, systemUser);

            // 10. Update Event Status
            event.setStatus(MetaWebhookEventStatus.PROCESSED);
            event.setLeadId(leadResponse.getId());
            event.setLeadCode(leadResponse.getLeadCode());
            event.setErrorMessage(null);
            event.setNextRetryAt(null);
            eventRepository.save(event);

            log.info("Successfully ingested Meta lead. leadgen_id: {}, created lead_code: {}",
                    event.getLeadgenId(), leadResponse.getLeadCode());

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

        // Fallback to superadmin or any admin
        Optional<User> superadminOpt = userRepository.findByUsername("superadmin");
        if (superadminOpt.isPresent() && !superadminOpt.get().isDeleted() && superadminOpt.get().isActive()) {
            return superadminOpt.get();
        }

        List<User> allUsers = userRepository.findAll();
        for (User u : allUsers) {
            if (!u.isDeleted() && u.isActive()) {
                if (u.getRoles() != null && u.getRoles().stream().anyMatch(r ->
                        RoleType.SUPER_ADMIN.name().equalsIgnoreCase(r.getName()) ||
                        RoleType.ADMIN.name().equalsIgnoreCase(r.getName()))) {
                    return u;
                }
            }
        }

        // Absolute fallback to first active user
        return allUsers.stream().filter(u -> !u.isDeleted() && u.isActive()).findFirst()
                .orElseThrow(() -> new IllegalStateException("No active users found in system to associate with imported lead"));
    }
}
