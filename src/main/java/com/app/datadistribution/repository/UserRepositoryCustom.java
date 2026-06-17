package com.app.datadistribution.repository;

import com.app.datadistribution.payload.PageResponse;
import com.app.datadistribution.payload.UserDTO;

public interface UserRepositoryCustom {
    PageResponse<UserDTO> searchUsersWithStats(int page, int size, String search, String role, String status, String sortBy, String sortDirection);
}
