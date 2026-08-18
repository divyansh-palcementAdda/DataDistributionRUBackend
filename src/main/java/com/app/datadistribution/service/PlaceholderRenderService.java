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
        if (templateContent == null || templateContent.isBlank()) {
            return "";
        }

        String result = templateContent;

        if (lead != null) {
            String fullName = lead.getFullName() != null ? lead.getFullName() : "Valued Customer";
            String firstName = fullName.split(" ")[0];

            result = result.replace("{{lead.name}}", fullName)
                           .replace("{{lead.fullName}}", fullName)
                           .replace("{{lead.firstName}}", firstName);
        }

        if (course != null) {
            result = result.replace("{{course.name}}", course.getCourseName() != null ? course.getCourseName() : "")
                           .replace("{{course.code}}", course.getCourseCode() != null ? course.getCourseCode() : "");

            if (course.getCourseType() != null && course.getCourseType().getName() != null) {
                result = result.replace("{{courseType.name}}", course.getCourseType().getName());
            } else {
                result = result.replace("{{courseType.name}}", "");
            }
        }

        if (usps != null && !usps.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (CourseUSP usp : usps) {
                if (usp != null && usp.isActive() && !usp.isDeleted()) {
                    sb.append("• ").append(usp.getContent()).append("\n");
                }
            }
            result = result.replace("{{course.usps}}", sb.toString().trim());
        } else {
            result = result.replace("{{course.usps}}", "");
        }

        if (counsellor != null) {
            String counsellorName = counsellor.getFirstName() != null ?
                    counsellor.getFirstName() + (counsellor.getLastName() != null ? " " + counsellor.getLastName() : "") :
                    counsellor.getUsername();
            result = result.replace("{{counsellor.name}}", counsellorName);
        } else if (lead != null && lead.getAssignedTo() != null) {
            User assigned = lead.getAssignedTo();
            String counsellorName = assigned.getFirstName() != null ?
                    assigned.getFirstName() + (assigned.getLastName() != null ? " " + assigned.getLastName() : "") :
                    assigned.getUsername();
            result = result.replace("{{counsellor.name}}", counsellorName);
        } else {
            result = result.replace("{{counsellor.name}}", "Academic Counsellor");
        }

        result = result.replace("{{institution.name}}", "DataDistribution Institute");

        return result;
    }
}
