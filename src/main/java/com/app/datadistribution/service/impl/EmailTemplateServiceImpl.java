package com.app.datadistribution.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import com.app.datadistribution.entity.LeadFollowUp;
import com.app.datadistribution.service.interfaces.IEmailTemplateService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class EmailTemplateServiceImpl implements IEmailTemplateService {

    @Value("${app.frontend.url:https://dds.areyoureporting.com}")
    private String frontendUrl;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMMM yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("hh:mm a");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd MMMM yyyy, hh:mm a");

    @Override
    public String buildFollowUpScheduledTemplate(
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
            String leadUrl) {

        String safeCounselor = escape(counselorName != null ? counselorName : "Counselor");
        String safeStudent = escape(studentName != null ? studentName : "N/A");
        String safePhone = escape(studentPhone != null ? studentPhone : "-");
        String safeEmail = escape(studentEmail != null ? studentEmail : "-");
        String safeLeadCode = escape(leadCode != null ? leadCode : "-");
        String safeType = escape(followUpType != null ? followUpType : "General Follow-up");
        String safeStatus = escape(leadStatusName != null ? leadStatusName : "FOLLOW_UP");
        String safeCourse = escape(courseName != null ? courseName : "General Enquiry");
        String safeRemarks = escape(remarks != null ? remarks : "No remarks provided");

        String formattedDate = followUpDate != null ? followUpDate.format(DATE_FORMATTER) : "Scheduled Date";
        String formattedTime = followUpDate != null ? followUpDate.format(TIME_FORMATTER) : "Scheduled Time";
        String actionLink = (leadUrl != null && !leadUrl.isBlank()) ? leadUrl : (frontendUrl + "/leads");

        StringBuilder body = new StringBuilder();
        body.append("<p style=\"font-size:15px; color:#374151; margin-bottom:20px;\">Hello <strong>").append(safeCounselor).append("</strong>,</p>");
        body.append("<p style=\"font-size:14px; color:#4B5563; margin-bottom:20px;\">A new follow-up has been scheduled for your assigned lead. Please review the details below:</p>");

        body.append("<div style=\"background-color:#F9FAFB; border:1px solid #E5E7EB; border-radius:8px; padding:18px; margin-bottom:24px;\">");
        body.append(buildTableRow("Student / Lead", safeStudent + " (" + safeLeadCode + ")"));
        body.append(buildTableRow("Contact", safePhone + " | " + safeEmail));
        body.append(buildTableRow("Follow-up Date", "<span style=\"color:#2563EB; font-weight:600;\">" + formattedDate + "</span>"));
        body.append(buildTableRow("Follow-up Time", "<span style=\"color:#2563EB; font-weight:600;\">" + formattedTime + "</span>"));
        body.append(buildTableRow("Follow-up Type", "<span style=\"background-color:#EFF6FF; color:#1D4ED8; padding:3px 8px; border-radius:4px; font-weight:500;\">" + safeType + "</span>"));
        body.append(buildTableRow("Lead Status", safeStatus));
        body.append(buildTableRow("Course Interested", safeCourse));
        body.append(buildTableRow("Remarks / Notes", "<em style=\"color:#4B5563;\">\"" + safeRemarks + "\"</em>"));
        body.append("</div>");

        body.append("<p style=\"font-size:14px; color:#4B5563;\">Please ensure the follow-up is completed on time and your feedback is updated in the CRM.</p>");

        return wrapInBaseLayout("Follow-up Scheduled", "New Follow-up Assignment", body.toString(), "Open Lead in CRM", actionLink);
    }

    @Override
    public String buildFollowUpRescheduledTemplate(
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
            String leadUrl) {

        String safeCounselor = escape(counselorName != null ? counselorName : "Counselor");
        String safeStudent = escape(studentName != null ? studentName : "N/A");
        String safeLeadCode = escape(leadCode != null ? leadCode : "-");
        String safeType = escape(followUpType != null ? followUpType : "Follow-up");
        String safeStatus = escape(leadStatusName != null ? leadStatusName : "FOLLOW_UP");
        String safeCourse = escape(courseName != null ? courseName : "-");
        String safeRemarks = escape(remarks != null ? remarks : "Rescheduled");

        String formattedPrev = previousDate != null ? previousDate.format(DATE_TIME_FORMATTER) : "Earlier Date";
        String formattedNew = newDate != null ? newDate.format(DATE_TIME_FORMATTER) : "Updated Date";
        String actionLink = (leadUrl != null && !leadUrl.isBlank()) ? leadUrl : (frontendUrl + "/leads");

        StringBuilder body = new StringBuilder();
        body.append("<p style=\"font-size:15px; color:#374151; margin-bottom:20px;\">Hello <strong>").append(safeCounselor).append("</strong>,</p>");
        body.append("<p style=\"font-size:14px; color:#4B5563; margin-bottom:20px;\">The follow-up schedule for <strong>").append(safeStudent).append("</strong> has been updated.</p>");

        body.append("<div style=\"background-color:#FFFBEB; border:1px solid #FDE68A; border-radius:8px; padding:18px; margin-bottom:24px;\">");
        body.append(buildTableRow("Student / Lead", safeStudent + " (" + safeLeadCode + ")"));
        body.append(buildTableRow("Previous Schedule", "<span style=\"color:#9CA3AF; text-decoration:line-through;\">" + formattedPrev + "</span>"));
        body.append(buildTableRow("New Schedule", "<span style=\"color:#D97706; font-weight:700; font-size:15px;\">" + formattedNew + "</span>"));
        body.append(buildTableRow("Follow-up Type", safeType));
        body.append(buildTableRow("Lead Status", safeStatus));
        body.append(buildTableRow("Course Interested", safeCourse));
        body.append(buildTableRow("Reason / Remarks", "<em style=\"color:#4B5563;\">\"" + safeRemarks + "\"</em>"));
        body.append("</div>");

        return wrapInBaseLayout("Follow-up Rescheduled", "Schedule Update Notification", body.toString(), "View Updated Follow-up", actionLink);
    }

    @Override
    public String buildFollowUpCompletedTemplate(
            String counselorName,
            String studentName,
            String leadCode,
            LocalDateTime scheduledDate,
            LocalDateTime completedAt,
            String finalStatus,
            String remarks,
            String leadUrl) {

        String safeCounselor = escape(counselorName != null ? counselorName : "Counselor");
        String safeStudent = escape(studentName != null ? studentName : "N/A");
        String safeLeadCode = escape(leadCode != null ? leadCode : "-");
        String safeStatus = escape(finalStatus != null ? finalStatus : "COMPLETED");
        String safeRemarks = escape(remarks != null ? remarks : "Completed successfully");

        String formattedSched = scheduledDate != null ? scheduledDate.format(DATE_TIME_FORMATTER) : "-";
        String formattedDone = completedAt != null ? completedAt.format(DATE_TIME_FORMATTER) : LocalDateTime.now().format(DATE_TIME_FORMATTER);
        String actionLink = (leadUrl != null && !leadUrl.isBlank()) ? leadUrl : (frontendUrl + "/leads");

        StringBuilder body = new StringBuilder();
        body.append("<p style=\"font-size:15px; color:#374151; margin-bottom:20px;\">Hello <strong>").append(safeCounselor).append("</strong>,</p>");
        body.append("<p style=\"font-size:14px; color:#4B5563; margin-bottom:20px;\">The following follow-up has been marked as <strong>Completed</strong>.</p>");

        body.append("<div style=\"background-color:#F0FDF4; border:1px solid #BBF7D0; border-radius:8px; padding:18px; margin-bottom:24px;\">");
        body.append(buildTableRow("Student / Lead", safeStudent + " (" + safeLeadCode + ")"));
        body.append(buildTableRow("Scheduled For", formattedSched));
        body.append(buildTableRow("Completed At", "<span style=\"color:#16A34A; font-weight:600;\">" + formattedDone + "</span>"));
        body.append(buildTableRow("Final Status", safeStatus));
        body.append(buildTableRow("Feedback / Remarks", "<em style=\"color:#4B5563;\">\"" + safeRemarks + "\"</em>"));
        body.append("</div>");

        return wrapInBaseLayout("Follow-up Completed", "Activity Completed Confirmation", body.toString(), "View Lead Details", actionLink);
    }

    @Override
    public String buildFollowUpCancelledTemplate(
            String counselorName,
            String studentName,
            String leadCode,
            LocalDateTime scheduledDate,
            LocalDateTime cancelledAt,
            String cancellationRemarks,
            String leadUrl) {

        String safeCounselor = escape(counselorName != null ? counselorName : "Counselor");
        String safeStudent = escape(studentName != null ? studentName : "N/A");
        String safeLeadCode = escape(leadCode != null ? leadCode : "-");
        String safeRemarks = escape(cancellationRemarks != null ? cancellationRemarks : "No reason specified");

        String formattedSched = scheduledDate != null ? scheduledDate.format(DATE_TIME_FORMATTER) : "-";
        String formattedCancel = cancelledAt != null ? cancelledAt.format(DATE_TIME_FORMATTER) : LocalDateTime.now().format(DATE_TIME_FORMATTER);
        String actionLink = (leadUrl != null && !leadUrl.isBlank()) ? leadUrl : (frontendUrl + "/leads");

        StringBuilder body = new StringBuilder();
        body.append("<p style=\"font-size:15px; color:#374151; margin-bottom:20px;\">Hello <strong>").append(safeCounselor).append("</strong>,</p>");
        body.append("<p style=\"font-size:14px; color:#4B5563; margin-bottom:20px;\">A scheduled follow-up has been <strong>Cancelled</strong>.</p>");

        body.append("<div style=\"background-color:#FEF2F2; border:1px solid #FECACA; border-radius:8px; padding:18px; margin-bottom:24px;\">");
        body.append(buildTableRow("Student / Lead", safeStudent + " (" + safeLeadCode + ")"));
        body.append(buildTableRow("Originally Scheduled", formattedSched));
        body.append(buildTableRow("Cancelled At", "<span style=\"color:#DC2626; font-weight:600;\">" + formattedCancel + "</span>"));
        body.append(buildTableRow("Cancellation Reason", "<em style=\"color:#4B5563;\">\"" + safeRemarks + "\"</em>"));
        body.append("</div>");

        return wrapInBaseLayout("Follow-up Cancelled", "Follow-up Cancellation Notice", body.toString(), "View Lead", actionLink);
    }

    @Override
    public String buildDailyFollowUpReminderTemplate(
            String counselorName,
            String formattedDate,
            List<LeadFollowUp> followUps,
            String dashboardUrl) {

        String safeCounselor = escape(counselorName != null ? counselorName : "Counselor");
        String safeDate = escape(formattedDate != null ? formattedDate : LocalDate.now().format(DATE_FORMATTER));
        int count = followUps != null ? followUps.size() : 0;
        String actionLink = (dashboardUrl != null && !dashboardUrl.isBlank()) ? dashboardUrl : (frontendUrl + "/followups");

        StringBuilder body = new StringBuilder();
        body.append("<p style=\"font-size:15px; color:#374151; margin-bottom:12px;\">Good Morning <strong>").append(safeCounselor).append("</strong>,</p>");
        body.append("<p style=\"font-size:14px; color:#4B5563; margin-bottom:20px;\">You have <strong>").append(count).append(" follow-up").append(count == 1 ? "" : "s").append("</strong> scheduled for today (").append(safeDate).append("):</p>");

        body.append("<div style=\"border:1px solid #E5E7EB; border-radius:8px; overflow:hidden; margin-bottom:24px;\">");
        body.append("<table style=\"width:100%; border-collapse:collapse; text-align:left; font-size:13px;\">");
        body.append("<thead style=\"background-color:#F3F4F6; border-bottom:1px solid #E5E7EB; color:#374151;\">");
        body.append("<tr>");
        body.append("<th style=\"padding:10px 12px; font-weight:600;\">#</th>");
        body.append("<th style=\"padding:10px 12px; font-weight:600;\">Student / Lead</th>");
        body.append("<th style=\"padding:10px 12px; font-weight:600;\">Time</th>");
        body.append("<th style=\"padding:10px 12px; font-weight:600;\">Type / Status</th>");
        body.append("<th style=\"padding:10px 12px; font-weight:600;\">Course</th>");
        body.append("</tr>");
        body.append("</thead>");
        body.append("<tbody>");

        if (followUps != null && !followUps.isEmpty()) {
            int idx = 1;
            for (LeadFollowUp f : followUps) {
                String student = (f.getLead() != null && f.getLead().getFullName() != null) ? escape(f.getLead().getFullName()) : "Lead #" + idx;
                String time = f.getFollowUpDate() != null ? f.getFollowUpDate().format(TIME_FORMATTER) : "-";
                String statusName = (f.getLead() != null && f.getLead().getCurrentStatus() != null) ? escape(f.getLead().getCurrentStatus().getName()) : f.getStatus().getDisplayName();
                String course = (f.getLead() != null && f.getLead().getCourse() != null) ? escape(f.getLead().getCourse().getCourseName()) : (f.getLead() != null && f.getLead().getCourseInterested() != null ? escape(f.getLead().getCourseInterested()) : "-");

                String rowBg = (idx % 2 == 0) ? "#F9FAFB" : "#FFFFFF";
                body.append("<tr style=\"background-color:").append(rowBg).append("; border-bottom:1px solid #F3F4F6;\">");
                body.append("<td style=\"padding:10px 12px; color:#6B7280; font-weight:600;\">").append(idx).append("</td>");
                body.append("<td style=\"padding:10px 12px; font-weight:600; color:#111827;\">").append(student).append("</td>");
                body.append("<td style=\"padding:10px 12px; color:#2563EB; font-weight:600;\">").append(time).append("</td>");
                body.append("<td style=\"padding:10px 12px; color:#4B5563;\">").append(statusName).append("</td>");
                body.append("<td style=\"padding:10px 12px; color:#6B7280;\">").append(course).append("</td>");
                body.append("</tr>");
                idx++;
            }
        }

        body.append("</tbody>");
        body.append("</table>");
        body.append("</div>");

        body.append("<p style=\"font-size:14px; color:#4B5563;\">Please ensure all follow-ups are completed and updated with appropriate feedback in the CRM today.</p>");

        return wrapInBaseLayout("Today's Follow-up Reminder", "Daily Follow-up Digest – " + safeDate, body.toString(), "View Today's Follow-ups", actionLink);
    }

    @Override
    public String buildDataAllocatedTemplate(
            String counselorName,
            int totalAllocated,
            String departmentName,
            String allocatedByName,
            LocalDateTime allocationTime,
            String crmUrl) {

        String safeCounselor = escape(counselorName != null ? counselorName : "Counselor");
        String safeDept = escape(departmentName != null ? departmentName : "General Department");
        String safeAllocatedBy = escape(allocatedByName != null ? allocatedByName : "Admin");
        String formattedTime = allocationTime != null ? allocationTime.format(DATE_TIME_FORMATTER) : LocalDateTime.now().format(DATE_TIME_FORMATTER);
        String actionLink = (crmUrl != null && !crmUrl.isBlank()) ? crmUrl : (frontendUrl + "/leads");

        StringBuilder body = new StringBuilder();
        body.append("<p style=\"font-size:15px; color:#374151; margin-bottom:20px;\">Hello <strong>").append(safeCounselor).append("</strong>,</p>");
        body.append("<p style=\"font-size:14px; color:#4B5563; margin-bottom:20px;\">New lead data has been allocated to you in the CRM.</p>");

        body.append("<div style=\"background-color:#F0FDF4; border:1px solid #BBF7D0; border-radius:8px; padding:18px; margin-bottom:24px;\">");
        body.append(buildTableRow("Total Leads Allocated", "<span style=\"color:#15803D; font-size:18px; font-weight:700;\">" + totalAllocated + "</span>"));
        body.append(buildTableRow("Department", safeDept));
        body.append(buildTableRow("Allocated By", safeAllocatedBy));
        body.append(buildTableRow("Allocation Time", formattedTime));
        body.append("</div>");

        body.append("<p style=\"font-size:14px; color:#4B5563;\">Please log in to the CRM to review your new leads and initiate candidate engagement.</p>");

        return wrapInBaseLayout("New Leads Allocated", "Data Allocation Notification", body.toString(), "Open Allocated Leads in CRM", actionLink);
    }

    @Override
    public String buildDataReassignedTemplate(
            String counselorName,
            int totalReassigned,
            String previousOwnerName,
            String reassignedByName,
            String departmentName,
            String reason,
            LocalDateTime reassignmentTime,
            String crmUrl) {

        String safeCounselor = escape(counselorName != null ? counselorName : "Counselor");
        String safeDept = escape(departmentName != null ? departmentName : "Department");
        String safePrev = escape(previousOwnerName != null ? previousOwnerName : "Previous Assignee");
        String safeAdmin = escape(reassignedByName != null ? reassignedByName : "Admin");
        String safeReason = escape(reason != null ? reason : "Lead reassignment");
        String formattedTime = reassignmentTime != null ? reassignmentTime.format(DATE_TIME_FORMATTER) : LocalDateTime.now().format(DATE_TIME_FORMATTER);
        String actionLink = (crmUrl != null && !crmUrl.isBlank()) ? crmUrl : (frontendUrl + "/leads");

        StringBuilder body = new StringBuilder();
        body.append("<p style=\"font-size:15px; color:#374151; margin-bottom:20px;\">Hello <strong>").append(safeCounselor).append("</strong>,</p>");
        body.append("<p style=\"font-size:14px; color:#4B5563; margin-bottom:20px;\">Lead data has been reassigned to your queue.</p>");

        body.append("<div style=\"background-color:#EFF6FF; border:1px solid #BFDBFE; border-radius:8px; padding:18px; margin-bottom:24px;\">");
        body.append(buildTableRow("Leads Reassigned", "<span style=\"color:#1D4ED8; font-size:18px; font-weight:700;\">" + totalReassigned + "</span>"));
        body.append(buildTableRow("Previously Assigned To", safePrev));
        body.append(buildTableRow("Reassigned By", safeAdmin));
        body.append(buildTableRow("Department", safeDept));
        body.append(buildTableRow("Reassignment Time", formattedTime));
        body.append(buildTableRow("Reason / Remarks", "<em style=\"color:#4B5563;\">\"" + safeReason + "\"</em>"));
        body.append("</div>");

        body.append("<p style=\"font-size:14px; color:#4B5563;\">Please review your updated lead list in the CRM to follow up with the assigned candidates.</p>");

        return wrapInBaseLayout("Leads Reassigned To You", "Lead Reassignment Notice", body.toString(), "Review Assigned Leads", actionLink);
    }

    @Override
    public String buildGenericEmailTemplate(
            String recipientName,
            String title,
            String content,
            String actionText,
            String actionUrl) {

        String safeRecipient = escape(recipientName != null ? recipientName : "User");
        String safeTitle = escape(title != null ? title : "Notification");
        String safeContent = content != null ? content : "";
        String safeActionText = escape(actionText != null ? actionText : "Open CRM");
        String safeActionUrl = (actionUrl != null && !actionUrl.isBlank()) ? actionUrl : frontendUrl;

        StringBuilder body = new StringBuilder();
        body.append("<p style=\"font-size:15px; color:#374151; margin-bottom:20px;\">Hello <strong>").append(safeRecipient).append("</strong>,</p>");
        body.append("<div style=\"font-size:14px; color:#4B5563; line-height:1.6; margin-bottom:24px;\">");
        body.append(safeContent);
        body.append("</div>");

        return wrapInBaseLayout(safeTitle, safeTitle, body.toString(), safeActionText, safeActionUrl);
    }

    // =========================================================================
    // Base HTML Layout & Helpers
    // =========================================================================

    private String wrapInBaseLayout(String pageTitle, String headerTitle, String bodyHtml, String buttonText, String buttonUrl) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>");
        html.append("<html lang=\"en\">");
        html.append("<head>");
        html.append("<meta charset=\"UTF-8\">");
        html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        html.append("<title>").append(escape(pageTitle)).append("</title>");
        html.append("</head>");
        html.append("<body style=\"margin:0; padding:0; background-color:#F3F4F6; font-family:-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;\">");

        html.append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background-color:#F3F4F6; padding:30px 10px;\">");
        html.append("<tr><td align=\"center\">");

        // Main Container
        html.append("<table width=\"600\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:600px; width:100%; background-color:#FFFFFF; border-radius:12px; box-shadow:0 4px 6px -1px rgba(0,0,0,0.1), 0 2px 4px -1px rgba(0,0,0,0.06); overflow:hidden;\">");

        // Header
        html.append("<tr><td style=\"background: linear-gradient(135deg, #1E3A8A 0%, #2563EB 100%); padding:28px 32px; text-align:left;\">");
        html.append("<div style=\"font-size:12px; font-weight:700; color:#93C5FD; text-transform:uppercase; letter-spacing:1px; margin-bottom:6px;\">Renaissance University CRM</div>");
        html.append("<h1 style=\"margin:0; font-size:22px; font-weight:700; color:#FFFFFF; line-height:1.3;\">").append(escape(headerTitle)).append("</h1>");
        html.append("</td></tr>");

        // Content Body
        html.append("<tr><td style=\"padding:32px;\">");
        html.append(bodyHtml);

        // CTA Button
        if (buttonText != null && !buttonText.isBlank() && buttonUrl != null && !buttonUrl.isBlank()) {
            html.append("<div style=\"text-align:center; margin-top:30px; margin-bottom:10px;\">");
            html.append("<a href=\"").append(escape(buttonUrl)).append("\" style=\"display:inline-block; background-color:#2563EB; color:#FFFFFF; font-size:14px; font-weight:600; text-decoration:none; padding:12px 28px; border-radius:6px; box-shadow:0 2px 4px rgba(37,99,235,0.2);\">");
            html.append(escape(buttonText));
            html.append("</a>");
            html.append("</div>");
        }

        html.append("</td></tr>");

        // Footer
        html.append("<tr><td style=\"background-color:#F9FAFB; border-top:1px solid #E5E7EB; padding:20px 32px; text-align:center; font-size:12px; color:#6B7280;\">");
        html.append("<p style=\"margin:0 0 6px 0;\">This is an automated notification from <strong>Renaissance University CRM</strong>.</p>");
        html.append("<p style=\"margin:0;\">Please do not reply directly to this email.</p>");
        html.append("</td></tr>");

        html.append("</table>"); // End container
        html.append("</td></tr></table>"); // End outer table
        html.append("</body></html>");

        return html.toString();
    }

    private String buildTableRow(String label, String value) {
        return "<div style=\"display:flex; justify-content:space-between; padding:6px 0; border-bottom:1px dashed #E5E7EB; font-size:13px;\">"
             + "<span style=\"color:#6B7280; font-weight:500; min-width:140px;\">" + label + ":</span>"
             + "<span style=\"color:#111827; text-align:right; font-weight:500;\">" + value + "</span>"
             + "</div>";
    }

    private String escape(String text) {
        if (text == null) return "";
        return HtmlUtils.htmlEscape(text);
    }
}
