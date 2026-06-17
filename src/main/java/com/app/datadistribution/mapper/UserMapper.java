package com.app.datadistribution.mapper;

import java.util.Set;
import java.util.stream.Collectors;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import com.app.datadistribution.Model.Role;
import com.app.datadistribution.Model.User;
import com.app.datadistribution.config.GlobalMapperConfiguration;
import com.app.datadistribution.payload.UserDTO;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(source = "roles", target = "roles", qualifiedByName = "mapRolesToNames")
    UserDTO toDto(User user);

    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "verificationToken", ignore = true)
    @Mapping(target = "emailVerified", ignore = true)
    User toEntity(UserDTO dto);

    @Named("mapRolesToNames")
    default Set<String> mapRolesToNames(Set<Role> roles) {
        if (roles == null) return null;
        return roles.stream()
                .map(Role::getName)
                .collect(Collectors.toSet());
    }
}
