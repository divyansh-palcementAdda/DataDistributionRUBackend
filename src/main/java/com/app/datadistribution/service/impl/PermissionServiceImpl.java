package com.app.datadistribution.service.impl;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.datadistribution.dto.user.PermissionDTO;
import com.app.datadistribution.dto.user.PermissionRequest;
import com.app.datadistribution.entity.Permission;
import com.app.datadistribution.enums.ActivityType;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.mapper.PermissionMapper;
import com.app.datadistribution.repository.PermissionRepository;
import com.app.datadistribution.service.interfaces.IActivityLogService;
import com.app.datadistribution.service.interfaces.IPermissionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PermissionServiceImpl implements IPermissionService {

    private final PermissionRepository repository;
    private final PermissionMapper mapper;
    private final IActivityLogService activityLogService;

    @Override
    @Transactional
    public PermissionDTO createPermission(PermissionRequest request) throws BadRequestException {
        if (repository.existsByNameAndIsDeletedFalse(request.getName())) {
            throw new BadRequestException("Permission '" + request.getName() + "' already exists");
        }

        Permission permission = Permission.builder()
                .name(request.getName())
                .description(request.getDescription())
                .active(true)
                .build();

        Permission saved = repository.save(permission);
        log.info("Created permission: {}", saved.getName());

        activityLogService.logActivity(ActivityType.PERMISSION_CREATED, "Created permission: " + saved.getName());

        return mapper.toDto(saved);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"rolePermissions", "userPermissions"}, allEntries = true)
    public PermissionDTO updatePermission(UUID id, PermissionRequest request) throws BadRequestException {
        Permission permission = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourcesNotFoundException("Permission not found with id: " + id));

        if (!permission.getName().equalsIgnoreCase(request.getName()) && repository.existsByNameAndIsDeletedFalse(request.getName())) {
            throw new BadRequestException("Permission name '" + request.getName() + "' already exists");
        }

        permission.setName(request.getName());
        permission.setDescription(request.getDescription());
        Permission saved = repository.save(permission);

        activityLogService.logActivity(ActivityType.PERMISSION_UPDATED, "Updated permission: " + saved.getName());

        return mapper.toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PermissionDTO getById(UUID id) {
        Permission permission = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourcesNotFoundException("Permission not found with id: " + id));
        return mapper.toDto(permission);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PermissionDTO> getAll() {
        return repository.findAllByIsDeletedFalse().stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    @CacheEvict(value = {"rolePermissions", "userPermissions"}, allEntries = true)
    public void delete(UUID id) throws BadRequestException {
        Permission permission = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourcesNotFoundException("Permission not found with id: " + id));

        permission.setDeleted(true);
        permission.setActive(false);
        repository.save(permission);
        log.info("Soft deleted permission: {}", permission.getName());

        activityLogService.logActivity(ActivityType.PERMISSION_DELETED, "Soft deleted permission: " + permission.getName());
    }
}
