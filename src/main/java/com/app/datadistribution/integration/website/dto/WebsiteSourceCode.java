package com.app.datadistribution.integration.website.dto;

import lombok.Getter;
import java.util.Arrays;
import java.util.Optional;

/**
 * Standard website form source codes mapped to canonical Lead Source names.
 */
@Getter
public enum WebsiteSourceCode {

    WEBSITE_CAMPUS_TOUR("WEBSITE_CAMPUS_TOUR", "Website - Campus Tour", "University Website Campus Tour Form"),
    WEBSITE_CONTACT_QUERY("WEBSITE_CONTACT_QUERY", "Website - Contact Query", "University Website Contact Query Form"),
    WEBSITE_PLACEMENT("WEBSITE_PLACEMENT", "Website - Placement", "University Website Placement Inquiry Form"),
    WEBSITE_COURSE_ENQUIRY("WEBSITE_COURSE_ENQUIRY", "Website - Course Enquiry", "University Website Course Enquiry Form"),
    WEBSITE_FEES_INQUIRY("WEBSITE_FEES_INQUIRY", "Website - Fees Inquiry", "University Website Fees Inquiry Form"),
    WEBSITE_UNIVERSITY_COMPARISON("WEBSITE_UNIVERSITY_COMPARISON", "Website - University Comparison", "University Website University Comparison Form"),
    WEBSITE_VIRTUAL_TOUR("WEBSITE_VIRTUAL_TOUR", "Website - Virtual Tour", "University Website Virtual Tour Form"),
    WEBSITE_GOOGLE_ADS("WEBSITE_GOOGLE_ADS", "Website - Google Ads", "Website Google Ads Campaign Form"),
    WEBSITE_NEWSPAPER("WEBSITE_NEWSPAPER", "Website - Newspaper", "Website Newspaper Campaign Form");

    private final String code;
    private final String canonicalName;
    private final String description;

    WebsiteSourceCode(String code, String canonicalName, String description) {
        this.code = code;
        this.canonicalName = canonicalName;
        this.description = description;
    }

    public static Optional<WebsiteSourceCode> fromCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String normalized = code.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(c -> c.getCode().equalsIgnoreCase(normalized))
                .findFirst();
    }
}
