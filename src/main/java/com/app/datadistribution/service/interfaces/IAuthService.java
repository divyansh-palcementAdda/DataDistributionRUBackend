package com.app.datadistribution.service.interfaces;

import java.util.UUID;
import com.app.datadistribution.dto.auth.LoginRequest;
import com.app.datadistribution.dto.auth.LoginResponse;
import com.app.datadistribution.dto.auth.RegisterRequest;
import com.app.datadistribution.dto.auth.TokenResponse;
import com.app.datadistribution.dto.user.UserResponse;
import com.app.datadistribution.exception.AccessDeniedException;
import com.app.datadistribution.exception.AuthenticationFailedException;
import com.app.datadistribution.exception.BadRequestException;
import jakarta.servlet.http.HttpServletRequest;

public interface IAuthService {

    LoginResponse login(LoginRequest loginRequest, HttpServletRequest request)
            throws AuthenticationFailedException, AccessDeniedException;

    TokenResponse refreshToken(String refreshToken)
            throws AccessDeniedException, BadRequestException;

    void logout(String refreshToken) throws BadRequestException;

    void logoutAll(UUID userId) throws BadRequestException;

    UserResponse register(RegisterRequest request) throws BadRequestException, AccessDeniedException;
}