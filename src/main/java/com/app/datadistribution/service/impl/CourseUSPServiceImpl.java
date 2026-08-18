package com.app.datadistribution.service.impl;

import com.app.datadistribution.dto.courseusp.CourseUSPDTO;
import com.app.datadistribution.entity.Course;
import com.app.datadistribution.entity.CourseUSP;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.repository.CourseRepository;
import com.app.datadistribution.repository.CourseUSPRepository;
import com.app.datadistribution.service.interfaces.ICourseUSPService;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseUSPServiceImpl implements ICourseUSPService {

    private final CourseUSPRepository courseUSPRepository;
    private final CourseRepository courseRepository;

    @Override
    @Transactional
    public CourseUSPDTO createUSP(UUID courseId, String content, Integer displayOrder, Boolean active) throws BadRequestException {
        if (content == null || content.isBlank()) {
            throw new BadRequestException("USP content is required");
        }

        Course course = courseRepository.findById(courseId)
                .filter(c -> !c.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Course not found with id: " + courseId));

        int order = displayOrder != null ? displayOrder : 0;
        boolean isActive = active != null ? active : true;

        CourseUSP usp = CourseUSP.builder()
                .course(course)
                .content(content)
                .displayOrder(order)
                .active(isActive)
                .build();

        CourseUSP saved = courseUSPRepository.save(usp);
        log.info("Created USP for course {}: {}", course.getCourseName(), saved.getContent());
        return toDto(saved);
    }

    @Override
    @Transactional
    public CourseUSPDTO updateUSP(UUID uspId, String content, Integer displayOrder, Boolean active) {
        CourseUSP usp = courseUSPRepository.findById(uspId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Course USP not found with id: " + uspId));

        if (content != null && !content.isBlank()) {
            usp.setContent(content);
        }
        if (displayOrder != null) {
            usp.setDisplayOrder(displayOrder);
        }
        if (active != null) {
            usp.setActive(active);
        }

        CourseUSP updated = courseUSPRepository.save(usp);
        log.info("Updated USP {}", updated.getId());
        return toDto(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourseUSPDTO> getUSPsByCourseId(UUID courseId, boolean activeOnly) {
        List<CourseUSP> usps = activeOnly ?
                courseUSPRepository.findByCourseIdAndActiveTrueAndIsDeletedFalseOrderByDisplayOrderAsc(courseId) :
                courseUSPRepository.findByCourseIdAndIsDeletedFalseOrderByDisplayOrderAsc(courseId);

        return usps.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public CourseUSPDTO getById(UUID uspId) {
        CourseUSP usp = courseUSPRepository.findById(uspId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Course USP not found with id: " + uspId));
        return toDto(usp);
    }

    @Override
    @Transactional
    public void deleteUSP(UUID uspId) {
        CourseUSP usp = courseUSPRepository.findById(uspId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Course USP not found with id: " + uspId));

        usp.setDeleted(true);
        courseUSPRepository.save(usp);
        log.info("Deleted USP {}", uspId);
    }

    private CourseUSPDTO toDto(CourseUSP usp) {
        if (usp == null) return null;
        return CourseUSPDTO.builder()
                .id(usp.getId())
                .courseId(usp.getCourse().getId())
                .content(usp.getContent())
                .displayOrder(usp.getDisplayOrder())
                .active(usp.isActive())
                .createdAt(usp.getCreatedAt())
                .updatedAt(usp.getUpdatedAt())
                .build();
    }
}
