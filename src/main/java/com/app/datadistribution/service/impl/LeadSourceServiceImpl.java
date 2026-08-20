package com.app.datadistribution.service.impl;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.lead.LeadSourcePageResponse;
import com.app.datadistribution.dto.lead.LeadSourceRequest;
import com.app.datadistribution.dto.lead.LeadSourceResponse;
import com.app.datadistribution.entity.LeadSource;
import com.app.datadistribution.exception.DuplicateResourceException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.mapper.LeadMapper;
import com.app.datadistribution.repository.LeadSourceRepository;
import com.app.datadistribution.service.interfaces.ILeadSourceService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeadSourceServiceImpl implements ILeadSourceService {

    private final LeadSourceRepository leadSourceRepository;
    private final LeadMapper leadMapper;
    private final com.app.datadistribution.service.interfaces.IDashboardCardPermissionService dashboardCardPermissionService;

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id", "name", "code", "description", "active", "createdAt", "updatedAt"
    );

    @Override
    @Transactional
    public LeadSourceResponse create(LeadSourceRequest request) {
        if (leadSourceRepository.existsByNameIgnoreCase(request.getName())) {
            throw new DuplicateResourceException("Lead source name already exists: " + request.getName());
        }
        String code = generateUniqueCode(request.getName(), request.getCode(), null);
        LeadSource leadSource = leadMapper.toEntity(request);
        leadSource.setCode(code);
        LeadSource saved = leadSourceRepository.save(leadSource);
        log.info("Created lead source: {} with code {}", saved.getName(), saved.getCode());

        try {
            dashboardCardPermissionService.ensureEntityCardAndPermission("LEAD_SOURCE", saved.getCode(), saved.getName(), "LEAD_SOURCE");
        } catch (Exception e) {
            log.warn("Failed to generate dashboard card/permission for new lead source {}", saved.getCode(), e);
        }

        return leadMapper.toDto(saved);
    }

    @Override
    @Transactional
    public LeadSourceResponse update(UUID id, LeadSourceRequest request) {
        LeadSource source = leadSourceRepository.findById(id)
                .filter(s -> !s.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Lead source not found with id: " + id));

        if (leadSourceRepository.existsByNameIgnoreCaseAndIdNot(request.getName(), id)) {
            throw new DuplicateResourceException("Lead source name already exists: " + request.getName());
        }

        String code = generateUniqueCode(request.getName(), request.getCode(), id);

        source.setName(request.getName());
        source.setCode(code);
        source.setDescription(request.getDescription());
        source.setActive(request.isActive());

        LeadSource updated = leadSourceRepository.save(source);
        log.info("Updated lead source: {} ({})", updated.getName(), updated.getCode());
        return leadMapper.toDto(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public LeadSourceResponse getById(UUID id) {
        LeadSource source = leadSourceRepository.findById(id)
                .filter(s -> !s.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Lead source not found with id: " + id));
        return leadMapper.toDto(source);
    }

    @Override
    @Transactional(readOnly = true)
    public LeadSourcePageResponse getAll(PageRequestDTO pageRequest) {
        return getAll(pageRequest, null);
    }

    @Override
    @Transactional(readOnly = true)
    public LeadSourcePageResponse getAll(PageRequestDTO pageRequest, String status) {
        String sortBy = pageRequest.getSortBy();
        if (sortBy == null || !ALLOWED_SORT_FIELDS.contains(sortBy)) {
            sortBy = "createdAt";
        }
        Sort.Direction direction = Sort.Direction.fromString(
                pageRequest.getSortDirection() != null ? pageRequest.getSortDirection() : "ASC"
        );
        Pageable pageable = PageRequest.of(pageRequest.getPage(), pageRequest.getSize(), Sort.by(direction, sortBy));

        Specification<LeadSource> spec = Specification.where(isNotDeleted());
        if (status != null && !status.isBlank()) {
            spec = spec.and(filterByStatus(status));
        }
        if (pageRequest.getSearch() != null && !pageRequest.getSearch().isBlank()) {
            spec = spec.and(searchLeadSources(pageRequest.getSearch()));
        }

        Page<LeadSource> page = leadSourceRepository.findAll(spec, pageable);
        List<LeadSourceResponse> content = page.getContent().stream()
                .map(leadMapper::toDto)
                .collect(Collectors.toList());

        return LeadSourcePageResponse.builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        LeadSource source = leadSourceRepository.findById(id)
                .filter(s -> !s.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Lead source not found with id: " + id));
        source.setDeleted(true);
        leadSourceRepository.save(source);
        log.info("Soft deleted lead source: {}", source.getName());
    }

    @Override
    @Transactional
    public LeadSourceResponse toggleActive(UUID id) {
        LeadSource source = leadSourceRepository.findById(id)
                .filter(s -> !s.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Lead source not found with id: " + id));
        source.setActive(!source.isActive());
        LeadSource saved = leadSourceRepository.save(source);
        log.info("Toggled lead source active status to {} for: {}", saved.isActive(), saved.getName());
        return leadMapper.toDto(saved);
    }

    private String generateUniqueCode(String name, String providedCode, UUID excludeId) {
        if (providedCode != null && !providedCode.isBlank()) {
            String trimmedCode = providedCode.trim().toUpperCase();
            boolean exists = (excludeId == null)
                    ? leadSourceRepository.existsByCodeIgnoreCase(trimmedCode)
                    : leadSourceRepository.existsByCodeIgnoreCaseAndIdNot(trimmedCode, excludeId);
            if (exists) {
                throw new DuplicateResourceException("Lead source code already exists: " + trimmedCode);
            }
            return trimmedCode;
        }

        String baseCode = name.replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
        if (baseCode.length() > 20) {
            baseCode = baseCode.substring(0, 20);
        }
        if (baseCode.isBlank()) {
            baseCode = "SRC";
        }

        String candidate = baseCode;
        boolean exists = (excludeId == null)
                ? leadSourceRepository.existsByCodeIgnoreCase(candidate)
                : leadSourceRepository.existsByCodeIgnoreCaseAndIdNot(candidate, excludeId);
        
        if (!exists) {
            return candidate;
        }

        while (true) {
            String randomSuffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            candidate = baseCode + "-" + randomSuffix;
            exists = (excludeId == null)
                    ? leadSourceRepository.existsByCodeIgnoreCase(candidate)
                    : leadSourceRepository.existsByCodeIgnoreCaseAndIdNot(candidate, excludeId);
            if (!exists) {
                return candidate;
            }
        }
    }

    private Specification<LeadSource> isNotDeleted() {
        return (root, query, cb) -> cb.equal(root.get("isDeleted"), false);
    }

    private Specification<LeadSource> filterByStatus(String status) {
        return (root, query, cb) -> {
            boolean active = "ACTIVE".equalsIgnoreCase(status);
            return cb.equal(root.get("active"), active);
        };
    }

    private Specification<LeadSource> searchLeadSources(String keyword) {
        return (root, query, cb) -> {
            String searchPattern = "%" + keyword.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("name")), searchPattern),
                    cb.like(cb.lower(root.get("code")), searchPattern),
                    cb.like(cb.lower(root.get("description")), searchPattern)
            );
        };
    }
}
