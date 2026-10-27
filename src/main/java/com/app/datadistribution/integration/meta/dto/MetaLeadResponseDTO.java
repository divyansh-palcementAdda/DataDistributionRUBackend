package com.app.datadistribution.integration.meta.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MetaLeadResponseDTO {

    private String id;

    @JsonProperty("created_time")
    private String createdTime;

    @JsonProperty("form_id")
    private String formId;

    @JsonProperty("ad_id")
    private String adId;

    @JsonProperty("adset_id")
    private String adsetId;

    @JsonProperty("adgroup_id")
    private String adgroupId;

    @JsonProperty("campaign_id")
    private String campaignId;

    @JsonProperty("page_id")
    private String pageId;

    @JsonProperty("platform")
    private String platform; // "fb", "ig", etc.

    @JsonProperty("field_data")
    private List<MetaLeadFieldDataDTO> fieldData;

    public String getAdsetId() {
        return adsetId != null && !adsetId.isBlank() ? adsetId : adgroupId;
    }

    public String getAdgroupId() {
        return adgroupId != null && !adgroupId.isBlank() ? adgroupId : adsetId;
    }
}
