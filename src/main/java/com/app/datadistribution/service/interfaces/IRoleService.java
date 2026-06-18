package com.app.datadistribution.service.interfaces;

import java.util.List;
import java.util.UUID;

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
}