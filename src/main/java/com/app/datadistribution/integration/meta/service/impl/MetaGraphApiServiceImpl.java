package com.app.datadistribution.integration.meta.service.impl;

import java.time.Duration;

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
import com.app.datadistribution.integration.meta.dto.MetaLeadResponseDTO;
import com.app.datadistribution.integration.meta.exception.MetaGraphApiException;
import com.app.datadistribution.integration.meta.service.interfaces.IMetaGraphApiService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class MetaGraphApiServiceImpl implements IMetaGraphApiService {

    public static final String SUPPORTED_LEAD_FIELDS = "id,created_time,form_id,ad_id,adset_id,campaign_id,platform,field_data";

    private final MetaProperties metaProperties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

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

        String baseUrl = metaProperties.getGraphApiBaseUrl();
        String apiVersion = metaProperties.getGraphApiVersion();

        String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .pathSegment(apiVersion, leadgenId)
                .queryParam("fields", SUPPORTED_LEAD_FIELDS)
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
        // Rate limit errors
        if (httpStatus == 429) {
            return true;
        }
        if (metaErrorCode != null) {
            int code = metaErrorCode;
            // 4: Application request limit reached, 17: User request limit reached, 32: Page request limit reached, 613: Rate limit
            if (code == 4 || code == 17 || code == 32 || code == 613) {
                return true;
            }
            // 1 or 2: Temporary API service error on Meta's side
            if (code == 1 || code == 2) {
                return true;
            }
            // 100: Invalid parameter or unsupported field -> Permanent
            // 190: Invalid or expired access token -> Permanent until admin updates token
            // 200..299: Permissions error -> Permanent until admin updates permissions
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
