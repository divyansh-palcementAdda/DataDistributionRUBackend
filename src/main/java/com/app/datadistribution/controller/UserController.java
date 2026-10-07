package com.app.datadistribution.controller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.app.datadistribution.common.ApiResponse;
import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.user.UserPageResponse;
import com.app.datadistribution.dto.user.UserRequest;
import com.app.datadistribution.dto.user.UserResponse;
import com.app.datadistribution.dto.user.UserUpdateRequest;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.dto.user.UserPerformanceFilterRequest;
import com.app.datadistribution.dto.user.UserPerformancePageResponse;
import com.app.datadistribution.service.interfaces.IUserPerformanceService;
import com.app.datadistribution.service.interfaces.IUserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import com.app.datadistribution.dto.user.UserBulkUploadPreviewResponseDTO;
import com.app.datadistribution.dto.user.UserBulkUploadResponseDTO;
import com.app.datadistribution.service.interfaces.IUserBulkUploadService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "Endpoints for managing system users")
public class UserController {

    private final IUserService userService;
    private final IUserPerformanceService userPerformanceService;
    private final IUserBulkUploadService bulkUploadService;

    @GetMapping("/performance")
    @PreAuthorize("hasAnyAuthority('USER_READ', 'DEPARTMENT_COUNSELLOR_VIEW', 'USER_ACTIVITY_VIEW', 'DASHBOARD_VIEW')")
    @Operation(summary = "Get consolidated user performance and operational analytics")
    public ResponseEntity<ApiResponse<UserPerformancePageResponse>> getUserPerformance(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "sortBy", defaultValue = "userName") String sortBy,
            @RequestParam(value = "sortDirection", defaultValue = "ASC") String sortDirection,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "role", required = false) String role,
            @RequestParam(value = "roles", required = false) List<String> roles,
            @RequestParam(value = "departmentId", required = false) UUID departmentId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "currentlyWorking", required = false) Boolean currentlyWorking) throws UnauthorizedException, BadRequestException {

        UserPerformanceFilterRequest filterRequest = UserPerformanceFilterRequest.builder()
                .page(page)
                .size(size)
                .sortBy(sortBy)
                .sortDirection(sortDirection)
                .search(search)
                .role(role)
                .roles(roles)
                .departmentId(departmentId)
                .status(status)
                .currentlyWorking(currentlyWorking)
                .build();

        UserPerformancePageResponse response = userPerformanceService.getUserPerformance(filterRequest);
        return ResponseEntity.ok(ApiResponse.success("User performance retrieved successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Get list of users with pagination, sorting, and search filtering")
    public ResponseEntity<ApiResponse<UserPageResponse>> getUsers(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "sortBy", defaultValue = "id") String sortBy,
            @RequestParam(value = "sortDirection", defaultValue = "ASC") String sortDirection,
            @RequestParam(value = "search", required = false) String search) {
        
        PageRequestDTO request = PageRequestDTO.builder()
                .page(page)
                .size(size)
                .sortBy(sortBy)
                .sortDirection(sortDirection)
                .search(search)
                .build();
        
        UserPageResponse response = userService.getUsers(request);
        return ResponseEntity.ok(ApiResponse.success("Users retrieved successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/by-role")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Get users filtered by one or more roles with pagination, sorting, search, and status filtering")
    public ResponseEntity<ApiResponse<UserPageResponse>> getUsersByRole(
            @RequestParam(value = "roleName", required = false) String roleName,
            @RequestParam(value = "roleNames", required = false) String roleNames,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "sortBy", defaultValue = "id") String sortBy,
            @RequestParam(value = "sortDirection", defaultValue = "ASC") String sortDirection,
            @RequestParam(value = "search", required = false) String search) throws BadRequestException, ResourcesNotFoundException {

        List<String> resolvedRoleNames = resolveRoleNames(roleName, roleNames);

        PageRequestDTO request = PageRequestDTO.builder()
                .page(page)
                .size(size)
                .sortBy(sortBy)
                .sortDirection(sortDirection)
                .search(search)
                .build();

        UserPageResponse response = userService.getUsersByRoles(resolvedRoleNames, status, request);
        return ResponseEntity.ok(ApiResponse.success("Users retrieved successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/creation-options")
    @PreAuthorize("hasAuthority('USER_READ') or hasAuthority('USER_CREATE')")
    @Operation(summary = "Get user creation options including available roles and configuration status")
    public ResponseEntity<ApiResponse<com.app.datadistribution.dto.user.UserCreationOptionsResponse>> getUserCreationOptions() {
        com.app.datadistribution.dto.user.UserCreationOptionsResponse response = userService.getUserCreationOptions();
        return ResponseEntity.ok(ApiResponse.success("User creation options retrieved successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Get user details by ID")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable("id") UUID id) {
        UserResponse response = userService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.success("User fetched successfully", response, HttpStatus.OK.value()));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USER_CREATE')")
    @Operation(summary = "Create a new user")
    public ResponseEntity<ApiResponse<UserResponse>> createUser(@Valid @RequestBody UserRequest request) throws BadRequestException {
        UserResponse response = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("User created successfully", response, HttpStatus.CREATED.value()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(summary = "Update an existing user by ID")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UserUpdateRequest request) throws ResourcesNotFoundException, BadRequestException {
        UserResponse response = userService.updateUser(id, request);
        return ResponseEntity.ok(ApiResponse.success("User updated successfully", response, HttpStatus.OK.value()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_DELETE')")
    @Operation(summary = "Soft delete a user by ID")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable("id") UUID id) {
        userService.deleteUser(id);
        return ResponseEntity.ok(ApiResponse.success("User deleted successfully", null, HttpStatus.OK.value()));
    }

    @PatchMapping("/{userId}/role/{roleId}")
    @PreAuthorize("hasAuthority('USER_ROLE_ASSIGN')")
    @Operation(summary = "Change user role")
    public ResponseEntity<ApiResponse<Void>> assignRole(
            @PathVariable("userId") UUID userId,
            @PathVariable("roleId") UUID roleId) throws ResourcesNotFoundException, BadRequestException {
        userService.assignRole(userId, roleId);
        return ResponseEntity.ok(ApiResponse.success("User role updated successfully", null, HttpStatus.OK.value()));
    }

    private List<String> resolveRoleNames(String roleName, String roleNames) throws BadRequestException {
        List<String> resolved = new ArrayList<>();
        if (roleName != null && !roleName.isBlank()) {
            resolved.add(roleName.trim());
        }
        if (roleNames != null && !roleNames.isBlank()) {
            Arrays.stream(roleNames.split(","))
                    .map(String::trim)
                    .filter(name -> !name.isEmpty())
                    .forEach(resolved::add);
        }
        if (resolved.isEmpty()) {
            throw new BadRequestException("At least one role must be specified via roleName or roleNames");
        }
        return resolved.stream().distinct().collect(Collectors.toList());
    }

    @PostMapping(value = "/bulk-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('USER_BULK_UPLOAD') or hasAuthority('USER_CREATE')")
    @Operation(summary = "Bulk upload and create users mapped to departments from Excel")
    public ResponseEntity<ApiResponse<UserBulkUploadResponseDTO>> bulkUpload(
            @RequestParam("file") MultipartFile file) throws BadRequestException {
        UserBulkUploadResponseDTO response = bulkUploadService.bulkUpload(file);
        return ResponseEntity.ok(ApiResponse.success("User bulk upload completed successfully", response, HttpStatus.OK.value()));
    }

    @PostMapping(value = "/bulk-upload/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('USER_BULK_UPLOAD') or hasAuthority('USER_CREATE')")
    @Operation(summary = "Validate and preview User Excel import without committing changes")
    public ResponseEntity<ApiResponse<UserBulkUploadPreviewResponseDTO>> validateBulkUpload(
            @RequestParam("file") MultipartFile file) throws BadRequestException {
        UserBulkUploadPreviewResponseDTO response = bulkUploadService.validateExcel(file);
        return ResponseEntity.ok(ApiResponse.success("User Excel validation preview generated", response, HttpStatus.OK.value()));
    }

    @GetMapping("/bulk-upload/template")
    @PreAuthorize("hasAuthority('USER_BULK_UPLOAD_TEMPLATE_DOWNLOAD') or hasAuthority('USER_BULK_UPLOAD') or hasAuthority('USER_CREATE') or hasAuthority('USER_VIEW')")
    @Operation(summary = "Download official User bulk upload Excel template")
    public ResponseEntity<byte[]> downloadTemplate() {
        byte[] excelBytes = bulkUploadService.generateTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(ContentDisposition.attachment().filename("user_bulk_upload_template.xlsx").build());
        headers.setContentLength(excelBytes.length);
        return new ResponseEntity<>(excelBytes, headers, HttpStatus.OK);
    }

    @GetMapping("/bulk-upload/{importId}/error-file")
    @PreAuthorize("hasAuthority('USER_BULK_UPLOAD') or hasAuthority('USER_CREATE') or hasAuthority('USER_VIEW')")
    @Operation(summary = "Download error sheet for an executed or validated user bulk upload")
    public ResponseEntity<byte[]> downloadErrorFile(@PathVariable("importId") UUID importId) {
        byte[] excelBytes = bulkUploadService.getErrorFile(importId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(ContentDisposition.attachment().filename("user_bulk_upload_errors_" + importId + ".xlsx").build());
        headers.setContentLength(excelBytes.length);
        return new ResponseEntity<>(excelBytes, headers, HttpStatus.OK);
    }
}
