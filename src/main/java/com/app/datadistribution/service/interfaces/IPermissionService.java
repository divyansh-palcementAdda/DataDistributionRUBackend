package com.app.datadistribution.service.interfaces;

import java.util.List;
import java.util.UUID;

import com.app.datadistribution.dto.user.PermissionDTO;
import com.app.datadistribution.dto.user.PermissionRequest;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.ResourcesNotFoundException;

public interface IPermissionService {

    PermissionDTO createPermission(PermissionRequest request) throws BadRequestException;

    PermissionDTO updatePermission(UUID id, PermissionRequest request) 
            throws ResourcesNotFoundException, BadRequestException;

    PermissionDTO getById(UUID id) throws ResourcesNotFoundException;

    List<PermissionDTO> getAll();

    void delete(UUID id) throws ResourcesNotFoundException, BadRequestException;
}
