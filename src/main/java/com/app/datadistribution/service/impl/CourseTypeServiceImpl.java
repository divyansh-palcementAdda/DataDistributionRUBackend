package com.app.datadistribution.service.impl;

import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.common.PageResponseDTO;
import com.app.datadistribution.dto.course.CourseTypeRequestDTO;
import com.app.datadistribution.dto.course.CourseTypeResponseDTO;
import com.app.datadistribution.entity.CourseType;
import com.app.datadistribution.enums.Status;
import com.app.datadistribution.exception.DuplicateResourceException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.mapper.CourseMapper;
import com.app.datadistribution.repository.CourseTypeRepository;
import com.app.datadistribution.service.interfaces.ICourseTypeService;
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
public class CourseTypeServiceImpl implements ICourseTypeService {

    private final CourseTypeRepository courseTypeRepository;
    private final CourseMapper courseMapper;

    @Override
    @Transactional
    public CourseTypeResponseDTO create(CourseTypeRequestDTO request) {
        if (courseTypeRepository.existsByNameIgnoreCase(request.getName())) {
            throw new DuplicateResourceException("Course type name already exists: " + request.getName());
        }
        CourseType courseType = courseMapper.toEntity(request);
        CourseType saved = courseTypeRepository.save(courseType);
        log.info("Created course type: {}", saved.getName());
        return courseMapper.toDto(saved);
    }

    @Override
    @Transactional
    public CourseTypeResponseDTO update(UUID id, CourseTypeRequestDTO request) {
        CourseType courseType = courseTypeRepository.findById(id)
                .filter(c -> !c.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Course type not found with id: " + id));

        if (courseTypeRepository.existsByNameIgnoreCaseAndIdNot(request.getName(), id)) {
            throw new DuplicateResourceException("Course type name already exists: " + request.getName());
        }

        courseType.setName(request.getName());
        courseType.setDescription(request.getDescription());
        courseType.setStatus(request.getStatus());

        CourseType updated = courseTypeRepository.save(courseType);
        log.info("Updated course type: {}", updated.getName());
        return courseMapper.toDto(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public CourseTypeResponseDTO getById(UUID id) {
        CourseType courseType = courseTypeRepository.findById(id)
                .filter(c -> !c.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Course type not found with id: " + id));
        return courseMapper.toDto(courseType);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<CourseTypeResponseDTO> getAll(PageRequestDTO pageRequest) {
        Sort.Direction direction = Sort.Direction.fromString(pageRequest.getSortDirection());
        Pageable pageable = PageRequest.of(pageRequest.getPage(), pageRequest.getSize(), Sort.by(direction, pageRequest.getSortBy()));

        Specification<CourseType> spec = Specification.where(isNotDeleted());
        if (pageRequest.getSearch() != null && !pageRequest.getSearch().isBlank()) {
            spec = spec.and(searchCourseTypes(pageRequest.getSearch()));
        }

        Page<CourseType> page = courseTypeRepository.findAll(spec, pageable);
        List<CourseTypeResponseDTO> content = page.getContent().stream()
                .map(courseMapper::toDto)
                .collect(Collectors.toList());

        return PageResponseDTO.of(content, page);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourseTypeResponseDTO> getAllActive() {
        Specification<CourseType> spec = Specification.where(isNotDeleted()).and(isActive());
        return courseTypeRepository.findAll(spec).stream()
                .map(courseMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        CourseType courseType = courseTypeRepository.findById(id)
                .filter(c -> !c.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Course type not found with id: " + id));
        courseType.setDeleted(true);
        courseTypeRepository.save(courseType);
        log.info("Soft deleted course type: {}", courseType.getName());
    }

    @Override
    @Transactional
    public CourseTypeResponseDTO toggleActive(UUID id) {
        CourseType courseType = courseTypeRepository.findById(id)
                .filter(c -> !c.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Course type not found with id: " + id));
        
        courseType.setStatus(courseType.getStatus() == Status.ACTIVE ? Status.INACTIVE : Status.ACTIVE);
        CourseType saved = courseTypeRepository.save(courseType);
        log.info("Toggled course type active status to {} for: {}", saved.getStatus(), saved.getName());
        return courseMapper.toDto(saved);
    }

    private Specification<CourseType> isNotDeleted() {
        return (root, query, cb) -> cb.equal(root.get("isDeleted"), false);
    }

    private Specification<CourseType> isActive() {
        return (root, query, cb) -> cb.equal(root.get("status"), Status.ACTIVE);
    }

    private Specification<CourseType> searchCourseTypes(String keyword) {
        return (root, query, cb) -> {
            String searchPattern = "%" + keyword.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("name")), searchPattern),
                    cb.like(cb.lower(root.get("description")), searchPattern)
            );
        };
    }
}
