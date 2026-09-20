package com.app.datadistribution.entity;

import com.app.datadistribution.common.BaseEntity;
import com.app.datadistribution.enums.PermissionEntity;
import com.app.datadistribution.enums.PermissionGroup;
import com.app.datadistribution.enums.PermissionOperationType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "permissions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Permission extends BaseEntity {

    @Column(unique = true, nullable = false, length = 100)
    private String name;

    @Column(length = 100)
    private String code;

    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity", length = 50)
    private PermissionEntity entity;

    @Enumerated(EnumType.STRING)
    @Column(name = "permission_group", length = 50)
    private PermissionGroup permissionGroup;

    @Enumerated(EnumType.STRING)
    @Column(name = "permission_type", length = 50)
    private PermissionOperationType permissionType;

    // Field-level metadata (populated for LEAD_FIELD permissions)
    @Column(name = "field_key", length = 100)
    private String fieldKey;

    @Column(name = "field_label", length = 100)
    private String fieldLabel;

    @Column(name = "field_group", length = 100)
    private String fieldGroup;

    @Column(name = "display_order")
    @Builder.Default
    private Integer displayOrder = 0;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
