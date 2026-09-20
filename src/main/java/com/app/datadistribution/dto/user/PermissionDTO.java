package com.app.datadistribution.dto.user;

import java.util.UUID;

import com.app.datadistribution.enums.PermissionEntity;
import com.app.datadistribution.enums.PermissionGroup;
import com.app.datadistribution.enums.PermissionOperationType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionDTO {

    private UUID id;
    private String name;
    private String code;
    private String description;
    private PermissionEntity entity;
    private PermissionGroup permissionGroup;
    private PermissionOperationType permissionType;
    private String fieldKey;
    private String fieldLabel;
    private String fieldGroup;
    private Integer displayOrder;
    private boolean active;
}
