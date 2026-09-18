package com.app.datadistribution.service.interfaces;

import com.app.datadistribution.dto.lead.CourseLeadMatrixRowDTO;
import com.app.datadistribution.dto.lead.LeadMatrixResponseDTO;
import com.app.datadistribution.dto.lead.ProgramLeadMatrixRowDTO;
import com.app.datadistribution.exception.UnauthorizedException;
import java.util.UUID;

public interface IUserLeadMatrixService {

    /**
     * Returns a course-wise lead status matrix for the given target user.
     * Rows = distinct courses from Lead.interestedCourses assigned to targetUserId.
     * Columns = all active LeadStatus records ordered by displayOrder.
     *
     * @param targetUserId the user whose matrix is requested
     * @return matrix response with dynamic status columns and course rows
     * @throws UnauthorizedException if the calling user is not authorized to view this user's data
     */
    LeadMatrixResponseDTO<CourseLeadMatrixRowDTO> getCourseMatrix(UUID targetUserId) throws UnauthorizedException;

    /**
     * Returns a program-wise lead status matrix for the given target user.
     * Rows = distinct programs mapped to courses from Lead.interestedCourses assigned to targetUserId.
     * Columns = all active LeadStatus records ordered by displayOrder.
     *
     * @param targetUserId the user whose matrix is requested
     * @return matrix response with dynamic status columns and program rows
     * @throws UnauthorizedException if the calling user is not authorized to view this user's data
     */
    LeadMatrixResponseDTO<ProgramLeadMatrixRowDTO> getProgramMatrix(UUID targetUserId) throws UnauthorizedException;
}
