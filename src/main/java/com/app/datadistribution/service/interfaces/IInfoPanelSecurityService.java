package com.app.datadistribution.service.interfaces;

import java.util.Map;

import com.app.datadistribution.dto.infopanel.CourseInfoPanelRequestDTO;
import com.app.datadistribution.dto.infopanel.CourseInfoPanelResponseDTO;
import com.app.datadistribution.dto.infopanel.InfoPanelPermissionMatrixDTO;
import com.app.datadistribution.entity.CourseInfoPanel;
import com.app.datadistribution.enums.PermissionType;
import com.app.datadistribution.exception.UnauthorizedException;

public interface IInfoPanelSecurityService {

    boolean hasReadPermission(PermissionType type);

    boolean hasWritePermission(PermissionType type);

    void validateFieldUpdates(CourseInfoPanel existing, CourseInfoPanelRequestDTO request) throws UnauthorizedException;

    void sanitizeResponse(CourseInfoPanelResponseDTO response);

    InfoPanelPermissionMatrixDTO getPermissionMatrix();

    Map<String, Boolean> getFieldPermissionsMap();
}
