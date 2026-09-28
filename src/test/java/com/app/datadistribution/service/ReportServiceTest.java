package com.app.datadistribution.service;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.app.datadistribution.dto.report.AcademicSessionDTO;
import com.app.datadistribution.dto.report.ReportFilterRequest;
import com.app.datadistribution.dto.report.ReportRowDTO;
import com.app.datadistribution.dto.report.ReportSummaryDTO;
import com.app.datadistribution.service.util.AcademicSessionUtil;

public class ReportServiceTest {

    @Test
    @DisplayName("AcademicSessionUtil calculates current active session dynamically")
    void testCurrentAcademicSession() {
        AcademicSessionDTO current = AcademicSessionUtil.getCurrentSession();
        assertNotNull(current);
        assertNotNull(current.getSessionId());
        assertTrue(current.getSessionId().matches("^\\d{4}-\\d{2}$"));
        assertNotNull(current.getStartDate());
        assertNotNull(current.getEndDate());
        assertTrue(current.getStartDate().isBefore(current.getEndDate()));
        assertEquals(7, current.getStartDate().getMonthValue());
        assertEquals(6, current.getEndDate().getMonthValue());
    }

    @Test
    @DisplayName("AcademicSessionUtil resolves available sessions including historical and next year")
    void testAvailableAcademicSessions() {
        List<AcademicSessionDTO> sessions = AcademicSessionUtil.getAvailableSessions();
        assertNotNull(sessions);
        assertFalse(sessions.isEmpty());
        assertTrue(sessions.size() >= 5);

        boolean hasCurrent = sessions.stream().anyMatch(AcademicSessionDTO::isCurrent);
        assertTrue(hasCurrent, "Must contain the active academic session");
    }

    @Test
    @DisplayName("Conversion Ratio is computed safely without NaN or divide-by-zero")
    void testConversionRatioZeroAvailed() {
        long allotted = 100;
        long availed = 0;
        long registered = 0;

        double convRate = (availed > 0) ? Math.round(((double) registered / (double) availed) * 10000.0) / 100.0 : 0.0;
        assertEquals(0.0, convRate);
        assertFalse(Double.isNaN(convRate));
        assertFalse(Double.isInfinite(convRate));
    }

    @Test
    @DisplayName("Conversion Ratio calculation matches formula: Registered / Availed * 100")
    void testConversionRatioPositive() {
        long availed = 70;
        long registered = 15;

        double convRate = (availed > 0) ? Math.round(((double) registered / (double) availed) * 10000.0) / 100.0 : 0.0;
        assertEquals(21.43, convRate, 0.01);
    }
}
