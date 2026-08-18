package com.app.datadistribution.mapper;

import com.app.datadistribution.dto.department.DepartmentSummaryDTO;
import com.app.datadistribution.entity.Department;
import com.app.datadistribution.entity.Permission;
import com.app.datadistribution.entity.Role;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.dto.user.UserRequest;
import com.app.datadistribution.dto.user.UserResponse;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring", builder = @org.mapstruct.Builder(disableBuilder = true))
public interface UserMapper {

    @Mapping(source = "roles", target = "roles", qualifiedByName = "mapRolesToNames")
    @Mapping(source = "roles", target = "permissions", qualifiedByName = "mapRolesToPermissions")
    @Mapping(source = "departments", target = "departments", qualifiedByName = "mapDepartmentsToSummaries")
    UserResponse toDto(User user);

    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "departments", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "tokenVersion", ignore = true)
    User toEntity(UserRequest dto);

    @Named("mapRolesToNames")
    default Set<String> mapRolesToNames(Set<Role> roles) {
        if (roles == null) return null;
        return roles.stream()
                .map(Role::getName)
                .collect(Collectors.toSet());
    }

    @Named("mapRolesToPermissions")
    default Set<String> mapRolesToPermissions(Set<Role> roles) {
        if (roles == null) return null;
        return roles.stream()
                .filter(Role::isActive)
                .flatMap(role -> role.getPermissions().stream())
                .map(Permission::getName)
                .collect(Collectors.toSet());
    }

    @Named("mapDepartmentsToSummaries")
    default List<DepartmentSummaryDTO> mapDepartmentsToSummaries(Set<Department> departments) {
        if (departments == null) return null;
        return departments.stream()
                .filter(d -> d.isActive() && !d.isDeleted())
                .map(d -> DepartmentSummaryDTO.builder()
                        .id(d.getId())
                        .name(d.getName())
                        .code(d.getCode())
                        .build())
                .collect(Collectors.toList());
    }
}
