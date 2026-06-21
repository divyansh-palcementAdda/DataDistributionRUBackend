package com.app.datadistribution.mapper;

import com.app.datadistribution.dto.user.PermissionDTO;
import com.app.datadistribution.entity.Permission;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PermissionMapper {

    PermissionDTO toDto(Permission permission);

    Permission toEntity(PermissionDTO dto);

    default String mapPermissionToString(Permission permission) {
        if (permission == null) {
            return null;
        }
        return permission.getName();
    }
}
