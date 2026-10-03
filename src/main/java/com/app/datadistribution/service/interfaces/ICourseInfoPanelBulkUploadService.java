package com.app.datadistribution.service.interfaces;

import java.util.UUID;

import org.springframework.web.multipart.MultipartFile;

import com.app.datadistribution.dto.infopanel.CourseInfoPanelBulkUploadResponseDTO;
import com.app.datadistribution.dto.infopanel.CourseInfoPanelPreviewResponseDTO;
import com.app.datadistribution.exception.BadRequestException;

public interface ICourseInfoPanelBulkUploadService {

    byte[] generateTemplate();

    CourseInfoPanelPreviewResponseDTO validateExcel(MultipartFile file) throws BadRequestException;

    CourseInfoPanelBulkUploadResponseDTO bulkUpload(MultipartFile file) throws BadRequestException;

    byte[] getErrorFile(UUID importId);
}
