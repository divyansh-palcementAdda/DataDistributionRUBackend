package com.app.datadistribution.controller;

import com.app.datadistribution.common.ApiResponse;
import com.app.datadistribution.dto.user.AssignPermissionsRequest;
import com.app.datadistribution.dto.user.PermissionDTO;
import com.app.datadistribution.dto.user.RoleDTO;
import com.app.datadistribution.dto.user.RoleRequest;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.service.interfaces.IRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
@Tag(name = "Role Management", description = "Endpoints for managing roles and their permissions")
public class RoleController {

    private final IRoleService roleService;

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_READ')")
    @Operation(summary = "Get all roles")
    public ResponseEntity<ApiResponse<List<RoleDTO>>> getAllRoles() {
        List<RoleDTO> roles = roleService.getAll();
        return ResponseEntity.ok(ApiResponse.success("Roles retrieved successfully", roles, HttpStatus.OK.value()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_READ')")
    @Operation(summary = "Get role details by ID")
    public ResponseEntity<ApiResponse<RoleDTO>> getRoleById(@PathVariable("id") UUID id) throws ResourcesNotFoundException {
        RoleDTO role = roleService.getById(id);
        return ResponseEntity.ok(ApiResponse.success("Role details retrieved successfully", role, HttpStatus.OK.value()));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_CREATE')")
    @Operation(summary = "Create role")
    public ResponseEntity<ApiResponse<RoleDTO>> createRole(@Valid @RequestBody RoleRequest request) throws BadRequestException {
        RoleDTO role = roleService.createRole(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Role created successfully", role, HttpStatus.CREATED.value()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_UPDATE')")
    @Operation(summary = "Update role")
    public ResponseEntity<ApiResponse<RoleDTO>> updateRole(@PathVariable("id") UUID id, @Valid @RequestBody RoleRequest request) 
            throws ResourcesNotFoundException, BadRequestException {
        RoleDTO role = roleService.updateRole(id, request);
        return ResponseEntity.ok(ApiResponse.success("Role updated successfully", role, HttpStatus.OK.value()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_DELETE')")
    @Operation(summary = "Soft delete role")
    public ResponseEntity<ApiResponse<Void>> deleteRole(@PathVariable("id") UUID id) throws ResourcesNotFoundException, BadRequestException {
        roleService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Role soft deleted successfully", null, HttpStatus.OK.value()));
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('ROLE_UPDATE')")
    @Operation(summary = "Activate role")
    public ResponseEntity<ApiResponse<RoleDTO>> activateRole(@PathVariable("id") UUID id) throws ResourcesNotFoundException {
        RoleDTO role = roleService.activateRole(id);
        return ResponseEntity.ok(ApiResponse.success("Role activated successfully", role, HttpStatus.OK.value()));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('ROLE_UPDATE')")
    @Operation(summary = "Deactivate role")
    public ResponseEntity<ApiResponse<RoleDTO>> deactivateRole(@PathVariable("id") UUID id) throws ResourcesNotFoundException {
        RoleDTO role = roleService.deactivateRole(id);
        return ResponseEntity.ok(ApiResponse.success("Role deactivated successfully", role, HttpStatus.OK.value()));
    }

    @PostMapping("/{roleId}/permissions")
    @PreAuthorize("hasAuthority('ROLE_ASSIGN_PERMISSION')")
    @Operation(summary = "Assign permissions to role")
    public ResponseEntity<ApiResponse<Void>> assignPermissions(
            @PathVariable("roleId") UUID roleId,
            @Valid @RequestBody AssignPermissionsRequest request) throws ResourcesNotFoundException, BadRequestException {
        roleService.assignPermissions(roleId, request.getPermissionIds());
        return ResponseEntity.ok(ApiResponse.success("Permissions assigned successfully", null, HttpStatus.OK.value()));
    }

    @DeleteMapping("/{roleId}/permissions/{permissionId}")
    @PreAuthorize("hasAuthority('ROLE_ASSIGN_PERMISSION')")
    @Operation(summary = "Remove permission from role")
    public ResponseEntity<ApiResponse<Void>> removePermissionFromRole(
            @PathVariable("roleId") UUID roleId,
            @PathVariable("permissionId") UUID permissionId) throws ResourcesNotFoundException, BadRequestException {
        roleService.removePermissionFromRole(roleId, permissionId);
        return ResponseEntity.ok(ApiResponse.success("Permission removed from role successfully", null, HttpStatus.OK.value()));
    }

    @GetMapping("/{roleId}/permissions")
    @PreAuthorize("hasAuthority('ROLE_READ')")
    @Operation(summary = "Get permissions by role")
    public ResponseEntity<ApiResponse<List<PermissionDTO>>> getPermissionsByRole(@PathVariable("roleId") UUID roleId) throws ResourcesNotFoundException {
        List<PermissionDTO> permissions = roleService.getRolePermissions(roleId);
        return ResponseEntity.ok(ApiResponse.success("Permissions retrieved successfully", permissions, HttpStatus.OK.value()));
    }
}
