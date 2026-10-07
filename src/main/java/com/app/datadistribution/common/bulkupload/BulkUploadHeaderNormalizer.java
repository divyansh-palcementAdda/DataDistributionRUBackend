package com.app.datadistribution.common.bulkupload;

public final class BulkUploadHeaderNormalizer {

    private BulkUploadHeaderNormalizer() {
    }

    /**
     * Normalizes a header string:
     * - Trims leading & trailing whitespace
     * - Converts to lower-case
     * - Replaces all non-alphanumeric characters (including asterisks, underscores, hyphens, periods) with single spaces
     * - Collapses consecutive spaces into one single space
     *
     * Example: "  Department   Name * " -> "department name"
     * Example: "S.No." -> "s no"
     */
    public static String normalize(String header) {
        if (header == null) {
            return "";
        }
        String cleaned = header.trim().toLowerCase().replaceAll("[^a-z0-9]+", " ");
        return cleaned.replaceAll("\\s+", " ").trim();
    }

    /**
     * Normalizes a header string by stripping all non-alphanumeric characters completely.
     * Useful for matching collapsed variants.
     *
     * Example: "Department Name" -> "departmentname"
     */
    public static String normalizeAlphanumericOnly(String header) {
        if (header == null) {
            return "";
        }
        return header.trim().toLowerCase().replaceAll("[^a-z0-9]", "");
    }
}
