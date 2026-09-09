package com.app.datadistribution.service.impl;

import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.course.CourseResponseDTO;
import com.app.datadistribution.dto.program.ProgramCourseMappingRequestDTO;
import com.app.datadistribution.dto.program.ProgramPagedResponseDTO;
import com.app.datadistribution.dto.program.ProgramRequestDTO;
import com.app.datadistribution.dto.program.ProgramResponseDTO;
import com.app.datadistribution.dto.program.ProgramSummaryDTO;
import com.app.datadistribution.entity.Course;
import com.app.datadistribution.entity.Program;
import com.app.datadistribution.enums.Status;
import com.app.datadistribution.exception.DuplicateResourceException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.mapper.CourseMapper;
import com.app.datadistribution.mapper.ProgramMapper;
import com.app.datadistribution.repository.CourseRepository;
import com.app.datadistribution.repository.ProgramRepository;
import com.app.datadistribution.service.interfaces.IProgramService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProgramServiceImpl implements IProgramService {

    private final ProgramRepository programRepository;
    private final CourseRepository courseRepository;
    private final ProgramMapper programMapper;
    private final CourseMapper courseMapper;

    @Override
    @Transactional
    public ProgramResponseDTO create(ProgramRequestDTO request) {
        if (programRepository.existsByNameIgnoreCase(request.getName())) {
            throw new DuplicateResourceException("Program name already exists: " + request.getName());
        }
        if (programRepository.existsByCodeIgnoreCase(request.getCode())) {
            throw new DuplicateResourceException("Program code already exists: " + request.getCode());
        }

        Program program = programMapper.toEntity(request);

        if (request.getCourseIds() != null && !request.getCourseIds().isEmpty()) {
            List<Course> courses = courseRepository.findAllById(request.getCourseIds());
            program.setCourses(new HashSet<>(courses));
        }

        Program saved = programRepository.save(program);
        log.info("Created program: {} ({})", saved.getName(), saved.getCode());
        return programMapper.toDto(saved);
    }

    @Override
    @Transactional
    public ProgramResponseDTO update(UUID id, ProgramRequestDTO request) {
        Program program = programRepository.findById(id)
                .filter(p -> !p.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Program not found with id: " + id));

        if (programRepository.existsByNameIgnoreCaseAndIdNot(request.getName(), id)) {
            throw new DuplicateResourceException("Program name already exists: " + request.getName());
        }
        if (programRepository.existsByCodeIgnoreCaseAndIdNot(request.getCode(), id)) {
            throw new DuplicateResourceException("Program code already exists: " + request.getCode());
        }

        program.setName(request.getName());
        program.setCode(request.getCode());
        program.setDescription(request.getDescription());
        if (request.getStatus() != null) {
            program.setStatus(request.getStatus());
        }

        if (request.getCourseIds() != null) {
            List<Course> courses = courseRepository.findAllById(request.getCourseIds());
            program.setCourses(new HashSet<>(courses));
        }

        Program updated = programRepository.save(program);
        log.info("Updated program: {}", updated.getCode());
        return programMapper.toDto(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public ProgramResponseDTO getById(UUID id) {
        Program program = programRepository.findById(id)
                .filter(p -> !p.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Program not found with id: " + id));
        return programMapper.toDto(program);
    }

    @Override
    @Transactional(readOnly = true)
    public ProgramPagedResponseDTO getAll(PageRequestDTO pageRequest) {
        Sort.Direction direction = Sort.Direction.fromString(pageRequest.getSortDirection() != null ? pageRequest.getSortDirection() : "DESC");
        String sortBy = pageRequest.getSortBy() != null ? pageRequest.getSortBy() : "createdAt";
        Pageable pageable = PageRequest.of(pageRequest.getPage(), pageRequest.getSize(), Sort.by(direction, sortBy));

        Specification<Program> spec = Specification.where(isNotDeleted());
        if (pageRequest.getSearch() != null && !pageRequest.getSearch().isBlank()) {
            spec = spec.and(searchPrograms(pageRequest.getSearch()));
        }

        Page<Program> page = programRepository.findAll(spec, pageable);
        List<ProgramResponseDTO> content = page.getContent().stream()
                .map(programMapper::toDto)
                .collect(Collectors.toList());

        return ProgramPagedResponseDTO.builder()
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
    public List<ProgramSummaryDTO> getAllActive() {
        return programRepository.findAllByStatusAndIsDeletedFalseOrderByNameAsc(Status.ACTIVE).stream()
                .map(programMapper::toSummaryDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Program program = programRepository.findById(id)
                .filter(p -> !p.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Program not found with id: " + id));
        program.setDeleted(true);
        programRepository.save(program);
        log.info("Soft deleted program: {}", program.getCode());
    }

    @Override
    @Transactional
    public ProgramResponseDTO toggleActive(UUID id) {
        Program program = programRepository.findById(id)
                .filter(p -> !p.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Program not found with id: " + id));

        program.setStatus(program.getStatus() == Status.ACTIVE ? Status.INACTIVE : Status.ACTIVE);
        Program saved = programRepository.save(program);
        log.info("Toggled program active status to {} for: {}", saved.getStatus(), saved.getCode());
        return programMapper.toDto(saved);
    }

    @Override
    @Transactional
    public ProgramResponseDTO mapCoursesToProgram(UUID programId, ProgramCourseMappingRequestDTO request) {
        Program program = programRepository.findById(programId)
                .filter(p -> !p.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Program not found with id: " + programId));

        if (request.getCourseIds() != null) {
            List<Course> courses = courseRepository.findAllById(request.getCourseIds());
            program.setCourses(new HashSet<>(courses));
        } else {
            program.setCourses(new HashSet<>());
        }

        Program saved = programRepository.save(program);
        log.info("Mapped {} courses to program: {}", saved.getCourses().size(), saved.getCode());
        return programMapper.toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourseResponseDTO> getCoursesByProgramId(UUID programId) {
        return courseRepository.findActiveCoursesByProgramId(programId).stream()
                .map(courseMapper::toDto)
                .collect(Collectors.toList());
    }

    private Specification<Program> isNotDeleted() {
        return (root, query, cb) -> cb.equal(root.get("isDeleted"), false);
    }

    private Specification<Program> searchPrograms(String keyword) {
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
