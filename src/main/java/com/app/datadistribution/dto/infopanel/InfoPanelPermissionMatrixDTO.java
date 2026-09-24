package com.app.datadistribution.dto.infopanel;

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
public class InfoPanelPermissionMatrixDTO {
    private String entity;
    private List<FieldGroupDTO> groups;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldGroupDTO {
        private String key;
        private String label;
        private List<FieldPermissionDTO> fields;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldPermissionDTO {
        private String key;
        private String label;
        private boolean view;
        private boolean edit;
    }
}
