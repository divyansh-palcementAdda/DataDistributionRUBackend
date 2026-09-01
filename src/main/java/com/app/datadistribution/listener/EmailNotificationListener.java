package com.app.datadistribution.listener;

import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.EmailType;
import com.app.datadistribution.event.FollowUpCancelledEvent;
import com.app.datadistribution.event.FollowUpCompletedEvent;
import com.app.datadistribution.event.FollowUpRescheduledEvent;
import com.app.datadistribution.event.FollowUpScheduledEvent;
import com.app.datadistribution.event.LeadAllocatedEvent;
import com.app.datadistribution.event.LeadReassignedEvent;
import com.app.datadistribution.repository.LeadFollowUpRepository;
import com.app.datadistribution.repository.LeadRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.interfaces.IEmailService;
import com.app.datadistribution.service.interfaces.IEmailTemplateService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailNotificationListener {

    private final IEmailService emailService;
    private final IEmailTemplateService emailTemplateService;
    private final UserRepository userRepository;
    private final LeadRepository leadRepository;
    private final LeadFollowUpRepository leadFollowUpRepository;

    @Value("${app.frontend.url:https://dds.areyoureporting.com}")
    private String frontendUrl;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy");

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFollowUpScheduled(FollowUpScheduledEvent event) {
        log.info("Processing FollowUpScheduledEvent after commit: {}", event);
        try {
            if (event.getAssignedUserId() == null) {
                log.warn("FollowUpScheduledEvent has null assignedUserId. Skipping email.");
                return;
            }

            Optional<User> userOpt = userRepository.findById(event.getAssignedUserId()).filter(u -> !u.isDeleted());
            if (userOpt.isEmpty()) {
                log.warn("Assigned user {} not found for follow-up {}. Skipping email.", event.getAssignedUserId(),
                        event.getFollowUpId());
                return;
            }
            User counselor = userOpt.get();

            Optional<Lead> leadOpt = leadRepository.findById(event.getLeadId()).filter(l -> !l.isDeleted());
            if (leadOpt.isEmpty()) {
                log.warn("Lead {} not found for follow-up {}. Skipping email.", event.getLeadId(),
                        event.getFollowUpId());
                return;
            }
            Lead lead = leadOpt.get();

            String counselorName = counselor.getFirstName() + " " + counselor.getLastName();
            String studentName = lead.getFullName();
            String studentPhone = lead.getPhoneNumber();
            String studentEmail = lead.getEmail();
            String leadCode = lead.getLeadCode();
            String followUpType = event.getFollowUpStatus() != null ? event.getFollowUpStatus() : "General Follow-up";
            String leadStatusName = lead.getCurrentStatus() != null ? lead.getCurrentStatus().getName()
                    : (lead.getLeadStatus() != null ? lead.getLeadStatus().getName() : "Pending");
            String courseName = lead.getCourse() != null ? lead.getCourse().getCourseName()
                    : (lead.getCourseInterested() != null ? lead.getCourseInterested() : "-");
            String leadUrl = frontendUrl + "/leads/" + lead.getId();

            String formattedDate = event.getFollowUpDate() != null ? event.getFollowUpDate().format(DATE_FORMATTER)
                    : "Upcoming";
            String subject = "Follow-up Scheduled – " + studentName + " – " + formattedDate;

            String htmlBody = emailTemplateService.buildFollowUpScheduledTemplate(
                    counselorName, studentName, studentPhone, studentEmail, leadCode,
                    event.getFollowUpDate(), followUpType, leadStatusName, courseName,
                    event.getRemarks(), leadUrl);

            String idempotencyKey = "FOLLOWUP_SCHEDULED:" + event.getFollowUpId();

            emailService.sendHtmlEmail(
                    counselor.getEmail(),
                    counselorName,
                    counselor.getId(),
                    subject,
                    htmlBody,
                    EmailType.FOLLOWUP_SCHEDULED,
                    event.getFollowUpId(),
                    idempotencyKey);

        } catch (Exception e) {
            log.error("Failed to process FollowUpScheduledEvent for follow-up {}: {}", event.getFollowUpId(),
                    e.getMessage(), e);
        }
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFollowUpRescheduled(FollowUpRescheduledEvent event) {
        log.info("Processing FollowUpRescheduledEvent after commit: {}", event);
        try {
            if (event.getAssignedUserId() == null)
                return;

            Optional<User> userOpt = userRepository.findById(event.getAssignedUserId()).filter(u -> !u.isDeleted());
            if (userOpt.isEmpty())
                return;
            User counselor = userOpt.get();

            Optional<Lead> leadOpt = leadRepository.findById(event.getLeadId()).filter(l -> !l.isDeleted());
            if (leadOpt.isEmpty())
                return;
            Lead lead = leadOpt.get();

            String counselorName = counselor.getFirstName() + " " + counselor.getLastName();
            String studentName = lead.getFullName();
            String studentPhone = lead.getPhoneNumber();
            String studentEmail = lead.getEmail();
            String leadCode = lead.getLeadCode();
            String followUpType = event.getFollowUpStatus() != null ? event.getFollowUpStatus() : "Follow-up";
            String leadStatusName = lead.getCurrentStatus() != null ? lead.getCurrentStatus().getName() : "-";
            String courseName = lead.getCourse() != null ? lead.getCourse().getCourseName()
                    : (lead.getCourseInterested() != null ? lead.getCourseInterested() : "-");
            String leadUrl = frontendUrl + "/leads/" + lead.getId();

            String formattedDate = event.getNewFollowUpDate() != null
                    ? event.getNewFollowUpDate().format(DATE_FORMATTER)
                    : "Updated";
            String subject = "Follow-up Rescheduled – " + studentName + " – " + formattedDate;

            String htmlBody = emailTemplateService.buildFollowUpRescheduledTemplate(
                    counselorName, studentName, studentPhone, studentEmail, leadCode,
                    event.getPreviousFollowUpDate(), event.getNewFollowUpDate(), followUpType,
                    leadStatusName, courseName, event.getRemarks(), leadUrl);

            String idempotencyKey = "FOLLOWUP_RESCHEDULED:" + event.getFollowUpId() + ":" + event.getNewFollowUpDate();

            emailService.sendHtmlEmail(
                    counselor.getEmail(),
                    counselorName,
                    counselor.getId(),
                    subject,
                    htmlBody,
                    EmailType.FOLLOWUP_RESCHEDULED,
                    event.getFollowUpId(),
                    idempotencyKey);

        } catch (Exception e) {
            log.error("Failed to process FollowUpRescheduledEvent for follow-up {}: {}", event.getFollowUpId(),
                    e.getMessage(), e);
        }
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFollowUpCompleted(FollowUpCompletedEvent event) {
        log.info("Processing FollowUpCompletedEvent after commit: {}", event);
        try {
            if (event.getAssignedUserId() == null)
                return;

            Optional<User> userOpt = userRepository.findById(event.getAssignedUserId()).filter(u -> !u.isDeleted());
            if (userOpt.isEmpty())
                return;
            User counselor = userOpt.get();

            Optional<Lead> leadOpt = leadRepository.findById(event.getLeadId()).filter(l -> !l.isDeleted());
            if (leadOpt.isEmpty())
                return;
            Lead lead = leadOpt.get();

            String counselorName = counselor.getFirstName() + " " + counselor.getLastName();
            String studentName = lead.getFullName();
            String leadCode = lead.getLeadCode();
            String leadUrl = frontendUrl + "/leads/" + lead.getId();

            String subject = "Follow-up Completed – " + studentName + " (" + leadCode + ")";

            String htmlBody = emailTemplateService.buildFollowUpCompletedTemplate(
                    counselorName, studentName, leadCode, event.getScheduledDate(),
                    event.getCompletedAt(), event.getFinalStatus(), event.getRemarks(), leadUrl);

            String idempotencyKey = "FOLLOWUP_COMPLETED:" + event.getFollowUpId();

            emailService.sendHtmlEmail(
                    counselor.getEmail(),
                    counselorName,
                    counselor.getId(),
                    subject,
                    htmlBody,
                    EmailType.FOLLOWUP_COMPLETED,
                    event.getFollowUpId(),
                    idempotencyKey);

        } catch (Exception e) {
            log.error("Failed to process FollowUpCompletedEvent for follow-up {}: {}", event.getFollowUpId(),
                    e.getMessage(), e);
        }
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFollowUpCancelled(FollowUpCancelledEvent event) {
        log.info("Processing FollowUpCancelledEvent after commit: {}", event);
        try {
            if (event.getAssignedUserId() == null)
                return;

            Optional<User> userOpt = userRepository.findById(event.getAssignedUserId()).filter(u -> !u.isDeleted());
            if (userOpt.isEmpty())
                return;
            User counselor = userOpt.get();

            Optional<Lead> leadOpt = leadRepository.findById(event.getLeadId()).filter(l -> !l.isDeleted());
            if (leadOpt.isEmpty())
                return;
            Lead lead = leadOpt.get();

            String counselorName = counselor.getFirstName() + " " + counselor.getLastName();
            String studentName = lead.getFullName();
            String leadCode = lead.getLeadCode();
            String leadUrl = frontendUrl + "/leads/" + lead.getId();

            String subject = "Follow-up Cancelled – " + studentName + " (" + leadCode + ")";

            String htmlBody = emailTemplateService.buildFollowUpCancelledTemplate(
                    counselorName, studentName, leadCode, event.getScheduledDate(),
                    event.getCancelledAt(), event.getCancellationRemarks(), leadUrl);

            String idempotencyKey = "FOLLOWUP_CANCELLED:" + event.getFollowUpId();

            emailService.sendHtmlEmail(
                    counselor.getEmail(),
                    counselorName,
                    counselor.getId(),
                    subject,
                    htmlBody,
                    EmailType.FOLLOWUP_CANCELLED,
                    event.getFollowUpId(),
                    idempotencyKey);

        } catch (Exception e) {
            log.error("Failed to process FollowUpCancelledEvent for follow-up {}: {}", event.getFollowUpId(),
                    e.getMessage(), e);
        }
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLeadAllocated(LeadAllocatedEvent event) {
        log.info("Processing LeadAllocatedEvent after commit: {}", event);
        try {
            if (event.getTargetUserId() == null || event.getAllocatedCount() <= 0)
                return;

            Optional<User> userOpt = userRepository.findById(event.getTargetUserId()).filter(u -> !u.isDeleted());
            if (userOpt.isEmpty())
                return;
            User counselor = userOpt.get();

            String allocatedByName = "Administrator";
            if (event.getAllocatedByUserId() != null) {
                userRepository.findById(event.getAllocatedByUserId())
                        .ifPresent(admin -> {
                        });
                Optional<User> adminOpt = userRepository.findById(event.getAllocatedByUserId());
                if (adminOpt.isPresent()) {
                    allocatedByName = adminOpt.get().getFirstName() + " " + adminOpt.get().getLastName();
                }
            }

            String counselorName = counselor.getFirstName() + " " + counselor.getLastName();
            String subject = "New Data Allocated to You – " + event.getAllocatedCount() + " Leads";
            String crmUrl = frontendUrl + "/leads";

            String htmlBody = emailTemplateService.buildDataAllocatedTemplate(
                    counselorName,
                    event.getAllocatedCount(),
                    event.getDepartmentName(),
                    allocatedByName,
                    event.getAllocationTime(),
                    crmUrl);

            String batch = event.getBatchId() != null ? event.getBatchId() : UUID.randomUUID().toString();
            String idempotencyKey = "LEAD_ALLOCATION:" + batch + ":" + counselor.getId();

            emailService.sendHtmlEmail(
                    counselor.getEmail(),
                    counselorName,
                    counselor.getId(),
                    subject,
                    htmlBody,
                    EmailType.DATA_ALLOCATED,
                    null,
                    idempotencyKey);

        } catch (Exception e) {
            log.error("Failed to process LeadAllocatedEvent for user {}: {}", event.getTargetUserId(), e.getMessage(),
                    e);
        }
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLeadReassigned(LeadReassignedEvent event) {
        log.info("Processing LeadReassignedEvent after commit: {}", event);
        try {
            if (event.getTargetUserId() == null || event.getReassignedCount() <= 0)
                return;

            // Do not send if target user is same as source user
            if (event.getSourceUserId() != null && event.getTargetUserId().equals(event.getSourceUserId())) {
                return;
            }

            Optional<User> userOpt = userRepository.findById(event.getTargetUserId()).filter(u -> !u.isDeleted());
            if (userOpt.isEmpty())
                return;
            User counselor = userOpt.get();

            String reassignedByName = "Administrator";
            if (event.getReassignedByUserId() != null) {
                Optional<User> adminOpt = userRepository.findById(event.getReassignedByUserId());
                if (adminOpt.isPresent()) {
                    reassignedByName = adminOpt.get().getFirstName() + " " + adminOpt.get().getLastName();
                }
            }

            String previousOwnerName = event.getSourceUserName() != null ? event.getSourceUserName()
                    : "Previous Counselor";
            if (event.getSourceUserId() != null && event.getSourceUserName() == null) {
                Optional<User> prevOpt = userRepository.findById(event.getSourceUserId());
                if (prevOpt.isPresent()) {
                    previousOwnerName = prevOpt.get().getFirstName() + " " + prevOpt.get().getLastName();
                }
            }

            String counselorName = counselor.getFirstName() + " " + counselor.getLastName();
            String subject = "New Lead Data Assigned to You – " + event.getReassignedCount() + " Leads";
            String crmUrl = frontendUrl + "/leads";

            String htmlBody = emailTemplateService.buildDataReassignedTemplate(
                    counselorName,
                    event.getReassignedCount(),
                    previousOwnerName,
                    reassignedByName,
                    event.getDepartmentName(),
                    event.getReason(),
                    event.getReassignmentTime(),
                    crmUrl);

            String batch = event.getBatchId() != null ? event.getBatchId() : UUID.randomUUID().toString();
            String idempotencyKey = "LEAD_REASSIGNMENT:" + batch + ":" + counselor.getId();

            emailService.sendHtmlEmail(
                    counselor.getEmail(),
                    counselorName,
                    counselor.getId(),
                    subject,
                    htmlBody,
                    EmailType.DATA_REASSIGNED,
                    null,
                    idempotencyKey);

        } catch (Exception e) {
            log.error("Failed to process LeadReassignedEvent for user {}: {}", event.getTargetUserId(), e.getMessage(),
                    e);
        }
    }
}
