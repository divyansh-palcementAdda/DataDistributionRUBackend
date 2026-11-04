package com.app.datadistribution.integration.meta.service.impl;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.app.datadistribution.integration.meta.config.MetaProperties;
import com.app.datadistribution.integration.meta.dto.MetaAdDTO;
import com.app.datadistribution.integration.meta.dto.MetaCampaignDTO;
import com.app.datadistribution.integration.meta.dto.MetaLeadResponseDTO;
import com.app.datadistribution.integration.meta.exception.MetaGraphApiException;
import com.app.datadistribution.integration.meta.service.interfaces.IMetaGraphApiService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class MetaGraphApiServiceImpl implements IMetaGraphApiService {

    public static final String ENRICHED_LEAD_FIELDS = "id,created_time,form_id,ad_id,ad_name,adset_id,adset_name,campaign_id,campaign_name,platform,field_data";
    public static final String BASELINE_LEAD_FIELDS = "id,created_time,form_id,ad_id,adset_id,campaign_id,platform,field_data";
    public static final String SUPPORTED_LEAD_FIELDS = ENRICHED_LEAD_FIELDS;

    private final MetaProperties metaProperties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // In-memory Caffeine caches for campaign metadata with 2-hour TTL
    private final Cache<String, String> campaignNameCache = Caffeine.newBuilder()
            .expireAfterWrite(2, TimeUnit.HOURS)
            .maximumSize(1000)
            .build();

    private final Cache<String, String> adCampaignNameCache = Caffeine.newBuilder()
            .expireAfterWrite(2, TimeUnit.HOURS)
            .maximumSize(1000)
            .build();

    @Autowired
    public MetaGraphApiServiceImpl(MetaProperties metaProperties, RestTemplateBuilder restTemplateBuilder) {
        this.metaProperties = metaProperties;
        this.restTemplate = restTemplateBuilder
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(15))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public MetaGraphApiServiceImpl(MetaProperties metaProperties, RestTemplate restTemplate) {
        this.metaProperties = metaProperties;
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public MetaLeadResponseDTO retrieveLead(String leadgenId) {
        if (leadgenId == null || leadgenId.isBlank()) {
            throw new IllegalArgumentException("leadgenId must not be null or blank");
        }

        String pageAccessToken = metaProperties.getPageAccessToken();
        if (pageAccessToken == null || pageAccessToken.isBlank()) {
            throw new IllegalStateException("Meta page access token is not configured in application properties/environment");
        }

        // 1. Try requesting enriched fields first
        try {
            MetaLeadResponseDTO dto = executeLeadQuery(leadgenId, pageAccessToken, ENRICHED_LEAD_FIELDS);
            if (dto != null && dto.getCampaignId() != null && dto.getCampaignName() != null && !dto.getCampaignName().isBlank()) {
                campaignNameCache.put(dto.getCampaignId(), dto.getCampaignName().trim());
            }
            return dto;
        } catch (MetaGraphApiException e) {
            // If code 100 (unsupported field on this leadgen endpoint), fallback to baseline fields
            if (Integer.valueOf(100).equals(e.getErrorCode())) {
                log.warn("Enriched fields rejected with code 100 for leadgen_id: {}. Falling back to baseline fields.", leadgenId);
                return executeLeadQuery(leadgenId, pageAccessToken, BASELINE_LEAD_FIELDS);
            }
            throw e;
        }
    }

    private MetaLeadResponseDTO executeLeadQuery(String leadgenId, String pageAccessToken, String fields) {
        String baseUrl = metaProperties.getGraphApiBaseUrl();
        String apiVersion = metaProperties.getGraphApiVersion();

        String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .pathSegment(apiVersion, leadgenId)
                .queryParam("fields", fields)
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(pageAccessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        try {
            log.info("Requesting lead details from Meta Graph API for leadgen_id: {}", leadgenId);
            ResponseEntity<MetaLeadResponseDTO> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    requestEntity,
                    MetaLeadResponseDTO.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("Successfully retrieved Meta lead for leadgen_id: {}, form_id: {}",
                        leadgenId, response.getBody().getFormId());
                return response.getBody();
            } else {
                throw new MetaGraphApiException(
                        response.getStatusCode().value(),
                        "Unexpected response status from Meta Graph API: " + response.getStatusCode(),
                        false,
                        null
                );
            }

        } catch (HttpClientErrorException e) {
            int status = e.getStatusCode().value();
            String responseBody = e.getResponseBodyAsString();
            ParsedMetaError parsedError = parseMetaErrorBody(responseBody);

            Integer errorCode = parsedError.code;
            Integer errorSubcode = parsedError.subcode;
            String errorType = parsedError.type;
            String rawMessage = parsedError.message != null ? parsedError.message : e.getStatusText();
            String sanitizedMessage = sanitize(rawMessage);

            boolean retryable = isRetryableClientError(status, errorCode);

            log.error("Meta Graph API client error for leadgen_id {}: Status {}, Code {}, Subcode {}, Type {}, Retryable: {}. Message: {}",
                    leadgenId, status, errorCode, errorSubcode, errorType, retryable, sanitizedMessage);

            throw new MetaGraphApiException(
                    status,
                    errorCode,
                    errorSubcode,
                    errorType,
                    sanitizedMessage,
                    retryable,
                    e
            );

        } catch (HttpServerErrorException e) {
            int status = e.getStatusCode().value();
            log.error("Server error from Meta Graph API for leadgen_id {}: Status {}. Retryable: true",
                    leadgenId, status);
            throw new MetaGraphApiException(
                    status,
                    null,
                    null,
                    "ServerError",
                    "Meta Graph API temporary server failure: " + status,
                    true,
                    e
            );

        } catch (ResourceAccessException e) {
            log.error("Network I/O timeout connecting to Meta Graph API for leadgen_id: {}. Retryable: true", leadgenId);
            throw new MetaGraphApiException(
                    504,
                    null,
                    null,
                    "NetworkTimeout",
                    "Network timeout connecting to Meta Graph API",
                    true,
                    e
            );

        } catch (MetaGraphApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error retrieving lead details for leadgen_id: {}", leadgenId, e);
            throw new MetaGraphApiException(
                    500,
                    null,
                    null,
                    "UnexpectedError",
                    "Failed to retrieve Meta lead: " + sanitize(e.getMessage()),
                    false,
                    e
            );
        }
    }

    @Override
    public String retrieveCampaignName(String campaignId, String adId) {
        // 1. Try resolving via campaignId first
        if (campaignId != null && !campaignId.isBlank()) {
            String trimmedCampId = campaignId.trim();
            String cached = campaignNameCache.getIfPresent(trimmedCampId);
            if (cached != null) {
                log.debug("Found cached Meta campaign name for campaign_id {}: {}", trimmedCampId, cached);
                return cached;
            }

            try {
                String fetchedName = fetchCampaignNameDirect(trimmedCampId);
                if (fetchedName != null && !fetchedName.isBlank()) {
                    campaignNameCache.put(trimmedCampId, fetchedName.trim());
                    return fetchedName.trim();
                }
            } catch (Exception e) {
                log.warn("Failed to fetch campaign name directly for campaign_id {}: {}", trimmedCampId, sanitize(e.getMessage()));
            }
        }

        // 2. If campaignId lookup didn't succeed, attempt resolving via adId
        if (adId != null && !adId.isBlank()) {
            String trimmedAdId = adId.trim();
            String cachedFromAd = adCampaignNameCache.getIfPresent(trimmedAdId);
            if (cachedFromAd != null) {
                log.debug("Found cached Meta campaign name for ad_id {}: {}", trimmedAdId, cachedFromAd);
                return cachedFromAd;
            }

            try {
                String fetchedName = fetchCampaignNameViaAd(trimmedAdId);
                if (fetchedName != null && !fetchedName.isBlank()) {
                    adCampaignNameCache.put(trimmedAdId, fetchedName.trim());
                    return fetchedName.trim();
                }
            } catch (Exception e) {
                log.warn("Failed to fetch campaign name via ad_id {}: {}", trimmedAdId, sanitize(e.getMessage()));
            }
        }

        return null;
    }

    private String fetchCampaignNameDirect(String campaignId) {
        String pageAccessToken = metaProperties.getPageAccessToken();
        if (pageAccessToken == null || pageAccessToken.isBlank()) {
            return null;
        }

        String baseUrl = metaProperties.getGraphApiBaseUrl();
        String apiVersion = metaProperties.getGraphApiVersion();

        String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .pathSegment(apiVersion, campaignId)
                .queryParam("fields", "id,name")
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(pageAccessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        log.info("Requesting campaign metadata from Meta Graph API for campaign_id: {}", campaignId);
        ResponseEntity<MetaCampaignDTO> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                requestEntity,
                MetaCampaignDTO.class
        );

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            return response.getBody().getName();
        }
        return null;
    }

    private String fetchCampaignNameViaAd(String adId) {
        String pageAccessToken = metaProperties.getPageAccessToken();
        if (pageAccessToken == null || pageAccessToken.isBlank()) {
            return null;
        }

        String baseUrl = metaProperties.getGraphApiBaseUrl();
        String apiVersion = metaProperties.getGraphApiVersion();

        java.net.URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .pathSegment(apiVersion, adId)
                .queryParam("fields", "id,name,campaign{id,name}")
                .build()
                .toUri();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(pageAccessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        log.info("Requesting ad and campaign metadata from Meta Graph API for ad_id: {}", adId);
        ResponseEntity<MetaAdDTO> response = restTemplate.exchange(
                uri,
                HttpMethod.GET,
                requestEntity,
                MetaAdDTO.class
        );

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            MetaAdDTO body = response.getBody();
            if (body.getCampaign() != null && body.getCampaign().getName() != null) {
                String cName = body.getCampaign().getName();
                if (body.getCampaign().getId() != null) {
                    campaignNameCache.put(body.getCampaign().getId(), cName);
                }
                return cName;
            }
        }
        return null;
    }

    private ParsedMetaError parseMetaErrorBody(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return new ParsedMetaError(null, null, null, null);
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode errorNode = root.get("error");
            if (errorNode != null) {
                String message = errorNode.has("message") ? errorNode.get("message").asText() : null;
                String type = errorNode.has("type") ? errorNode.get("type").asText() : null;
                Integer code = errorNode.has("code") ? errorNode.get("code").asInt() : null;
                Integer subcode = errorNode.has("error_subcode") ? errorNode.get("error_subcode").asInt() : null;
                return new ParsedMetaError(code, subcode, type, message);
            }
        } catch (Exception ex) {
            log.debug("Could not parse Meta error JSON body: {}", ex.getMessage());
        }
        return new ParsedMetaError(null, null, null, responseBody);
    }

    private boolean isRetryableClientError(int httpStatus, Integer metaErrorCode) {
        if (httpStatus == 429) {
            return true;
        }
        if (metaErrorCode != null) {
            int code = metaErrorCode;
            if (code == 4 || code == 17 || code == 32 || code == 613) {
                return true;
            }
            if (code == 1 || code == 2) {
                return true;
            }
            if (code == 100 || code == 190 || (code >= 200 && code <= 299)) {
                return false;
            }
        }
        return false;
    }

    private String sanitize(String input) {
        if (input == null) return null;
        return input.replaceAll("(?i)(access_token|bearer)[=:\\s]+[A-Za-z0-9_.-]+", "$1 [REDACTED]");
    }

    private static class ParsedMetaError {
        final Integer code;
        final Integer subcode;
        final String type;
        final String message;

        ParsedMetaError(Integer code, Integer subcode, String type, String message) {
            this.code = code;
            this.subcode = subcode;
            this.type = type;
            this.message = message;
        }
    }
}
