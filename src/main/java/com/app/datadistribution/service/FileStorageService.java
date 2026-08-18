package com.app.datadistribution.service;

import com.app.datadistribution.exception.BadRequestException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
public class FileStorageService {

    @Value("${app.file-storage.base-path:${APP_FILE_STORAGE_PATH:uploads}}")
    private String basePath;

    @Value("${app.file-storage.public-url:${APP_FILE_STORAGE_PUBLIC_URL:/uploads}}")
    private String publicUrl;

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final List<String> ALLOWED_CONTENT_TYPES = List.of(
            "image/png", "image/jpeg", "image/jpg", "image/webp"
    );
    private static final List<String> ALLOWED_EXTENSIONS = List.of(
            ".png", ".jpeg", ".jpg", ".webp"
    );

    public Path getStorageBasePath() {
        Path path = Paths.get(basePath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            log.error("Failed to create storage base directory: {}", path, e);
        }
        return path;
    }

    public String storeFile(MultipartFile file, String subFolder) throws BadRequestException {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Cannot upload an empty file");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BadRequestException("File size exceeds maximum allowed limit of 5MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new BadRequestException("Unsupported image format. Allowed formats: PNG, JPEG, JPG, WEBP");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        }

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BadRequestException("Unsupported file extension: " + extension);
        }

        String uniqueFileName = UUID.randomUUID().toString() + extension;

        Path baseRoot = getStorageBasePath();
        Path targetDir = baseRoot.resolve(subFolder).normalize();

        // Path Traversal Security Check
        if (!targetDir.startsWith(baseRoot)) {
            throw new BadRequestException("Invalid storage path detected");
        }

        try {
            Files.createDirectories(targetDir);
            Path targetLocation = targetDir.resolve(uniqueFileName).normalize();
            if (!targetLocation.startsWith(baseRoot)) {
                throw new BadRequestException("Invalid file location path detected");
            }

            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            String relativePath = subFolder.endsWith("/") ? subFolder + uniqueFileName : subFolder + "/" + uniqueFileName;
            String cleanPublicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;

            log.info("Physically stored file on VPS filesystem at: {}", targetLocation);
            return cleanPublicUrl + "/" + relativePath;
        } catch (IOException ex) {
            log.error("Error writing file to VPS storage: {}", ex.getMessage(), ex);
            throw new BadRequestException("Could not save image to server storage. Please try again!");
        }
    }

    public boolean deleteFile(String storageUrlOrKey) {
        if (storageUrlOrKey == null || storageUrlOrKey.isBlank()) {
            return false;
        }
        try {
            String cleanPublicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
            String relativePath = storageUrlOrKey.replace(cleanPublicUrl, "");
            if (relativePath.startsWith("/")) {
                relativePath = relativePath.substring(1);
            }

            Path filePath = getStorageBasePath().resolve(relativePath).normalize();

            // Path Traversal Security Check
            if (!filePath.startsWith(getStorageBasePath())) {
                log.warn("Attempted path traversal deletion: {}", storageUrlOrKey);
                return false;
            }

            boolean deleted = Files.deleteIfExists(filePath);
            if (deleted) {
                log.info("Physically deleted file from VPS storage: {}", filePath);
            }
            return deleted;
        } catch (IOException e) {
            log.warn("Could not delete physical file: {}", storageUrlOrKey, e);
            return false;
        }
    }

    public boolean exists(String storageUrlOrKey) {
        if (storageUrlOrKey == null || storageUrlOrKey.isBlank()) {
            return false;
        }
        try {
            String cleanPublicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
            String relativePath = storageUrlOrKey.replace(cleanPublicUrl, "");
            if (relativePath.startsWith("/")) {
                relativePath = relativePath.substring(1);
            }
            Path filePath = getStorageBasePath().resolve(relativePath).normalize();
            return Files.exists(filePath);
        } catch (Exception e) {
            return false;
        }
    }

    public String getPublicUrl(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) return "";
        String cleanPublicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
        String cleanRel = relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;
        return cleanPublicUrl + "/" + cleanRel;
    }
}
