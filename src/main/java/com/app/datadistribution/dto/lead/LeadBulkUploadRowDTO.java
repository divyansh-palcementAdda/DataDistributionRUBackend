package com.app.datadistribution.dto.lead;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeadBulkUploadRowDTO {

    private int rowNumber;

    private String fullNameRaw;
    private String phoneNumberRaw;
    private String alternatePhoneNumberRaw;
    private String emailRaw;
    private String courseRaw;
    private String programRaw;
    private String courseTypeRaw;
    private String leadSourceRaw;
    private String sourceDetailsRaw;
    private String boardRaw;
    private String gradeRaw;
    private String streamRaw;
    private String departmentRaw;
    private String cityRaw;
    private String stateRaw;
    private String countryRaw;
    private String remarksRaw;
}
