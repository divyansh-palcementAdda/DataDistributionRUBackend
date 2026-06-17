package com.app.datadistribution.service.interfaces;

import com.app.datadistribution.exception.AccessDeniedException;
import com.app.datadistribution.exception.AuthenticationFailedException;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.payload.JwtResponse;
import com.app.datadistribution.payload.LoginRequest;
import com.app.datadistribution.payload.UserDTO;
import com.app.datadistribution.payload.UserRequest;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Authentication service interface handling login, token refresh, logout, registration,
 * and related security operations.
 */
public interface IAuthService {

    /**
     * Authenticates a user and creates a new session (access + refresh token pair).
     *
     * @param loginRequest credentials (username/email + password)
     * @param request      HTTP request (used to extract IP, User-Agent, etc.)
     * @return JwtResponse containing access token, refresh token, user info
     * @throws AuthenticationFailedException invalid credentials or account not active/verified
     * @throws AccessDeniedException         account locked/inactive
     */
    JwtResponse login(LoginRequest loginRequest, HttpServletRequest request)
            throws AuthenticationFailedException, AccessDeniedException;

    /**
     * Uses a valid refresh token to issue a new access token (token rotation).
     *
     * @param refreshToken the current refresh token
     * @return new JwtResponse with fresh access token (and same refresh token)
     * @throws AccessDeniedException invalid, expired, or revoked refresh token
     * @throws BadRequestException   refresh token is missing or malformed
     */
    JwtResponse refreshAccessToken(String refreshToken)
            throws AccessDeniedException, BadRequestException;

    /**
     * Revokes a single refresh token (logs out one device/session).
     *
     * @param refreshToken the refresh token to revoke
     * @throws BadRequestException if refresh token is missing/invalid format
     */
    void logout(String refreshToken) throws BadRequestException;

    /**
     * Revokes ALL refresh tokens for a given user (global logout - all devices).
     *
     * @param userId the ID of the user whose sessions should be terminated
     * @throws BadRequestException if userId is null
     */
    void logoutAll(Long userId) throws BadRequestException;

    /**
     * Registers a new user, encodes password, assigns default role(s),
     * and usually triggers email verification / OTP sending.
     *
     * @param request registration data
     * @return UserDTO of the newly created user
     * @throws BadRequestException           validation failure or duplicate email/username
     * @throws AccessDeniedException         rare – e.g. registration temporarily blocked
     */
    UserDTO register(UserRequest request) throws BadRequestException, AccessDeniedException;

    // ────────────────────────────────────────────────────────────────
    // Optional / common methods if you have email verification flow
    // ────────────────────────────────────────────────────────────────

    /**
     * Verifies user's email using OTP or verification token.
     *
     * @param email            user's email
     * Resends verification email / OTP to user.
     *
     * @param email user's email address
     * @throws BadRequestException user not found or already verified
     */
    void sendOtp(String email) throws BadRequestException;

    /**
     * Verifies user's email using OTP.
     *
     * @param email user's email
     * @param otp   6-digit OTP
     * @throws BadRequestException invalid/expired code
     */
    void verifyOtp(String email, String otp) throws BadRequestException;
}