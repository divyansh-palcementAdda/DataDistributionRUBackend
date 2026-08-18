package com.app.datadistribution.service.impl;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.app.datadistribution.dto.courseimage.CourseImageDTO;
import com.app.datadistribution.entity.Course;
import com.app.datadistribution.entity.CourseImage;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.repository.CourseImageRepository;
import com.app.datadistribution.repository.CourseRepository;
import com.app.datadistribution.service.FileStorageService;
import com.app.datadistribution.service.interfaces.ICourseImageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseImageServiceImpl implements ICourseImageService {

    private final CourseImageRepository courseImageRepository;
    private final CourseRepository courseRepository;
    private final FileStorageService fileStorageService;

    @Override
    @Transactional
    public CourseImageDTO uploadImage(UUID courseId, MultipartFile file, String displayName, Integer displayOrder) throws BadRequestException {
        Course course = courseRepository.findById(courseId)
                .filter(c -> !c.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Course not found with id: " + courseId));

        String imageUrl = fileStorageService.storeFile(file, "courses/" + courseId + "/images");
        String name = (displayName != null && !displayName.isBlank()) ? displayName : file.getOriginalFilename();
        int order = displayOrder != null ? displayOrder : 0;

        CourseImage courseImage = CourseImage.builder()
                .course(course)
                .imageUrl(imageUrl)
                .storageKey(imageUrl)
                .displayName(name)
                .displayOrder(order)
                .active(true)
                .build();

        CourseImage saved = courseImageRepository.save(courseImage);
        log.info("Uploaded course image {} for course {}", saved.getDisplayName(), course.getCourseName());
        return toDto(saved);
    }

    @Override
    @Transactional
    public CourseImageDTO updateImage(UUID imageId, String displayName, Integer displayOrder, Boolean active) {
        CourseImage image = courseImageRepository.findById(imageId)
                .filter(img -> !img.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Course image not found with id: " + imageId));

        if (displayName != null) {
            image.setDisplayName(displayName);
        }
        if (displayOrder != null) {
            image.setDisplayOrder(displayOrder);
        }
        if (active != null) {
            image.setActive(active);
        }

        CourseImage updated = courseImageRepository.save(image);
        log.info("Updated course image {}", updated.getId());
        return toDto(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourseImageDTO> getImagesByCourseId(UUID courseId, boolean activeOnly) {
        List<CourseImage> images = activeOnly ?
                courseImageRepository.findByCourseIdAndActiveTrueAndIsDeletedFalseOrderByDisplayOrderAsc(courseId) :
                courseImageRepository.findByCourseIdAndIsDeletedFalseOrderByDisplayOrderAsc(courseId);

        return images.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public CourseImageDTO getById(UUID imageId) {
        CourseImage image = courseImageRepository.findById(imageId)
                .filter(img -> !img.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Course image not found with id: " + imageId));
        return toDto(image);
    }

    @Override
    @Transactional
    public void deleteImage(UUID imageId) {
        CourseImage image = courseImageRepository.findById(imageId)
                .filter(img -> !img.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Course image not found with id: " + imageId));

        image.setDeleted(true);
        courseImageRepository.save(image);
        fileStorageService.deleteFile(image.getImageUrl());
        log.info("Deleted course image {}", imageId);
    }

    private CourseImageDTO toDto(CourseImage image) {
        if (image == null) return null;
        return CourseImageDTO.builder()
                .id(image.getId())
                .courseId(image.getCourse().getId())
                .imageUrl(image.getImageUrl())
                .displayName(image.getDisplayName())
                .displayOrder(image.getDisplayOrder())
                .active(image.isActive())
                .createdAt(image.getCreatedAt())
                .updatedAt(image.getUpdatedAt())
                .build();
    }
}
