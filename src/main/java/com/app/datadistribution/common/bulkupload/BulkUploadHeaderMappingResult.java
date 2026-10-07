package com.app.datadistribution.common.bulkupload;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import org.slf4j.Logger;

@Getter
@Builder
public class BulkUploadHeaderMappingResult {

    private final Map<String, Integer> fieldToColIndex;
    private final Map<Integer, String> colIndexToRawHeader;
    private final Map<Integer, BulkUploadFieldDefinition> colIndexToDefinition;

    public Integer getColIndex(String fieldKey) {
        return fieldToColIndex.get(fieldKey);
    }

    public boolean hasField(String fieldKey) {
        return fieldToColIndex.containsKey(fieldKey);
    }

    public void logHeaderMapping(Logger log, String importType, String importId) {
        log.info("==================================================");
        log.info("BULK_UPLOAD_HEADER_MAPPING");
        log.info("Import Type: {}", importType);
        log.info("importId={}", importId);

        for (Map.Entry<Integer, String> entry : colIndexToRawHeader.entrySet()) {
            int colIdx = entry.getKey();
            String rawHeader = entry.getValue();
            String colLetter = BulkUploadHeaderParser.getColumnLetter(colIdx);
            BulkUploadFieldDefinition def = colIndexToDefinition.get(colIdx);
            String fieldKey = def != null ? def.getFieldKey() : "ignored/metadata";

            log.info("Excel Column {} (idx {}) → \"{}\" → field={}", colLetter, colIdx, rawHeader, fieldKey);
        }
        log.info("==================================================");
    }
}
