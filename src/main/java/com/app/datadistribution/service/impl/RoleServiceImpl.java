package com.app.datadistribution.service.impl;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.datadistribution.Model.Role;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.mapper.RoleMapper;
import com.app.datadistribution.payload.RoleDTO;
import com.app.datadistribution.payload.RoleRequest;
import com.app.datadistribution.repository.RoleRepository;
import com.app.datadistribution.service.interfaces.IRoleService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class RoleServiceImpl implements IRoleService {

    private final RoleRepository repository;
    private final RoleMapper mapper;

    @Override
    public RoleDTO createRole(RoleRequest request) throws BadRequestException {

        if (repository.existsByName(request.getName())) {
            throw new BadRequestException(
                    "Role '" + request.getName() + "' already exists");
        }

        Role role = Role.builder()
                .name(request.getName())
                .build();

        Role saved = repository.save(role);
        log.info("Created role: {}", saved.getName());

        return mapper.toDto(saved);
    }

    @Override
    public RoleDTO updateRole(Long id, RoleRequest request) throws BadRequestException {

        Role role = repository.findById(id)
                .orElseThrow(() ->
                        new ResourcesNotFoundException("Role", "id", id));

        if (!role.getName().equalsIgnoreCase(request.getName())
                && repository.existsByName(request.getName())) {
            throw new BadRequestException(
                    "Role name '" + request.getName() + "' already exists");
        }

        role.setName(request.getName());
        return mapper.toDto(role);
    }

    @Override
    @Transactional(readOnly = true)
    public RoleDTO getById(Long id) {

        Role role = repository.findById(id)
                .orElseThrow(() ->
                        new ResourcesNotFoundException("Role", "id", id));

        return mapper.toDto(role);
    }

    @Override
    @Transactional(readOnly = true)
    public RoleDTO getByName(String name) {

        Role role = repository.findByName(name)
                .orElseThrow(() ->
                        new ResourcesNotFoundException("Role", "name", name));

        return mapper.toDto(role);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleDTO> getAll() {
        return repository.fetchAllSummary();
    }

    @Override
    public void delete(Long id) throws BadRequestException {

        Role role = repository.findById(id)
                .orElseThrow(() ->
                        new ResourcesNotFoundException("Role", "id", id));

        if (!role.getUsers().isEmpty()) {
            throw new BadRequestException(
                    "Cannot delete role '" + role.getName() +
                    "' because it is assigned to " + role.getUsers().size() + " users");
        }

        repository.delete(role);
        log.info("Deleted role: {}", role.getName());
    }

    @Override
    public boolean existsByName(String name) {
        return repository.existsByName(name);
    }

	
}
