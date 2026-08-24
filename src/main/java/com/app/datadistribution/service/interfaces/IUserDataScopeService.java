package com.app.datadistribution.service.interfaces;

import com.app.datadistribution.dto.dashboard.DashboardAnalyticsFilterRequest;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.service.dto.UserDataScope;

public interface IUserDataScopeService {
    UserDataScope getScopeForCurrentUser() throws UnauthorizedException, BadRequestException;
    UserDataScope getScopeForCurrentUser(String requestedScope) throws UnauthorizedException, BadRequestException;
    UserDataScope getScopeForCurrentUser(DashboardAnalyticsFilterRequest filterRequest) throws UnauthorizedException, BadRequestException;
    UserDataScope getScopeForUser(User user);
    UserDataScope getScopeForUser(User user, String requestedScope) throws BadRequestException, UnauthorizedException;
    UserDataScope getScopeForUser(User user, DashboardAnalyticsFilterRequest filterRequest) throws BadRequestException, UnauthorizedException;
}

