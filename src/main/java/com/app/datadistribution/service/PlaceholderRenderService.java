package com.app.datadistribution.service;

import com.app.datadistribution.entity.Course;
import com.app.datadistribution.entity.CourseUSP;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.User;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PlaceholderRenderService {

    public String render(String templateContent, Lead lead, Course course, List<CourseUSP> usps, User counsellor) {
        return render(templateContent, lead, course, null, usps, counsellor);
    }

    public String render(String templateContent, Lead lead, Course course, CourseUSP selectedUsp, List<CourseUSP> usps, User counsellor) {
        if (templateContent == null || templateContent.isBlank()) {
            return "";
        }

        String result = templateContent;

        if (lead != null) {
            String fullName = lead.getFullName() != null ? lead.getFullName() : "Valued Student";
            String firstName = fullName.split(" ")[0];

            result = result.replace("{{lead.name}}", fullName)
                           .replace("{{lead.fullName}}", fullName)
                           .replace("{{lead.firstName}}", firstName)
                           .replace("{{leadName}}", fullName)
                           .replace("{{studentName}}", fullName);
        }

        if (course != null) {
            String courseName = course.getCourseName() != null ? course.getCourseName() : "";
            String courseCode = course.getCourseCode() != null ? course.getCourseCode() : "";
            String courseDesc = course.getDescription() != null ? course.getDescription() : "";

            result = result.replace("{{course.name}}", courseName)
                           .replace("{{course.code}}", courseCode)
                           .replace("{{courseName}}", courseName)
                           .replace("{{courseDescription}}", courseDesc);

            if (course.getCourseType() != null && course.getCourseType().getName() != null) {
                result = result.replace("{{courseType.name}}", course.getCourseType().getName());
            } else {
                result = result.replace("{{courseType.name}}", "");
            }
        }

        if (selectedUsp != null) {
            String uspContent = selectedUsp.getContent() != null ? selectedUsp.getContent() : "";
            result = result.replace("{{usp.content}}", uspContent)
                           .replace("{{uspName}}", uspContent)
                           .replace("{{uspContent}}", uspContent)
                           .replace("{{subject}}", uspContent)
                           .replace("{{usp.subject}}", uspContent)
                           .replace("{{uspDescription}}", uspContent);
        }

        if (usps != null && !usps.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (CourseUSP usp : usps) {
                if (usp != null && usp.isActive() && !usp.isDeleted()) {
                    sb.append("• ").append(usp.getContent()).append("\n");
                }
            }
            result = result.replace("{{course.usps}}", sb.toString().trim());
        } else if (selectedUsp != null && selectedUsp.getContent() != null) {
            result = result.replace("{{course.usps}}", "• " + selectedUsp.getContent());
        } else {
            result = result.replace("{{course.usps}}", "");
        }

        if (counsellor != null) {
            String counsellorName = counsellor.getFirstName() != null ?
                    counsellor.getFirstName() + (counsellor.getLastName() != null ? " " + counsellor.getLastName() : "") :
                    counsellor.getUsername();
            result = result.replace("{{counsellor.name}}", counsellorName)
                           .replace("{{counselor.name}}", counsellorName)
                           .replace("{{counsellorName}}", counsellorName)
                           .replace("{{counselorName}}", counsellorName);
        } else if (lead != null && lead.getAssignedTo() != null) {
            User assigned = lead.getAssignedTo();
            String counsellorName = assigned.getFirstName() != null ?
                    assigned.getFirstName() + (assigned.getLastName() != null ? " " + assigned.getLastName() : "") :
                    assigned.getUsername();
            result = result.replace("{{counsellor.name}}", counsellorName)
                           .replace("{{counselor.name}}", counsellorName)
                           .replace("{{counsellorName}}", counsellorName)
                           .replace("{{counselorName}}", counsellorName);
        } else {
            result = result.replace("{{counsellor.name}}", "Academic Counselor")
                           .replace("{{counselor.name}}", "Academic Counselor")
                           .replace("{{counsellorName}}", "Academic Counselor")
                           .replace("{{counselorName}}", "Academic Counselor");
        }

        result = result.replace("{{institution.name}}", "DataDistribution Institute");

        // Clean up any remaining unpopulated placeholder tags
        result = result.replaceAll("\\{\\{[^}]+\\}\\}", "").trim();

        return result;
    }
}
