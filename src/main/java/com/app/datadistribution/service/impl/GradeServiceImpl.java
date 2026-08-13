package com.app.datadistribution.service.impl;

import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.lead.GradePageResponse;
import com.app.datadistribution.dto.lead.GradeRequest;
import com.app.datadistribution.dto.lead.GradeResponse;
import com.app.datadistribution.entity.Grade;
import com.app.datadistribution.exception.DuplicateResourceException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.mapper.LeadMapper;
import com.app.datadistribution.repository.GradeRepository;
import com.app.datadistribution.service.interfaces.IGradeService;
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
public class GradeServiceImpl implements IGradeService {

    private final GradeRepository gradeRepository;
    private final LeadMapper leadMapper;
    private final com.app.datadistribution.service.interfaces.IDashboardCardPermissionService dashboardCardPermissionService;

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id", "name", "code", "description", "active", "displayOrder", "createdAt", "updatedAt"
    );

    @Override
    @Transactional
    public GradeResponse create(GradeRequest request) {
        if (gradeRepository.existsByNameIgnoreCase(request.getName())) {
            throw new DuplicateResourceException("Grade name already exists: " + request.getName());
        }

        String code = generateUniqueCode(request.getName(), request.getCode(), null);

        Grade grade = leadMapper.toEntity(request);
        grade.setCode(code);
        if (request.getDisplayOrder() != null) {
            grade.setDisplayOrder(request.getDisplayOrder());
        }

        Grade saved = gradeRepository.save(grade);
        log.info("Created grade: {} with code {}", saved.getName(), saved.getCode());

        try {
            dashboardCardPermissionService.ensureEntityCardAndPermission("GRADE", saved.getCode(), saved.getName(), "GRADE");
        } catch (Exception e) {
            log.warn("Failed to generate dashboard card/permission for new grade {}", saved.getCode(), e);
        }

        return leadMapper.toDto(saved);
    }

    @Override
    @Transactional
    public GradeResponse update(UUID id, GradeRequest request) {
        Grade grade = gradeRepository.findById(id)
                .filter(g -> !g.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Grade not found with id: " + id));

        if (gradeRepository.existsByNameIgnoreCaseAndIdNot(request.getName(), id)) {
            throw new DuplicateResourceException("Grade name already exists: " + request.getName());
        }

        String code = generateUniqueCode(request.getName(), request.getCode(), id);

        grade.setName(request.getName());
        grade.setCode(code);
        grade.setDescription(request.getDescription());
        grade.setActive(request.isActive());
        if (request.getDisplayOrder() != null) {
            grade.setDisplayOrder(request.getDisplayOrder());
        }

        Grade updated = gradeRepository.save(grade);
        log.info("Updated grade: {} ({})", updated.getName(), updated.getCode());
        return leadMapper.toDto(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public GradeResponse getById(UUID id) {
        Grade grade = gradeRepository.findById(id)
                .filter(g -> !g.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Grade not found with id: " + id));
        return leadMapper.toDto(grade);
    }

    @Override
    @Transactional(readOnly = true)
    public GradePageResponse getAll(PageRequestDTO pageRequest) {
        return getAll(pageRequest, null);
    }

    @Override
    @Transactional(readOnly = true)
    public GradePageResponse getAll(PageRequestDTO pageRequest, String status) {
        String sortBy = pageRequest.getSortBy();
        if (sortBy == null || !ALLOWED_SORT_FIELDS.contains(sortBy)) {
            sortBy = "displayOrder";
        }
        Sort.Direction direction = Sort.Direction.fromString(
                pageRequest.getSortDirection() != null ? pageRequest.getSortDirection() : "ASC"
        );
        Pageable pageable = PageRequest.of(pageRequest.getPage(), pageRequest.getSize(), Sort.by(direction, sortBy));

        Specification<Grade> spec = Specification.where(isNotDeleted());
        if (status != null && !status.isBlank()) {
            spec = spec.and(filterByStatus(status));
        }
        if (pageRequest.getSearch() != null && !pageRequest.getSearch().isBlank()) {
            spec = spec.and(searchGrades(pageRequest.getSearch()));
        }

        Page<Grade> page = gradeRepository.findAll(spec, pageable);
        List<GradeResponse> content = page.getContent().stream()
                .map(leadMapper::toDto)
                .collect(Collectors.toList());

        return GradePageResponse.builder()
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
        Grade grade = gradeRepository.findById(id)
                .filter(g -> !g.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Grade not found with id: " + id));
        grade.setDeleted(true);
        gradeRepository.save(grade);
        log.info("Soft deleted grade: {}", grade.getName());
    }

    @Override
    @Transactional
    public GradeResponse toggleActive(UUID id) {
        Grade grade = gradeRepository.findById(id)
                .filter(g -> !g.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Grade not found with id: " + id));
        grade.setActive(!grade.isActive());
        Grade saved = gradeRepository.save(grade);
        log.info("Toggled grade active status to {} for: {}", saved.isActive(), saved.getName());
        return leadMapper.toDto(saved);
    }

    private String generateUniqueCode(String name, String providedCode, UUID excludeId) {
        if (providedCode != null && !providedCode.isBlank()) {
            String trimmedCode = providedCode.trim().toUpperCase();
            boolean exists = (excludeId == null)
                    ? gradeRepository.existsByCodeIgnoreCase(trimmedCode)
                    : gradeRepository.existsByCodeIgnoreCaseAndIdNot(trimmedCode, excludeId);
            if (exists) {
                throw new DuplicateResourceException("Grade code already exists: " + trimmedCode);
            }
            return trimmedCode;
        }

        String baseCode = name.replaceAll("[^a-zA-Z0-9]", "_").toUpperCase();
        if (baseCode.length() > 20) {
            baseCode = baseCode.substring(0, 20);
        }
        if (baseCode.isBlank()) {
            baseCode = "GRADE";
        }

        String candidate = baseCode;
        boolean exists = (excludeId == null)
                ? gradeRepository.existsByCodeIgnoreCase(candidate)
                : gradeRepository.existsByCodeIgnoreCaseAndIdNot(candidate, excludeId);

        if (!exists) {
            return candidate;
        }

        while (true) {
            String randomSuffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            candidate = baseCode + "_" + randomSuffix;
            exists = (excludeId == null)
                    ? gradeRepository.existsByCodeIgnoreCase(candidate)
                    : gradeRepository.existsByCodeIgnoreCaseAndIdNot(candidate, excludeId);
            if (!exists) {
                return candidate;
            }
        }
    }

    private Specification<Grade> isNotDeleted() {
        return (root, query, cb) -> cb.equal(root.get("isDeleted"), false);
    }

    private Specification<Grade> filterByStatus(String status) {
        return (root, query, cb) -> {
            boolean active = "ACTIVE".equalsIgnoreCase(status);
            return cb.equal(root.get("active"), active);
        };
    }

    private Specification<Grade> searchGrades(String keyword) {
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
