package com.app.datadistribution.dto.lead;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class LeadResponseOpenApiTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("Verify OpenAPI ModelResolver introspects LeadResponse without conflicting setter exception")
    void testLeadResponseOpenApiIntrospection() {
        Map<String, Schema> schemas = ModelConverters.getInstance().read(LeadResponse.class);
        assertNotNull(schemas, "Schemas should not be null");
        assertTrue(schemas.containsKey("LeadResponse"), "LeadResponse schema should be present");

        Schema leadResponseSchema = schemas.get("LeadResponse");
        Map<String, Schema> properties = leadResponseSchema.getProperties();
        assertNotNull(properties, "Properties should not be null");
        assertTrue(properties.containsKey("isAvailed"), "Property 'isAvailed' should be present in schema");
        assertFalse(properties.containsKey("availed"), "Property 'availed' should not be present in schema");
    }

    @Test
    @DisplayName("Verify OpenAPI ModelResolver introspects LeadAvailedResponse without conflicting setter exception")
    void testLeadAvailedResponseOpenApiIntrospection() {
        Map<String, Schema> schemas = ModelConverters.getInstance().read(LeadAvailedResponse.class);
        assertNotNull(schemas, "Schemas should not be null");
        assertTrue(schemas.containsKey("LeadAvailedResponse"), "LeadAvailedResponse schema should be present");

        Schema schema = schemas.get("LeadAvailedResponse");
        Map<String, Schema> properties = schema.getProperties();
        assertNotNull(properties, "Properties should not be null");
        assertTrue(properties.containsKey("isAvailed"), "Property 'isAvailed' should be present in schema");
        assertFalse(properties.containsKey("availed"), "Property 'availed' should not be present in schema");
    }

    @Test
    @DisplayName("Verify Jackson serializes isAvailed correctly in LeadResponse")
    void testLeadResponseSerialization() throws Exception {
        LeadResponse response = LeadResponse.builder()
                .id(UUID.randomUUID())
                .leadCode("LEAD-101")
                .fullName("Test Student")
                .isAvailed(true)
                .build();

        String json = objectMapper.writeValueAsString(response);
        assertTrue(json.contains("\"isAvailed\":true"), "JSON should contain \"isAvailed\":true");
        assertFalse(json.contains("\"availed\":"), "JSON should not contain \"availed\":");
    }

    @Test
    @DisplayName("Verify Jackson serializes isAvailed correctly in LeadAvailedResponse")
    void testLeadAvailedResponseSerialization() throws Exception {
        LeadAvailedResponse response = LeadAvailedResponse.builder()
                .id(UUID.randomUUID())
                .leadId(UUID.randomUUID())
                .leadCode("LEAD-101")
                .isAvailed(true)
                .build();

        String json = objectMapper.writeValueAsString(response);
        assertTrue(json.contains("\"isAvailed\":true"), "JSON should contain \"isAvailed\":true");
        assertFalse(json.contains("\"availed\":"), "JSON should not contain \"availed\":");
    }
}
