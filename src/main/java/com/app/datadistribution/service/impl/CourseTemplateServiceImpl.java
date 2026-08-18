package com.app.datadistribution.service.impl;

import com.app.datadistribution.dto.course.CourseSummaryDTO;
import com.app.datadistribution.dto.course.CourseTypeResponseDTO;
import com.app.datadistribution.dto.coursetemplate.CourseTemplateRequestDTO;
import com.app.datadistribution.dto.coursetemplate.CourseTemplateResponseDTO;
import com.app.datadistribution.entity.Course;
import com.app.datadistribution.entity.CourseTemplate;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadFeedback;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.repository.CourseRepository;
import com.app.datadistribution.repository.CourseTemplateRepository;
import com.app.datadistribution.repository.LeadFeedbackRepository;
import com.app.datadistribution.repository.LeadRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.interfaces.ICourseTemplateService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseTemplateServiceImpl implements ICourseTemplateService {

    private final CourseTemplateRepository courseTemplateRepository;
    private final CourseRepository courseRepository;
    private final LeadRepository leadRepository;
    private final LeadFeedbackRepository leadFeedbackRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public CourseTemplateResponseDTO create(CourseTemplateRequestDTO request) throws BadRequestException {
        Course course = courseRepository.findById(request.getCourseId())
                .filter(c -> !c.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Course not found with id: " + request.getCourseId()));

        CourseTemplate template = CourseTemplate.builder()
                .name(request.getName())
                .subject(request.getSubject())
                .content(request.getContent())
                .channel(request.getChannel())
                .course(course)
                .active(request.isActive())
                .build();

        CourseTemplate saved = courseTemplateRepository.save(template);
        log.info("Created course template: {} for course: {}", saved.getName(), course.getCourseName());
        return toDto(saved);
    }

    @Override
    @Transactional
    public CourseTemplateResponseDTO update(UUID id, CourseTemplateRequestDTO request) throws BadRequestException {
        CourseTemplate template = courseTemplateRepository.findById(id)
                .filter(t -> !t.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Course template not found with id: " + id));

        Course course = courseRepository.findById(request.getCourseId())
                .filter(c -> !c.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Course not found with id: " + request.getCourseId()));

        template.setName(request.getName());
        template.setSubject(request.getSubject());
        template.setContent(request.getContent());
        template.setChannel(request.getChannel());
        template.setCourse(course);
        template.setActive(request.isActive());

        CourseTemplate updated = courseTemplateRepository.save(template);
        log.info("Updated course template: {}", updated.getName());
        return toDto(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public CourseTemplateResponseDTO getById(UUID id) {
        CourseTemplate template = courseTemplateRepository.findById(id)
                .filter(t -> !t.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Course template not found with id: " + id));
        return toDto(template);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourseTemplateResponseDTO> getTemplatesByCourseId(UUID courseId) {
        return courseTemplateRepository.findByCourseIdAndIsDeletedFalse(courseId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourseTemplateResponseDTO> getAllTemplates() {
        return courseTemplateRepository.findByIsDeletedFalse().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteTemplate(UUID id) {
        CourseTemplate template = courseTemplateRepository.findById(id)
                .filter(t -> !t.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Course template not found with id: " + id));
        template.setDeleted(true);
        courseTemplateRepository.save(template);
        log.info("Deleted course template: {}", template.getName());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourseTemplateResponseDTO> getTemplatesForLead(UUID leadId) {
        Lead lead = leadRepository.findById(leadId)
                .filter(l -> !l.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Lead not found with id: " + leadId));

        Set<UUID> courseIds = new HashSet<>();
        if (lead.getCourse() != null && !lead.getCourse().isDeleted()) {
            courseIds.add(lead.getCourse().getId());
        }
        if (lead.getInterestedCourses() != null) {
            for (Course c : lead.getInterestedCourses()) {
                if (c != null && !c.isDeleted()) {
                    courseIds.add(c.getId());
                }
            }
        }

        if (courseIds.isEmpty()) {
            return new ArrayList<>();
        }

        return courseTemplateRepository.findByCourseIdInAndIsDeletedFalse(new ArrayList<>(courseIds)).stream()
                .filter(CourseTemplate::isActive)
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void sendTemplateToLead(UUID leadId, UUID templateId) throws UnauthorizedException {
        Lead lead = leadRepository.findById(leadId)
                .filter(l -> !l.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Lead not found with id: " + leadId));

        CourseTemplate template = courseTemplateRepository.findById(templateId)
                .filter(t -> !t.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Course template not found with id: " + templateId));

        User currentUser = getCurrentUserEntity();

        String feedbackText = String.format("Template sent: '%s' [%s] for course '%s'.",
                template.getName(), template.getChannel(), template.getCourse().getCourseName());

        LeadFeedback feedback = LeadFeedback.builder()
                .lead(lead)
                .feedback(feedbackText)
                .statusAtTime(lead.getCurrentStatus())
                .createdByUser(currentUser)
                .build();
        leadFeedbackRepository.save(feedback);

        log.info("Sent template {} to lead {} by user {}", template.getName(), lead.getLeadCode(), currentUser.getUsername());
    }

    private User getCurrentUserEntity() throws UnauthorizedException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new UnauthorizedException("User is not authenticated");
        }
        String username = auth.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourcesNotFoundException("User not found with username: " + username));
    }

    private CourseTemplateResponseDTO toDto(CourseTemplate template) {
        if (template == null) return null;

        CourseSummaryDTO courseSummary = null;
        if (template.getCourse() != null) {
            CourseTypeResponseDTO typeDto = null;
            if (template.getCourse().getCourseType() != null) {
                typeDto = CourseTypeResponseDTO.builder()
                        .id(template.getCourse().getCourseType().getId())
                        .name(template.getCourse().getCourseType().getName())
                        .description(template.getCourse().getCourseType().getDescription())
                        .status(template.getCourse().getCourseType().getStatus())
                        .createdAt(template.getCourse().getCourseType().getCreatedAt())
                        .updatedAt(template.getCourse().getCourseType().getUpdatedAt())
                        .build();
            }
            courseSummary = CourseSummaryDTO.builder()
                    .id(template.getCourse().getId())
                    .courseName(template.getCourse().getCourseName())
                    .courseCode(template.getCourse().getCourseCode())
                    .status(template.getCourse().getStatus())
                    .courseType(typeDto)
                    .build();
        }

        return CourseTemplateResponseDTO.builder()
                .id(template.getId())
                .name(template.getName())
                .subject(template.getSubject())
                .content(template.getContent())
                .channel(template.getChannel())
                .course(courseSummary)
                .active(template.isActive())
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .build();
    }
}
