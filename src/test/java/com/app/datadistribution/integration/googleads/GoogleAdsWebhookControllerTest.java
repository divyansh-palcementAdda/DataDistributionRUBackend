package com.app.datadistribution.integration.googleads;

import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.integration.googleads.controller.GoogleAdsWebhookController;
import com.app.datadistribution.integration.googleads.dto.GoogleAdsWebhookRequest;
import com.app.datadistribution.integration.googleads.exception.GoogleAdsAuthException;
import com.app.datadistribution.integration.googleads.exception.GoogleAdsDisabledException;
import com.app.datadistribution.integration.googleads.service.interfaces.IGoogleAdsWebhookService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class GoogleAdsWebhookControllerTest {

    private MockMvc mockMvc;

    @Mock
    private IGoogleAdsWebhookService webhookService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private GoogleAdsWebhookController controller;

    private static final String ENDPOINT = "/api/integrations/google-ads/leads";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("Should return HTTP 200 with {} on successful lead ingestion")
    void shouldReturn200OnSuccess() throws Exception {
        doNothing().when(webhookService).processWebhook(any(GoogleAdsWebhookRequest.class), anyString());

        String payload = """
                {
                  "lead_id": "GOOGLE_LEAD_123",
                  "google_key": "valid_secret_key",
                  "is_test": false,
                  "user_column_data": [
                    { "column_id": "FULL_NAME", "string_value": "Rohit Verma" },
                    { "column_id": "PHONE_NUMBER", "string_value": "+919876543210" }
                  ]
                }
                """;

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(content().json("{}"));
    }

    @Test
    @DisplayName("Should return HTTP 200 with {} for test lead")
    void shouldReturn200ForTestLead() throws Exception {
        doNothing().when(webhookService).processWebhook(any(GoogleAdsWebhookRequest.class), anyString());

        String payload = """
                {
                  "lead_id": "TEST_LEAD_1",
                  "google_key": "valid_secret_key",
                  "is_test": true
                }
                """;

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(content().json("{}"));
    }

    @Test
    @DisplayName("Should return HTTP 401 when google_key is invalid or unauthorized")
    void shouldReturn401OnAuthFailure() throws Exception {
        doThrow(new GoogleAdsAuthException("Invalid google_key"))
                .when(webhookService).processWebhook(any(GoogleAdsWebhookRequest.class), anyString());

        String payload = """
                {
                  "lead_id": "GOOGLE_LEAD_123",
                  "google_key": "wrong_key"
                }
                """;

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("{}"));
    }

    @Test
    @DisplayName("Should return HTTP 503 when Google Ads integration is disabled")
    void shouldReturn503WhenDisabled() throws Exception {
        doThrow(new GoogleAdsDisabledException("Google Ads lead integration is disabled"))
                .when(webhookService).processWebhook(any(GoogleAdsWebhookRequest.class), anyString());

        String payload = """
                {
                  "lead_id": "GOOGLE_LEAD_123",
                  "google_key": "any_key"
                }
                """;

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().json("{}"));
    }

    @Test
    @DisplayName("Should return HTTP 400 when body is malformed or invalid")
    void shouldReturn400OnMalformedJson() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid_json_format"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{}"));
    }

    @Test
    @DisplayName("Should return HTTP 400 when missing lead_id or invalid payload data")
    void shouldReturn400OnBadRequestException() throws Exception {
        doThrow(new BadRequestException("Missing lead_id"))
                .when(webhookService).processWebhook(any(GoogleAdsWebhookRequest.class), anyString());

        String payload = """
                {
                  "google_key": "valid_secret_key"
                }
                """;

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{}"));
    }

    @Test
    @DisplayName("Should return HTTP 500 on unexpected runtime error")
    void shouldReturn500OnInternalError() throws Exception {
        doThrow(new RuntimeException("Database connection timeout"))
                .when(webhookService).processWebhook(any(GoogleAdsWebhookRequest.class), anyString());

        String payload = """
                {
                  "lead_id": "GOOGLE_LEAD_123",
                  "google_key": "valid_secret_key"
                }
                """;

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isInternalServerError())
                .andExpect(content().json("{}"));
    }
}

