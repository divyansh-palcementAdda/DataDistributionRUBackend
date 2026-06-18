package com.app.datadistribution.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.app.datadistribution.dto.user.RoleDTO;
import com.app.datadistribution.entity.Role;

@Mapper(componentModel = "spring")
public interface RoleMapper {

    @Mapping(target = "userCount", expression = "java(countUsers(role))")
    RoleDTO toDto(Role role);

    default Long countUsers(Role role) {
        return role.getUsers() == null ? 0L : (long) role.getUsers().size();
    }
}
