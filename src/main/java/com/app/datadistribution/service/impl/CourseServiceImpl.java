package com.app.datadistribution.service.impl;

import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.course.CoursePagedResponseDTO;
import com.app.datadistribution.dto.course.CourseRequestDTO;
import com.app.datadistribution.dto.course.CourseResponseDTO;
import com.app.datadistribution.entity.Course;
import com.app.datadistribution.entity.CourseType;
import com.app.datadistribution.enums.Status;
import com.app.datadistribution.exception.DuplicateResourceException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.mapper.CourseMapper;
import com.app.datadistribution.repository.CourseRepository;
import com.app.datadistribution.repository.CourseTypeRepository;
import com.app.datadistribution.service.interfaces.ICourseService;
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
public class CourseServiceImpl implements ICourseService {

    private final CourseRepository courseRepository;
    private final CourseTypeRepository courseTypeRepository;
    private final CourseMapper courseMapper;

    @Override
    @Transactional
    public CourseResponseDTO create(CourseRequestDTO request) {
        if (courseRepository.existsByCourseNameIgnoreCase(request.getCourseName())) {
            throw new DuplicateResourceException("Course name already exists: " + request.getCourseName());
        }
        if (courseRepository.existsByCourseCodeIgnoreCase(request.getCourseCode())) {
            throw new DuplicateResourceException("Course code already exists: " + request.getCourseCode());
        }

        CourseType courseType = courseTypeRepository.findById(request.getCourseTypeId())
                .filter(ct -> !ct.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Course type not found with id: " + request.getCourseTypeId()));

        Course course = courseMapper.toEntity(request);
        course.setCourseType(courseType);
        
        Course saved = courseRepository.save(course);
        log.info("Created course: {} ({})", saved.getCourseName(), saved.getCourseCode());
        return courseMapper.toDto(saved);
    }

    @Override
    @Transactional
    public CourseResponseDTO update(UUID id, CourseRequestDTO request) {
        Course course = courseRepository.findById(id)
                .filter(c -> !c.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Course not found with id: " + id));

        if (courseRepository.existsByCourseNameIgnoreCaseAndIdNot(request.getCourseName(), id)) {
            throw new DuplicateResourceException("Course name already exists: " + request.getCourseName());
        }
        if (courseRepository.existsByCourseCodeIgnoreCaseAndIdNot(request.getCourseCode(), id)) {
            throw new DuplicateResourceException("Course code already exists: " + request.getCourseCode());
        }

        CourseType courseType = courseTypeRepository.findById(request.getCourseTypeId())
                .filter(ct -> !ct.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Course type not found with id: " + request.getCourseTypeId()));

        course.setCourseName(request.getCourseName());
        course.setCourseCode(request.getCourseCode());
        course.setDescription(request.getDescription());
        course.setDuration(request.getDuration());
        course.setDurationUnit(request.getDurationUnit());
        course.setFees(request.getFees());
        course.setCourseType(courseType);
        course.setStatus(request.getStatus());

        Course updated = courseRepository.save(course);
        log.info("Updated course: {}", updated.getCourseCode());
        return courseMapper.toDto(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public CourseResponseDTO getById(UUID id) {
        Course course = courseRepository.findById(id)
                .filter(c -> !c.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Course not found with id: " + id));
        return courseMapper.toDto(course);
    }

    @Override
    @Transactional(readOnly = true)
    public CoursePagedResponseDTO getAll(PageRequestDTO pageRequest, UUID courseTypeId) {
        Sort.Direction direction = Sort.Direction.fromString(pageRequest.getSortDirection());
        Pageable pageable = PageRequest.of(pageRequest.getPage(), pageRequest.getSize(), Sort.by(direction, pageRequest.getSortBy()));

        Specification<Course> spec = Specification.where(isNotDeleted());
        if (courseTypeId != null) {
            spec = spec.and(filterByCourseType(courseTypeId));
        }
        if (pageRequest.getSearch() != null && !pageRequest.getSearch().isBlank()) {
            spec = spec.and(searchCourses(pageRequest.getSearch()));
        }

        Page<Course> page = courseRepository.findAll(spec, pageable);
        List<CourseResponseDTO> content = page.getContent().stream()
                .map(courseMapper::toDto)
                .collect(Collectors.toList());

        return CoursePagedResponseDTO.builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourseResponseDTO> getAllActive() {
        Specification<Course> spec = Specification.where(isNotDeleted()).and(isActive());
        return courseRepository.findAll(spec).stream()
                .map(courseMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Course course = courseRepository.findById(id)
                .filter(c -> !c.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Course not found with id: " + id));
        course.setDeleted(true);
        courseRepository.save(course);
        log.info("Soft deleted course: {}", course.getCourseCode());
    }

    @Override
    @Transactional
    public CourseResponseDTO toggleActive(UUID id) {
        Course course = courseRepository.findById(id)
                .filter(c -> !c.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Course not found with id: " + id));

        course.setStatus(course.getStatus() == Status.ACTIVE ? Status.INACTIVE : Status.ACTIVE);
        Course saved = courseRepository.save(course);
        log.info("Toggled course active status to {} for: {}", saved.getStatus(), saved.getCourseCode());
        return courseMapper.toDto(saved);
    }

    private Specification<Course> isNotDeleted() {
        return (root, query, cb) -> cb.equal(root.get("isDeleted"), false);
    }

    private Specification<Course> isActive() {
        return (root, query, cb) -> cb.equal(root.get("status"), Status.ACTIVE);
    }

    private Specification<Course> filterByCourseType(UUID courseTypeId) {
        return (root, query, cb) -> cb.equal(root.get("courseType").get("id"), courseTypeId);
    }

    private Specification<Course> searchCourses(String keyword) {
        return (root, query, cb) -> {
            String searchPattern = "%" + keyword.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("courseName")), searchPattern),
                    cb.like(cb.lower(root.get("courseCode")), searchPattern),
                    cb.like(cb.lower(root.get("courseType").get("name")), searchPattern)
            );
        };
    }
}
