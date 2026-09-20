package com.app.datadistribution;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.app.datadistribution.config.PermissionMetadataRegistry;
import com.app.datadistribution.enums.PermissionGroup;
import com.app.datadistribution.enums.PermissionType;

public class PermissionMetadataRegistryTest {

    @Test
    @DisplayName("Every PermissionType enum constant must have valid metadata mapped")
    void testAllPermissionTypesAreMapped() {
        List<String> unmapped = new ArrayList<>();

        for (PermissionType type : PermissionType.values()) {
            PermissionMetadataRegistry.PermissionMetadata meta = PermissionMetadataRegistry.getMetadata(type);
            if (meta == null) {
                unmapped.add(type.name() + ": No metadata found in PermissionMetadataRegistry");
                continue;
            }

            if (meta.getEntity() == null) {
                unmapped.add(type.name() + ": entity is null");
            }
            if (meta.getPermissionGroup() == null) {
                unmapped.add(type.name() + ": permissionGroup is null");
            }
            if (meta.getPermissionType() == null) {
                unmapped.add(type.name() + ": permissionType (operation type) is null");
            }
            if (meta.getCode() == null || meta.getCode().isBlank()) {
                unmapped.add(type.name() + ": code is null or empty");
            }

            if (meta.getPermissionGroup() == PermissionGroup.LEAD_FIELD) {
                if (meta.getFieldKey() == null || meta.getFieldKey().isBlank()) {
                    unmapped.add(type.name() + ": fieldKey is missing for LEAD_FIELD");
                }
                if (meta.getFieldLabel() == null || meta.getFieldLabel().isBlank()) {
                    unmapped.add(type.name() + ": fieldLabel is missing for LEAD_FIELD");
                }
                if (meta.getFieldGroup() == null || meta.getFieldGroup().isBlank()) {
                    unmapped.add(type.name() + ": fieldGroup is missing for LEAD_FIELD");
                }
                if (meta.getDisplayOrder() == null) {
                    unmapped.add(type.name() + ": displayOrder is missing for LEAD_FIELD");
                }
            }
        }

        assertNotNull(PermissionMetadataRegistry.getAll());
        assertFalse(PermissionMetadataRegistry.getAll().isEmpty());
        if (!unmapped.isEmpty()) {
            throw new AssertionError("Found " + unmapped.size() + " unmapped/invalid permissions: \n" + String.join("\n", unmapped));
        }
    }
}
