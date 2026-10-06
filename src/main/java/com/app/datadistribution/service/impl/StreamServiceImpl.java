package com.app.datadistribution.service.impl;

import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.stream.StreamPageResponse;
import com.app.datadistribution.dto.stream.StreamRequest;
import com.app.datadistribution.dto.stream.StreamResponse;
import com.app.datadistribution.entity.Stream;
import com.app.datadistribution.exception.DuplicateResourceException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.mapper.LeadMapper;
import com.app.datadistribution.repository.StreamRepository;
import com.app.datadistribution.service.interfaces.IDashboardCardPermissionService;
import com.app.datadistribution.service.interfaces.IStreamService;
import com.app.datadistribution.service.interfaces.IUserDataScopeService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class StreamServiceImpl implements IStreamService {

    private final StreamRepository streamRepository;
    private final LeadMapper leadMapper;
    private final IDashboardCardPermissionService dashboardCardPermissionService;
    private final IUserDataScopeService dataScopeService;

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id", "name", "code", "description", "active", "status", "displayOrder", "display_order",
            "createdAt", "created_at", "updatedAt", "updated_at", "totalData", "total_data",
            "totalAllottedData", "total_allotted_data", "totalUnallottedData", "total_unallotted_data",
            "totalAvailedData", "total_availed_data"
    );

    @Override
    @Transactional
    public StreamResponse create(StreamRequest request) {
        if (streamRepository.existsByNameIgnoreCase(request.getName())) {
            throw new DuplicateResourceException("Stream name already exists: " + request.getName());
        }

        String code = generateUniqueCode(request.getName(), request.getCode(), null);

        Stream stream = leadMapper.toEntity(request);
        stream.setCode(code);
        if (request.getDisplayOrder() != null) {
            stream.setDisplayOrder(request.getDisplayOrder());
        }

        Stream saved = streamRepository.save(stream);
        log.info("Created stream: {} with code {}", saved.getName(), saved.getCode());

        try {
            dashboardCardPermissionService.ensureEntityCardAndPermission("STREAM", saved.getCode(), saved.getName(), "STREAM");
        } catch (Exception e) {
            log.warn("Failed to generate dashboard card/permission for new stream {}", saved.getCode(), e);
        }

        return leadMapper.toDto(saved);
    }

    @Override
    @Transactional
    public StreamResponse update(UUID id, StreamRequest request) {
        Stream stream = streamRepository.findById(id)
                .filter(s -> !s.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Stream not found with id: " + id));

        if (streamRepository.existsByNameIgnoreCaseAndIdNot(request.getName(), id)) {
            throw new DuplicateResourceException("Stream name already exists: " + request.getName());
        }

        String code = generateUniqueCode(request.getName(), request.getCode(), id);

        stream.setName(request.getName());
        stream.setCode(code);
        stream.setDescription(request.getDescription());
        stream.setActive(request.isActive());
        if (request.getDisplayOrder() != null) {
            stream.setDisplayOrder(request.getDisplayOrder());
        }

        Stream updated = streamRepository.save(stream);
        log.info("Updated stream: {} ({})", updated.getName(), updated.getCode());
        return leadMapper.toDto(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public StreamResponse getById(UUID id) {
        Stream stream = streamRepository.findById(id)
                .filter(s -> !s.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Stream not found with id: " + id));
        return leadMapper.toDto(stream);
    }

    @Override
    @Transactional(readOnly = true)
    public StreamPageResponse getAll(PageRequestDTO pageRequest) {
        return getAll(pageRequest, null);
    }

    @Override
    @Transactional(readOnly = true)
    public StreamPageResponse getAll(PageRequestDTO pageRequest, String status) {
        try {
            com.app.datadistribution.service.dto.UserDataScope dataScope = dataScopeService.getScopeForCurrentUser();
            return streamRepository.fetchStreamsWithLeadStats(pageRequest, status, dataScope);
        } catch (Exception e) {
            log.error("Failed to retrieve user data scope for streams, falling back to default", e);
            throw new RuntimeException("Failed to resolve user data scope", e);
        }
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Stream stream = streamRepository.findById(id)
                .filter(s -> !s.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Stream not found with id: " + id));
        stream.setDeleted(true);
        streamRepository.save(stream);
        log.info("Soft deleted stream: {}", stream.getName());
    }

    @Override
    @Transactional
    public StreamResponse toggleActive(UUID id) {
        Stream stream = streamRepository.findById(id)
                .filter(s -> !s.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Stream not found with id: " + id));
        stream.setActive(!stream.isActive());
        Stream saved = streamRepository.save(stream);
        log.info("Toggled stream active status to {} for: {}", saved.isActive(), saved.getName());
        return leadMapper.toDto(saved);
    }

    private String generateUniqueCode(String name, String providedCode, UUID excludeId) {
        if (providedCode != null && !providedCode.isBlank()) {
            String trimmedCode = providedCode.trim().toUpperCase();
            boolean exists = (excludeId == null)
                    ? streamRepository.existsByCodeIgnoreCase(trimmedCode)
                    : streamRepository.existsByCodeIgnoreCaseAndIdNot(trimmedCode, excludeId);
            if (exists) {
                throw new DuplicateResourceException("Stream code already exists: " + trimmedCode);
            }
            return trimmedCode;
        }

        String baseCode = name.replaceAll("[^a-zA-Z0-9]", "_").toUpperCase();
        if (baseCode.length() > 20) {
            baseCode = baseCode.substring(0, 20);
        }
        if (baseCode.isBlank()) {
            baseCode = "STREAM";
        }

        String candidate = baseCode;
        boolean exists = (excludeId == null)
                ? streamRepository.existsByCodeIgnoreCase(candidate)
                : streamRepository.existsByCodeIgnoreCaseAndIdNot(candidate, excludeId);

        if (!exists) {
            return candidate;
        }

        while (true) {
            String randomSuffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            candidate = baseCode + "_" + randomSuffix;
            exists = (excludeId == null)
                    ? streamRepository.existsByCodeIgnoreCase(candidate)
                    : streamRepository.existsByCodeIgnoreCaseAndIdNot(candidate, excludeId);
            if (!exists) {
                return candidate;
            }
        }
    }
}
