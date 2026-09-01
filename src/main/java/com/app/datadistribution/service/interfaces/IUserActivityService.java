package com.app.datadistribution.service.interfaces;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.app.datadistribution.dto.useractivity.UserDailyActivityResponseDTO;
import com.app.datadistribution.dto.useractivity.UserInactivityPeriodDTO;
import com.app.datadistribution.dto.useractivity.UserSessionHistoryDTO;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.entity.UserLoginSession;
import com.app.datadistribution.enums.LogoutReason;
import com.app.datadistribution.exception.AccessDeniedException;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.exception.UnauthorizedException;

public interface IUserActivityService {

    UserLoginSession recordLogin(User user, String ipAddress, String deviceInfo, Long tokenVersion);

    void recordHeartbeat(UUID userId);

    void recordLogout(UUID userId, String refreshToken, LogoutReason logoutReason);

    UserDailyActivityResponseDTO getDailyActivity(UUID targetUserId, LocalDate date)
            throws UnauthorizedException, AccessDeniedException, BadRequestException, ResourcesNotFoundException;

    List<UserSessionHistoryDTO> getSessionHistory(UUID targetUserId, LocalDate date)
            throws UnauthorizedException, AccessDeniedException, BadRequestException, ResourcesNotFoundException;

    List<UserInactivityPeriodDTO> getInactivityHistory(UUID targetUserId, LocalDate date)
            throws UnauthorizedException, AccessDeniedException, BadRequestException, ResourcesNotFoundException;
}
