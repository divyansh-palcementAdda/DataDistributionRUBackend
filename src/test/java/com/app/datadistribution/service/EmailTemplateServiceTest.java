package com.app.datadistribution.service;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.app.datadistribution.entity.Course;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadFollowUp;
import com.app.datadistribution.entity.LeadStatus;
import com.app.datadistribution.enums.FollowUpStatus;
import com.app.datadistribution.service.impl.EmailTemplateServiceImpl;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class EmailTemplateServiceTest {

    @InjectMocks
    private EmailTemplateServiceImpl emailTemplateService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailTemplateService, "frontendUrl", "http://localhost:3000");
    }

    @Test
    void testBuildFollowUpScheduledTemplate() {
        String html = emailTemplateService.buildFollowUpScheduledTemplate(
                "John Doe",
                "Alice Smith",
                "9876543210",
                "alice@example.com",
                "LD-1001",
                LocalDateTime.now().plusDays(1),
                "Counselling Follow-up",
                "FOLLOW_UP",
                "B.Tech Computer Science",
                "Interested in AI specialization",
                "http://localhost:3000/leads/123");

        assertNotNull(html);
        assertTrue(html.contains("John Doe"));
        assertTrue(html.contains("Alice Smith"));
        assertTrue(html.contains("LD-1001"));
        assertTrue(html.contains("9876543210"));
        assertTrue(html.contains("B.Tech Computer Science"));
        assertTrue(html.contains("Interested in AI specialization"));
        assertTrue(html.contains("http://localhost:3000/leads/123"));
    }

    @Test
    void testBuildFollowUpRescheduledTemplate() {
        LocalDateTime prev = LocalDateTime.now();
        LocalDateTime next = LocalDateTime.now().plusDays(2);

        String html = emailTemplateService.buildFollowUpRescheduledTemplate(
                "John Doe",
                "Alice Smith",
                "9876543210",
                "alice@example.com",
                "LD-1001",
                prev,
                next,
                "Follow-up",
                "FOLLOW_UP",
                "MBA Finance",
                "Student requested evening callback",
                "http://localhost:3000/leads/123");

        assertNotNull(html);
        assertTrue(html.contains("Follow-up Rescheduled"));
        assertTrue(html.contains("Alice Smith"));
        assertTrue(html.contains("Student requested evening callback"));
    }

    @Test
    void testBuildFollowUpCompletedTemplate() {
        String html = emailTemplateService.buildFollowUpCompletedTemplate(
                "John Doe",
                "Alice Smith",
                "LD-1001",
                LocalDateTime.now().minusHours(2),
                LocalDateTime.now(),
                "COMPLETED",
                "Student agreed to visit campus on Monday",
                "http://localhost:3000/leads/123");

        assertNotNull(html);
        assertTrue(html.contains("Follow-up Completed"));
        assertTrue(html.contains("Student agreed to visit campus"));
    }

    @Test
    void testBuildFollowUpCancelledTemplate() {
        String html = emailTemplateService.buildFollowUpCancelledTemplate(
                "John Doe",
                "Alice Smith",
                "LD-1001",
                LocalDateTime.now(),
                LocalDateTime.now(),
                "Wrong contact number provided",
                "http://localhost:3000/leads/123");

        assertNotNull(html);
        assertTrue(html.contains("Follow-up Cancelled"));
        assertTrue(html.contains("Wrong contact number provided"));
    }

    @Test
    void testBuildDailyFollowUpReminderTemplate() {
        LeadStatus status = LeadStatus.builder().name("COUNSELING_SCHEDULED").build();
        Course course = Course.builder().courseName("B.Com Honours").build();
        Lead lead = Lead.builder()
                .fullName("Raj Patel")
                .currentStatus(status)
                .course(course)
                .build();

        LeadFollowUp followUp = LeadFollowUp.builder()
                .lead(lead)
                .status(FollowUpStatus.PENDING)
                .followUpDate(LocalDateTime.now())
                .remarks("Review fee structure")
                .build();

        String html = emailTemplateService.buildDailyFollowUpReminderTemplate(
                "John Doe",
                "01 September 2026",
                List.of(followUp),
                "http://localhost:3000/followups");

        assertNotNull(html);
        assertTrue(html.contains("Good Morning <strong>John Doe</strong>"));
        assertTrue(html.contains("Raj Patel"));
        assertTrue(html.contains("COUNSELING_SCHEDULED"));
        assertTrue(html.contains("B.Com Honours"));
    }

    @Test
    void testBuildDataAllocatedTemplate() {
        String html = emailTemplateService.buildDataAllocatedTemplate(
                "John Doe",
                50,
                "Engineering Department",
                "Admin User",
                LocalDateTime.now(),
                "http://localhost:3000/leads");

        assertNotNull(html);
        assertTrue(html.contains("50"));
        assertTrue(html.contains("Engineering Department"));
        assertTrue(html.contains("Admin User"));
    }

    @Test
    void testBuildDataReassignedTemplate() {
        String html = emailTemplateService.buildDataReassignedTemplate(
                "New Counselor",
                30,
                "Old Counselor",
                "Admin User",
                "Management Department",
                "Workload balancing",
                LocalDateTime.now(),
                "http://localhost:3000/leads");

        assertNotNull(html);
        assertTrue(html.contains("30"));
        assertTrue(html.contains("Old Counselor"));
        assertTrue(html.contains("Workload balancing"));
    }

    @Test
    void testBuildGenericEmailTemplate() {
        String html = emailTemplateService.buildGenericEmailTemplate(
                "John Doe",
                "System Notice",
                "<p>Important update on admission cycle.</p>",
                "Check Details",
                "http://localhost:3000/notifications");

        assertNotNull(html);
        assertTrue(html.contains("System Notice"));
        assertTrue(html.contains("Important update on admission cycle."));
    }
}
