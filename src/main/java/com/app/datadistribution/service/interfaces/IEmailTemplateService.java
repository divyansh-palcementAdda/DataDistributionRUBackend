package com.app.datadistribution.service.interfaces;

import java.time.LocalDateTime;
import java.util.List;

import com.app.datadistribution.entity.LeadFollowUp;

public interface IEmailTemplateService {

    String buildFollowUpScheduledTemplate(
            String counselorName,
            String studentName,
            String studentPhone,
            String studentEmail,
            String leadCode,
            LocalDateTime followUpDate,
            String followUpType,
            String leadStatusName,
            String courseName,
            String remarks,
            String leadUrl);

    String buildFollowUpRescheduledTemplate(
            String counselorName,
            String studentName,
            String studentPhone,
            String studentEmail,
            String leadCode,
            LocalDateTime previousDate,
            LocalDateTime newDate,
            String followUpType,
            String leadStatusName,
            String courseName,
            String remarks,
            String leadUrl);

    String buildFollowUpCompletedTemplate(
            String counselorName,
            String studentName,
            String leadCode,
            LocalDateTime scheduledDate,
            LocalDateTime completedAt,
            String finalStatus,
            String remarks,
            String leadUrl);

    String buildFollowUpCancelledTemplate(
            String counselorName,
            String studentName,
            String leadCode,
            LocalDateTime scheduledDate,
            LocalDateTime cancelledAt,
            String cancellationRemarks,
            String leadUrl);

    String buildDailyFollowUpReminderTemplate(
            String counselorName,
            String formattedDate,
            List<LeadFollowUp> followUps,
            String dashboardUrl);

    String buildDataAllocatedTemplate(
            String counselorName,
            int totalAllocated,
            String departmentName,
            String allocatedByName,
            LocalDateTime allocationTime,
            String crmUrl);

    String buildDataReassignedTemplate(
            String counselorName,
            int totalReassigned,
            String previousOwnerName,
            String reassignedByName,
            String departmentName,
            String reason,
            LocalDateTime reassignmentTime,
            String crmUrl);

    String buildGenericEmailTemplate(
            String recipientName,
            String title,
            String content,
            String actionText,
            String actionUrl);
}
