package com.app.datadistribution.integration.googleads.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GoogleAdsWebhookRequest {

    @JsonProperty("lead_id")
    private String leadId;

    @JsonProperty("user_column_data")
    @Builder.Default
    private List<UserColumnData> userColumnData = new ArrayList<>();

    @JsonProperty("api_version")
    private String apiVersion;

    @JsonProperty("form_id")
    private String formId;

    @JsonProperty("campaign_id")
    private String campaignId;

    @JsonProperty("google_key")
    private String googleKey;

    @JsonProperty("is_test")
    private Boolean isTest;

    @JsonProperty("gcl_id")
    private String gclId;

    @JsonProperty("adgroup_id")
    private String adgroupId;

    @JsonProperty("creative_id")
    private String creativeId;

    @JsonProperty("lead_submit_time")
    private String leadSubmitTime;

    @JsonProperty("lead_source")
    private String leadSource;

    public boolean isTestSubmission() {
        return Boolean.TRUE.equals(isTest);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UserColumnData {

        @JsonProperty("column_id")
        private String columnId;

        @JsonProperty("string_value")
        private String stringValue;

        @JsonProperty("column_name")
        private String columnName;
    }
}

