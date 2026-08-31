package com.app.datadistribution.service.interfaces;

import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.department.DepartmentRequest;
import com.app.datadistribution.dto.department.DepartmentResponse;
import com.app.datadistribution.dto.user.UserResponse;
import com.app.datadistribution.dto.user.UserSummaryResponse;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.exception.UnauthorizedException;
import org.springframework.data.domain.Page;
import java.util.List;
import java.util.UUID;

public interface IDepartmentService {
    DepartmentResponse createDepartment(DepartmentRequest request) throws BadRequestException, UnauthorizedException;
    DepartmentResponse updateDepartment(UUID id, DepartmentRequest request) throws BadRequestException, ResourcesNotFoundException, UnauthorizedException;
    DepartmentResponse getDepartmentById(UUID id) throws ResourcesNotFoundException, UnauthorizedException, BadRequestException;
    Page<DepartmentResponse> getAllDepartments(PageRequestDTO pageRequest, Boolean active, String search) throws UnauthorizedException, BadRequestException;
    List<DepartmentResponse> getAllActiveDepartments() throws UnauthorizedException, BadRequestException;
    void deleteDepartment(UUID id) throws BadRequestException, ResourcesNotFoundException, UnauthorizedException;

    // User department mapping
    List<DepartmentResponse> getUserDepartments(UUID userId) throws ResourcesNotFoundException, UnauthorizedException;
    void assignDepartmentsToUser(UUID userId, List<UUID> departmentIds) throws BadRequestException, ResourcesNotFoundException, UnauthorizedException;
    void addDepartmentToUser(UUID userId, UUID departmentId) throws BadRequestException, ResourcesNotFoundException, UnauthorizedException;
    void removeDepartmentFromUser(UUID userId, UUID departmentId) throws BadRequestException, ResourcesNotFoundException, UnauthorizedException;

    // Department Users / HODs / Counsellors
    List<UserSummaryResponse> getDepartmentUsers(UUID departmentId) throws ResourcesNotFoundException, UnauthorizedException, BadRequestException;
    List<UserSummaryResponse> getDepartmentHods(UUID departmentId) throws ResourcesNotFoundException, UnauthorizedException, BadRequestException;
    List<UserSummaryResponse> getDepartmentCounsellors(UUID departmentId) throws ResourcesNotFoundException, UnauthorizedException, BadRequestException;
}
