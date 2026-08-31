package com.app.datadistribution.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.app.datadistribution.dto.communication.InfoPanelResponseDTO;
import com.app.datadistribution.dto.communication.WhatsAppPreviewRequestDTO;
import com.app.datadistribution.dto.communication.WhatsAppPreviewResponseDTO;
import com.app.datadistribution.dto.lead.LeadCoursesUpdateRequest;
import com.app.datadistribution.dto.lead.LeadResponse;
import com.app.datadistribution.entity.Course;
import com.app.datadistribution.entity.CourseCommunicationConfig;
import com.app.datadistribution.entity.CourseImage;
import com.app.datadistribution.entity.CourseTemplate;
import com.app.datadistribution.entity.CourseUSP;
import com.app.datadistribution.entity.Department;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.mapper.LeadMapper;
import com.app.datadistribution.mapper.UserMapper;
import com.app.datadistribution.repository.CourseCommunicationConfigRepository;
import com.app.datadistribution.repository.CourseImageRepository;
import com.app.datadistribution.repository.CourseRepository;
import com.app.datadistribution.repository.CourseTemplateRepository;
import com.app.datadistribution.repository.CourseUSPRepository;
import com.app.datadistribution.repository.LeadRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.service.dto.UserDataScope.ScopeType;
import com.app.datadistribution.service.impl.CourseCommunicationServiceImpl;
import com.app.datadistribution.service.impl.LeadServiceImpl;
import com.app.datadistribution.service.interfaces.ICourseImageService;
import com.app.datadistribution.service.interfaces.ICourseUSPService;
import com.app.datadistribution.service.interfaces.ILeadDataScopeService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LeadInfoPanelAndWhatsAppTest {

    @Mock
    private LeadRepository leadRepository;
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private CourseUSPRepository uspRepository;
    @Mock
    private CourseTemplateRepository templateRepository;
    @Mock
    private CourseImageRepository imageRepository;
    @Mock
    private CourseCommunicationConfigRepository configRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ILeadDataScopeService leadDataScopeService;
    @Mock
    private ICourseUSPService uspService;
    @Mock
    private ICourseImageService imageService;
    @Mock
    private com.app.datadistribution.repository.LeadFeedbackRepository feedbackRepository;
    @Mock
    private LeadMapper leadMapper;
    @Mock
    private UserMapper userMapper;

    @Spy
    private PlaceholderRenderService placeholderRenderService = new PlaceholderRenderService();

    @InjectMocks
    private CourseCommunicationServiceImpl communicationService;

    @InjectMocks
    private LeadServiceImpl leadService;

    private User counselor;
    private Department dept;
    private Lead lead;
    private Course courseMba;
    private Course courseBba;
    private Course courseBca;
    private CourseUSP uspPlacement;
    private CourseUSP uspFaculty;
    private CourseTemplate waTemplate;
    private CourseImage courseImage;

    @BeforeEach
    void setUp() throws Exception {
        dept = Department.builder().name("Management").code("MGMT").active(true).build();
        dept.setId(UUID.randomUUID());

        counselor = User.builder().username("counselor1").firstName("Ravi").lastName("Sharma").active(true).build();
        counselor.setId(UUID.randomUUID());

        courseMba = Course.builder().courseName("MBA").courseCode("MBA-01").status(com.app.datadistribution.enums.Status.ACTIVE).build();
        courseMba.setId(UUID.randomUUID());

        courseBba = Course.builder().courseName("BBA").courseCode("BBA-01").status(com.app.datadistribution.enums.Status.ACTIVE).build();
        courseBba.setId(UUID.randomUUID());

        courseBca = Course.builder().courseName("BCA").courseCode("BCA-01").status(com.app.datadistribution.enums.Status.ACTIVE).build();
        courseBca.setId(UUID.randomUUID());

        uspPlacement = CourseUSP.builder().course(courseMba).content("100% Placement Assistance with top MNCs").active(true).displayOrder(1).build();
        uspPlacement.setId(UUID.randomUUID());

        uspFaculty = CourseUSP.builder().course(courseBba).content("IIM Alumni Faculty").active(true).displayOrder(1).build();
        uspFaculty.setId(UUID.randomUUID());

        waTemplate = CourseTemplate.builder()
                .course(courseMba)
                .name("MBA WhatsApp Outreach")
                .subject("MBA Admissions 2026")
                .channel("WHATSAPP")
                .content("Dear {{lead.name}}, explore {{course.name}} at {{institution.name}}.\n\nFeature: {{usp.content}}\n\nCounsellor: {{counsellor.name}}")
                .active(true)
                .build();
        waTemplate.setId(UUID.randomUUID());

        courseImage = CourseImage.builder()
                .course(courseMba)
                .imageUrl("https://cdn.example.com/mba-banner.jpg")
                .displayName("MBA Banner")
                .active(true)
                .build();
        courseImage.setId(UUID.randomUUID());

        lead = Lead.builder()
                .leadCode("LEAD-9001")
                .fullName("Megha Patel")
                .phoneNumber("9876543210")
                .assignedTo(counselor)
                .department(dept)
                .course(courseMba)
                .interestedCourses(new HashSet<>(Set.of(courseMba, courseBba, courseBca)))
                .build();
        lead.setId(UUID.randomUUID());

        when(leadRepository.findById(lead.getId())).thenReturn(Optional.of(lead));
        when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> inv.getArgument(0));

        when(courseRepository.findById(courseMba.getId())).thenReturn(Optional.of(courseMba));
        when(courseRepository.findById(courseBba.getId())).thenReturn(Optional.of(courseBba));
        when(courseRepository.findById(courseBca.getId())).thenReturn(Optional.of(courseBca));

        when(uspRepository.findByIdAndCourseIdAndIsDeletedFalse(uspPlacement.getId(), courseMba.getId())).thenReturn(Optional.of(uspPlacement));
        when(uspRepository.findByIdAndCourseIdAndIsDeletedFalse(uspFaculty.getId(), courseBba.getId())).thenReturn(Optional.of(uspFaculty));
        when(uspRepository.findByIdAndCourseIdAndIsDeletedFalse(uspFaculty.getId(), courseMba.getId())).thenReturn(Optional.empty());

        when(templateRepository.findByIdAndCourseIdAndIsDeletedFalse(waTemplate.getId(), courseMba.getId())).thenReturn(Optional.of(waTemplate));
        when(templateRepository.findByCourseIdAndChannelIgnoreCaseAndActiveTrueAndIsDeletedFalse(courseMba.getId(), "WHATSAPP"))
                .thenReturn(List.of(waTemplate));

        when(imageRepository.findByCourseIdAndActiveTrueAndIsDeletedFalseOrderByDisplayOrderAsc(courseMba.getId()))
                .thenReturn(List.of(courseImage));

        UserDataScope scope = UserDataScope.builder().scopeType(ScopeType.SYSTEM).isAdmin(true).userId(counselor.getId()).build();
        when(leadDataScopeService.getCurrentUserScope()).thenReturn(scope);

        when(userRepository.findByUsername(counselor.getUsername())).thenReturn(Optional.of(counselor));

        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn(counselor.getUsername());
        when(auth.getPrincipal()).thenReturn(counselor.getUsername());
        SecurityContext sc = mock(SecurityContext.class);
        when(sc.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(sc);

        when(leadMapper.toDto(any(Lead.class))).thenReturn(com.app.datadistribution.dto.lead.LeadResponse.builder().id(lead.getId()).build());
    }

    @Test
    @DisplayName("Test 1: Lead multi-course update saves all courses transactionally")
    void test1_UpdateLeadCourses_Success() throws Exception {
        LeadCoursesUpdateRequest request = LeadCoursesUpdateRequest.builder()
                .courseIds(List.of(courseMba.getId(), courseBba.getId()))
                .build();

        LeadResponse response = leadService.updateLeadCourses(lead.getId(), request);

        assertNotNull(response);
        assertEquals(2, lead.getInterestedCourses().size());
        assertTrue(lead.getInterestedCourses().contains(courseMba));
        assertTrue(lead.getInterestedCourses().contains(courseBba));
        assertFalse(lead.getInterestedCourses().contains(courseBca));
    }

    @Test
    @DisplayName("Test 2: Info Panel returns multiple courses in courses summary list")
    void test2_GetInfoPanel_ReturnsMultipleCourses() {
        InfoPanelResponseDTO infoPanel = communicationService.getInfoPanelForLead(lead.getId(), courseMba.getId());

        assertNotNull(infoPanel);
        assertNotNull(infoPanel.getCourses());
        assertEquals(3, infoPanel.getCourses().size());
        assertNotNull(infoPanel.getCourse());
        assertEquals("MBA", infoPanel.getCourse().getCourseName());
    }

    @Test
    @DisplayName("Test 3: WhatsApp preview generates correct message, image and click-to-chat URL")
    void test3_WhatsAppPreview_Success() throws Exception {
        WhatsAppPreviewRequestDTO request = WhatsAppPreviewRequestDTO.builder()
                .courseId(courseMba.getId())
                .uspId(uspPlacement.getId())
                .templateId(waTemplate.getId())
                .build();

        WhatsAppPreviewResponseDTO preview = communicationService.previewWhatsApp(lead.getId(), request);

        assertNotNull(preview);
        assertEquals("Megha Patel", preview.getLead().getFullName());
        assertEquals("919876543210", preview.getLead().getWhatsappNumber());
        assertEquals("MBA", preview.getCourse().getCourseName());
        assertEquals("100% Placement Assistance with top MNCs", preview.getUsp().getContent());
        assertTrue(preview.getMessage().contains("Dear Megha Patel"));
        assertTrue(preview.getMessage().contains("explore MBA"));
        assertTrue(preview.getMessage().contains("100% Placement Assistance"));
        assertTrue(preview.getMessage().contains("Ravi Sharma"));
        assertNotNull(preview.getWhatsAppUrl());
        assertTrue(preview.getWhatsAppUrl().startsWith("https://web.whatsapp.com/send?phone=919876543210&text="));
    }

    @Test
    @DisplayName("Test 4: Mismatched USP (USP belongs to Course B, but Course A requested) -> REJECTED")
    void test4_MismatchedUsp_Rejected() {
        WhatsAppPreviewRequestDTO request = WhatsAppPreviewRequestDTO.builder()
                .courseId(courseMba.getId())
                .uspId(uspFaculty.getId()) // Belongs to BBA, not MBA
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                communicationService.previewWhatsApp(lead.getId(), request));

        assertTrue(ex.getMessage().contains("Selected USP does not belong to the specified Course"));
    }

    @Test
    @DisplayName("Test 5: Lead without phone number -> REJECTED")
    void test5_MissingPhoneNumber_Rejected() {
        lead.setPhoneNumber(null);
        lead.setAlternatePhoneNumber(null);

        WhatsAppPreviewRequestDTO request = WhatsAppPreviewRequestDTO.builder()
                .courseId(courseMba.getId())
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                communicationService.previewWhatsApp(lead.getId(), request));

        assertTrue(ex.getMessage().contains("phone/WhatsApp number is missing"));
    }

    @Test
    @DisplayName("Test 6: Inactive Course -> REJECTED")
    void test6_InactiveCourse_Rejected() {
        courseMba.setStatus(com.app.datadistribution.enums.Status.INACTIVE);

        WhatsAppPreviewRequestDTO request = WhatsAppPreviewRequestDTO.builder()
                .courseId(courseMba.getId())
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                communicationService.previewWhatsApp(lead.getId(), request));

        assertTrue(ex.getMessage().contains("Selected Course is inactive"));
    }
}
