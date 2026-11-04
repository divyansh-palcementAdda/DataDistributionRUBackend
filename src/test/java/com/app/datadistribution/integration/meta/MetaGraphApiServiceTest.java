package com.app.datadistribution.integration.meta;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.app.datadistribution.integration.meta.config.MetaProperties;
import com.app.datadistribution.integration.meta.dto.MetaLeadResponseDTO;
import com.app.datadistribution.integration.meta.exception.MetaGraphApiException;
import com.app.datadistribution.integration.meta.service.impl.MetaGraphApiServiceImpl;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MetaGraphApiServiceTest {

    private MetaProperties metaProperties;
    private RestTemplate restTemplate;
    private MockRestServiceServer mockServer;
    private MetaGraphApiServiceImpl graphApiService;

    private static final String PAGE_ACCESS_TOKEN = "EAABtest_token_secret_value_12345";
    private static final String LEADGEN_ID = "1681819043581192";

    @BeforeEach
    void setUp() {
        metaProperties = new MetaProperties();
        metaProperties.setPageAccessToken(PAGE_ACCESS_TOKEN);
        metaProperties.setGraphApiBaseUrl("https://graph.facebook.com");
        metaProperties.setGraphApiVersion("v21.0");

        restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);
        graphApiService = new MetaGraphApiServiceImpl(metaProperties, restTemplate);
    }

    @Test
    @DisplayName("Lead Retrieval: Successful retrieval with campaign_name, ad_name, adset_name")
    void shouldRetrieveLeadSuccessfullyWithValidFields() {
        String mockResponseBody = "{"
                + "\"id\":\"1681819043581192\","
                + "\"created_time\":\"2026-10-27T10:30:00+0000\","
                + "\"form_id\":\"7770001\","
                + "\"ad_id\":\"8880002\","
                + "\"ad_name\":\"Test Ad Creative 1\","
                + "\"adset_id\":\"9990003\","
                + "\"adset_name\":\"Indore Target Adset\","
                + "\"campaign_id\":\"1110004\","
                + "\"campaign_name\":\"MBA Admission 2027\","
                + "\"platform\":\"fb\","
                + "\"field_data\":["
                + "  {\"name\":\"full_name\",\"values\":[\"Rohan Mehta\"]},"
                + "  {\"name\":\"phone_number\",\"values\":[\"+91 9988776655\"]},"
                + "  {\"name\":\"email\",\"values\":[\"rohan@example.com\"]}"
                + "]"
                + "}";

        // Verify request URL includes supported fields
        mockServer.expect(requestTo(containsString("campaign_name")))
                .andExpect(requestTo(not(containsString("adgroup_id"))))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer " + PAGE_ACCESS_TOKEN))
                .andRespond(withSuccess(mockResponseBody, MediaType.APPLICATION_JSON));

        MetaLeadResponseDTO result = graphApiService.retrieveLead(LEADGEN_ID);

        mockServer.verify();
        assertNotNull(result);
        assertEquals("1681819043581192", result.getId());
        assertEquals("7770001", result.getFormId());
        assertEquals("8880002", result.getAdId());
        assertEquals("Test Ad Creative 1", result.getAdName());
        assertEquals("9990003", result.getAdsetId());
        assertEquals("Indore Target Adset", result.getAdsetName());
        assertEquals("9990003", result.getAdgroupId()); // Alias compatibility check
        assertEquals("1110004", result.getCampaignId());
        assertEquals("MBA Admission 2027", result.getCampaignName());
        assertEquals("fb", result.getPlatform());
        assertNotNull(result.getFieldData());
        assertEquals(3, result.getFieldData().size());
        assertEquals("Rohan Mehta", result.getFieldData().get(0).getFirstValue());
    }

    @Test
    @DisplayName("Direct Campaign Lookup: Retrieves campaign name and caches for subsequent requests")
    void shouldRetrieveCampaignNameDirectlyAndCache() {
        String campaignId = "CAMP_12345";
        String campaignResponse = "{\"id\":\"CAMP_12345\",\"name\":\"BBA Lead Generation\"}";

        // Expect exactly one HTTP request
        mockServer.expect(requestTo(containsString("/v21.0/" + campaignId + "?fields=id,name")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer " + PAGE_ACCESS_TOKEN))
                .andRespond(withSuccess(campaignResponse, MediaType.APPLICATION_JSON));

        String name1 = graphApiService.retrieveCampaignName(campaignId, null);
        assertEquals("BBA Lead Generation", name1);
        mockServer.verify();

        // Second call should return cached value without making a second HTTP request
        String name2 = graphApiService.retrieveCampaignName(campaignId, null);
        assertEquals("BBA Lead Generation", name2);
    }

    @Test
    @DisplayName("Ad Fallback Lookup: Retrieves campaign name via ad ID when campaign ID is null")
    void shouldRetrieveCampaignNameViaAdFallback() {
        String adId = "AD_77777";
        String adResponse = "{\"id\":\"AD_77777\",\"name\":\"Ad 1\",\"campaign\":{\"id\":\"CAMP_999\",\"name\":\"MCA Admissions Campaign\"}}";

        mockServer.expect(requestTo(allOf(containsString("/v21.0/" + adId), containsString("fields=id,name,campaign"))))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer " + PAGE_ACCESS_TOKEN))
                .andRespond(withSuccess(adResponse, MediaType.APPLICATION_JSON));

        String name = graphApiService.retrieveCampaignName(null, adId);
        assertEquals("MCA Admissions Campaign", name);
        mockServer.verify();
    }

    @Test
    @DisplayName("Safe Failure: Returns null when campaign lookup encounters HTTP 404 or server error")
    void shouldReturnNullSafelyOnCampaignLookupFailure() {
        String campaignId = "CAMP_FAIL_500";

        mockServer.expect(requestTo(containsString(campaignId)))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        String name = graphApiService.retrieveCampaignName(campaignId, null);
        assertNull(name);
        mockServer.verify();
    }

    @Test
    @DisplayName("Error Handling: Code 100 invalid-field error throws non-retryable MetaGraphApiException")
    void shouldHandleCode100InvalidFieldError() {
        String errorJson = "{"
                + "\"error\":{"
                + "  \"message\":\"(#100) Tried accessing nonexisting field (unknown_field)\","
                + "  \"type\":\"OAuthException\","
                + "  \"code\":100"
                + "}"
                + "}";

        // If both enriched and baseline fail with 100
        mockServer.expect(requestTo(containsString(LEADGEN_ID)))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).body(errorJson).contentType(MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(containsString(LEADGEN_ID)))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).body(errorJson).contentType(MediaType.APPLICATION_JSON));

        MetaGraphApiException ex = assertThrows(MetaGraphApiException.class, () ->
                graphApiService.retrieveLead(LEADGEN_ID));

        mockServer.verify();
        assertEquals(400, ex.getHttpStatus());
        assertEquals(100, ex.getErrorCode());
        assertEquals("OAuthException", ex.getErrorType());
        assertFalse(ex.isRetryable(), "Code 100 must not be retryable");
        assertTrue(ex.getMetaMessage().contains("Tried accessing nonexisting field"));
    }

    @Test
    @DisplayName("Error Handling: Code 190 expired token throws non-retryable MetaGraphApiException")
    void shouldHandleCode190ExpiredTokenError() {
        String errorJson = "{"
                + "\"error\":{"
                + "  \"message\":\"Error validating access token: Session has expired on Monday, 26-Oct-26\","
                + "  \"type\":\"OAuthException\","
                + "  \"code\":190,"
                + "  \"error_subcode\":463"
                + "}"
                + "}";

        mockServer.expect(requestTo(containsString(LEADGEN_ID)))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).body(errorJson).contentType(MediaType.APPLICATION_JSON));

        MetaGraphApiException ex = assertThrows(MetaGraphApiException.class, () ->
                graphApiService.retrieveLead(LEADGEN_ID));

        mockServer.verify();
        assertEquals(400, ex.getHttpStatus());
        assertEquals(190, ex.getErrorCode());
        assertEquals(463, ex.getErrorSubcode());
        assertFalse(ex.isRetryable(), "Code 190 expired token must not be retryable without manual intervention");
    }

    @Test
    @DisplayName("Error Handling: Code 200 permission error throws non-retryable MetaGraphApiException")
    void shouldHandleCode200PermissionError() {
        String errorJson = "{"
                + "\"error\":{"
                + "  \"message\":\"(#200) Requires leads_retrieval permission to access lead details\","
                + "  \"type\":\"OAuthException\","
                + "  \"code\":200"
                + "}"
                + "}";

        mockServer.expect(requestTo(containsString(LEADGEN_ID)))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).body(errorJson).contentType(MediaType.APPLICATION_JSON));

        MetaGraphApiException ex = assertThrows(MetaGraphApiException.class, () ->
                graphApiService.retrieveLead(LEADGEN_ID));

        mockServer.verify();
        assertEquals(403, ex.getHttpStatus());
        assertEquals(200, ex.getErrorCode());
        assertFalse(ex.isRetryable(), "Code 200 permission error must not be retryable without granting permissions");
    }

    @Test
    @DisplayName("Error Handling: Code 4 rate-limit error is classified as retryable")
    void shouldHandleRateLimitErrorAsRetryable() {
        String errorJson = "{"
                + "\"error\":{"
                + "  \"message\":\"Application request limit reached\","
                + "  \"type\":\"OAuthException\","
                + "  \"code\":4"
                + "}"
                + "}";

        mockServer.expect(requestTo(containsString(LEADGEN_ID)))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).body(errorJson).contentType(MediaType.APPLICATION_JSON));

        MetaGraphApiException ex = assertThrows(MetaGraphApiException.class, () ->
                graphApiService.retrieveLead(LEADGEN_ID));

        mockServer.verify();
        assertEquals(400, ex.getHttpStatus());
        assertEquals(4, ex.getErrorCode());
        assertTrue(ex.isRetryable(), "Rate limit code 4 should be retryable");
    }

    @Test
    @DisplayName("Error Handling: HTTP 500 server error is classified as retryable")
    void shouldHandleHttp500AsRetryable() {
        mockServer.expect(requestTo(containsString(LEADGEN_ID)))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        MetaGraphApiException ex = assertThrows(MetaGraphApiException.class, () ->
                graphApiService.retrieveLead(LEADGEN_ID));

        mockServer.verify();
        assertEquals(500, ex.getHttpStatus());
        assertTrue(ex.isRetryable(), "Server error 500 should be retryable");
    }

    @Test
    @DisplayName("Configuration: Throws IllegalStateException when pageAccessToken is not configured")
    void shouldThrowIllegalStateExceptionWhenTokenMissing() {
        metaProperties.setPageAccessToken(null);

        assertThrows(IllegalStateException.class, () ->
                graphApiService.retrieveLead(LEADGEN_ID));
    }
}
