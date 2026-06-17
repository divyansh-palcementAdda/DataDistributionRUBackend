package com.app.datadistribution.service.interfaces;

import java.util.List;

import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.payload.RoleDTO;
import com.app.datadistribution.payload.RoleRequest;

public interface IRoleService {

    /**
     * Create a new role
     */
    RoleDTO createRole(RoleRequest request) throws BadRequestException;

    /**
     * Update existing role (mainly name)
     */
    RoleDTO updateRole(Long id, RoleRequest request) 
            throws ResourcesNotFoundException, BadRequestException;

    /**
     * Get role by ID
     */
    RoleDTO getById(Long id) throws ResourcesNotFoundException;

    /**
     * Get role by name (case-sensitive)
     */
    RoleDTO getByName(String name) throws ResourcesNotFoundException;

    /**
     * Get all roles
     */
    List<RoleDTO> getAll();

    /**
     * Delete role (only if no users are assigned)
     */
    void delete(Long id) throws ResourcesNotFoundException, BadRequestException;

    /**
     * Check if role exists by name
     */
    boolean existsByName(String name);
}