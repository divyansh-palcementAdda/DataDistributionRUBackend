package com.app.datadistribution.dto.user;

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
public class UserBulkUploadRowErrorDTO {
    private int rowNumber;
    private String name;
    private String email;
    private String username;
    private String role;
    private String departmentName;
    private String field;
    private String errorCode;
    private String errorMessage;
}
