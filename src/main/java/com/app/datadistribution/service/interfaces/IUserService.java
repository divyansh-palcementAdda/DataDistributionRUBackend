package com.app.datadistribution.service.interfaces;

import java.util.List;

import com.app.datadistribution.exception.AccessDeniedException;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.payload.UserDTO;
import com.app.datadistribution.payload.UserDeviceDTO;
import com.app.datadistribution.payload.UserUpdateRequest;

public interface IUserService {

	UserDTO updateUser(Long userId, UserUpdateRequest request) throws AccessDeniedException, ResourcesNotFoundException, BadRequestException;

	void deleteUser(Long userId) throws AccessDeniedException, ResourcesNotFoundException, BadRequestException;

	UserDTO getUserById(Long userId) throws AccessDeniedException, ResourcesNotFoundException;


	List<UserDeviceDTO> getMyDevices() throws AccessDeniedException;
	List<UserDeviceDTO> getMyLoginActivities() throws AccessDeniedException;
	void logoutDevice(Long deviceId) throws AccessDeniedException, BadRequestException;
	void logoutAllDevices() throws AccessDeniedException;
}
