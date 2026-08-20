package com.app.datadistribution.service.impl;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.datadistribution.dto.communication.CourseCommunicationConfigDTO;
import com.app.datadistribution.dto.communication.InfoPanelResponseDTO;
import com.app.datadistribution.dto.communication.SendCommunicationRequestDTO;
import com.app.datadistribution.dto.course.CourseSummaryDTO;
import com.app.datadistribution.dto.courseimage.CourseImageDTO;
import com.app.datadistribution.dto.coursetemplate.CourseTemplateResponseDTO;
import com.app.datadistribution.dto.courseusp.CourseUSPDTO;
import com.app.datadistribution.entity.Course;
import com.app.datadistribution.entity.CourseCommunicationConfig;
import com.app.datadistribution.entity.CourseImage;
import com.app.datadistribution.entity.CourseTemplate;
import com.app.datadistribution.entity.CourseUSP;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadFeedback;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.repository.CourseCommunicationConfigRepository;
import com.app.datadistribution.repository.CourseImageRepository;
import com.app.datadistribution.repository.CourseRepository;
import com.app.datadistribution.repository.CourseTemplateRepository;
import com.app.datadistribution.repository.CourseUSPRepository;
import com.app.datadistribution.repository.LeadFeedbackRepository;
import com.app.datadistribution.repository.LeadRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.PlaceholderRenderService;
import com.app.datadistribution.service.interfaces.ICourseCommunicationService;
import com.app.datadistribution.service.interfaces.ICourseImageService;
import com.app.datadistribution.service.interfaces.ICourseUSPService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseCommunicationServiceImpl implements ICourseCommunicationService {

    private final CourseCommunicationConfigRepository configRepository;
    private final CourseRepository courseRepository;
    private final CourseTemplateRepository templateRepository;
    private final CourseImageRepository imageRepository;
    private final CourseUSPRepository uspRepository;
    private final LeadRepository leadRepository;
    private final LeadFeedbackRepository feedbackRepository;
    private final UserRepository userRepository;
    private final PlaceholderRenderService placeholderRenderService;
    private final ICourseImageService imageService;
    private final ICourseUSPService uspService;

    @Override
    @Transactional(readOnly = true)
    public CourseCommunicationConfigDTO getCommunicationConfig(UUID courseId) {
        Course course = courseRepository.findById(courseId)
                .filter(c -> !c.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Course not found with id: " + courseId));

        CourseCommunicationConfig config = configRepository.findByCourseIdAndIsDeletedFalse(courseId)
                .orElse(null);

        return toDto(config, courseId);
    }

    @Override
    @Transactional
    public CourseCommunicationConfigDTO updateCommunicationConfig(UUID courseId, CourseCommunicationConfigDTO configDTO) throws BadRequestException {
        Course course = courseRepository.findById(courseId)
                .filter(c -> !c.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Course not found with id: " + courseId));

        CourseCommunicationConfig config = configRepository.findByCourseIdAndIsDeletedFalse(courseId)
                .orElseGet(() -> CourseCommunicationConfig.builder().course(course).build());

        // Validate & resolve info panel template/image
        if (configDTO.getInfoPanelTemplateId() != null) {
            CourseTemplate t = templateRepository.findById(configDTO.getInfoPanelTemplateId())
                    .filter(tmpl -> !tmpl.isDeleted() && tmpl.getCourse().getId().equals(courseId))
                    .orElseThrow(() -> new BadRequestException("Info panel template invalid or does not belong to course"));
            config.setInfoPanelTemplate(t);
        } else {
            config.setInfoPanelTemplate(null);
        }

        if (configDTO.getInfoPanelImageId() != null) {
            CourseImage img = imageRepository.findByIdAndCourseIdAndIsDeletedFalse(configDTO.getInfoPanelImageId(), courseId)
                    .orElseThrow(() -> new BadRequestException("Info panel image invalid or does not belong to course"));
            config.setInfoPanelImage(img);
        } else {
            config.setInfoPanelImage(null);
        }

        // Validate & resolve email template/image
        if (configDTO.getEmailTemplateId() != null) {
            CourseTemplate t = templateRepository.findById(configDTO.getEmailTemplateId())
                    .filter(tmpl -> !tmpl.isDeleted() && tmpl.getCourse().getId().equals(courseId))
                    .orElseThrow(() -> new BadRequestException("Email template invalid or does not belong to course"));
            config.setEmailTemplate(t);
        } else {
            config.setEmailTemplate(null);
        }

        if (configDTO.getEmailImageId() != null) {
            CourseImage img = imageRepository.findByIdAndCourseIdAndIsDeletedFalse(configDTO.getEmailImageId(), courseId)
                    .orElseThrow(() -> new BadRequestException("Email image invalid or does not belong to course"));
            config.setEmailImage(img);
        } else {
            config.setEmailImage(null);
        }

        // Validate & resolve whatsapp template/image
        if (configDTO.getWhatsappTemplateId() != null) {
            CourseTemplate t = templateRepository.findById(configDTO.getWhatsappTemplateId())
                    .filter(tmpl -> !tmpl.isDeleted() && tmpl.getCourse().getId().equals(courseId))
                    .orElseThrow(() -> new BadRequestException("WhatsApp template invalid or does not belong to course"));
            config.setWhatsappTemplate(t);
        } else {
            config.setWhatsappTemplate(null);
        }

        if (configDTO.getWhatsappImageId() != null) {
            CourseImage img = imageRepository.findByIdAndCourseIdAndIsDeletedFalse(configDTO.getWhatsappImageId(), courseId)
                    .orElseThrow(() -> new BadRequestException("WhatsApp image invalid or does not belong to course"));
            config.setWhatsappImage(img);
        } else {
            config.setWhatsappImage(null);
        }

        CourseCommunicationConfig saved = configRepository.save(config);
        log.info("Updated channel communication config for course {}", course.getCourseName());
        return toDto(saved, courseId);
    }

    @Override
    @Transactional(readOnly = true)
    public InfoPanelResponseDTO getInfoPanelForLead(UUID leadId, UUID courseId) {
        Lead lead = leadRepository.findById(leadId)
                .filter(l -> !l.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Lead not found with id: " + leadId));

        Course course = null;
        if (courseId != null) {
            course = courseRepository.findById(courseId)
                    .filter(c -> !c.isDeleted())
                    .orElse(null);
        }
        if (course == null && lead.getCourse() != null) {
            course = lead.getCourse();
        }
        if (course == null && lead.getInterestedCourses() != null && !lead.getInterestedCourses().isEmpty()) {
            course = lead.getInterestedCourses().iterator().next();
        }
        if (course == null) {
            throw new ResourcesNotFoundException("No course associated with lead " + leadId);
        }

        List<CourseUSP> usps = uspRepository.findByCourseIdAndActiveTrueAndIsDeletedFalseOrderByDisplayOrderAsc(course.getId());
        List<CourseImage> images = imageRepository.findByCourseIdAndActiveTrueAndIsDeletedFalseOrderByDisplayOrderAsc(course.getId());

        CourseCommunicationConfig config = configRepository.findByCourseIdAndIsDeletedFalse(course.getId()).orElse(null);

        CourseTemplate template = (config != null && config.getInfoPanelTemplate() != null) ? config.getInfoPanelTemplate() : null;
        CourseImage activeImage = (config != null && config.getInfoPanelImage() != null) ? config.getInfoPanelImage() :
                (!images.isEmpty() ? images.get(0) : null);

        User currentUser = null;
        try {
            currentUser = getCurrentUserEntity();
        } catch (Exception ignored) {}

        String rawSubject = template != null ? template.getSubject() : "Course Information: " + course.getCourseName();
        String rawContent = template != null ? template.getContent() : "Explore {{course.name}}.\n\nUSPs:\n{{course.usps}}";

        String renderedSubject = placeholderRenderService.render(rawSubject, lead, course, usps, currentUser);
        String renderedContent = placeholderRenderService.render(rawContent, lead, course, usps, currentUser);

        CourseSummaryDTO courseSummary = CourseSummaryDTO.builder()
                .id(course.getId())
                .courseName(course.getCourseName())
                .courseCode(course.getCourseCode())
                .status(course.getStatus())
                .build();

        List<CourseUSPDTO> uspDtos = uspService.getUSPsByCourseId(course.getId(), true);
        List<CourseImageDTO> imageDtos = imageService.getImagesByCourseId(course.getId(), true);

        return InfoPanelResponseDTO.builder()
                .leadId(lead.getId())
                .leadCode(lead.getLeadCode())
                .leadFullName(lead.getFullName())
                .course(courseSummary)
                .template(template != null ? toTemplateDto(template) : null)
                .activeImage(activeImage != null ? toImageDto(activeImage) : null)
                .renderedSubject(renderedSubject)
                .renderedContent(renderedContent)
                .usps(uspDtos)
                .availableImages(imageDtos)
                .build();
    }

    @Override
    @Transactional
    public void sendCourseEmail(UUID leadId, SendCommunicationRequestDTO request) throws BadRequestException, UnauthorizedException {
        Lead lead = leadRepository.findById(leadId)
                .filter(l -> !l.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Lead not found with id: " + leadId));

        Course course = resolveCourseForLead(lead, request.getCourseId());
        CourseCommunicationConfig config = configRepository.findByCourseIdAndIsDeletedFalse(course.getId()).orElse(null);

        CourseTemplate template = resolveTemplate(request.getTemplateId(), config != null ? config.getEmailTemplate() : null, course.getId());
        CourseImage selectedImage = resolveImage(request.getImageId(), config != null ? config.getEmailImage() : null, course.getId());

        User currentUser = getCurrentUserEntity();
        List<CourseUSP> usps = uspRepository.findByCourseIdAndActiveTrueAndIsDeletedFalseOrderByDisplayOrderAsc(course.getId());

        String subject = placeholderRenderService.render(template.getSubject(), lead, course, usps, currentUser);
        String body = placeholderRenderService.render(template.getContent(), lead, course, usps, currentUser);

        if (request.getCustomMessageOverride() != null && !request.getCustomMessageOverride().isBlank()) {
            body = request.getCustomMessageOverride();
        }

        String recipient = (request.getRecipientOverride() != null && !request.getRecipientOverride().isBlank()) ?
                request.getRecipientOverride() : lead.getEmail();

        if (recipient == null || recipient.isBlank()) {
            throw new BadRequestException("Lead email address is missing");
        }

        String imageInfo = selectedImage != null ? selectedImage.getImageUrl() : "No image attached";
        String feedbackMsg = String.format("Email Sent to '%s' | Subject: '%s' | Image: %s", recipient, subject, imageInfo);

        LeadFeedback feedback = LeadFeedback.builder()
                .lead(lead)
                .feedback(feedbackMsg)
                .statusAtTime(lead.getCurrentStatus())
                .createdByUser(currentUser)
                .build();
        feedbackRepository.save(feedback);

        log.info("Dispatched Email template {} to lead {} ({})", template.getName(), lead.getLeadCode(), recipient);
    }

    @Override
    @Transactional
    public void sendCourseWhatsApp(UUID leadId, SendCommunicationRequestDTO request) throws BadRequestException, UnauthorizedException {
        Lead lead = leadRepository.findById(leadId)
                .filter(l -> !l.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Lead not found with id: " + leadId));

        Course course = resolveCourseForLead(lead, request.getCourseId());
        CourseCommunicationConfig config = configRepository.findByCourseIdAndIsDeletedFalse(course.getId()).orElse(null);

        CourseTemplate template = resolveTemplate(request.getTemplateId(), config != null ? config.getWhatsappTemplate() : null, course.getId());
        CourseImage selectedImage = resolveImage(request.getImageId(), config != null ? config.getWhatsappImage() : null, course.getId());

        User currentUser = getCurrentUserEntity();
        List<CourseUSP> usps = uspRepository.findByCourseIdAndActiveTrueAndIsDeletedFalseOrderByDisplayOrderAsc(course.getId());

        String message = placeholderRenderService.render(template.getContent(), lead, course, usps, currentUser);

        if (request.getCustomMessageOverride() != null && !request.getCustomMessageOverride().isBlank()) {
            message = request.getCustomMessageOverride();
        }

        String recipient = (request.getRecipientOverride() != null && !request.getRecipientOverride().isBlank()) ?
                request.getRecipientOverride() : lead.getPhoneNumber();

        if (recipient == null || recipient.isBlank()) {
            throw new BadRequestException("Lead phone number is missing");
        }

        String imageInfo = selectedImage != null ? selectedImage.getImageUrl() : "No image attached";
        String feedbackMsg = String.format("WhatsApp Sent to '%s' | Message: '%s' | Image: %s", recipient, message, imageInfo);

        LeadFeedback feedback = LeadFeedback.builder()
                .lead(lead)
                .feedback(feedbackMsg)
                .statusAtTime(lead.getCurrentStatus())
                .createdByUser(currentUser)
                .build();
        feedbackRepository.save(feedback);

        log.info("Dispatched WhatsApp template {} to lead {} ({})", template.getName(), lead.getLeadCode(), recipient);
    }

    private Course resolveCourseForLead(Lead lead, UUID requestedCourseId) {
        if (requestedCourseId != null) {
            return courseRepository.findById(requestedCourseId)
                    .filter(c -> !c.isDeleted())
                    .orElseThrow(() -> new ResourcesNotFoundException("Course not found with id: " + requestedCourseId));
        }
        if (lead.getCourse() != null && !lead.getCourse().isDeleted()) {
            return lead.getCourse();
        }
        if (lead.getInterestedCourses() != null && !lead.getInterestedCourses().isEmpty()) {
            return lead.getInterestedCourses().iterator().next();
        }
        throw new ResourcesNotFoundException("No active course associated with lead");
    }

    private CourseTemplate resolveTemplate(UUID requestedTemplateId, CourseTemplate defaultTemplate, UUID courseId) throws BadRequestException {
        if (requestedTemplateId != null) {
            return templateRepository.findById(requestedTemplateId)
                    .filter(t -> !t.isDeleted() && t.getCourse().getId().equals(courseId))
                    .orElseThrow(() -> new BadRequestException("Requested template invalid or does not belong to course"));
        }
        if (defaultTemplate != null && !defaultTemplate.isDeleted()) {
            return defaultTemplate;
        }
        List<CourseTemplate> courseTemplates = templateRepository.findByCourseIdAndIsDeletedFalse(courseId);
        if (!courseTemplates.isEmpty()) {
            return courseTemplates.get(0);
        }
        throw new BadRequestException("No communication template configured for course");
    }

    private CourseImage resolveImage(UUID requestedImageId, CourseImage defaultImage, UUID courseId) throws BadRequestException, UnauthorizedException {
        if (requestedImageId != null) {
            // Verify permission COURSE_TEMPLATE_IMAGE_SELECT
            if (!hasAuthority("COURSE_TEMPLATE_IMAGE_SELECT")) {
                throw new UnauthorizedException("User does not have permission to manually select communication image (COURSE_TEMPLATE_IMAGE_SELECT required)");
            }
            return imageRepository.findByIdAndCourseIdAndIsDeletedFalse(requestedImageId, courseId)
                    .filter(img -> img.isActive())
                    .orElseThrow(() -> new BadRequestException("Requested image invalid, inactive, or does not belong to course"));
        }
        return (defaultImage != null && defaultImage.isActive() && !defaultImage.isDeleted()) ? defaultImage : null;
    }

    private boolean hasAuthority(String authorityName) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        for (GrantedAuthority granted : auth.getAuthorities()) {
            if (granted.getAuthority().equals(authorityName) || granted.getAuthority().equals("SUPER_ADMIN")) {
                return true;
            }
        }
        return false;
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

    private CourseCommunicationConfigDTO toDto(CourseCommunicationConfig config, UUID courseId) {
        if (config == null) {
            return CourseCommunicationConfigDTO.builder()
                    .courseId(courseId)
                    .build();
        }
        return CourseCommunicationConfigDTO.builder()
                .id(config.getId())
                .courseId(courseId)
                .infoPanelTemplate(config.getInfoPanelTemplate() != null ? toTemplateDto(config.getInfoPanelTemplate()) : null)
                .emailTemplate(config.getEmailTemplate() != null ? toTemplateDto(config.getEmailTemplate()) : null)
                .whatsappTemplate(config.getWhatsappTemplate() != null ? toTemplateDto(config.getWhatsappTemplate()) : null)
                .infoPanelImage(config.getInfoPanelImage() != null ? toImageDto(config.getInfoPanelImage()) : null)
                .emailImage(config.getEmailImage() != null ? toImageDto(config.getEmailImage()) : null)
                .whatsappImage(config.getWhatsappImage() != null ? toImageDto(config.getWhatsappImage()) : null)
                .infoPanelTemplateId(config.getInfoPanelTemplate() != null ? config.getInfoPanelTemplate().getId() : null)
                .emailTemplateId(config.getEmailTemplate() != null ? config.getEmailTemplate().getId() : null)
                .whatsappTemplateId(config.getWhatsappTemplate() != null ? config.getWhatsappTemplate().getId() : null)
                .infoPanelImageId(config.getInfoPanelImage() != null ? config.getInfoPanelImage().getId() : null)
                .emailImageId(config.getEmailImage() != null ? config.getEmailImage().getId() : null)
                .whatsappImageId(config.getWhatsappImage() != null ? config.getWhatsappImage().getId() : null)
                .build();
    }

    private CourseTemplateResponseDTO toTemplateDto(CourseTemplate template) {
        if (template == null) return null;
        return CourseTemplateResponseDTO.builder()
                .id(template.getId())
                .name(template.getName())
                .subject(template.getSubject())
                .content(template.getContent())
                .channel(template.getChannel())
                .active(template.isActive())
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .build();
    }

    private CourseImageDTO toImageDto(CourseImage image) {
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
