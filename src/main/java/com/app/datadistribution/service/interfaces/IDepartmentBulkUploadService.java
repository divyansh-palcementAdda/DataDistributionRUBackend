package com.app.datadistribution.service.interfaces;

import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;
import com.app.datadistribution.dto.department.DepartmentBulkUploadPreviewResponseDTO;
import com.app.datadistribution.dto.department.DepartmentBulkUploadResponseDTO;
import com.app.datadistribution.exception.BadRequestException;

public interface IDepartmentBulkUploadService {

    byte[] generateTemplate();

    DepartmentBulkUploadPreviewResponseDTO validateExcel(MultipartFile file) throws BadRequestException;

    DepartmentBulkUploadResponseDTO bulkUpload(MultipartFile file) throws BadRequestException;

    byte[] getErrorFile(UUID importId);
}
