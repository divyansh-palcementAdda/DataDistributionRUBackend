package com.app.datadistribution.service.impl;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.datadistribution.dto.user.PermissionDTO;
import com.app.datadistribution.dto.user.RoleDTO;
import com.app.datadistribution.dto.user.RoleRequest;
import com.app.datadistribution.entity.Permission;
import com.app.datadistribution.entity.Role;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.ActivityType;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.mapper.PermissionMapper;
import com.app.datadistribution.mapper.RoleMapper;
import com.app.datadistribution.repository.PermissionRepository;
import com.app.datadistribution.repository.RoleRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.interfaces.IActivityLogService;
import com.app.datadistribution.service.interfaces.IRoleService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class RoleServiceImpl implements IRoleService {

    private final RoleRepository repository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final RoleMapper mapper;
    private final PermissionMapper permissionMapper;
    private final IActivityLogService activityLogService;

    @Override
    @Transactional
    public RoleDTO createRole(RoleRequest request) throws BadRequestException {
        if (repository.existsByNameAndIsDeletedFalse(request.getName())) {
            throw new BadRequestException("Role '" + request.getName() + "' already exists");
        }

        Role role = Role.builder()
                .name(request.getName())
                .description(request.getDescription())
                .active(true)
                .build();

        Role saved = repository.save(role);
        log.info("Created role: {}", saved.getName());

        activityLogService.logActivity(ActivityType.ROLE_CREATED, "Created role: " + saved.getName());

        return mapper.toDto(saved);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"rolePermissions", "userPermissions"}, allEntries = true)
    public RoleDTO updateRole(UUID id, RoleRequest request) throws BadRequestException {
        Role role = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourcesNotFoundException("Role not found with id: " + id));

        if (!role.getName().equalsIgnoreCase(request.getName()) && repository.existsByNameAndIsDeletedFalse(request.getName())) {
            throw new BadRequestException("Role name '" + request.getName() + "' already exists");
        }

        role.setName(request.getName());
        role.setDescription(request.getDescription());
        Role saved = repository.save(role);

        activityLogService.logActivity(ActivityType.ROLE_UPDATED, "Updated role: " + saved.getName());

        return mapper.toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public RoleDTO getById(UUID id) {
        Role role = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourcesNotFoundException("Role not found with id: " + id));
        return mapper.toDto(role);
    }

    @Override
    @Transactional(readOnly = true)
    public RoleDTO getByName(String name) {
        Role role = repository.findByNameAndIsDeletedFalse(name)
                .orElseThrow(() -> new ResourcesNotFoundException("Role not found with name: " + name));
        return mapper.toDto(role);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleDTO> getAll() {
        return repository.fetchAllSummary();
    }

    @Override
    @Transactional
    @CacheEvict(value = {"rolePermissions", "userPermissions"}, allEntries = true)
    public void delete(UUID id) throws BadRequestException {
        Role role = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourcesNotFoundException("Role not found with id: " + id));

        if (role.getUsers() != null && !role.getUsers().isEmpty()) {
            throw new BadRequestException("Cannot delete role '" + role.getName() + "' because it is assigned to users");
        }

        role.setDeleted(true);
        role.setActive(false);
        repository.save(role);
        log.info("Soft deleted role: {}", role.getName());

        activityLogService.logActivity(ActivityType.ROLE_DELETED, "Soft deleted role: " + role.getName());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByName(String name) {
        return repository.existsByNameAndIsDeletedFalse(name);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"rolePermissions", "userPermissions"}, allEntries = true)
    public RoleDTO activateRole(UUID id) throws ResourcesNotFoundException {
        Role role = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourcesNotFoundException("Role not found with id: " + id));
        role.setActive(true);
        Role saved = repository.save(role);

        activityLogService.logActivity(ActivityType.ROLE_ACTIVATED, "Activated role: " + saved.getName());

        return mapper.toDto(saved);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"rolePermissions", "userPermissions"}, allEntries = true)
    public RoleDTO deactivateRole(UUID id) throws ResourcesNotFoundException {
        Role role = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourcesNotFoundException("Role not found with id: " + id));
        role.setActive(false);
        Role saved = repository.save(role);

        activityLogService.logActivity(ActivityType.ROLE_DEACTIVATED, "Deactivated role: " + saved.getName());

        return mapper.toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PermissionDTO> getRolePermissions(UUID roleId) throws ResourcesNotFoundException {
        Role role = repository.findByIdAndIsDeletedFalse(roleId)
                .orElseThrow(() -> new ResourcesNotFoundException("Role not found with id: " + roleId));
        return role.getPermissions().stream()
                .filter(p -> !p.isDeleted())
                .map(permissionMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    @CacheEvict(value = {"rolePermissions", "userPermissions"}, allEntries = true)
    public void assignPermissions(UUID roleId, List<UUID> permissionIds) throws ResourcesNotFoundException, BadRequestException {
        Role role = repository.findByIdAndIsDeletedFalse(roleId)
                .orElseThrow(() -> new ResourcesNotFoundException("Role not found with id: " + roleId));

        Set<Permission> permissions = new HashSet<>();
        for (UUID permId : permissionIds) {
            Permission permission = permissionRepository.findByIdAndIsDeletedFalse(permId)
                    .orElseThrow(() -> new ResourcesNotFoundException("Permission not found with id: " + permId));
            permissions.add(permission);
        }

        role.setPermissions(permissions);
        repository.save(role);

        activityLogService.logActivity(ActivityType.PERMISSION_ASSIGNED, 
                String.format("Assigned %d permissions to role: %s", permissions.size(), role.getName()));
    }

    @Override
    @Transactional
    @CacheEvict(value = {"rolePermissions", "userPermissions"}, allEntries = true)
    public void removePermissionFromRole(UUID roleId, UUID permissionId) throws ResourcesNotFoundException, BadRequestException {
        Role role = repository.findByIdAndIsDeletedFalse(roleId)
                .orElseThrow(() -> new ResourcesNotFoundException("Role not found with id: " + roleId));

        Permission permission = permissionRepository.findByIdAndIsDeletedFalse(permissionId)
                .orElseThrow(() -> new ResourcesNotFoundException("Permission not found with id: " + permissionId));

        if (!role.getPermissions().contains(permission)) {
            throw new BadRequestException("Permission '" + permission.getName() + "' is not assigned to role '" + role.getName() + "'");
        }

        role.getPermissions().remove(permission);
        repository.save(role);

        activityLogService.logActivity(ActivityType.PERMISSION_REMOVED, 
                String.format("Removed permission '%s' from role: %s", permission.getName(), role.getName()));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "rolePermissions", key = "#roleId")
    public Collection<PermissionDTO> getCachedPermissionsForRole(UUID roleId) {
        Role role = repository.findByIdAndIsDeletedFalse(roleId)
                .orElseThrow(() -> new ResourcesNotFoundException("Role not found with id: " + roleId));
        return role.getPermissions().stream()
                .filter(p -> !p.isDeleted())
                .map(permissionMapper::toDto)
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "userPermissions", key = "#username")
    public Collection<PermissionDTO> getCachedPermissionsForUser(String username) {
        User user = userRepository.findByUsername(username)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("User not found with username: " + username));
        
        return user.getRoles().stream()
                .filter(r -> r.isActive() && !r.isDeleted())
                .flatMap(r -> r.getPermissions().stream())
                .filter(p -> p.isActive() && !p.isDeleted())
                .map(permissionMapper::toDto)
                .collect(Collectors.toSet());
    }
}
