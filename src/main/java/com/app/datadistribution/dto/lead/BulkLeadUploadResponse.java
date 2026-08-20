package com.app.datadistribution.dto.lead;

import java.util.ArrayList;
import java.util.List;
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
public class BulkLeadUploadResponse {

    private int totalRows;
    private int successCount;
    private int failedCount;
    private int duplicateCount;
    private int skippedCount;

    @Builder.Default
    private List<BulkLeadUploadRowError> failedRows = new ArrayList<>();

    public List<BulkLeadUploadRowError> getErrors() {
        return failedRows;
    }

    public int getSuccessfulRows() {
        return successCount;
    }

    public int getDuplicateRows() {
        return duplicateCount;
    }
}
