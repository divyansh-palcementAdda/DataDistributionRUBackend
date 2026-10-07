package com.app.datadistribution.service.interfaces;

import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;
import com.app.datadistribution.dto.user.UserBulkUploadPreviewResponseDTO;
import com.app.datadistribution.dto.user.UserBulkUploadResponseDTO;
import com.app.datadistribution.exception.BadRequestException;

public interface IUserBulkUploadService {

    byte[] generateTemplate();

    UserBulkUploadPreviewResponseDTO validateExcel(MultipartFile file) throws BadRequestException;

    UserBulkUploadResponseDTO bulkUpload(MultipartFile file) throws BadRequestException;

    byte[] getErrorFile(UUID importId);
}
