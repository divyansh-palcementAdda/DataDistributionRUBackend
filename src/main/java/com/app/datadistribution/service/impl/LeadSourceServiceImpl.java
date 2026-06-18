package com.app.datadistribution.service.impl;

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
import java.util.List;
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
public class LeadSourceServiceImpl implements ILeadSourceService {

    private final LeadSourceRepository leadSourceRepository;
    private final LeadMapper leadMapper;

    @Override
    @Transactional
    public LeadSourceResponse create(LeadSourceRequest request) {
        if (leadSourceRepository.existsByNameIgnoreCase(request.getName())) {
            throw new DuplicateResourceException("Lead source name already exists: " + request.getName());
        }
        LeadSource leadSource = leadMapper.toEntity(request);
        LeadSource saved = leadSourceRepository.save(leadSource);
        log.info("Created lead source: {}", saved.getName());
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

        source.setName(request.getName());
        source.setDescription(request.getDescription());
        source.setActive(request.isActive());

        LeadSource updated = leadSourceRepository.save(source);
        log.info("Updated lead source: {}", updated.getName());
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
        Sort.Direction direction = Sort.Direction.fromString(pageRequest.getSortDirection());
        Pageable pageable = PageRequest.of(pageRequest.getPage(), pageRequest.getSize(), Sort.by(direction, pageRequest.getSortBy()));

        Specification<LeadSource> spec = Specification.where(isNotDeleted());
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

    private Specification<LeadSource> isNotDeleted() {
        return (root, query, cb) -> cb.equal(root.get("isDeleted"), false);
    }

    private Specification<LeadSource> searchLeadSources(String keyword) {
        return (root, query, cb) -> {
            String searchPattern = "%" + keyword.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("name")), searchPattern),
                    cb.like(cb.lower(root.get("description")), searchPattern)
            );
        };
    }
}
