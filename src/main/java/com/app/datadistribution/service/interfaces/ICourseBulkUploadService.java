package com.app.datadistribution.service.interfaces;

import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;
import com.app.datadistribution.dto.course.CourseBulkUploadPreviewResponseDTO;
import com.app.datadistribution.dto.course.CourseBulkUploadResponseDTO;
import com.app.datadistribution.exception.BadRequestException;

public interface ICourseBulkUploadService {

    byte[] generateTemplate();

    CourseBulkUploadPreviewResponseDTO validateExcel(MultipartFile file) throws BadRequestException;

    CourseBulkUploadResponseDTO bulkUpload(MultipartFile file) throws BadRequestException;

    byte[] getErrorFile(UUID importId);
}
