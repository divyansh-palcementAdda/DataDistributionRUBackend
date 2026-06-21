package com.app.datadistribution.controller;

import com.app.datadistribution.common.ApiResponse;
import com.app.datadistribution.dto.user.PermissionDTO;
import com.app.datadistribution.dto.user.PermissionRequest;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.service.interfaces.IPermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/permissions")
@RequiredArgsConstructor
@Tag(name = "Permission Management", description = "Endpoints for managing permissions")
public class PermissionController {

    private final IPermissionService permissionService;

    @GetMapping
    @PreAuthorize("hasAuthority('PERMISSION_READ')")
    @Operation(summary = "Get all permissions")
    public ResponseEntity<ApiResponse<List<PermissionDTO>>> getAllPermissions() {
        List<PermissionDTO> permissions = permissionService.getAll();
        return ResponseEntity.ok(ApiResponse.success("Permissions retrieved successfully", permissions, HttpStatus.OK.value()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERMISSION_READ')")
    @Operation(summary = "Get permission details by ID")
    public ResponseEntity<ApiResponse<PermissionDTO>> getPermissionById(@PathVariable("id") UUID id) throws ResourcesNotFoundException {
        PermissionDTO permission = permissionService.getById(id);
        return ResponseEntity.ok(ApiResponse.success("Permission details retrieved successfully", permission, HttpStatus.OK.value()));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERMISSION_CREATE')")
    @Operation(summary = "Create permission")
    public ResponseEntity<ApiResponse<PermissionDTO>> createPermission(@Valid @RequestBody PermissionRequest request) throws BadRequestException {
        PermissionDTO permission = permissionService.createPermission(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Permission created successfully", permission, HttpStatus.CREATED.value()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERMISSION_UPDATE')")
    @Operation(summary = "Update permission")
    public ResponseEntity<ApiResponse<PermissionDTO>> updatePermission(
            @PathVariable("id") UUID id,
            @Valid @RequestBody PermissionRequest request) throws ResourcesNotFoundException, BadRequestException {
        PermissionDTO permission = permissionService.updatePermission(id, request);
        return ResponseEntity.ok(ApiResponse.success("Permission updated successfully", permission, HttpStatus.OK.value()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERMISSION_DELETE')")
    @Operation(summary = "Soft delete permission")
    public ResponseEntity<ApiResponse<Void>> deletePermission(@PathVariable("id") UUID id) throws ResourcesNotFoundException, BadRequestException {
        permissionService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Permission soft deleted successfully", null, HttpStatus.OK.value()));
    }
}
