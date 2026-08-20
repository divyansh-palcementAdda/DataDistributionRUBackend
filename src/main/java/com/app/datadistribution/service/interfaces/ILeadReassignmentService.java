package com.app.datadistribution.service.interfaces;

import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.lead.LeadResponse;
import com.app.datadistribution.dto.reassign.*;
import com.app.datadistribution.enums.FollowUpStatus;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.exception.UnauthorizedException;
import java.time.LocalDate;
import java.util.UUID;

public interface ILeadReassignmentService {

    ReassignablePageResponseDTO<FollowUpReassignableDTO> getReassignableFollowUps(
            UUID responsibleUserId,
            LocalDate scheduledDate,
            LocalDate fromDate,
            LocalDate toDate,
            FollowUpStatus status,
            PageRequestDTO pageRequest) throws UnauthorizedException, BadRequestException, ResourcesNotFoundException;

    FollowUpReassignResponse reassignFollowUps(FollowUpReassignRequest request)
            throws UnauthorizedException, BadRequestException, ResourcesNotFoundException;

    ReassignablePageResponseDTO<LeadResponse> getReassignableLeads(
            UUID assignedUserId,
            UUID courseTypeId,
            UUID gradeId,
            UUID boardId,
            UUID leadSourceId,
            UUID statusId,
            UUID departmentId,
            PageRequestDTO pageRequest) throws UnauthorizedException, BadRequestException, ResourcesNotFoundException;

    LeadReassignResponse reassignLeads(LeadReassignRequest request)
            throws UnauthorizedException, BadRequestException, ResourcesNotFoundException;
}
