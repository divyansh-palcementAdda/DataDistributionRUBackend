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
public class MetaWebhookPayloadDTO {

    private String object;
    private List<Entry> entry;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Entry {
        private String id;
        private Long time;
        private List<Change> changes;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Change {
        private String field;
        private Value value;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Value {
        @JsonProperty("created_time")
        private Long createdTime;

        @JsonProperty("leadgen_id")
        private String leadgenId;

        @JsonProperty("page_id")
        private String pageId;

        @JsonProperty("form_id")
        private String formId;

        @JsonProperty("ad_id")
        private String adId;

        @JsonProperty("adgroup_id")
        private String adgroupId;

        @JsonProperty("adset_id")
        private String adsetId;

        public String getEffectiveAdgroupId() {
            return (adsetId != null && !adsetId.isBlank()) ? adsetId : adgroupId;
        }
    }
}
