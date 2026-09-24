package com.app.datadistribution.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.datadistribution.dto.course.CourseSummaryDTO;
import com.app.datadistribution.dto.infopanel.CompetitorComparisonDTO;
import com.app.datadistribution.dto.infopanel.CompetitorRequestDTO;
import com.app.datadistribution.dto.infopanel.CompetitorResponseDTO;
import com.app.datadistribution.dto.infopanel.CourseInfoPanelRequestDTO;
import com.app.datadistribution.dto.infopanel.CourseInfoPanelResponseDTO;
import com.app.datadistribution.dto.infopanel.InfoPanelPermissionMatrixDTO;
import com.app.datadistribution.dto.infopanel.LeadCallerGuidanceResponseDTO;
import com.app.datadistribution.entity.CompetitorCourseComparison;
import com.app.datadistribution.entity.Course;
import com.app.datadistribution.entity.CourseInfoPanel;
import com.app.datadistribution.entity.CourseInfoPanelCompetitor;
import com.app.datadistribution.entity.CourseInfoPanelCompetitorBranch;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.repository.CompetitorCourseComparisonRepository;
import com.app.datadistribution.repository.CourseInfoPanelCompetitorBranchRepository;
import com.app.datadistribution.repository.CourseInfoPanelCompetitorRepository;
import com.app.datadistribution.repository.CourseInfoPanelRepository;
import com.app.datadistribution.repository.CourseRepository;
import com.app.datadistribution.repository.LeadRepository;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.service.interfaces.ICourseInfoPanelService;
import com.app.datadistribution.service.interfaces.IInfoPanelSecurityService;
import com.app.datadistribution.service.interfaces.ILeadDataScopeService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CourseInfoPanelServiceImpl implements ICourseInfoPanelService {

    private final CourseInfoPanelRepository infoPanelRepository;
    private final CourseInfoPanelCompetitorRepository competitorRepository;
    private final CourseInfoPanelCompetitorBranchRepository branchRepository;
    private final CompetitorCourseComparisonRepository comparisonRepository;
    private final CourseRepository courseRepository;
    private final LeadRepository leadRepository;
    private final IInfoPanelSecurityService securityService;
    private final ILeadDataScopeService leadDataScopeService;

    @Override
    @Transactional(readOnly = true)
    public CourseInfoPanelResponseDTO getInfoPanelByCourseId(UUID courseId, String academicSession) {
        Course course = courseRepository.findById(courseId)
                .filter(c -> !c.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Course not found with id: " + courseId));

        String session = (academicSession != null && !academicSession.isBlank()) ? academicSession.trim() : "2026-27";

        CourseInfoPanel panel = infoPanelRepository.findByCourseIdAndAcademicSessionAndIsDeletedFalse(courseId, session)
                .orElseGet(() -> infoPanelRepository.findFirstByCourseIdAndIsDeletedFalseOrderByCreatedAtDesc(courseId).orElse(null));

        if (panel == null) {
            // Return virtual starter representation based on Course master
            CourseInfoPanelResponseDTO fallback = CourseInfoPanelResponseDTO.builder()
                    .id(null)
                    .course(toCourseSummary(course))
                    .academicSession(session)
                    .school(course.getCourseType() != null ? course.getCourseType().getName() : "")
                    .courseName(course.getCourseName())
                    .courseFee(course.getFees() != null ? ("₹ " + String.format("%,.0f", course.getFees())) : "")
                    .duration(course.getDuration() != null ? (course.getDuration() + " " + (course.getDurationUnit() != null ? course.getDurationUnit() : "Years")) : "")
                    .eligibility("10+2 with minimum 50% from a recognized board")
                    .jobOpportunities("")
                    .hostelFee("")
                    .courseDetails(course.getDescription() != null ? course.getDescription() : "")
                    .courseSpecialities("")
                    .renaissanceUniversityUsps("30-acre modern campus with state-of-the-art infrastructure\n100% dedicated placement & internship cell\n250+ top recruiters and corporate tie-ups")
                    .howWeAreDifferent("Experiential industry immersion over pure classroom theory\n1-on-1 career counseling and professional development mentorship")
                    .callerGuidance("Establish rapport with the lead.\nAsk about their career aspirations and target domains.\nHighlight Renaissance University placement track record and campus facilities.")
                    .active(true)
                    .competitors(Collections.emptyList())
                    .build();
            securityService.sanitizeResponse(fallback);
            return fallback;
        }

        CourseInfoPanelResponseDTO response = toResponseDTO(panel);
        securityService.sanitizeResponse(response);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public LeadCallerGuidanceResponseDTO getCallerGuidanceForLead(UUID leadId, UUID courseId) {
        Lead lead = leadRepository.findById(leadId)
                .filter(l -> !l.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Lead not found with id: " + leadId));

        try {
            UserDataScope dataScope = leadDataScopeService.getCurrentUserScope();
            leadDataScopeService.validateLeadReadAccess(lead, dataScope);
        } catch (Exception e) {
            log.warn("Lead access check warning for caller guidance lead {}: {}", leadId, e.getMessage());
        }

        // Collect interested courses
        List<CourseSummaryDTO> interestedCourses = new ArrayList<>();
        Course primaryCourse = lead.getCourse();
        if (primaryCourse != null && !primaryCourse.isDeleted()) {
            interestedCourses.add(toCourseSummary(primaryCourse));
        }

        if (lead.getInterestedCourses() != null) {
            for (Course ic : lead.getInterestedCourses()) {
                if (ic != null && !ic.isDeleted() && interestedCourses.stream().noneMatch(c -> c.getId().equals(ic.getId()))) {
                    interestedCourses.add(toCourseSummary(ic));
                }
            }
        }

        // Determine active course to display
        UUID targetCourseId = courseId;
        if (targetCourseId == null) {
            if (primaryCourse != null && !primaryCourse.isDeleted()) {
                targetCourseId = primaryCourse.getId();
            } else if (!interestedCourses.isEmpty()) {
                targetCourseId = interestedCourses.get(0).getId();
            }
        }

        CourseInfoPanelResponseDTO infoPanelResponse = null;
        if (targetCourseId != null) {
            infoPanelResponse = getInfoPanelByCourseId(targetCourseId, null);
        }

        return LeadCallerGuidanceResponseDTO.builder()
                .leadId(lead.getId())
                .leadCode(lead.getLeadCode())
                .leadFullName(lead.getFullName())
                .primaryCourse(primaryCourse != null ? toCourseSummary(primaryCourse) : null)
                .interestedCourses(interestedCourses)
                .activeCourseId(targetCourseId)
                .infoPanel(infoPanelResponse)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CourseInfoPanelResponseDTO getInfoPanelById(UUID id) {
        CourseInfoPanel panel = infoPanelRepository.findById(id)
                .filter(p -> !p.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Info panel not found with id: " + id));

        CourseInfoPanelResponseDTO response = toResponseDTO(panel);
        securityService.sanitizeResponse(response);
        return response;
    }

    @Override
    @Transactional
    public CourseInfoPanelResponseDTO createInfoPanel(CourseInfoPanelRequestDTO request) throws BadRequestException, UnauthorizedException {
        if (request == null || request.getCourseId() == null) {
            throw new BadRequestException("Course ID is required to create Info Panel");
        }

        Course course = courseRepository.findById(request.getCourseId())
                .filter(c -> !c.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Course not found with id: " + request.getCourseId()));

        String session = (request.getAcademicSession() != null && !request.getAcademicSession().isBlank())
                ? request.getAcademicSession().trim()
                : "2026-27";

        // Check duplicate
        if (infoPanelRepository.existsByCourseIdAndAcademicSessionAndIsDeletedFalse(course.getId(), session)) {
            throw new BadRequestException("An Info Panel for course '" + course.getCourseName() + "' and session '" + session + "' already exists. Please update it instead.");
        }

        securityService.validateFieldUpdates(null, request);

        CourseInfoPanel panel = CourseInfoPanel.builder()
                .course(course)
                .academicSession(session)
                .school(request.getSchool() != null ? request.getSchool().trim() : (course.getCourseType() != null ? course.getCourseType().getName() : ""))
                .courseName(request.getCourseName() != null ? request.getCourseName().trim() : course.getCourseName())
                .courseFee(request.getCourseFee() != null ? request.getCourseFee().trim() : (course.getFees() != null ? "₹ " + String.format("%,.0f", course.getFees()) : ""))
                .duration(request.getDuration() != null ? request.getDuration().trim() : (course.getDuration() != null ? course.getDuration() + " " + (course.getDurationUnit() != null ? course.getDurationUnit() : "Years") : ""))
                .eligibility(request.getEligibility())
                .jobOpportunities(request.getJobOpportunities())
                .hostelFee(request.getHostelFee())
                .courseDetails(request.getCourseDetails())
                .courseSpecialities(request.getCourseSpecialities())
                .renaissanceUniversityUsps(request.getRenaissanceUniversityUsps())
                .howWeAreDifferent(request.getHowWeAreDifferent())
                .callerGuidance(request.getCallerGuidance())
                .active(request.getActive() != null ? request.getActive() : true)
                .build();

        CourseInfoPanel savedPanel = infoPanelRepository.save(panel);

        // Save competitors if provided in request
        if (request.getCompetitors() != null && !request.getCompetitors().isEmpty()) {
            for (CompetitorRequestDTO compReq : request.getCompetitors()) {
                saveCompetitor(savedPanel, compReq);
            }
        }

        return getInfoPanelById(savedPanel.getId());
    }

    @Override
    @Transactional
    public CourseInfoPanelResponseDTO updateInfoPanel(UUID id, CourseInfoPanelRequestDTO request) throws UnauthorizedException {
        CourseInfoPanel existing = infoPanelRepository.findById(id)
                .filter(p -> !p.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Info panel not found with id: " + id));

        securityService.validateFieldUpdates(existing, request);

        if (request.getAcademicSession() != null && !request.getAcademicSession().isBlank()) {
            existing.setAcademicSession(request.getAcademicSession().trim());
        }
        if (request.getSchool() != null) existing.setSchool(request.getSchool().trim());
        if (request.getCourseName() != null) existing.setCourseName(request.getCourseName().trim());
        if (request.getCourseFee() != null) existing.setCourseFee(request.getCourseFee().trim());
        if (request.getDuration() != null) existing.setDuration(request.getDuration().trim());
        if (request.getEligibility() != null) existing.setEligibility(request.getEligibility());
        if (request.getJobOpportunities() != null) existing.setJobOpportunities(request.getJobOpportunities());
        if (request.getHostelFee() != null) existing.setHostelFee(request.getHostelFee());
        if (request.getCourseDetails() != null) existing.setCourseDetails(request.getCourseDetails());
        if (request.getCourseSpecialities() != null) existing.setCourseSpecialities(request.getCourseSpecialities());
        if (request.getRenaissanceUniversityUsps() != null) existing.setRenaissanceUniversityUsps(request.getRenaissanceUniversityUsps());
        if (request.getHowWeAreDifferent() != null) existing.setHowWeAreDifferent(request.getHowWeAreDifferent());
        if (request.getCallerGuidance() != null) existing.setCallerGuidance(request.getCallerGuidance());
        if (request.getActive() != null) existing.setActive(request.getActive());

        infoPanelRepository.save(existing);
        return getInfoPanelById(existing.getId());
    }

    @Override
    @Transactional
    public void deleteInfoPanel(UUID id) {
        CourseInfoPanel panel = infoPanelRepository.findById(id)
                .filter(p -> !p.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Info panel not found with id: " + id));
        panel.setDeleted(true);
        infoPanelRepository.save(panel);
    }

    @Override
    @Transactional
    public CompetitorResponseDTO addCompetitor(UUID infoPanelId, CompetitorRequestDTO request) throws BadRequestException {
        CourseInfoPanel panel = infoPanelRepository.findById(infoPanelId)
                .filter(p -> !p.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Info panel not found with id: " + infoPanelId));

        CourseInfoPanelCompetitor saved = saveCompetitor(panel, request);
        return toCompetitorDTO(saved);
    }

    @Override
    @Transactional
    public CompetitorResponseDTO updateCompetitor(UUID infoPanelId, UUID competitorId, CompetitorRequestDTO request) {
        CourseInfoPanelCompetitor comp = competitorRepository.findByIdAndInfoPanelIdAndIsDeletedFalse(competitorId, infoPanelId)
                .orElseThrow(() -> new ResourcesNotFoundException("Competitor not found with id: " + competitorId));

        if (request.getCollegeName() != null && !request.getCollegeName().isBlank()) {
            comp.setCollegeName(request.getCollegeName().trim());
        }
        if (request.getDisplayOrder() != null) {
            comp.setDisplayOrder(request.getDisplayOrder());
        }
        if (request.getActive() != null) {
            comp.setActive(request.getActive());
        }

        competitorRepository.save(comp);

        // Update branches
        if (request.getBranches() != null) {
            branchRepository.deleteByCompetitorId(comp.getId());
            for (String bName : request.getBranches()) {
                if (bName != null && !bName.trim().isEmpty()) {
                    branchRepository.save(CourseInfoPanelCompetitorBranch.builder()
                            .competitor(comp)
                            .branchName(bName.trim())
                            .build());
                }
            }
        }

        // Update comparison
        if (request.getComparison() != null) {
            CompetitorComparisonDTO cDto = request.getComparison();
            CompetitorCourseComparison compData = comparisonRepository.findByCompetitorIdAndIsDeletedFalse(comp.getId())
                    .orElseGet(() -> CompetitorCourseComparison.builder().competitor(comp).build());

            compData.setCourseFeePerYear(cDto.getCourseFeePerYear());
            compData.setDuration(cDto.getDuration());
            compData.setOdds(cDto.getOdds());
            compData.setEligibility(cDto.getEligibility());
            compData.setHostel(cDto.getHostel());
            compData.setDistanceFromCity(cDto.getDistanceFromCity());
            compData.setRegistrationFee(cDto.getRegistrationFee());
            compData.setAveragePlacements(cDto.getAveragePlacements());
            compData.setHighestPlacement(cDto.getHighestPlacement());
            comparisonRepository.save(compData);
        }

        CourseInfoPanelCompetitor refreshed = competitorRepository.findById(comp.getId()).orElse(comp);
        return toCompetitorDTO(refreshed);
    }

    @Override
    @Transactional
    public void deleteCompetitor(UUID infoPanelId, UUID competitorId) {
        CourseInfoPanelCompetitor comp = competitorRepository.findByIdAndInfoPanelIdAndIsDeletedFalse(competitorId, infoPanelId)
                .orElseThrow(() -> new ResourcesNotFoundException("Competitor not found with id: " + competitorId));
        comp.setDeleted(true);
        competitorRepository.save(comp);
    }

    @Override
    @Transactional
    public void reorderCompetitors(UUID infoPanelId, List<UUID> competitorIds) {
        if (competitorIds == null || competitorIds.isEmpty()) return;
        List<CourseInfoPanelCompetitor> list = competitorRepository.findByInfoPanelIdAndIsDeletedFalseOrderByDisplayOrderAsc(infoPanelId);
        for (int i = 0; i < competitorIds.size(); i++) {
            UUID targetId = competitorIds.get(i);
            final int order = i;
            list.stream().filter(c -> c.getId().equals(targetId)).findFirst().ifPresent(c -> {
                c.setDisplayOrder(order);
                competitorRepository.save(c);
            });
        }
    }

    @Override
    public InfoPanelPermissionMatrixDTO getPermissionMatrix() {
        return securityService.getPermissionMatrix();
    }

    private CourseInfoPanelCompetitor saveCompetitor(CourseInfoPanel panel, CompetitorRequestDTO compReq) throws BadRequestException {
        if (compReq == null || compReq.getCollegeName() == null || compReq.getCollegeName().isBlank()) {
            throw new BadRequestException("Competitor college name is required");
        }

        CourseInfoPanelCompetitor competitor = CourseInfoPanelCompetitor.builder()
                .infoPanel(panel)
                .collegeName(compReq.getCollegeName().trim())
                .displayOrder(compReq.getDisplayOrder() != null ? compReq.getDisplayOrder() : 0)
                .active(compReq.getActive() != null ? compReq.getActive() : true)
                .build();

        CourseInfoPanelCompetitor savedComp = competitorRepository.save(competitor);

        if (compReq.getBranches() != null) {
            for (String b : compReq.getBranches()) {
                if (b != null && !b.trim().isEmpty()) {
                    branchRepository.save(CourseInfoPanelCompetitorBranch.builder()
                            .competitor(savedComp)
                            .branchName(b.trim())
                            .build());
                }
            }
        }

        if (compReq.getComparison() != null) {
            CompetitorComparisonDTO cDto = compReq.getComparison();
            CompetitorCourseComparison compData = CompetitorCourseComparison.builder()
                    .competitor(savedComp)
                    .courseFeePerYear(cDto.getCourseFeePerYear())
                    .duration(cDto.getDuration())
                    .odds(cDto.getOdds())
                    .eligibility(cDto.getEligibility())
                    .hostel(cDto.getHostel())
                    .distanceFromCity(cDto.getDistanceFromCity())
                    .registrationFee(cDto.getRegistrationFee())
                    .averagePlacements(cDto.getAveragePlacements())
                    .highestPlacement(cDto.getHighestPlacement())
                    .build();
            comparisonRepository.save(compData);
        }

        return competitorRepository.findById(savedComp.getId()).orElse(savedComp);
    }

    private CourseInfoPanelResponseDTO toResponseDTO(CourseInfoPanel panel) {
        List<CourseInfoPanelCompetitor> comps = competitorRepository.findByInfoPanelIdAndIsDeletedFalseOrderByDisplayOrderAsc(panel.getId());
        List<CompetitorResponseDTO> compDtos = comps.stream().map(this::toCompetitorDTO).collect(Collectors.toList());

        return CourseInfoPanelResponseDTO.builder()
                .id(panel.getId())
                .course(panel.getCourse() != null ? toCourseSummary(panel.getCourse()) : null)
                .academicSession(panel.getAcademicSession())
                .school(panel.getSchool())
                .courseName(panel.getCourseName())
                .courseFee(panel.getCourseFee())
                .duration(panel.getDuration())
                .eligibility(panel.getEligibility())
                .jobOpportunities(panel.getJobOpportunities())
                .hostelFee(panel.getHostelFee())
                .courseDetails(panel.getCourseDetails())
                .courseSpecialities(panel.getCourseSpecialities())
                .renaissanceUniversityUsps(panel.getRenaissanceUniversityUsps())
                .howWeAreDifferent(panel.getHowWeAreDifferent())
                .callerGuidance(panel.getCallerGuidance())
                .active(panel.isActive())
                .competitors(compDtos)
                .createdAt(panel.getCreatedAt())
                .updatedAt(panel.getUpdatedAt())
                .build();
    }

    private CompetitorResponseDTO toCompetitorDTO(CourseInfoPanelCompetitor comp) {
        List<String> branches = branchRepository.findByCompetitorIdAndIsDeletedFalse(comp.getId()).stream()
                .map(CourseInfoPanelCompetitorBranch::getBranchName)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        CompetitorCourseComparison compData = comparisonRepository.findByCompetitorIdAndIsDeletedFalse(comp.getId()).orElse(null);
        CompetitorComparisonDTO compDataDTO = compData != null ? CompetitorComparisonDTO.builder()
                .courseFeePerYear(compData.getCourseFeePerYear())
                .duration(compData.getDuration())
                .odds(compData.getOdds())
                .eligibility(compData.getEligibility())
                .hostel(compData.getHostel())
                .distanceFromCity(compData.getDistanceFromCity())
                .registrationFee(compData.getRegistrationFee())
                .averagePlacements(compData.getAveragePlacements())
                .highestPlacement(compData.getHighestPlacement())
                .build() : new CompetitorComparisonDTO();

        return CompetitorResponseDTO.builder()
                .id(comp.getId())
                .collegeName(comp.getCollegeName())
                .displayOrder(comp.getDisplayOrder())
                .active(comp.isActive())
                .branches(branches)
                .comparison(compDataDTO)
                .createdAt(comp.getCreatedAt())
                .updatedAt(comp.getUpdatedAt())
                .build();
    }

    private CourseSummaryDTO toCourseSummary(Course c) {
        if (c == null) return null;
        return CourseSummaryDTO.builder()
                .id(c.getId())
                .courseName(c.getCourseName())
                .courseCode(c.getCourseCode())
                .status(c.getStatus())
                .build();
    }
}
