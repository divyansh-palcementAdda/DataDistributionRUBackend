package com.app.datadistribution.controller;

import com.app.datadistribution.common.ApiResponse;
import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.department.DepartmentRequest;
import com.app.datadistribution.dto.department.DepartmentResponse;
import com.app.datadistribution.dto.department.UserDepartmentAssignRequest;
import com.app.datadistribution.dto.user.UserResponse;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.service.interfaces.IDepartmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Department Management", description = "Endpoints for managing dynamic departments, multi-department user assignments, HOD hierarchy, and department-level visibility scopes")
public class DepartmentController {

    private final IDepartmentService departmentService;

    @PostMapping("/departments")
    @PreAuthorize("hasAuthority('DEPARTMENT_CREATE')")
    @Operation(
        summary = "Create a new department",
        description = "Creates a new master department with a unique name and code. System-wide Admin permissions required."
    )
    public ResponseEntity<ApiResponse<DepartmentResponse>> createDepartment(
            @Valid @RequestBody DepartmentRequest request)
            throws BadRequestException, UnauthorizedException {
        DepartmentResponse response = departmentService.createDepartment(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Department created successfully", response, HttpStatus.CREATED.value()));
    }

    @PutMapping("/departments/{id}")
    @PreAuthorize("hasAuthority('DEPARTMENT_UPDATE')")
    @Operation(
        summary = "Update an existing department",
        description = "Updates department details (name, code, description, active status) by ID. Requires DEPARTMENT_UPDATE authority."
    )
    public ResponseEntity<ApiResponse<DepartmentResponse>> updateDepartment(
            @Parameter(description = "UUID of the department to update", required = true)
            @PathVariable UUID id,
            @Valid @RequestBody DepartmentRequest request)
            throws BadRequestException, ResourcesNotFoundException, UnauthorizedException {
        DepartmentResponse response = departmentService.updateDepartment(id, request);
        return ResponseEntity.ok(ApiResponse.success("Department updated successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/departments/{id}")
    @PreAuthorize("hasAuthority('DEPARTMENT_VIEW')")
    @Operation(
        summary = "Get department details by ID",
        description = "Retrieves detailed information for a single department, including assigned user counts, HODs, and Counsellors."
    )
    public ResponseEntity<ApiResponse<DepartmentResponse>> getDepartmentById(
            @Parameter(description = "UUID of the department to fetch", required = true)
            @PathVariable UUID id)
            throws ResourcesNotFoundException, UnauthorizedException {
        DepartmentResponse response = departmentService.getDepartmentById(id);
        return ResponseEntity.ok(ApiResponse.success("Department fetched successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/departments")
    @PreAuthorize("hasAuthority('DEPARTMENT_VIEW')")
    @Operation(
        summary = "Get paginated departments list",
        description = "Fetches a paginated list of departments supporting sorting, search keyword filtering, and active status filtering."
    )
    public ResponseEntity<ApiResponse<Page<DepartmentResponse>>> getAllDepartments(
            @Parameter(description = "Page number (0-indexed)", example = "0")
            @RequestParam(required = false, defaultValue = "0") int page,
            @Parameter(description = "Page size limit", example = "10")
            @RequestParam(required = false, defaultValue = "10") int size,
            @Parameter(description = "Field name to sort by", example = "name")
            @RequestParam(required = false, defaultValue = "name") String sortBy,
            @Parameter(description = "Sort direction: ASC or DESC", example = "ASC")
            @RequestParam(required = false, defaultValue = "ASC") String sortDirection,
            @Parameter(description = "Filter by active status (true/false)")
            @RequestParam(required = false) Boolean active,
            @Parameter(description = "Search keyword (matches name, code, or description)")
            @RequestParam(required = false) String search) throws UnauthorizedException {
        PageRequestDTO pageRequest = PageRequestDTO.builder()
                .page(page)
                .size(size)
                .sortBy(sortBy)
                .sortDirection(sortDirection)
                .search(search)
                .build();
        Page<DepartmentResponse> pageResponse = departmentService.getAllDepartments(pageRequest, active, search);
        return ResponseEntity.ok(ApiResponse.success("Departments retrieved successfully", pageResponse, HttpStatus.OK.value()));
    }

    @GetMapping("/departments/active")
    @PreAuthorize("hasAuthority('DEPARTMENT_VIEW')")
    @Operation(
        summary = "Get all active departments",
        description = "Retrieves an unpaginated list of all active departments accessible within the current user's data scope."
    )
    public ResponseEntity<ApiResponse<List<DepartmentResponse>>> getAllActiveDepartments() throws UnauthorizedException {
        List<DepartmentResponse> list = departmentService.getAllActiveDepartments();
        return ResponseEntity.ok(ApiResponse.success("Active departments retrieved successfully", list, HttpStatus.OK.value()));
    }

    @DeleteMapping("/departments/{id}")
    @PreAuthorize("hasAuthority('DEPARTMENT_DELETE')")
    @Operation(
        summary = "Soft delete a department",
        description = "Soft-deletes a department by ID. Fails if active leads are associated with the department."
    )
    public ResponseEntity<ApiResponse<Void>> deleteDepartment(
            @Parameter(description = "UUID of the department to delete", required = true)
            @PathVariable UUID id)
            throws BadRequestException, ResourcesNotFoundException, UnauthorizedException {
        departmentService.deleteDepartment(id);
        return ResponseEntity.ok(ApiResponse.success("Department deleted successfully", null, HttpStatus.OK.value()));
    }

    @GetMapping("/departments/{id}/users")
    @PreAuthorize("hasAuthority('DEPARTMENT_USER_VIEW') or hasAuthority('DEPARTMENT_VIEW')")
    @Operation(
        summary = "Get all users in a department",
        description = "Retrieves all users (HODs, Counsellors, Callers) mapped to the specified department."
    )
    public ResponseEntity<ApiResponse<List<UserResponse>>> getDepartmentUsers(
            @Parameter(description = "UUID of the department", required = true)
            @PathVariable UUID id)
            throws ResourcesNotFoundException, UnauthorizedException {
        List<UserResponse> users = departmentService.getDepartmentUsers(id);
        return ResponseEntity.ok(ApiResponse.success("Department users retrieved successfully", users, HttpStatus.OK.value()));
    }

    @GetMapping("/departments/{id}/hods")
    @PreAuthorize("hasAuthority('DEPARTMENT_HOD_VIEW') or hasAuthority('DEPARTMENT_VIEW')")
    @Operation(
        summary = "Get HODs in a department",
        description = "Retrieves only Head of Department (HOD) users assigned to the specified department."
    )
    public ResponseEntity<ApiResponse<List<UserResponse>>> getDepartmentHods(
            @Parameter(description = "UUID of the department", required = true)
            @PathVariable UUID id)
            throws ResourcesNotFoundException, UnauthorizedException {
        List<UserResponse> hods = departmentService.getDepartmentHods(id);
        return ResponseEntity.ok(ApiResponse.success("Department HODs retrieved successfully", hods, HttpStatus.OK.value()));
    }

    @GetMapping("/departments/{id}/counsellors")
    @PreAuthorize("hasAuthority('DEPARTMENT_COUNSELLOR_VIEW') or hasAuthority('DEPARTMENT_VIEW')")
    @Operation(
        summary = "Get counsellors in a department",
        description = "Retrieves counsellor users assigned to the specified department."
    )
    public ResponseEntity<ApiResponse<List<UserResponse>>> getDepartmentCounsellors(
            @Parameter(description = "UUID of the department", required = true)
            @PathVariable UUID id)
            throws ResourcesNotFoundException, UnauthorizedException {
        List<UserResponse> counsellors = departmentService.getDepartmentCounsellors(id);
        return ResponseEntity.ok(ApiResponse.success("Department counsellors retrieved successfully", counsellors, HttpStatus.OK.value()));
    }

    @GetMapping("/users/{userId}/departments")
    @PreAuthorize("hasAuthority('DEPARTMENT_USER_VIEW') or hasAuthority('DEPARTMENT_VIEW')")
    @Operation(
        summary = "Get mapped departments for a user",
        description = "Retrieves all departments mapped to a specific user ID."
    )
    public ResponseEntity<ApiResponse<List<DepartmentResponse>>> getUserDepartments(
            @Parameter(description = "UUID of the target user", required = true)
            @PathVariable UUID userId)
            throws ResourcesNotFoundException, UnauthorizedException {
        List<DepartmentResponse> depts = departmentService.getUserDepartments(userId);
        return ResponseEntity.ok(ApiResponse.success("User departments retrieved successfully", depts, HttpStatus.OK.value()));
    }

    @PutMapping("/users/{userId}/departments")
    @PreAuthorize("hasAuthority('DEPARTMENT_USER_ASSIGN')")
    @Operation(
        summary = "Bulk assign/update departments for a user",
        description = "Replaces existing department mappings for a user with the provided department ID list. Rejects mapping for Admin users."
    )
    public ResponseEntity<ApiResponse<Void>> assignDepartmentsToUser(
            @Parameter(description = "UUID of the user", required = true)
            @PathVariable UUID userId,
            @RequestBody UserDepartmentAssignRequest request)
            throws BadRequestException, ResourcesNotFoundException, UnauthorizedException {
        departmentService.assignDepartmentsToUser(userId, request.getDepartmentIds());
        return ResponseEntity.ok(ApiResponse.success("User departments updated successfully", null, HttpStatus.OK.value()));
    }

    @PostMapping("/users/{userId}/departments/{departmentId}")
    @PreAuthorize("hasAuthority('DEPARTMENT_USER_ASSIGN')")
    @Operation(
        summary = "Map a user to a department",
        description = "Adds a single department mapping to a user. Supports multi-department user mapping."
    )
    public ResponseEntity<ApiResponse<Void>> addDepartmentToUser(
            @Parameter(description = "UUID of the user", required = true)
            @PathVariable UUID userId,
            @Parameter(description = "UUID of the department to add", required = true)
            @PathVariable UUID departmentId)
            throws BadRequestException, ResourcesNotFoundException, UnauthorizedException {
        departmentService.addDepartmentToUser(userId, departmentId);
        return ResponseEntity.ok(ApiResponse.success("Department added to user successfully", null, HttpStatus.OK.value()));
    }

    @DeleteMapping("/users/{userId}/departments/{departmentId}")
    @PreAuthorize("hasAuthority('DEPARTMENT_USER_REMOVE')")
    @Operation(
        summary = "Remove a department mapping from a user",
        description = "Removes a specific department mapping from a user ID."
    )
    public ResponseEntity<ApiResponse<Void>> removeDepartmentFromUser(
            @Parameter(description = "UUID of the user", required = true)
            @PathVariable UUID userId,
            @Parameter(description = "UUID of the department to remove", required = true)
            @PathVariable UUID departmentId)
            throws BadRequestException, ResourcesNotFoundException, UnauthorizedException {
        departmentService.removeDepartmentFromUser(userId, departmentId);
        return ResponseEntity.ok(ApiResponse.success("Department removed from user successfully", null, HttpStatus.OK.value()));
    }
}
