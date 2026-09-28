package com.app.datadistribution.service.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.datadistribution.dto.report.AcademicSessionDTO;
import com.app.datadistribution.dto.report.ReportFilterRequest;
import com.app.datadistribution.dto.report.ReportRowDTO;
import com.app.datadistribution.dto.report.ReportSummaryDTO;
import com.app.datadistribution.dto.report.UserPerformanceReportResponse;
import com.app.datadistribution.entity.Department;
import com.app.datadistribution.entity.LeadStatus;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.repository.DepartmentRepository;
import com.app.datadistribution.repository.LeadStatusRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.service.dto.UserDataScope.ScopeType;
import com.app.datadistribution.service.interfaces.IReportService;
import com.app.datadistribution.service.interfaces.IUserDataScopeService;
import com.app.datadistribution.service.util.AcademicSessionUtil;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements IReportService {

    private final IUserDataScopeService dataScopeService;
    private final LeadStatusRepository leadStatusRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;

    @PersistenceContext
    private final EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public UserPerformanceReportResponse getUserPerformanceReport(ReportFilterRequest filter)
            throws UnauthorizedException, BadRequestException {
        if (filter == null) {
            filter = new ReportFilterRequest();
        }

        // 1. Resolve & Enforce Scope Security
        UserDataScope dataScope = dataScopeService.getScopeForCurrentUser();
        if (dataScope == null) {
            throw new UnauthorizedException("User data scope could not be determined");
        }

        ResolvedScope resolvedScope = resolveAndValidateScope(dataScope, filter);

        // 2. Resolve Date Boundaries
        ResolvedDates resolvedDates = resolveDates(filter);

        // 3. Resolve Registered Status IDs from DB configuration
        Set<UUID> registeredStatusIds = getDescendantStatusIds("REGISTERED");

        // 4. Execute Unified Database Aggregation
        List<ReportRowDTO> allRows = executeAggregationQuery(filter, resolvedScope, resolvedDates, registeredStatusIds);

        // 5. Compute Summary Totals from all matching rows
        ReportSummaryDTO summary = computeSummary(allRows);

        // 6. Apply Server-Side Sorting
        sortRows(allRows, filter.getSortBy(), filter.getSortDirection());

        // 7. Apply Server-Side Pagination
        int page = filter.getPage() != null && filter.getPage() >= 0 ? filter.getPage() : 0;
        int size = filter.getSize() != null && filter.getSize() > 0 ? filter.getSize() : 10;
        long totalElements = allRows.size();
        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;

        int fromIndex = page * size;
        List<ReportRowDTO> pagedRows;
        if (fromIndex >= allRows.size()) {
            pagedRows = Collections.emptyList();
        } else {
            int toIndex = Math.min(fromIndex + size, allRows.size());
            pagedRows = allRows.subList(fromIndex, toIndex);
        }

        AcademicSessionDTO activeSession = AcademicSessionUtil.getCurrentSession();

        return UserPerformanceReportResponse.builder()
                .summary(summary)
                .rows(pagedRows)
                .page(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .last((page + 1) >= totalPages)
                .activeSession(activeSession.getSessionId())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportUserPerformanceReportToExcel(ReportFilterRequest filter)
            throws UnauthorizedException, BadRequestException {
        if (filter == null) {
            filter = new ReportFilterRequest();
        }

        UserDataScope dataScope = dataScopeService.getScopeForCurrentUser();
        if (dataScope == null) {
            throw new UnauthorizedException("User data scope could not be determined");
        }

        ResolvedScope resolvedScope = resolveAndValidateScope(dataScope, filter);
        ResolvedDates resolvedDates = resolveDates(filter);
        Set<UUID> registeredStatusIds = getDescendantStatusIds("REGISTERED");

        List<ReportRowDTO> allRows = executeAggregationQuery(filter, resolvedScope, resolvedDates, registeredStatusIds);
        ReportSummaryDTO summary = computeSummary(allRows);
        sortRows(allRows, filter.getSortBy(), filter.getSortDirection());

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Performance Report");

            // Fonts & Styles
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);

            CellStyle titleStyle = workbook.createCellStyle();
            titleStyle.setFont(titleFont);

            Font boldFont = workbook.createFont();
            boldFont.setBold(true);

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(boldFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);

            CellStyle summaryStyle = workbook.createCellStyle();
            summaryStyle.setFont(boldFont);
            summaryStyle.setFillForegroundColor(IndexedColors.LEMON_CHIFFON.getIndex());
            summaryStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            summaryStyle.setBorderBottom(BorderStyle.THIN);
            summaryStyle.setBorderTop(BorderStyle.THIN);
            summaryStyle.setBorderLeft(BorderStyle.THIN);
            summaryStyle.setBorderRight(BorderStyle.THIN);

            int rowIdx = 0;

            // 1. Report Title & Meta
            Row titleRow = sheet.createRow(rowIdx++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("User Performance & Departmental Report");
            titleCell.setCellStyle(titleStyle);

            Row metaRow = sheet.createRow(rowIdx++);
            String dateInfo = (resolvedDates.startDate != null ? resolvedDates.startDate.toString() : "All Time")
                    + " to " + (resolvedDates.endDate != null ? resolvedDates.endDate.toString() : "Present");
            metaRow.createCell(0).setCellValue("Date Range: " + dateInfo + " | Mode: " + resolvedScope.mode);

            rowIdx++; // Blank line

            // 2. Summary Cards / Block
            Row sumHeadRow = sheet.createRow(rowIdx++);
            sumHeadRow.createCell(0).setCellValue("Summary Metric");
            sumHeadRow.createCell(1).setCellValue("Value");
            sumHeadRow.getCell(0).setCellStyle(headerStyle);
            sumHeadRow.getCell(1).setCellStyle(headerStyle);

            addSummaryRow(sheet, rowIdx++, "Total Allotted Data", summary.getTotalAllotted(), dataStyle);
            addSummaryRow(sheet, rowIdx++, "Total Availed Data", summary.getTotalAvailed(), dataStyle);
            addSummaryRow(sheet, rowIdx++, "Total Unallotted Data", summary.getTotalUnallotted(), dataStyle);
            addSummaryRow(sheet, rowIdx++, "Total Registered Data", summary.getTotalRegistered(), dataStyle);
            addSummaryRow(sheet, rowIdx++, "Conversion Ratio (%)", summary.getConversionRate() + "%", dataStyle);
            addSummaryRow(sheet, rowIdx++, "Total Follow-ups", summary.getTotalFollowUps(), dataStyle);

            rowIdx++; // Blank line

            // 3. Table Header
            Row tableHead = sheet.createRow(rowIdx++);
            boolean isDeptMode = "DEPARTMENTAL".equalsIgnoreCase(resolvedScope.mode);
            String[] columns = isDeptMode
                    ? new String[]{"Department", "Course", "Lead Status", "Allotted", "Availed", "Unallotted", "Registered", "Conversion %", "Follow-ups"}
                    : new String[]{"Course", "Lead Status", "Allotted", "Availed", "Unallotted", "Registered", "Conversion %", "Follow-ups"};

            for (int i = 0; i < columns.length; i++) {
                Cell c = tableHead.createCell(i);
                c.setCellValue(columns[i]);
                c.setCellStyle(headerStyle);
            }

            // 4. Data Rows
            for (ReportRowDTO r : allRows) {
                Row row = sheet.createRow(rowIdx++);
                int cIdx = 0;
                if (isDeptMode) {
                    Cell cDept = row.createCell(cIdx++);
                    cDept.setCellValue(r.getDepartmentName() != null ? r.getDepartmentName() : "—");
                    cDept.setCellStyle(dataStyle);
                }
                Cell cCourse = row.createCell(cIdx++);
                cCourse.setCellValue(r.getCourseName() != null ? r.getCourseName() : "General");
                cCourse.setCellStyle(dataStyle);

                Cell cStatus = row.createCell(cIdx++);
                cStatus.setCellValue(r.getLeadStatusName() != null ? r.getLeadStatusName() : "—");
                cStatus.setCellStyle(dataStyle);

                Cell cAllotted = row.createCell(cIdx++);
                cAllotted.setCellValue(r.getTotalAllotted());
                cAllotted.setCellStyle(dataStyle);

                Cell cAvailed = row.createCell(cIdx++);
                cAvailed.setCellValue(r.getTotalAvailed());
                cAvailed.setCellStyle(dataStyle);

                Cell cUnallotted = row.createCell(cIdx++);
                cUnallotted.setCellValue(r.getTotalUnallotted());
                cUnallotted.setCellStyle(dataStyle);

                Cell cReg = row.createCell(cIdx++);
                cReg.setCellValue(r.getTotalRegistered());
                cReg.setCellStyle(dataStyle);

                Cell cConv = row.createCell(cIdx++);
                cConv.setCellValue(String.format(Locale.US, "%.2f%%", r.getConversionRate()));
                cConv.setCellStyle(dataStyle);

                Cell cFu = row.createCell(cIdx++);
                cFu.setCellValue(r.getTotalFollowUps());
                cFu.setCellStyle(dataStyle);
            }

            // 5. Total Row
            Row totalRow = sheet.createRow(rowIdx++);
            int cIdx = 0;
            if (isDeptMode) {
                Cell cDept = totalRow.createCell(cIdx++);
                cDept.setCellValue("Total / Overall");
                cDept.setCellStyle(summaryStyle);
            }
            Cell cCourse = totalRow.createCell(cIdx++);
            cCourse.setCellValue(isDeptMode ? "—" : "Total / Overall");
            cCourse.setCellStyle(summaryStyle);

            Cell cStatus = totalRow.createCell(cIdx++);
            cStatus.setCellValue("—");
            cStatus.setCellStyle(summaryStyle);

            Cell cAllotted = totalRow.createCell(cIdx++);
            cAllotted.setCellValue(summary.getTotalAllotted());
            cAllotted.setCellStyle(summaryStyle);

            Cell cAvailed = totalRow.createCell(cIdx++);
            cAvailed.setCellValue(summary.getTotalAvailed());
            cAvailed.setCellStyle(summaryStyle);

            Cell cUnallotted = totalRow.createCell(cIdx++);
            cUnallotted.setCellValue(summary.getTotalUnallotted());
            cUnallotted.setCellStyle(summaryStyle);

            Cell cReg = totalRow.createCell(cIdx++);
            cReg.setCellValue(summary.getTotalRegistered());
            cReg.setCellStyle(summaryStyle);

            Cell cConv = totalRow.createCell(cIdx++);
            cConv.setCellValue(String.format(Locale.US, "%.2f%%", summary.getConversionRate()));
            cConv.setCellStyle(summaryStyle);

            Cell cFu = totalRow.createCell(cIdx++);
            cFu.setCellValue(summary.getTotalFollowUps());
            cFu.setCellStyle(summaryStyle);

            // Auto-size columns
            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            log.error("Failed to generate Excel report", e);
            throw new RuntimeException("Error generating Excel report: " + e.getMessage(), e);
        }
    }

    private void addSummaryRow(Sheet sheet, int rowIdx, String label, Object value, CellStyle style) {
        Row r = sheet.createRow(rowIdx);
        Cell c0 = r.createCell(0);
        c0.setCellValue(label);
        c0.setCellStyle(style);

        Cell c1 = r.createCell(1);
        if (value instanceof Number n) {
            c1.setCellValue(n.doubleValue());
        } else {
            c1.setCellValue(String.valueOf(value));
        }
        c1.setCellStyle(style);
    }

    @Override
    public List<AcademicSessionDTO> getAcademicSessions() {
        return AcademicSessionUtil.getAvailableSessions();
    }

    @Override
    public AcademicSessionDTO getActiveSession() {
        return AcademicSessionUtil.getCurrentSession();
    }

    // =========================================================================
    // Core Database Aggregation Implementation
    // =========================================================================

    @SuppressWarnings("unchecked")
    private List<ReportRowDTO> executeAggregationQuery(
            ReportFilterRequest filter,
            ResolvedScope scope,
            ResolvedDates dates,
            Set<UUID> registeredStatusIds) {

        StringBuilder sql = new StringBuilder();
        Map<String, Object> params = new HashMap<>();

        // Base Scope Predicates for leads
        StringBuilder scopeWhere = new StringBuilder(" l.is_deleted = false ");
        if ("SELF".equalsIgnoreCase(scope.mode)) {
            scopeWhere.append(" AND l.assigned_to_id = :scopeSelfUserId ");
            params.put("scopeSelfUserId", scope.targetUserId);
        } else if ("DEPARTMENTAL".equalsIgnoreCase(scope.mode)) {
            if (scope.filteredDepartmentId != null) {
                scopeWhere.append(" AND l.department_id = :scopeDeptId ");
                params.put("scopeDeptId", scope.filteredDepartmentId);
            } else if (scope.allowedDepartmentIds != null && !scope.allowedDepartmentIds.isEmpty()) {
                scopeWhere.append(" AND l.department_id IN (:scopeAllowedDeptIds) ");
                params.put("scopeAllowedDeptIds", scope.allowedDepartmentIds);
            }
            if (scope.filteredUserId != null) {
                scopeWhere.append(" AND l.assigned_to_id = :scopeTargetUserId ");
                params.put("scopeTargetUserId", scope.filteredUserId);
            }
        }

        // Additional Filter: Course ID
        if (filter.getCourseId() != null) {
            params.put("filterCourseId", filter.getCourseId());
        }

        // Additional Filter: Lead Status ID
        if (filter.getLeadStatusId() != null) {
            scopeWhere.append(" AND l.lead_status_id = :filterLeadStatusId ");
            params.put("filterLeadStatusId", filter.getLeadStatusId());
        }

        // Date Filter Clause for Leads
        if (dates.startDate != null) {
            scopeWhere.append(" AND l.created_at >= :dateStart ");
            params.put("dateStart", dates.startDate.atStartOfDay());
        }
        if (dates.endDate != null) {
            scopeWhere.append(" AND l.created_at <= :dateEnd ");
            params.put("dateEnd", dates.endDate.atTime(LocalTime.MAX));
        }

        // Availed date condition
        StringBuilder availedDateCond = new StringBuilder();
        if (dates.startDate != null) {
            availedDateCond.append(" AND la.availed_at >= :dateStart ");
        }
        if (dates.endDate != null) {
            availedDateCond.append(" AND la.availed_at <= :dateEnd ");
        }

        // Follow-up date condition
        StringBuilder followUpDateCond = new StringBuilder();
        if (dates.startDate != null) {
            followUpDateCond.append(" AND lfu.follow_up_date >= :dateStart ");
        }
        if (dates.endDate != null) {
            followUpDateCond.append(" AND lfu.follow_up_date <= :dateEnd ");
        }

        // Construct CTE / derived union for course mapping
        sql.append("SELECT ")
           .append("  comb.course_id, ")
           .append("  comb.course_name, ")
           .append("  comb.course_code, ")
           .append("  ls.id AS lead_status_id, ")
           .append("  ls.name AS lead_status_name, ")
           .append("  ls.code AS lead_status_code, ")
           .append("  comb.department_id, ")
           .append("  comb.department_name, ")
           .append("  COUNT(DISTINCT CASE WHEN comb.assigned_to_id IS NOT NULL THEN comb.lead_id END) AS total_allotted, ")
           .append("  COUNT(DISTINCT CASE WHEN comb.assigned_to_id IS NOT NULL AND EXISTS ( ")
           .append("      SELECT 1 FROM lead_availed la ")
           .append("      WHERE la.lead_id = comb.lead_id ")
           .append("        AND la.availed_by_user_id = comb.assigned_to_id ")
           .append("        AND la.is_deleted = false ")
           .append(availedDateCond)
           .append("  ) THEN comb.lead_id END) AS total_availed, ")
           .append("  COUNT(DISTINCT CASE WHEN comb.assigned_to_id IS NULL THEN comb.lead_id END) AS total_unallotted, ");

        if (!registeredStatusIds.isEmpty()) {
            sql.append("  COUNT(DISTINCT CASE WHEN (comb.lead_status_id IN (:registeredStatusIds) OR comb.registration_status = 'CHECK_SUCCESSFUL') THEN comb.lead_id END) AS total_registered, ");
            params.put("registeredStatusIds", registeredStatusIds);
        } else {
            sql.append("  COUNT(DISTINCT CASE WHEN comb.registration_status = 'CHECK_SUCCESSFUL' THEN comb.lead_id END) AS total_registered, ");
        }

        sql.append("  COUNT(DISTINCT lfu.id) AS total_followups ")
           .append("FROM ( ")
           // 1. Registered course leads
           .append("  SELECT DISTINCT ")
           .append("    l.id AS lead_id, ")
           .append("    l.assigned_to_id, ")
           .append("    l.department_id, ")
           .append("    l.lead_status_id, ")
           .append("    l.registration_status, ")
           .append("    c.id AS course_id, ")
           .append("    c.course_name AS course_name, ")
           .append("    c.course_code AS course_code, ")
           .append("    d.name AS department_name ")
           .append("  FROM leads l ")
           .append("  JOIN courses c ON c.id = l.course_id AND c.is_deleted = false ")
           .append(filter.getCourseId() != null ? " AND c.id = :filterCourseId " : "")
           .append("  LEFT JOIN departments d ON d.id = l.department_id AND d.is_deleted = false ")
           .append("  WHERE ").append(scopeWhere)
           .append("  UNION ")
           // 2. Interested course leads
           .append("  SELECT DISTINCT ")
           .append("    l.id AS lead_id, ")
           .append("    l.assigned_to_id, ")
           .append("    l.department_id, ")
           .append("    l.lead_status_id, ")
           .append("    l.registration_status, ")
           .append("    c.id AS course_id, ")
           .append("    c.course_name AS course_name, ")
           .append("    c.course_code AS course_code, ")
           .append("    d.name AS department_name ")
           .append("  FROM leads l ")
           .append("  JOIN lead_interested_courses lic ON lic.lead_id = l.id ")
           .append("  JOIN courses c ON c.id = lic.course_id AND c.is_deleted = false ")
           .append(filter.getCourseId() != null ? " AND c.id = :filterCourseId " : "")
           .append("  LEFT JOIN departments d ON d.id = l.department_id AND d.is_deleted = false ")
           .append("  WHERE ").append(scopeWhere);

        // 3. Leads without any course (only if courseId filter is NOT applied)
        if (filter.getCourseId() == null) {
            sql.append("  UNION ")
               .append("  SELECT DISTINCT ")
               .append("    l.id AS lead_id, ")
               .append("    l.assigned_to_id, ")
               .append("    l.department_id, ")
               .append("    l.lead_status_id, ")
               .append("    l.registration_status, ")
               .append("    NULL AS course_id, ")
               .append("    'General / Not Specified' AS course_name, ")
               .append("    'GENERAL' AS course_code, ")
               .append("    d.name AS department_name ")
               .append("  FROM leads l ")
               .append("  LEFT JOIN departments d ON d.id = l.department_id AND d.is_deleted = false ")
               .append("  WHERE l.course_id IS NULL ")
               .append("    AND NOT EXISTS (SELECT 1 FROM lead_interested_courses lic WHERE lic.lead_id = l.id) ")
               .append("    AND ").append(scopeWhere);
        }

        sql.append(") comb ")
           .append("JOIN lead_statuses ls ON ls.id = comb.lead_status_id AND ls.is_deleted = false ")
           .append("LEFT JOIN lead_follow_ups lfu ON lfu.lead_id = comb.lead_id AND lfu.is_deleted = false ")
           .append(followUpDateCond)
           .append("GROUP BY ")
           .append("  comb.course_id, ")
           .append("  comb.course_name, ")
           .append("  comb.course_code, ")
           .append("  ls.id, ")
           .append("  ls.name, ")
           .append("  ls.code, ")
           .append("  comb.department_id, ")
           .append("  comb.department_name ");

        Query query = entityManager.createNativeQuery(sql.toString());
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            query.setParameter(entry.getKey(), entry.getValue());
        }

        List<?> results = query.getResultList();
        List<ReportRowDTO> rows = new ArrayList<>(results.size());

        for (Object item : results) {
            Object[] r = item instanceof Object[] ? (Object[]) item : new Object[]{item};
            UUID courseId = parseUUID(r[0]);
            String courseName = r[1] != null ? r[1].toString() : "General";
            String courseCode = r[2] != null ? r[2].toString() : "";
            UUID leadStatusId = parseUUID(r[3]);
            String leadStatusName = r[4] != null ? r[4].toString() : "—";
            String leadStatusCode = r[5] != null ? r[5].toString() : "";
            UUID deptId = parseUUID(r[6]);
            String deptName = r[7] != null ? r[7].toString() : "—";

            long totalAllotted = parseLong(r[8]);
            long totalAvailed = parseLong(r[9]);
            long totalUnallotted = parseLong(r[10]);
            long totalRegistered = parseLong(r[11]);
            long totalFollowUps = parseLong(r[12]);

            // Conversion Ratio = Registered / Availed * 100
            double conversionRate = 0.0;
            if (totalAvailed > 0) {
                conversionRate = Math.round(((double) totalRegistered / (double) totalAvailed) * 10000.0) / 100.0;
            }

            rows.add(ReportRowDTO.builder()
                    .departmentId(deptId)
                    .departmentName(deptName)
                    .courseId(courseId)
                    .courseName(courseName)
                    .courseCode(courseCode)
                    .leadStatusId(leadStatusId)
                    .leadStatusName(leadStatusName)
                    .leadStatusCode(leadStatusCode)
                    .totalAllotted(totalAllotted)
                    .totalAvailed(totalAvailed)
                    .totalUnallotted(totalUnallotted)
                    .totalRegistered(totalRegistered)
                    .conversionRate(conversionRate)
                    .totalFollowUps(totalFollowUps)
                    .build());
        }

        return rows;
    }

    // =========================================================================
    // Scope & Date Helper Methods
    // =========================================================================

    private ResolvedScope resolveAndValidateScope(UserDataScope dataScope, ReportFilterRequest filter)
            throws UnauthorizedException {

        ResolvedScope resolved = new ResolvedScope();

        // 1. Counselor (Strictly SELF Scope)
        if (dataScope.getScopeType() == ScopeType.SELF || (!dataScope.isAdmin() && !dataScope.isHod())) {
            resolved.mode = "SELF";
            resolved.targetUserId = dataScope.getUserId();
            resolved.filteredDepartmentId = null;
            resolved.filteredUserId = dataScope.getUserId();
            return resolved;
        }

        // 2. HOD Scope (SELF or Permitted Departments)
        if (dataScope.getScopeType() == ScopeType.DEPARTMENT) {
            Set<UUID> allowedDeptIds = dataScope.getDepartmentIds() != null ? dataScope.getDepartmentIds() : Collections.emptySet();
            resolved.allowedDepartmentIds = allowedDeptIds;

            if ("SELF".equalsIgnoreCase(filter.getReportMode())) {
                resolved.mode = "SELF";
                resolved.targetUserId = dataScope.getUserId();
                resolved.filteredUserId = dataScope.getUserId();
                return resolved;
            }

            // Departmental Mode
            resolved.mode = "DEPARTMENTAL";
            if (filter.getDepartmentId() != null) {
                if (!allowedDeptIds.contains(filter.getDepartmentId())) {
                    throw new UnauthorizedException("Access denied: Department is outside your authorized scope");
                }
                resolved.filteredDepartmentId = filter.getDepartmentId();
            }

            if (filter.getUserId() != null) {
                Set<UUID> deptUserIds = dataScope.getDepartmentUserIds() != null ? dataScope.getDepartmentUserIds() : Collections.emptySet();
                if (!deptUserIds.contains(filter.getUserId()) && !filter.getUserId().equals(dataScope.getUserId())) {
                    throw new UnauthorizedException("Access denied: User is outside your department scope");
                }
                resolved.filteredUserId = filter.getUserId();
            }
            return resolved;
        }

        // 3. Admin / System Scope
        resolved.mode = "SELF".equalsIgnoreCase(filter.getReportMode()) ? "SELF" : "DEPARTMENTAL";
        if ("SELF".equalsIgnoreCase(resolved.mode)) {
            resolved.targetUserId = dataScope.getUserId();
            resolved.filteredUserId = dataScope.getUserId();
        } else {
            resolved.filteredDepartmentId = filter.getDepartmentId();
            resolved.filteredUserId = filter.getUserId();
        }
        return resolved;
    }

    private ResolvedDates resolveDates(ReportFilterRequest filter) throws BadRequestException {
        ResolvedDates res = new ResolvedDates();
        LocalDate today = LocalDate.now(AcademicSessionUtil.APP_ZONE);

        String preset = filter.getDatePreset() != null ? filter.getDatePreset().trim().toUpperCase() : null;

        if (preset == null && filter.getFromDate() == null && filter.getToDate() == null && filter.getSessionId() != null) {
            preset = "THIS_SESSION";
        }

        if (preset == null && (filter.getFromDate() != null || filter.getToDate() != null)) {
            preset = "CUSTOM";
        }

        if (preset == null) {
            preset = "ALL_TIME";
        }

        switch (preset) {
            case "TODAY":
                res.startDate = today;
                res.endDate = today;
                break;
            case "YESTERDAY":
                res.startDate = today.minusDays(1);
                res.endDate = today.minusDays(1);
                break;
            case "THIS_WEEK":
                res.startDate = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                res.endDate = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
                break;
            case "THIS_MONTH":
                res.startDate = today.withDayOfMonth(1);
                res.endDate = today.with(TemporalAdjusters.lastDayOfMonth());
                break;
            case "THIS_SESSION":
                AcademicSessionDTO session = filter.getSessionId() != null
                        ? AcademicSessionUtil.findSessionById(filter.getSessionId())
                        : AcademicSessionUtil.getCurrentSession();
                res.startDate = session.getStartDate();
                res.endDate = session.getEndDate();
                break;
            case "CUSTOM":
                if (filter.getFromDate() != null && filter.getToDate() != null) {
                    if (filter.getFromDate().isAfter(filter.getToDate())) {
                        throw new BadRequestException("fromDate cannot be after toDate");
                    }
                }
                res.startDate = filter.getFromDate();
                res.endDate = filter.getToDate();
                break;
            case "ALL_TIME":
            default:
                res.startDate = null;
                res.endDate = null;
                break;
        }

        return res;
    }

    private ReportSummaryDTO computeSummary(List<ReportRowDTO> rows) {
        long allotted = 0;
        long availed = 0;
        long unallotted = 0;
        long registered = 0;
        long followUps = 0;

        for (ReportRowDTO r : rows) {
            allotted += r.getTotalAllotted();
            availed += r.getTotalAvailed();
            unallotted += r.getTotalUnallotted();
            registered += r.getTotalRegistered();
            followUps += r.getTotalFollowUps();
        }

        double convRate = 0.0;
        if (availed > 0) {
            convRate = Math.round(((double) registered / (double) availed) * 10000.0) / 100.0;
        }

        return ReportSummaryDTO.builder()
                .totalAllotted(allotted)
                .totalAvailed(availed)
                .totalUnallotted(unallotted)
                .totalRegistered(registered)
                .conversionRate(convRate)
                .totalFollowUps(followUps)
                .build();
    }

    private void sortRows(List<ReportRowDTO> rows, String sortBy, String sortDir) {
        if (rows == null || rows.isEmpty()) return;
        boolean desc = "DESC".equalsIgnoreCase(sortDir);
        String field = sortBy != null ? sortBy.trim().toLowerCase() : "coursename";

        Comparator<ReportRowDTO> comparator;
        switch (field) {
            case "course":
            case "coursename":
                comparator = Comparator.comparing(ReportRowDTO::getCourseName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                break;
            case "leadstatus":
            case "leadstatusname":
            case "status":
                comparator = Comparator.comparing(ReportRowDTO::getLeadStatusName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                break;
            case "allotted":
            case "totalallotted":
                comparator = Comparator.comparingLong(ReportRowDTO::getTotalAllotted);
                break;
            case "availed":
            case "totalavailed":
                comparator = Comparator.comparingLong(ReportRowDTO::getTotalAvailed);
                break;
            case "unallotted":
            case "totalunallotted":
                comparator = Comparator.comparingLong(ReportRowDTO::getTotalUnallotted);
                break;
            case "registered":
            case "totalregistered":
                comparator = Comparator.comparingLong(ReportRowDTO::getTotalRegistered);
                break;
            case "conversion":
            case "conversionrate":
                comparator = Comparator.comparingDouble(ReportRowDTO::getConversionRate);
                break;
            case "followups":
            case "totalfollowups":
                comparator = Comparator.comparingLong(ReportRowDTO::getTotalFollowUps);
                break;
            case "department":
            case "departmentname":
                comparator = Comparator.comparing(ReportRowDTO::getDepartmentName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                break;
            default:
                comparator = Comparator.comparing(ReportRowDTO::getCourseName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                break;
        }

        if (desc) {
            comparator = comparator.reversed();
        }
        rows.sort(comparator);
    }

    private Set<UUID> getDescendantStatusIds(String rootCode) {
        List<LeadStatus> allStatuses = leadStatusRepository.findAll();
        Set<UUID> collected = new HashSet<>();

        Optional<LeadStatus> rootStatus = allStatuses.stream()
                .filter(s -> !s.isDeleted() && rootCode.equalsIgnoreCase(s.getCode()))
                .findFirst();

        if (rootStatus.isPresent()) {
            collected.add(rootStatus.get().getId());
            boolean addedNew;
            do {
                addedNew = false;
                for (LeadStatus s : allStatuses) {
                    if (!s.isDeleted() && !collected.contains(s.getId())) {
                        if (s.getParentStatus() != null && collected.contains(s.getParentStatus().getId())) {
                            collected.add(s.getId());
                            addedNew = true;
                        }
                    }
                }
            } while (addedNew);
        }

        for (LeadStatus s : allStatuses) {
            if (!s.isDeleted() && s.getCode() != null) {
                if (s.getCode().toUpperCase().startsWith(rootCode.toUpperCase()) ||
                    s.getCode().toUpperCase().endsWith(rootCode.toUpperCase())) {
                    collected.add(s.getId());
                }
            }
        }

        return collected;
    }

    private UUID parseUUID(Object obj) {
        if (obj == null) return null;
        if (obj instanceof UUID) return (UUID) obj;
        if (obj instanceof byte[] b && b.length == 16) {
            ByteBuffer bb = ByteBuffer.wrap(b);
            return new UUID(bb.getLong(), bb.getLong());
        }
        try {
            return UUID.fromString(obj.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private long parseLong(Object obj) {
        if (obj == null) return 0L;
        if (obj instanceof Number num) return num.longValue();
        try {
            return Long.parseLong(obj.toString());
        } catch (Exception e) {
            return 0L;
        }
    }

    private static class ResolvedScope {
        String mode; // "SELF" or "DEPARTMENTAL"
        UUID targetUserId;
        UUID filteredUserId;
        UUID filteredDepartmentId;
        Set<UUID> allowedDepartmentIds;
    }

    private static class ResolvedDates {
        LocalDate startDate;
        LocalDate endDate;
    }
}
