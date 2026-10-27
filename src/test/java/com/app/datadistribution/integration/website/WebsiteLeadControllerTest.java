package com.app.datadistribution.integration.website;

import com.app.datadistribution.exception.GlobalExceptionHandler;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.integration.website.controller.WebsiteLeadController;
import com.app.datadistribution.integration.website.dto.WebsiteLeadRequestDTO;
import com.app.datadistribution.integration.website.dto.WebsiteLeadResponseDTO;
import com.app.datadistribution.integration.website.security.WebsiteApiKeyValidator;
import com.app.datadistribution.integration.website.security.WebsiteRateLimiter;
import com.app.datadistribution.integration.website.service.interfaces.IWebsiteLeadService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WebsiteLeadControllerTest {

    private MockMvc mockMvc;

    @Mock
    private IWebsiteLeadService websiteLeadService;

    @Mock
    private WebsiteApiKeyValidator apiKeyValidator;

    @Mock
    private WebsiteRateLimiter rateLimiter;

    @InjectMocks
    private WebsiteLeadController controller;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String API_KEY = "test_valid_api_key_123";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /api/integrations/website/leads: Successfully created returns 201 Created")
    void shouldReturn201WhenLeadCreatedSuccessfully() throws Exception {
        when(rateLimiter.tryAcquire(anyString())).thenReturn(true);
        doNothing().when(apiKeyValidator).validate(API_KEY);

        UUID leadId = UUID.randomUUID();
        WebsiteLeadResponseDTO serviceResponse = WebsiteLeadResponseDTO.builder()
                .externalLeadId("ext-form-1")
                .leadId(leadId)
                .leadCode("LEAD-ABC12345")
                .sourceCode("WEBSITE_FEES_INQUIRY")
                .status("CREATED")
                .message("Website lead received successfully")
                .build();

        when(websiteLeadService.ingestLead(any(WebsiteLeadRequestDTO.class), anyString()))
                .thenReturn(serviceResponse);

        WebsiteLeadRequestDTO request = WebsiteLeadRequestDTO.builder()
                .sourceCode("WEBSITE_FEES_INQUIRY")
                .externalLeadId("ext-form-1")
                .name("Karan Verma")
                .mobile("9876543210")
                .email("karan@example.com")
                .build();

        mockMvc.perform(post("/api/integrations/website/leads")
                        .header(WebsiteApiKeyValidator.API_KEY_HEADER, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.leadCode").value("LEAD-ABC12345"))
                .andExpect(jsonPath("$.data.status").value("CREATED"))
                .andExpect(jsonPath("$.data.externalLeadId").value("ext-form-1"));
    }

    @Test
    @DisplayName("POST /api/integrations/website/leads: Idempotent duplicate submission returns 200 OK")
    void shouldReturn200WhenDuplicateLeadRetried() throws Exception {
        when(rateLimiter.tryAcquire(anyString())).thenReturn(true);
        doNothing().when(apiKeyValidator).validate(API_KEY);

        UUID leadId = UUID.randomUUID();
        WebsiteLeadResponseDTO serviceResponse = WebsiteLeadResponseDTO.builder()
                .externalLeadId("ext-form-1")
                .leadId(leadId)
                .leadCode("LEAD-ABC12345")
                .sourceCode("WEBSITE_FEES_INQUIRY")
                .status("EXISTING_SUBMISSION")
                .message("Website lead already processed (idempotent submission)")
                .build();

        when(websiteLeadService.ingestLead(any(WebsiteLeadRequestDTO.class), anyString()))
                .thenReturn(serviceResponse);

        WebsiteLeadRequestDTO request = WebsiteLeadRequestDTO.builder()
                .sourceCode("WEBSITE_FEES_INQUIRY")
                .externalLeadId("ext-form-1")
                .name("Karan Verma")
                .mobile("9876543210")
                .build();

        mockMvc.perform(post("/api/integrations/website/leads")
                        .header(WebsiteApiKeyValidator.API_KEY_HEADER, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("EXISTING_SUBMISSION"))
                .andExpect(jsonPath("$.data.leadCode").value("LEAD-ABC12345"));
    }

    @Test
    @DisplayName("POST /api/integrations/website/leads: Missing or Invalid API key returns 401 Unauthorized")
    void shouldReturn401WhenApiKeyIsInvalid() throws Exception {
        when(rateLimiter.tryAcquire(anyString())).thenReturn(true);
        doThrow(new UnauthorizedException("Invalid Website Integration API Key"))
                .when(apiKeyValidator).validate("wrong_key");

        WebsiteLeadRequestDTO request = WebsiteLeadRequestDTO.builder()
                .sourceCode("WEBSITE_FEES_INQUIRY")
                .name("Karan Verma")
                .mobile("9876543210")
                .build();

        mockMvc.perform(post("/api/integrations/website/leads")
                        .header(WebsiteApiKeyValidator.API_KEY_HEADER, "wrong_key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid Website Integration API Key"));
    }

    @Test
    @DisplayName("POST /api/integrations/website/leads: Validation failure on missing required fields returns 400 Bad Request")
    void shouldReturn400WhenValidationFails() throws Exception {
        // Missing mobile and name
        WebsiteLeadRequestDTO invalidRequest = WebsiteLeadRequestDTO.builder()
                .sourceCode("WEBSITE_FEES_INQUIRY")
                .build();

        mockMvc.perform(post("/api/integrations/website/leads")
                        .header(WebsiteApiKeyValidator.API_KEY_HEADER, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @Test
    @DisplayName("POST /api/integrations/website/leads: Rate limit exceeded returns 429 Too Many Requests")
    void shouldReturn429WhenRateLimitExceeded() throws Exception {
        when(rateLimiter.tryAcquire(anyString())).thenReturn(false);

        WebsiteLeadRequestDTO request = WebsiteLeadRequestDTO.builder()
                .sourceCode("WEBSITE_FEES_INQUIRY")
                .name("Karan Verma")
                .mobile("9876543210")
                .build();

        mockMvc.perform(post("/api/integrations/website/leads")
                        .header(WebsiteApiKeyValidator.API_KEY_HEADER, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Rate limit exceeded. Please try again later."));
    }
}
