package com.app.datadistribution.service.interfaces;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.app.datadistribution.dto.user.PermissionDTO;
import com.app.datadistribution.dto.user.RoleDTO;
import com.app.datadistribution.dto.user.RoleRequest;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.ResourcesNotFoundException;

public interface IRoleService {

    RoleDTO createRole(RoleRequest request) throws BadRequestException;

    RoleDTO updateRole(UUID id, RoleRequest request) 
            throws ResourcesNotFoundException, BadRequestException;

    RoleDTO getById(UUID id) throws ResourcesNotFoundException;

    RoleDTO getByName(String name) throws ResourcesNotFoundException;

    List<RoleDTO> getAll();

    void delete(UUID id) throws ResourcesNotFoundException, BadRequestException;

    boolean existsByName(String name);

    RoleDTO activateRole(UUID id) throws ResourcesNotFoundException;

    RoleDTO deactivateRole(UUID id) throws ResourcesNotFoundException;

    List<PermissionDTO> getRolePermissions(UUID roleId) throws ResourcesNotFoundException;

    void assignPermissions(UUID roleId, List<UUID> permissionIds) throws ResourcesNotFoundException, BadRequestException;

    void removePermissionFromRole(UUID roleId, UUID permissionId) throws ResourcesNotFoundException, BadRequestException;

    Collection<PermissionDTO> getCachedPermissionsForRole(UUID roleId);

    Collection<PermissionDTO> getCachedPermissionsForUser(String username);
}