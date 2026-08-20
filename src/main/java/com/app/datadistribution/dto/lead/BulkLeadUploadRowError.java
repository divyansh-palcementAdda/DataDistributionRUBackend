package com.app.datadistribution.dto.lead;

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
public class BulkLeadUploadRowError {

    private int rowNumber;
    private String field;
    private String value;
    private String reason;
}
