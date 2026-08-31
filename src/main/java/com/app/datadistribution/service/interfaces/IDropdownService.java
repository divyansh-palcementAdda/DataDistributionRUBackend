package com.app.datadistribution.service.interfaces;

import com.app.datadistribution.dto.dropdown.CourseDropdownResponse;
import com.app.datadistribution.dto.dropdown.DropdownOptionResponse;
import com.app.datadistribution.dto.dropdown.DropdownPageResponse;
import com.app.datadistribution.dto.dropdown.LeadDropdownResponse;
import com.app.datadistribution.dto.dropdown.LeadStatusDropdownResponse;
import com.app.datadistribution.dto.dropdown.UserDropdownResponse;
import com.app.datadistribution.enums.SentimentCategory;
import com.app.datadistribution.exception.AccessDeniedException;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.UnauthorizedException;
import java.util.List;
import java.util.UUID;

/**
 * Service interface for ultra-lightweight, RBAC-aware, and data-scoped dropdown queries.
 */
public interface IDropdownService {

    List<UserDropdownResponse> getUsersDropdown(UUID departmentId, String role, String search) throws UnauthorizedException, AccessDeniedException, BadRequestException;

    List<DropdownOptionResponse> getDepartmentsDropdown(String search) throws UnauthorizedException, AccessDeniedException, BadRequestException;

    DropdownPageResponse<LeadDropdownResponse> getLeadsDropdown(int page, int size, String search, UUID departmentId, UUID statusId) throws UnauthorizedException, AccessDeniedException, BadRequestException;

    List<LeadStatusDropdownResponse> getLeadStatusesDropdown(SentimentCategory sentimentCategory);

    List<DropdownOptionResponse> getLeadSourcesDropdown(String search);

    List<CourseDropdownResponse> getCoursesDropdown(UUID courseTypeId, String search);

    List<DropdownOptionResponse> getCourseTypesDropdown(String search);

    List<DropdownOptionResponse> getBoardsDropdown(String search);

    List<DropdownOptionResponse> getGradesDropdown(String search);

    List<DropdownOptionResponse> getRolesDropdown(String search);

    List<DropdownOptionResponse> getPermissionsDropdown(String search);
    List<com.app.datadistribution.dto.dropdown.FollowUpStatusDropdownResponse> getFollowUpStatusesDropdown();
    List<LeadStatusDropdownResponse> getFollowUpLeadStatusesDropdown();
}
