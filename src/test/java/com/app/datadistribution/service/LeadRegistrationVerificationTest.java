package com.app.datadistribution.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.app.datadistribution.dto.lead.LeadResponse;
import com.app.datadistribution.dto.lead.LeadStatusChangeRequest;
import com.app.datadistribution.dto.lead.ManualRegistrationApprovalRequest;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.entity.Course;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadFeedback;
import com.app.datadistribution.entity.LeadStatus;
import com.app.datadistribution.entity.Role;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.RegistrationStatus;
import com.app.datadistribution.enums.RoleType;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.integration.cms.dto.MatchedStudentDTO;
import com.app.datadistribution.integration.cms.dto.StudentVerificationRequest;
import com.app.datadistribution.integration.cms.dto.StudentVerificationResponse;
import com.app.datadistribution.integration.cms.enums.MatchStatus;
import com.app.datadistribution.integration.cms.service.IStudentVerificationService;
import com.app.datadistribution.mapper.LeadMapper;
import com.app.datadistribution.repository.CourseRepository;
import com.app.datadistribution.repository.LeadFeedbackRepository;
import com.app.datadistribution.repository.LeadRepository;
import com.app.datadistribution.repository.LeadStatusRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.impl.LeadServiceImpl;
import com.app.datadistribution.service.interfaces.ILeadDataScopeService;
import com.app.datadistribution.service.interfaces.ILeadStatusTransitionService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class LeadRegistrationVerificationTest {

        @Mock
        private LeadRepository leadRepository;
        @Mock
        private LeadStatusRepository leadStatusRepository;
        @Mock
        private CourseRepository courseRepository;
        @Mock
        private UserRepository userRepository;
        @Mock
        private LeadFeedbackRepository leadFeedbackRepository;
        @Mock
        private ILeadDataScopeService leadDataScopeService;
        @Mock
        private ILeadStatusTransitionService leadStatusTransitionService;
        @Mock
        private IStudentVerificationService studentVerificationService;
        @Mock
        private LeadMapper leadMapper;

        @InjectMocks
        private LeadServiceImpl leadService;

        private User adminUser;
        private User counselorUser;
        private Lead lead;
        private LeadStatus connectedStatus;
        private LeadStatus registeredStatus;
        private Course course;

        @BeforeEach
        void setUp() {
                Role adminRole = Role.builder().name(RoleType.ADMIN.name()).build();
                adminRole.setId(UUID.randomUUID());

                Role counselorRole = Role.builder().name(RoleType.COUNSELOR.name()).build();
                counselorRole.setId(UUID.randomUUID());

                adminUser = User.builder()
                                .username("admin_user")
                                .roles(Set.of(adminRole))
                                .build();
                adminUser.setId(UUID.randomUUID());

                counselorUser = User.builder()
                                .username("counselor_user")
                                .roles(Set.of(counselorRole))
                                .build();
                counselorUser.setId(UUID.randomUUID());

                connectedStatus = LeadStatus.builder()
                                .code("CONNECTED")
                                .name("Connected")
                                .active(true)
                                .build();
                connectedStatus.setId(UUID.randomUUID());

                registeredStatus = LeadStatus.builder()
                                .code("REGISTERED")
                                .name("Registered")
                                .active(true)
                                .build();
                registeredStatus.setId(UUID.randomUUID());

                course = Course.builder()
                                .courseName("B.Tech Computer Science")
                                .courseCode("BT-CSE")
                                .status(com.app.datadistribution.enums.Status.ACTIVE)
                                .build();
                course.setId(UUID.randomUUID());

                lead = Lead.builder()
                                .leadCode("LEAD-REG-01")
                                .fullName("Rohan Verma")
                                .phoneNumber("9876543210")
                                .email("rohan@example.com")
                                .currentStatus(connectedStatus)
                                .course(course)
                                .registrationStatus(RegistrationStatus.NONE)
                                .active(true)
                                .build();
                lead.setId(UUID.randomUUID());
        }

        private void mockSecurityUser(User user) {
                org.springframework.security.core.context.SecurityContext context = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
                context.setAuthentication(new UsernamePasswordAuthenticationToken(user.getUsername(), "pass", java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))));
                SecurityContextHolder.setContext(context);
                lenient().when(userRepository.findByUsername(user.getUsername())).thenReturn(Optional.of(user));
        }

        @Test
        void testChangeStatus_ToRegistered_CMSFullMatch_TransitionsToRegisteredAndSavesEnrollmentId() throws Exception {
                mockSecurityUser(counselorUser);
                UserDataScope scope = UserDataScope.builder().build();
                when(leadDataScopeService.getCurrentUserScope()).thenReturn(scope);
                when(leadRepository.findById(lead.getId())).thenReturn(Optional.of(lead));
                when(leadStatusRepository.findByCodeIgnoreCase("REGISTERED")).thenReturn(Optional.of(registeredStatus));

                MatchedStudentDTO matched = MatchedStudentDTO.builder()
                                .studentId("CMS-STU-99")
                                .enrollmentNumber("RU2026-ENG-099")
                                .studentName("Rohan Verma")
                                .email("rohan@example.com")
                                .build();

                StudentVerificationResponse cmsResp = StudentVerificationResponse.builder()
                                .verified(true)
                                .matchStatus(MatchStatus.FULL_MATCH)
                                .confidenceScore(100)
                                .matchedStudents(List.of(matched))
                                .message("Full match found")
                                .build();

                when(studentVerificationService.verifyStudent(any(StudentVerificationRequest.class)))
                                .thenReturn(cmsResp);

                Lead registeredLead = Lead.builder()
                                .leadCode("LEAD-REG-01")
                                .fullName("Rohan Verma")
                                .phoneNumber("9876543210")
                                .email("rohan@example.com")
                                .currentStatus(registeredStatus)
                                .registrationStatus(RegistrationStatus.COMPLETED_MATCHED)
                                .enrollmentId("RU2026-ENG-099")
                                .build();
                registeredLead.setId(lead.getId());

                when(leadStatusTransitionService.executeStatusTransition(eq(lead), eq(registeredStatus),
                                eq(counselorUser), any()))
                                .thenReturn(registeredLead);
                when(leadFeedbackRepository.save(any(LeadFeedback.class))).thenAnswer(i -> i.getArgument(0));

                LeadResponse mockResponse = LeadResponse.builder()
                                .id(lead.getId())
                                .leadCode("LEAD-REG-01")
                                .registrationStatus(RegistrationStatus.COMPLETED_MATCHED)
                                .enrollmentId("RU2026-ENG-099")
                                .build();
                when(leadMapper.toDto(registeredLead)).thenReturn(mockResponse);

                LeadStatusChangeRequest req = new LeadStatusChangeRequest();
                req.setStatusCode("REGISTERED");
                req.setFeedback("Student enrolled");

                LeadResponse result = leadService.changeStatus(lead.getId(), req);

                assertNotNull(result);
                assertEquals(RegistrationStatus.COMPLETED_MATCHED, result.getRegistrationStatus());
                assertEquals("RU2026-ENG-099", result.getEnrollmentId());
                assertEquals("RU2026-ENG-099", lead.getEnrollmentId());
                assertEquals(RegistrationStatus.COMPLETED_MATCHED, lead.getRegistrationStatus());
        }

        @Test
        void testChangeStatus_ToRegistered_CMSNoMatch_ThrowsBadRequest_SetsRejectedStatus() throws Exception {
                mockSecurityUser(counselorUser);
                UserDataScope scope = UserDataScope.builder().build();
                when(leadDataScopeService.getCurrentUserScope()).thenReturn(scope);
                when(leadRepository.findById(lead.getId())).thenReturn(Optional.of(lead));
                when(leadStatusRepository.findByCodeIgnoreCase("REGISTERED")).thenReturn(Optional.of(registeredStatus));

                StudentVerificationResponse cmsResp = StudentVerificationResponse.builder()
                                .verified(false)
                                .matchStatus(MatchStatus.NO_MATCH)
                                .confidenceScore(0)
                                .message("No student found matching credentials")
                                .build();

                when(studentVerificationService.verifyStudent(any(StudentVerificationRequest.class)))
                                .thenReturn(cmsResp);

                LeadStatusChangeRequest req = new LeadStatusChangeRequest();
                req.setStatusCode("REGISTERED");

                BadRequestException ex = assertThrows(BadRequestException.class,
                                () -> leadService.changeStatus(lead.getId(), req));

                assertNotNull(ex);
                assertEquals(RegistrationStatus.CHECK_REJECTED, lead.getRegistrationStatus());
                assertEquals("No student found matching credentials", lead.getRegistrationCheckFailureReason());
                assertEquals(connectedStatus, lead.getCurrentStatus());
                verify(leadRepository).save(lead);
        }

        @Test
        void testChangeStatus_ToRegistered_CMSTimeout_ThrowsBadRequest_SetsPendingStatus() throws Exception {
                mockSecurityUser(counselorUser);
                UserDataScope scope = UserDataScope.builder().build();
                when(leadDataScopeService.getCurrentUserScope()).thenReturn(scope);
                when(leadRepository.findById(lead.getId())).thenReturn(Optional.of(lead));
                when(leadStatusRepository.findByCodeIgnoreCase("REGISTERED")).thenReturn(Optional.of(registeredStatus));

                StudentVerificationResponse cmsResp = StudentVerificationResponse.builder()
                                .verified(false)
                                .matchStatus(MatchStatus.ERROR)
                                .confidenceScore(0)
                                .message("CMS service timeout or connection failure")
                                .build();

                when(studentVerificationService.verifyStudent(any(StudentVerificationRequest.class)))
                                .thenReturn(cmsResp);

                LeadStatusChangeRequest req = new LeadStatusChangeRequest();
                req.setStatusCode("REGISTERED");

                assertThrows(BadRequestException.class, () -> leadService.changeStatus(lead.getId(), req));

                assertEquals(RegistrationStatus.CHECK_PENDING, lead.getRegistrationStatus());
                assertEquals("CMS service timeout or connection failure", lead.getRegistrationCheckFailureReason());
                verify(leadRepository).save(lead);
        }

        @Test
        void testManualApproveRegistration_AdminUser_ApprovesWithoutCMS() throws Exception {
                mockSecurityUser(adminUser);
                UserDataScope scope = UserDataScope.builder().build();
                when(leadDataScopeService.getCurrentUserScope()).thenReturn(scope);
                when(leadRepository.findById(lead.getId())).thenReturn(Optional.of(lead));
                when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
                when(leadStatusRepository.findByCodeIgnoreCase("REGISTERED")).thenReturn(Optional.of(registeredStatus));

                Lead approvedLead = Lead.builder()
                                .leadCode("LEAD-REG-01")
                                .course(course)
                                .currentStatus(registeredStatus)
                                .registrationStatus(RegistrationStatus.MANUALLY_APPROVED)
                                .enrollmentId("MANUAL-ENROLL-123")
                                .build();
                approvedLead.setId(lead.getId());

                when(leadStatusTransitionService.executeStatusTransition(eq(lead), eq(registeredStatus), eq(adminUser),
                                any()))
                                .thenReturn(approvedLead);
                when(leadFeedbackRepository.save(any(LeadFeedback.class))).thenAnswer(i -> i.getArgument(0));

                LeadResponse mockResponse = LeadResponse.builder()
                                .id(lead.getId())
                                .registrationStatus(RegistrationStatus.MANUALLY_APPROVED)
                                .enrollmentId("MANUAL-ENROLL-123")
                                .build();
                when(leadMapper.toDto(approvedLead)).thenReturn(mockResponse);

                ManualRegistrationApprovalRequest request = ManualRegistrationApprovalRequest.builder()
                                .registeredCourseId(course.getId())
                                .enrollmentId("MANUAL-ENROLL-123")
                                .remarks("Manually verified admission receipt")
                                .build();

                LeadResponse result = leadService.manualApproveRegistration(lead.getId(), request);

                assertNotNull(result);
                assertEquals(RegistrationStatus.MANUALLY_APPROVED, result.getRegistrationStatus());
                assertEquals("MANUAL-ENROLL-123", result.getEnrollmentId());
                verify(studentVerificationService, never()).verifyStudent(any());
        }

        @Test
        void testManualApproveRegistration_NonAdminUser_ThrowsUnauthorizedException() {
                mockSecurityUser(counselorUser);

                ManualRegistrationApprovalRequest request = ManualRegistrationApprovalRequest.builder()
                                .registeredCourseId(course.getId())
                                .build();

                assertThrows(UnauthorizedException.class,
                                () -> leadService.manualApproveRegistration(lead.getId(), request));

                verify(studentVerificationService, never()).verifyStudent(any());
        }
}
