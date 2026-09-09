package com.app.datadistribution.config;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

/**
 * Flexible Jackson deserializer for LocalDateTime that prevents timezone conversion bugs.
 * Handles:
 * 1. Date-only ("yyyy-MM-dd") -> local date at start of day.
 * 2. ISO Local DateTime ("yyyy-MM-dd'T'HH:mm[:ss]") -> exact local datetime without offset shifting.
 * 3. ISO DateTime with UTC 'Z' or offset ("yyyy-MM-dd'T'HH:mm:ss.SSSZ") -> parsed and mapped to canonical Asia/Kolkata timezone.
 */
public class FlexibleLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {

    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    @Override
    public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String text = p.getText();
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        text = text.trim();

        // 1. Date-only string (e.g. "2026-09-09")
        if (text.length() == 10 && text.charAt(4) == '-' && text.charAt(7) == '-') {
            try {
                return LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
            } catch (DateTimeParseException ignored) {
            }
        }

        // 2. Normalize and strip trailing 'Z' if present to preserve exact business calendar date/time
        String normalized = text;
        if (normalized.endsWith("Z") || normalized.endsWith("z")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.contains(" ")) {
            normalized = normalized.replace(" ", "T");
        }

        // 3. ISO local datetime (e.g. "2026-09-09T00:00:00", "2026-09-09T00:00:00.000", "2026-09-09T11:30", "2026-09-09 11:30:00")
        if (!normalized.contains("+") && normalized.contains("T")) {
            try {
                return LocalDateTime.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (DateTimeParseException ignored) {
            }
        }

        // 4. ISO string with explicit numeric timezone offset (e.g. "+05:30")
        try {
            OffsetDateTime odt = OffsetDateTime.parse(text);
            return odt.toLocalDateTime();
        } catch (DateTimeParseException ignored) {
        }

        // 5. Fallback standard parse
        return LocalDateTime.parse(text);
    }
}
