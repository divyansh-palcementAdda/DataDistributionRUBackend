package com.app.datadistribution.integration.cms.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.app.datadistribution.integration.cms.dto.MatchedStudentDTO;
import com.app.datadistribution.integration.cms.dto.StudentVerificationRequest;
import com.app.datadistribution.integration.cms.dto.StudentVerificationResponse;
import com.app.datadistribution.integration.cms.enums.MatchStatus;
import com.app.datadistribution.integration.cms.service.impl.StudentVerificationServiceImpl;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class StudentVerificationServiceImplTest {

        @Mock
        private RestTemplate restTemplate;

        @InjectMocks
        private StudentVerificationServiceImpl studentVerificationService;

        @BeforeEach
        void setUp() {
                ReflectionTestUtils.setField(studentVerificationService, "cmsBaseUrl",
                                "https://cms.areyoureporting.com/api");
                ReflectionTestUtils.setField(studentVerificationService, "cmsApiKey", "test-api-key");
        }

        @Test
        void testVerifyStudent_InsufficientIdentifyingFields_ReturnsNoMatch() {
                StudentVerificationRequest emptyRequest = StudentVerificationRequest.builder()
                                .leadId(UUID.randomUUID())
                                .build();

                StudentVerificationResponse response = studentVerificationService.verifyStudent(emptyRequest);

                assertNotNull(response);
                assertFalse(response.isVerified());
                assertEquals(MatchStatus.NO_MATCH, response.getMatchStatus());
        }

        @Test
        void testVerifyStudent_SuccessFullMatch() {
                StudentVerificationRequest request = StudentVerificationRequest.builder()
                                .leadId(UUID.randomUUID())
                                .studentName("Rahul Sharma")
                                .mobile("9876543210")
                                .email("rahul@example.com")
                                .build();

                MatchedStudentDTO matched = MatchedStudentDTO.builder()
                                .studentId("CMS-STU-1001")
                                .enrollmentNumber("RU2026-ENG-001")
                                .studentName("Rahul Sharma")
                                .email("rahul@example.com")
                                .build();

                StudentVerificationResponse expectedResponse = StudentVerificationResponse.builder()
                                .verified(true)
                                .matchStatus(MatchStatus.FULL_MATCH)
                                .confidenceScore(100)
                                .multipleMatches(false)
                                .totalCandidatesEvaluated(1)
                                .matchedStudents(List.of(matched))
                                .message("Full match found")
                                .build();

                when(restTemplate.exchange(
                                anyString(),
                                eq(HttpMethod.POST),
                                any(HttpEntity.class),
                                eq(StudentVerificationResponse.class)))
                                .thenReturn(new ResponseEntity<>(expectedResponse, HttpStatus.OK));

                StudentVerificationResponse response = studentVerificationService.verifyStudent(request);

                assertNotNull(response);
                assertTrue(response.isVerified());
                assertEquals(MatchStatus.FULL_MATCH, response.getMatchStatus());
                assertEquals(100, response.getConfidenceScore());
                assertEquals("RU2026-ENG-001", response.getMatchedStudents().get(0).getEnrollmentNumber());
        }

        @Test
        void testVerifyStudent_Http500Error_ReturnsErrorStatus() {
                StudentVerificationRequest request = StudentVerificationRequest.builder()
                                .leadId(UUID.randomUUID())
                                .mobile("9876543210")
                                .build();

                when(restTemplate.exchange(
                                anyString(),
                                eq(HttpMethod.POST),
                                any(HttpEntity.class),
                                eq(StudentVerificationResponse.class)))
                                .thenThrow(new HttpClientErrorException(HttpStatus.INTERNAL_SERVER_ERROR,
                                                "Server Error"));

                StudentVerificationResponse response = studentVerificationService.verifyStudent(request);

                assertNotNull(response);
                assertFalse(response.isVerified());
                assertEquals(MatchStatus.ERROR, response.getMatchStatus());
        }

        @Test
        void testVerifyStudent_NetworkTimeout_ReturnsErrorStatus() {
                StudentVerificationRequest request = StudentVerificationRequest.builder()
                                .leadId(UUID.randomUUID())
                                .mobile("9876543210")
                                .build();

                when(restTemplate.exchange(
                                anyString(),
                                eq(HttpMethod.POST),
                                any(HttpEntity.class),
                                eq(StudentVerificationResponse.class)))
                                .thenThrow(new ResourceAccessException("Connection timed out"));

                StudentVerificationResponse response = studentVerificationService.verifyStudent(request);

                assertNotNull(response);
                assertFalse(response.isVerified());
                assertEquals(MatchStatus.ERROR, response.getMatchStatus());
        }
}
