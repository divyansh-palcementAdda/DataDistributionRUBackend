package com.app.datadistribution.service.util;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import com.app.datadistribution.dto.report.AcademicSessionDTO;

public final class AcademicSessionUtil {

    public static final ZoneId APP_ZONE = ZoneId.of("Asia/Kolkata");

    private AcademicSessionUtil() {}

    public static AcademicSessionDTO getCurrentSession() {
        return getSessionForDate(LocalDate.now(APP_ZONE));
    }

    public static AcademicSessionDTO getSessionForDate(LocalDate date) {
        int year = date.getYear();
        int month = date.getMonthValue();
        int startYear = (month >= 7) ? year : year - 1;
        int endYear = startYear + 1;

        String sessionId = startYear + "-" + String.valueOf(endYear).substring(2);
        LocalDate startDate = LocalDate.of(startYear, 7, 1);
        LocalDate endDate = LocalDate.of(endYear, 6, 30);

        return AcademicSessionDTO.builder()
                .sessionId(sessionId)
                .sessionName("Academic Session " + sessionId)
                .startDate(startDate)
                .endDate(endDate)
                .isCurrent(true)
                .build();
    }

    public static List<AcademicSessionDTO> getAvailableSessions() {
        AcademicSessionDTO current = getCurrentSession();
        int currentStartYear = current.getStartDate().getYear();

        List<AcademicSessionDTO> list = new ArrayList<>();
        // 1 future session, current session, and 4 previous sessions
        for (int y = currentStartYear + 1; y >= currentStartYear - 4; y--) {
            int startYear = y;
            int endYear = y + 1;
            String sessionId = startYear + "-" + String.valueOf(endYear).substring(2);
            LocalDate startDate = LocalDate.of(startYear, 7, 1);
            LocalDate endDate = LocalDate.of(endYear, 6, 30);
            boolean isCur = sessionId.equals(current.getSessionId());

            list.add(AcademicSessionDTO.builder()
                    .sessionId(sessionId)
                    .sessionName("Academic Session " + sessionId)
                    .startDate(startDate)
                    .endDate(endDate)
                    .isCurrent(isCur)
                    .build());
        }
        return list;
    }

    public static AcademicSessionDTO findSessionById(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return getCurrentSession();
        }
        for (AcademicSessionDTO s : getAvailableSessions()) {
            if (s.getSessionId().equalsIgnoreCase(sessionId.trim())) {
                return s;
            }
        }
        try {
            String clean = sessionId.trim();
            if (clean.matches("^\\d{4}-\\d{2}$")) {
                int startYear = Integer.parseInt(clean.substring(0, 4));
                int endYear = startYear + 1;
                return AcademicSessionDTO.builder()
                        .sessionId(clean)
                        .sessionName("Academic Session " + clean)
                        .startDate(LocalDate.of(startYear, 7, 1))
                        .endDate(LocalDate.of(endYear, 6, 30))
                        .isCurrent(clean.equals(getCurrentSession().getSessionId()))
                        .build();
            }
        } catch (Exception ignored) {}
        return getCurrentSession();
    }
}
