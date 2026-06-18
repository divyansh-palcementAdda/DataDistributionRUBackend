package com.app.datadistribution.mapper;

import com.app.datadistribution.entity.Permission;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PermissionMapper {

    default String mapPermissionToString(Permission permission) {
        if (permission == null) {
            return null;
        }
        return permission.getName();
    }
}
