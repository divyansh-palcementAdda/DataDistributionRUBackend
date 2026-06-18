package com.app.datadistribution.service.interfaces;

import java.util.UUID;
import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.user.UserPageResponse;
import com.app.datadistribution.dto.user.UserRequest;
import com.app.datadistribution.dto.user.UserResponse;
import com.app.datadistribution.dto.user.UserUpdateRequest;
import com.app.datadistribution.exception.AccessDeniedException;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.ResourcesNotFoundException;

public interface IUserService {

    UserResponse getUserById(UUID userId) throws ResourcesNotFoundException;

    UserPageResponse getUsers(PageRequestDTO pageRequest);

    UserResponse createUser(UserRequest request) throws BadRequestException;

    UserResponse updateUser(UUID userId, UserUpdateRequest request) throws ResourcesNotFoundException, BadRequestException;

    void deleteUser(UUID userId) throws ResourcesNotFoundException;
}
