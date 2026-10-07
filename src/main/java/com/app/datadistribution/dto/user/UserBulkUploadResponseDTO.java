package com.app.datadistribution.dto.user;

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
public class UserBulkUploadResponseDTO {
    private boolean success;
    private String message;
    private int totalRows;
    private int successfulRows;
    private int failedRows;
    private int createdRecords;
    private int updatedRecords;
    private boolean errorFileAvailable;
    private String importId;

    @Builder.Default
    private List<UserBulkUploadRowErrorDTO> errors = new ArrayList<>();
}
