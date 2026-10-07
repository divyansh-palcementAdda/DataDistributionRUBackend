package com.app.datadistribution.common.bulkupload;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BulkUploadFieldDefinition {

    private final String headerName;
    private final String fieldKey;
    private final boolean required;
    private final String dataType;
    private final String sampleValue;
    private final String description;

    @Builder.Default
    private final Set<String> aliases = new HashSet<>();

    public boolean matches(String normalizedHeader, String alphanumericHeader) {
        if (normalizedHeader == null || normalizedHeader.isBlank()) {
            return false;
        }

        String normDef = BulkUploadHeaderNormalizer.normalize(headerName);
        String alphaDef = BulkUploadHeaderNormalizer.normalizeAlphanumericOnly(headerName);

        if (normalizedHeader.equals(normDef) || alphanumericHeader.equals(alphaDef)) {
            return true;
        }

        String normKey = BulkUploadHeaderNormalizer.normalize(fieldKey);
        String alphaKey = BulkUploadHeaderNormalizer.normalizeAlphanumericOnly(fieldKey);
        if (normalizedHeader.equals(normKey) || alphanumericHeader.equals(alphaKey)) {
            return true;
        }

        for (String alias : aliases) {
            String normAlias = BulkUploadHeaderNormalizer.normalize(alias);
            String alphaAlias = BulkUploadHeaderNormalizer.normalizeAlphanumericOnly(alias);
            if (normalizedHeader.equals(normAlias) || alphanumericHeader.equals(alphaAlias)) {
                return true;
            }
        }

        return false;
    }
}
