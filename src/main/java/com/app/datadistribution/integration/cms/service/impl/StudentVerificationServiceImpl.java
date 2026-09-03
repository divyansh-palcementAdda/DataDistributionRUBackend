package com.app.datadistribution.integration.cms.service.impl;

import com.app.datadistribution.integration.cms.dto.StudentVerificationRequest;
import com.app.datadistribution.integration.cms.dto.StudentVerificationResponse;
import com.app.datadistribution.integration.cms.enums.MatchStatus;
import com.app.datadistribution.integration.cms.service.IStudentVerificationService;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentVerificationServiceImpl implements IStudentVerificationService {

    @Qualifier("cmsRestTemplate")
    private final RestTemplate restTemplate;

    @Value("${cms.base-url:https://cms.areyoureporting.com/api}")
    private String cmsBaseUrl;

    @Value("${integration.api.key:default-integration-dev-key}")
    private String cmsApiKey;

    @Override
    public StudentVerificationResponse verifyStudent(StudentVerificationRequest request) {
        if (request == null || !request.hasIdentifyingFields()) {
            log.warn("[CMS-Verification] Verification request aborted: insufficient identifying data for leadId={}",
                    request != null ? request.getLeadId() : "null");
            return StudentVerificationResponse.builder()
                    .verified(false)
                    .matchStatus(MatchStatus.NO_MATCH)
                    .confidenceScore(0)
                    .multipleMatches(false)
                    .totalCandidatesEvaluated(0)
                    .matchedStudents(Collections.emptyList())
                    .message("Insufficient identifying student data provided for CMS verification.")
                    .build();
        }

        String normalizedBaseUrl = cmsBaseUrl.endsWith("/")
                ? cmsBaseUrl.substring(0, cmsBaseUrl.length() - 1)
                : cmsBaseUrl;
        String endpointUrl = normalizedBaseUrl + "/integration/student/verify";

        log.info("[CMS-Verification] Invoking CMS student verification at URL: {} for leadId: {}",
                endpointUrl, request.getLeadId());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (cmsApiKey != null && !cmsApiKey.isBlank()) {
            String key = cmsApiKey.trim();
            headers.set("X-API-KEY", key);
            headers.set("x-api-key", key);
            headers.set("X-Integration-Key", key);
            headers.set("x-integration-key", key);
            headers.set("Integration-Key", key);
            headers.set("integration-api-key", key);
            headers.set("api-key", key);
            headers.set("Authorization", "Bearer " + key);
            headers.set("X-Auth-Token", key);

            endpointUrl = endpointUrl + "?apiKey=" + java.net.URLEncoder.encode(key, java.nio.charset.StandardCharsets.UTF_8)
                    + "&integrationKey=" + java.net.URLEncoder.encode(key, java.nio.charset.StandardCharsets.UTF_8);

            String masked = key.length() > 6 ? key.substring(0, 3) + "***" + key.substring(key.length() - 2) : "***";
            log.info("[CMS-Verification] Attached integration key ({}) to outgoing CMS request headers & query params.", masked);
        } else {
            log.warn("[CMS-Verification] WARNING: No cms.api-key / integration.api.key configured! CMS may reject request with 401 Unauthorized.");
        }

        HttpEntity<StudentVerificationRequest> entity = new HttpEntity<>(request, headers);

        try {
            long startTime = System.currentTimeMillis();
            ResponseEntity<StudentVerificationResponse> responseEntity = restTemplate.exchange(
                    endpointUrl,
                    HttpMethod.POST,
                    entity,
                    StudentVerificationResponse.class
            );
            long duration = System.currentTimeMillis() - startTime;

            StudentVerificationResponse response = responseEntity.getBody();
            if (response != null) {
                log.info("[CMS-Verification] Response received in {}ms for leadId={}: verified={}, matchStatus={}, score={}, candidates={}",
                        duration,
                        request.getLeadId(),
                        response.isVerified(),
                        response.getMatchStatus(),
                        response.getConfidenceScore(),
                        response.getTotalCandidatesEvaluated());
                return response;
            } else {
                log.warn("[CMS-Verification] Empty response body received from CMS in {}ms for leadId={}", duration, request.getLeadId());
                return StudentVerificationResponse.builder()
                        .verified(false)
                        .matchStatus(MatchStatus.ERROR)
                        .confidenceScore(0)
                        .multipleMatches(false)
                        .totalCandidatesEvaluated(0)
                        .matchedStudents(Collections.emptyList())
                        .message("Empty response received from CMS verification service.")
                        .build();
            }
        } catch (HttpStatusCodeException e) {
            String body = e.getResponseBodyAsString();
            log.error("[CMS-Verification] HTTP error {} from CMS for leadId={}: {}",
                    e.getStatusCode(), request.getLeadId(), body);

            String detailedMsg = "CMS verification returned HTTP error: " + e.getStatusCode();
            if (body != null && !body.isBlank()) {
                if (body.contains("\"message\":")) {
                    try {
                        int idx = body.indexOf("\"message\":\"");
                        if (idx != -1) {
                            int endIdx = body.indexOf("\"", idx + 11);
                            if (endIdx != -1) {
                                detailedMsg = body.substring(idx + 11, endIdx);
                            }
                        }
                    } catch (Exception parseEx) {
                        detailedMsg = body;
                    }
                } else {
                    detailedMsg = body;
                }
            }

            return StudentVerificationResponse.builder()
                    .verified(false)
                    .matchStatus(MatchStatus.ERROR)
                    .confidenceScore(0)
                    .multipleMatches(false)
                    .totalCandidatesEvaluated(0)
                    .matchedStudents(Collections.emptyList())
                    .message(detailedMsg)
                    .build();
        } catch (ResourceAccessException e) {
            log.error("[CMS-Verification] Network or timeout error connecting to CMS for leadId={}: {}",
                    request.getLeadId(), e.getMessage());
            return StudentVerificationResponse.builder()
                    .verified(false)
                    .matchStatus(MatchStatus.ERROR)
                    .confidenceScore(0)
                    .multipleMatches(false)
                    .totalCandidatesEvaluated(0)
                    .matchedStudents(Collections.emptyList())
                    .message("CMS service timeout or connection failure: " + e.getMessage())
                    .build();
        } catch (Exception e) {
            log.error("[CMS-Verification] Unexpected error calling CMS for leadId={}: {}",
                    request.getLeadId(), e.getMessage(), e);
            return StudentVerificationResponse.builder()
                    .verified(false)
                    .matchStatus(MatchStatus.ERROR)
                    .confidenceScore(0)
                    .multipleMatches(false)
                    .totalCandidatesEvaluated(0)
                    .matchedStudents(Collections.emptyList())
                    .message("CMS verification unexpected error: " + e.getMessage())
                    .build();
        }
    }
}
