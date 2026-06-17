package com.app.datadistribution.service.impl;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.app.datadistribution.Model.ActivityType;
import com.app.datadistribution.Model.Role;
import com.app.datadistribution.Model.User;
import com.app.datadistribution.Model.UserDevice;
import com.app.datadistribution.Model.UserStatus;
import com.app.datadistribution.exception.AccessDeniedException;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.payload.PageResponse;
import com.app.datadistribution.payload.SuccessEntry;
import com.app.datadistribution.payload.UserDTO;
import com.app.datadistribution.payload.UserDeviceDTO;
import com.app.datadistribution.payload.UserRequest;
import com.app.datadistribution.payload.UserUpdateRequest;
import com.app.datadistribution.repository.IUserRepository;
import com.app.datadistribution.repository.RoleRepository;
import com.app.datadistribution.repository.UserDeviceRepository;
import com.app.datadistribution.security.UserSecurityUtil;
import com.app.datadistribution.service.interfaces.IActivityLogService;
import com.app.datadistribution.service.interfaces.IEmailService;
import com.app.datadistribution.service.interfaces.IUserService;


import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class UserServiceImpl implements IUserService {@Override
	public UserDTO updateUser(Long userId, UserUpdateRequest request)
			throws AccessDeniedException, ResourcesNotFoundException, BadRequestException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void deleteUser(Long userId) throws AccessDeniedException, ResourcesNotFoundException, BadRequestException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public UserDTO getUserById(Long userId) throws AccessDeniedException, ResourcesNotFoundException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<UserDeviceDTO> getMyDevices() throws AccessDeniedException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<UserDeviceDTO> getMyLoginActivities() throws AccessDeniedException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void logoutDevice(Long deviceId) throws AccessDeniedException, BadRequestException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void logoutAllDevices() throws AccessDeniedException {
		// TODO Auto-generated method stub
		
	}}
