package com.app.datadistribution.service.interfaces;

import com.app.datadistribution.dto.courseimage.CourseImageDTO;
import com.app.datadistribution.exception.BadRequestException;
import java.util.List;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public interface ICourseImageService {
    CourseImageDTO uploadImage(UUID courseId, MultipartFile file, String displayName, Integer displayOrder) throws BadRequestException;
    CourseImageDTO updateImage(UUID imageId, String displayName, Integer displayOrder, Boolean active);
    List<CourseImageDTO> getImagesByCourseId(UUID courseId, boolean activeOnly);
    CourseImageDTO getById(UUID imageId);
    void deleteImage(UUID imageId);
}
