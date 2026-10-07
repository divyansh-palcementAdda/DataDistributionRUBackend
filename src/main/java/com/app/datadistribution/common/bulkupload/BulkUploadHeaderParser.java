package com.app.datadistribution.common.bulkupload;

import com.app.datadistribution.exception.BadRequestException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;

public final class BulkUploadHeaderParser {

    private static final DataFormatter FORMATTER = new DataFormatter();

    public static final Set<String> DEFAULT_METADATA_ALIASES = new HashSet<>(Arrays.asList(
            "s no", "sno", "s no ", "serial number", "sl no", "sr no", "#", "s no.", "s.no."
    ));

    private BulkUploadHeaderParser() {
    }

    public static BulkUploadHeaderMappingResult parseHeaders(
            Row headerRow,
            List<BulkUploadFieldDefinition> definitions,
            String sheetName
    ) throws BadRequestException {
        return parseHeaders(headerRow, definitions, sheetName, DEFAULT_METADATA_ALIASES, null);
    }

    public static BulkUploadHeaderMappingResult parseHeaders(
            Row headerRow,
            List<BulkUploadFieldDefinition> definitions,
            String sheetName,
            Set<String> metadataAliases,
            HeaderValidationCustomizer customValidator
    ) throws BadRequestException {
        if (headerRow == null) {
            throw new BadRequestException("Header row in sheet '" + sheetName + "' is missing.");
        }

        Set<String> effectiveMetadata = metadataAliases != null ? metadataAliases : DEFAULT_METADATA_ALIASES;

        Map<String, Integer> fieldToCol = new LinkedHashMap<>();
        Map<Integer, String> colToRaw = new LinkedHashMap<>();
        Map<Integer, BulkUploadFieldDefinition> colToDef = new LinkedHashMap<>();

        int lastCellNum = headerRow.getLastCellNum();
        if (lastCellNum < 1) {
            throw new BadRequestException("Header row in sheet '" + sheetName + "' contains no columns.");
        }

        List<String> expectedColumnNames = definitions.stream()
                .map(BulkUploadFieldDefinition::getHeaderName)
                .toList();

        for (int c = 0; c < lastCellNum; c++) {
            Cell cell = headerRow.getCell(c);
            String rawText = cell != null ? FORMATTER.formatCellValue(cell) : null;

            if (rawText == null || rawText.isBlank()) {
                // Check if all remaining cells are also blank (trailing empty columns)
                boolean allRemainingBlank = true;
                for (int next = c + 1; next < lastCellNum; next++) {
                    Cell nextCell = headerRow.getCell(next);
                    String nextText = nextCell != null ? FORMATTER.formatCellValue(nextCell) : null;
                    if (nextText != null && !nextText.isBlank()) {
                        allRemainingBlank = false;
                        break;
                    }
                }
                if (allRemainingBlank) {
                    break;
                }
                continue;
            }

            String trimmed = rawText.trim();
            String norm = BulkUploadHeaderNormalizer.normalize(trimmed);
            String alpha = BulkUploadHeaderNormalizer.normalizeAlphanumericOnly(trimmed);
            String colLetter = getColumnLetter(c);

            // 1. Check if it's metadata (e.g. S.No.)
            boolean isMetadata = false;
            if (effectiveMetadata != null) {
                for (String meta : effectiveMetadata) {
                    if (norm.equals(BulkUploadHeaderNormalizer.normalize(meta))
                            || alpha.equals(BulkUploadHeaderNormalizer.normalizeAlphanumericOnly(meta))) {
                        isMetadata = true;
                        break;
                    }
                }
            }
            if (isMetadata) {
                colToRaw.put(c, trimmed);
                continue;
            }

            // 2. Match with canonical definitions
            BulkUploadFieldDefinition matched = null;
            for (BulkUploadFieldDefinition def : definitions) {
                if (def.matches(norm, alpha)) {
                    matched = def;
                    break;
                }
            }

            // 3. Unknown Header Check
            if (matched == null) {
                throw new BadRequestException(
                        "Unknown column '" + trimmed + "' at column " + colLetter + ". "
                                + "Expected columns: " + expectedColumnNames
                );
            }

            // 4. Duplicate Header Check
            if (fieldToCol.containsKey(matched.getFieldKey())) {
                int previousCol = fieldToCol.get(matched.getFieldKey());
                String prevColLetter = getColumnLetter(previousCol);
                String prevHeader = colToRaw.get(previousCol);
                throw new BadRequestException(
                        "Duplicate header '" + trimmed + "' detected at column " + colLetter
                                + " (already mapped at column " + prevColLetter + " as '" + prevHeader + "')."
                );
            }

            fieldToCol.put(matched.getFieldKey(), c);
            colToRaw.put(c, trimmed);
            colToDef.put(c, matched);
        }

        // 5. Custom or Default Missing Required Header validation
        if (customValidator != null) {
            customValidator.validateRequiredHeaders(fieldToCol, definitions, sheetName);
        } else {
            for (BulkUploadFieldDefinition def : definitions) {
                if (def.isRequired() && !fieldToCol.containsKey(def.getFieldKey())) {
                    throw new BadRequestException(
                            "Sheet '" + sheetName + "' is missing required column: '" + def.getHeaderName() + "'. "
                                    + "Expected columns: " + expectedColumnNames
                    );
                }
            }
        }

        return BulkUploadHeaderMappingResult.builder()
                .fieldToColIndex(fieldToCol)
                .colIndexToRawHeader(colToRaw)
                .colIndexToDefinition(colToDef)
                .build();
    }

    public static String getColumnLetter(int columnIndex) {
        StringBuilder sb = new StringBuilder();
        int num = columnIndex + 1;
        while (num > 0) {
            int rem = (num - 1) % 26;
            sb.insert(0, (char) ('A' + rem));
            num = (num - 1) / 26;
        }
        return sb.toString();
    }

    @FunctionalInterface
    public interface HeaderValidationCustomizer {
        void validateRequiredHeaders(
                Map<String, Integer> fieldToCol,
                List<BulkUploadFieldDefinition> definitions,
                String sheetName
        ) throws BadRequestException;
    }
}
