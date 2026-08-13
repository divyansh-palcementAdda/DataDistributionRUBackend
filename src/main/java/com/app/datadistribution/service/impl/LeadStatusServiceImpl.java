package com.app.datadistribution.service.impl;

import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.lead.LeadStatusPageResponse;
import com.app.datadistribution.dto.lead.LeadStatusRequest;
import com.app.datadistribution.dto.lead.LeadStatusResponse;
import com.app.datadistribution.entity.LeadStatus;
import com.app.datadistribution.exception.DuplicateResourceException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.mapper.LeadMapper;
import com.app.datadistribution.repository.LeadStatusRepository;
import com.app.datadistribution.service.interfaces.ILeadStatusService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeadStatusServiceImpl implements ILeadStatusService {

    private final LeadStatusRepository leadStatusRepository;
    private final LeadMapper leadMapper;
    private final com.app.datadistribution.service.interfaces.IDashboardCardPermissionService dashboardCardPermissionService;

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id", "name", "code", "description", "active", "displayOrder", "createdAt", "updatedAt"
    );

    @Override
    @Transactional
    public LeadStatusResponse create(LeadStatusRequest request) {
        if (leadStatusRepository.existsByNameIgnoreCase(request.getName())) {
            throw new DuplicateResourceException("Lead status name already exists: " + request.getName());
        }

        String code = generateUniqueCode(request.getName(), request.getCode(), null);

        LeadStatus leadStatus = leadMapper.toEntity(request);
        leadStatus.setCode(code);
        if (request.getDisplayOrder() != null) {
            leadStatus.setDisplayOrder(request.getDisplayOrder());
        }
        if (request.getSentimentCategory() != null) {
            leadStatus.setSentimentCategory(request.getSentimentCategory());
        }

        LeadStatus saved = leadStatusRepository.save(leadStatus);
        log.info("Created lead status: {} with code {}", saved.getName(), saved.getCode());

        try {
            dashboardCardPermissionService.ensureEntityCardAndPermission("LEAD_STATUS", saved.getCode(), saved.getName(), "LEAD_STATUS");
        } catch (Exception e) {
            log.warn("Failed to generate dashboard card/permission for new lead status {}", saved.getCode(), e);
        }

        return leadMapper.toDto(saved);
    }

    @Override
    @Transactional
    public LeadStatusResponse update(UUID id, LeadStatusRequest request) {
        LeadStatus status = leadStatusRepository.findById(id)
                .filter(s -> !s.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Lead status not found with id: " + id));

        if (leadStatusRepository.existsByNameIgnoreCaseAndIdNot(request.getName(), id)) {
            throw new DuplicateResourceException("Lead status name already exists: " + request.getName());
        }

        String code = generateUniqueCode(request.getName(), request.getCode(), id);

        status.setName(request.getName());
        status.setCode(code);
        status.setDescription(request.getDescription());
        status.setActive(request.isActive());
        if (request.getDisplayOrder() != null) {
            status.setDisplayOrder(request.getDisplayOrder());
        }
        if (request.getSentimentCategory() != null) {
            status.setSentimentCategory(request.getSentimentCategory());
        }

        LeadStatus updated = leadStatusRepository.save(status);
        log.info("Updated lead status: {} ({})", updated.getName(), updated.getCode());
        return leadMapper.toDto(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public LeadStatusResponse getById(UUID id) {
        LeadStatus status = leadStatusRepository.findById(id)
                .filter(s -> !s.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Lead status not found with id: " + id));
        return leadMapper.toDto(status);
    }

    @Override
    @Transactional(readOnly = true)
    public LeadStatusPageResponse getAll(PageRequestDTO pageRequest) {
        return getAll(pageRequest, null);
    }

    @Override
    @Transactional(readOnly = true)
    public LeadStatusPageResponse getAll(PageRequestDTO pageRequest, String status) {
        String sortBy = pageRequest.getSortBy();
        if (sortBy == null || !ALLOWED_SORT_FIELDS.contains(sortBy)) {
            sortBy = "displayOrder";
        }
        Sort.Direction direction = Sort.Direction.fromString(
                pageRequest.getSortDirection() != null ? pageRequest.getSortDirection() : "ASC"
        );
        Pageable pageable = PageRequest.of(pageRequest.getPage(), pageRequest.getSize(), Sort.by(direction, sortBy));

        Specification<LeadStatus> spec = Specification.where(isNotDeleted());
        if (status != null && !status.isBlank()) {
            spec = spec.and(filterByStatus(status));
        }
        if (pageRequest.getSearch() != null && !pageRequest.getSearch().isBlank()) {
            spec = spec.and(searchLeadStatuses(pageRequest.getSearch()));
        }

        Page<LeadStatus> page = leadStatusRepository.findAll(spec, pageable);
        List<LeadStatusResponse> content = page.getContent().stream()
                .map(leadMapper::toDto)
                .collect(Collectors.toList());

        return LeadStatusPageResponse.builder()
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
        LeadStatus status = leadStatusRepository.findById(id)
                .filter(s -> !s.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Lead status not found with id: " + id));
        status.setDeleted(true);
        leadStatusRepository.save(status);
        log.info("Soft deleted lead status: {}", status.getName());
    }

    @Override
    @Transactional
    public LeadStatusResponse toggleActive(UUID id) {
        LeadStatus status = leadStatusRepository.findById(id)
                .filter(s -> !s.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Lead status not found with id: " + id));
        status.setActive(!status.isActive());
        LeadStatus saved = leadStatusRepository.save(status);
        log.info("Toggled lead status active status to {} for: {}", saved.isActive(), saved.getName());
        return leadMapper.toDto(saved);
    }

    private String generateUniqueCode(String name, String providedCode, UUID excludeId) {
        if (providedCode != null && !providedCode.isBlank()) {
            String trimmedCode = providedCode.trim().toUpperCase();
            boolean exists = (excludeId == null)
                    ? leadStatusRepository.existsByCodeIgnoreCase(trimmedCode)
                    : leadStatusRepository.existsByCodeIgnoreCaseAndIdNot(trimmedCode, excludeId);
            if (exists) {
                throw new DuplicateResourceException("Lead status code already exists: " + trimmedCode);
            }
            return trimmedCode;
        }

        String baseCode = name.replaceAll("[^a-zA-Z0-9]", "_").toUpperCase();
        if (baseCode.length() > 20) {
            baseCode = baseCode.substring(0, 20);
        }
        if (baseCode.isBlank()) {
            baseCode = "STATUS";
        }

        String candidate = baseCode;
        boolean exists = (excludeId == null)
                ? leadStatusRepository.existsByCodeIgnoreCase(candidate)
                : leadStatusRepository.existsByCodeIgnoreCaseAndIdNot(candidate, excludeId);

        if (!exists) {
            return candidate;
        }

        while (true) {
            String randomSuffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            candidate = baseCode + "_" + randomSuffix;
            exists = (excludeId == null)
                    ? leadStatusRepository.existsByCodeIgnoreCase(candidate)
                    : leadStatusRepository.existsByCodeIgnoreCaseAndIdNot(candidate, excludeId);
            if (!exists) {
                return candidate;
            }
        }
    }

    private Specification<LeadStatus> isNotDeleted() {
        return (root, query, cb) -> cb.equal(root.get("isDeleted"), false);
    }

    private Specification<LeadStatus> filterByStatus(String status) {
        return (root, query, cb) -> {
            boolean active = "ACTIVE".equalsIgnoreCase(status);
            return cb.equal(root.get("active"), active);
        };
    }

    private Specification<LeadStatus> searchLeadStatuses(String keyword) {
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
