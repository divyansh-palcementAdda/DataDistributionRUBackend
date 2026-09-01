package com.app.datadistribution.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class OpenApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Verify /v3/api-docs endpoint responds with 200 OK and valid JSON containing LeadResponse")
    void testOpenApiDocsEndpoint() throws Exception {
        mockMvc.perform(get("/v3/api-docs")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.components.schemas.LeadResponse").exists())
                .andExpect(jsonPath("$.components.schemas.LeadResponse.properties.isAvailed").exists())
                .andExpect(jsonPath("$.components.schemas.LeadAvailedResponse").exists())
                .andExpect(jsonPath("$.components.schemas.LeadAvailedResponse.properties.isAvailed").exists());
    }
}
