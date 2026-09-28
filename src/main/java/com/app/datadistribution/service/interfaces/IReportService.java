package com.app.datadistribution.service.interfaces;

import java.util.List;
import com.app.datadistribution.dto.report.AcademicSessionDTO;
import com.app.datadistribution.dto.report.ReportFilterRequest;
import com.app.datadistribution.dto.report.UserPerformanceReportResponse;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.UnauthorizedException;

public interface IReportService {

    UserPerformanceReportResponse getUserPerformanceReport(ReportFilterRequest filter)
            throws UnauthorizedException, BadRequestException;

    byte[] exportUserPerformanceReportToExcel(ReportFilterRequest filter)
            throws UnauthorizedException, BadRequestException;

    List<AcademicSessionDTO> getAcademicSessions();

    AcademicSessionDTO getActiveSession();
}
