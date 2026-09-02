package com.app.datadistribution.service.interfaces;

import com.app.datadistribution.dto.user.UserPerformanceFilterRequest;
import com.app.datadistribution.dto.user.UserPerformancePageResponse;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.UnauthorizedException;

public interface IUserPerformanceService {

    UserPerformancePageResponse getUserPerformance(UserPerformanceFilterRequest filterRequest)
            throws UnauthorizedException, BadRequestException;
}
