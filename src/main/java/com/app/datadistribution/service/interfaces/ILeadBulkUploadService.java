package com.app.datadistribution.service.interfaces;

import com.app.datadistribution.dto.lead.BulkLeadUploadResponse;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.UnauthorizedException;
import java.util.List;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public interface ILeadBulkUploadService {

    BulkLeadUploadResponse bulkUploadLeads(
            MultipartFile file,
            UUID programId,
            UUID courseTypeId,
            UUID gradeId,
            UUID boardId,
            UUID leadSourceId,
            List<UUID> leadSourceIds,
            UUID statusId,
            UUID departmentId,
            UUID assignedToUserId) throws BadRequestException, UnauthorizedException;

    default BulkLeadUploadResponse bulkUploadLeads(
            MultipartFile file,
            UUID courseTypeId,
            UUID gradeId,
            UUID boardId,
            UUID leadSourceId,
            List<UUID> leadSourceIds,
            UUID statusId,
            UUID departmentId,
            UUID assignedToUserId) throws BadRequestException, UnauthorizedException {
        return bulkUploadLeads(file, null, courseTypeId, gradeId, boardId, leadSourceId, leadSourceIds, statusId, departmentId, assignedToUserId);
    }

    byte[] downloadTemplate();
}
