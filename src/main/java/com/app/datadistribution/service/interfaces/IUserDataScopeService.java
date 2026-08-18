package com.app.datadistribution.service.interfaces;

import com.app.datadistribution.entity.User;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.service.dto.UserDataScope;

public interface IUserDataScopeService {
    UserDataScope getScopeForCurrentUser() throws UnauthorizedException;
    UserDataScope getScopeForUser(User user);
}
