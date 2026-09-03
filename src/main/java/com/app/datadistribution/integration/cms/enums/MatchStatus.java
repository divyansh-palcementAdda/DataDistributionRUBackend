package com.app.datadistribution.integration.cms.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum MatchStatus {
    MATCH_CONFIRMED,
    POSSIBLE_MATCH,
    FULL_MATCH,
    HIGH_CONFIDENCE_MATCH,
    PARTIAL_MATCH,
    MULTIPLE_MATCHES,
    NO_MATCH,
    ERROR;

    @JsonCreator
    public static MatchStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            return NO_MATCH;
        }
        String upper = value.trim().toUpperCase();
        for (MatchStatus status : values()) {
            if (status.name().equals(upper)) {
                return status;
            }
        }
        if (upper.contains("CONFIRM") || upper.contains("FULL")) {
            return MATCH_CONFIRMED;
        }
        if (upper.contains("POSSIBLE") || upper.contains("PARTIAL") || upper.contains("MULTIPLE")) {
            return POSSIBLE_MATCH;
        }
        return NO_MATCH;
    }
}
