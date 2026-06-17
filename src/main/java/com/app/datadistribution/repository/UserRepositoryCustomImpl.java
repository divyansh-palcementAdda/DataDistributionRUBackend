package com.app.datadistribution.repository;

import org.springframework.stereotype.Repository;

import com.app.datadistribution.payload.PageResponse;
import com.app.datadistribution.payload.UserDTO;

@Repository
public class UserRepositoryCustomImpl implements UserRepositoryCustom {

	@Override
	public PageResponse<UserDTO> searchUsersWithStats(int page, int size, String search, String role, String status,
			String sortBy, String sortDirection) {
		// TODO Auto-generated method stub
		return null;
	}
	
}
