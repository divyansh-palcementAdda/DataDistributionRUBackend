package com.app.datadistribution.service.impl;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.app.datadistribution.dto.infopanel.CourseInfoPanelBulkUploadResponseDTO;
import com.app.datadistribution.dto.infopanel.CourseInfoPanelBulkUploadRowErrorDTO;
import com.app.datadistribution.dto.infopanel.CourseInfoPanelPreviewResponseDTO;
import com.app.datadistribution.dto.report.AcademicSessionDTO;
import com.app.datadistribution.entity.CompetitorCourseComparison;
import com.app.datadistribution.entity.Course;
import com.app.datadistribution.entity.CourseType;
import com.app.datadistribution.entity.CourseInfoPanel;
import com.app.datadistribution.entity.CourseInfoPanelCompetitor;
import com.app.datadistribution.entity.CourseInfoPanelCompetitorBranch;
import com.app.datadistribution.enums.PermissionType;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.repository.CompetitorCourseComparisonRepository;
import com.app.datadistribution.repository.CourseInfoPanelCompetitorBranchRepository;
import com.app.datadistribution.repository.CourseInfoPanelCompetitorRepository;
import com.app.datadistribution.repository.CourseInfoPanelRepository;
import com.app.datadistribution.repository.CourseRepository;
import com.app.datadistribution.repository.CourseTypeRepository;
import com.app.datadistribution.service.interfaces.ICourseInfoPanelBulkUploadService;
import com.app.datadistribution.service.util.AcademicSessionUtil;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CourseInfoPanelBulkUploadServiceImpl implements ICourseInfoPanelBulkUploadService {

    private final CourseRepository courseRepository;
    private final CourseTypeRepository courseTypeRepository;
    private final CourseInfoPanelRepository infoPanelRepository;
    private final CourseInfoPanelCompetitorRepository competitorRepository;
    private final CourseInfoPanelCompetitorBranchRepository branchRepository;
    private final CompetitorCourseComparisonRepository comparisonRepository;

    private static final Pattern SESSION_PATTERN = Pattern.compile("^\\d{4}-\\d{2}$");
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB

    // Temporary storage for generated error files (with 2-hour TTL cache cleanup)
    @Getter
    @AllArgsConstructor
    private static class StoredErrorFile {
        private final byte[] content;
        private final Instant createdAt;
    }
    private final Map<UUID, StoredErrorFile> errorFileCache = new ConcurrentHashMap<>();

    private void cleanOldErrorFiles() {
        Instant expiryThreshold = Instant.now().minusSeconds(7200); // 2 hours
        errorFileCache.entrySet().removeIf(entry -> entry.getValue().getCreatedAt().isBefore(expiryThreshold));
    }

    // =========================================================================
    // 1. TEMPLATE GENERATION
    // =========================================================================

    @Override
    public byte[] generateTemplate() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // Styles
            CellStyle s1HeaderStyle = createHeaderStyle(workbook, IndexedColors.ROYAL_BLUE.getIndex());
            CellStyle s2HeaderStyle = createHeaderStyle(workbook, IndexedColors.SEA_GREEN.getIndex());
            CellStyle textStyle = createDataStyle(workbook, false);
            CellStyle exampleStyle = createDataStyle(workbook, true);

            // ----------------------------------------------------
            // Sheet 1: Course Info
            // ----------------------------------------------------
            Sheet s1 = workbook.createSheet("Course Info");
            s1.createFreezePane(0, 1);

            String[] s1Headers = {
                    "Course Name *", "Academic Session *", "School", "Course Fee",
                    "Duration", "Eligibility", "Job Opportunities", "Hostel Fee",
                    "Course Details", "Course Specialities / USPs", "Renaissance University USPs",
                    "How We Are Different", "Caller Guidance"
            };

            Row s1HeaderRow = s1.createRow(0);
            s1HeaderRow.setHeightInPoints(24);
            for (int i = 0; i < s1Headers.length; i++) {
                Cell cell = s1HeaderRow.createCell(i);
                cell.setCellValue(s1Headers[i]);
                cell.setCellStyle(s1HeaderStyle);
            }

            // Sample Row 1 - BBA
            Row s1Sample1 = s1.createRow(1);
            s1Sample1.createCell(0).setCellValue("BBA");
            s1Sample1.createCell(1).setCellValue("2026-27");
            s1Sample1.createCell(2).setCellValue("School of Management");
            s1Sample1.createCell(3).setCellValue("₹ 75,000 / Year");
            s1Sample1.createCell(4).setCellValue("3 Years");
            s1Sample1.createCell(5).setCellValue("10+2 with minimum 50% from any recognized board");
            s1Sample1.createCell(6).setCellValue("Business Analyst, Marketing Specialist, HR Manager, Financial Consultant, Operations Associate");
            s1Sample1.createCell(7).setCellValue("₹ 65,000 / Year (Triple Sharing with Mess)");
            s1Sample1.createCell(8).setCellValue("Comprehensive 3-year undergraduate program focusing on general business management principles, analytics, leadership, and digital business tools.");
            s1Sample1.createCell(9).setCellValue("Harvard Business Publishing case studies; Live corporate simulation labs; Bloomberg Terminal access; 100% internship assurance");
            s1Sample1.createCell(10).setCellValue("30-acre state-of-the-art lush green campus; 250+ active campus recruiters; Dedicated Career Acceleration & Incubation Center");
            s1Sample1.createCell(11).setCellValue("Experiential learning with 50% practical exposure; Direct executive mentoring from corporate CXOs; Global exchange partnerships");
            s1Sample1.createCell(12).setCellValue("Greet candidate warmly. Ask about target career domain (Marketing, Finance, HR). Emphasize RU modern labs and placement track record.");
            for (int i = 0; i < s1Headers.length; i++) {
                s1Sample1.getCell(i).setCellStyle(exampleStyle);
            }

            // Sample Row 2 - BCA
            Row s1Sample2 = s1.createRow(2);
            s1Sample2.createCell(0).setCellValue("BCA");
            s1Sample2.createCell(1).setCellValue("2026-27");
            s1Sample2.createCell(2).setCellValue("School of Computer Science");
            s1Sample2.createCell(3).setCellValue("₹ 65,000 / Year");
            s1Sample2.createCell(4).setCellValue("3 Years");
            s1Sample2.createCell(5).setCellValue("10+2 with Mathematics/CS/IT with min 50%");
            s1Sample2.createCell(6).setCellValue("Full Stack Developer, Software Engineer, Cloud Architect, Data Analyst");
            s1Sample2.createCell(7).setCellValue("₹ 65,000 / Year (Triple Sharing with Mess)");
            s1Sample2.createCell(8).setCellValue("Modern computer application curriculum covering AI/ML, Cloud Computing, Full Stack Web Development, and DevOps.");
            s1Sample2.createCell(9).setCellValue("Advanced NVIDIA GPU Computing Lab; AWS Academy Curriculum Integration; Industry-sponsored Capstone projects");
            s1Sample2.createCell(10).setCellValue("Ranked #1 Emerging University for Tech; Robust Alumni Network in Top MNCs (Google, TCS, Infosys, Wipro)");
            s1Sample2.createCell(11).setCellValue("Full stack hands-on project per semester; Specialized certifications included in curriculum at no extra cost");
            s1Sample2.createCell(12).setCellValue("Focus on technical aspirations. Highlight coding bootcamps, hackathons, and high median placement packages.");
            for (int i = 0; i < s1Headers.length; i++) {
                s1Sample2.getCell(i).setCellStyle(exampleStyle);
            }

            for (int i = 0; i < s1Headers.length; i++) {
                s1.autoSizeColumn(i);
                int currentWidth = s1.getColumnWidth(i);
                s1.setColumnWidth(i, Math.max(currentWidth + 1200, 4800));
            }

            // ----------------------------------------------------
            // Sheet 2: Other Colleges
            // ----------------------------------------------------
            Sheet s2 = workbook.createSheet("Other Colleges");
            s2.createFreezePane(0, 1);

            String[] s2Headers = {
                    "Course Name *", "Academic Session *", "College Name *", "Branch",
                    "Course Fee Per Year", "Duration", "Odds", "Eligibility",
                    "Hostel", "Distance From City", "Registration Fee",
                    "Average Placements", "Highest Placement"
            };

            Row s2HeaderRow = s2.createRow(0);
            s2HeaderRow.setHeightInPoints(24);
            for (int i = 0; i < s2Headers.length; i++) {
                Cell cell = s2HeaderRow.createCell(i);
                cell.setCellValue(s2Headers[i]);
                cell.setCellStyle(s2HeaderStyle);
            }

            // Sample Competitors for BBA
            String[][] s2Samples = {
                    {"BBA", "2026-27", "Prestige Institute of Management", "Main Campus", "₹ 1,10,000", "3 Years", "Rigid attendance, high student-to-teacher ratio (80:1), limited industry internships", "10+2 with 50%", "₹ 85,000 / Year", "14 km from Indore City Center", "₹ 1,500", "₹ 4.5 LPA", "₹ 12 LPA"},
                    {"BBA", "2026-27", "Prestige Institute of Management", "City Campus", "₹ 1,15,000", "3 Years", "No residential hostel on site; crowded campus infrastructure", "10+2 with 50%", "No Hostel Available", "3 km from City Center", "₹ 1,500", "₹ 4.2 LPA", "₹ 10 LPA"},
                    {"BBA", "2026-27", "Acropolis Faculty of Management", "Manglia Bypass", "₹ 70,000", "3 Years", "Theoretical syllabus focus, minimal international exposure, remote location", "10+2 with 45%", "₹ 72,000 / Year", "18 km from City Center", "₹ 1,000", "₹ 3.8 LPA", "₹ 8.5 LPA"},
                    {"BCA", "2026-27", "IPS Academy", "Rajendra Nagar Campus", "₹ 85,000", "3 Years", "Outdated curriculum on legacy frameworks; heavy exam-oriented theory", "10+2 with 50%", "₹ 65,000 / Year", "10 km from City Center", "₹ 1,200", "₹ 4.0 LPA", "₹ 9.5 LPA"}
            };

            for (int r = 0; r < s2Samples.length; r++) {
                Row row = s2.createRow(r + 1);
                for (int c = 0; c < s2Samples[r].length; c++) {
                    Cell cell = row.createCell(c);
                    cell.setCellValue(s2Samples[r][c]);
                    cell.setCellStyle(exampleStyle);
                }
            }

            for (int i = 0; i < s2Headers.length; i++) {
                s2.autoSizeColumn(i);
                int currentWidth = s2.getColumnWidth(i);
                s2.setColumnWidth(i, Math.max(currentWidth + 1200, 4800));
            }

            // ----------------------------------------------------
            // Sheet 3: Instructions
            // ----------------------------------------------------
            Sheet s3 = workbook.createSheet("Instructions");
            CellStyle instHeaderStyle = createHeaderStyle(workbook, IndexedColors.DARK_BLUE.getIndex());

            int rIdx = 0;
            Row tRow = s3.createRow(rIdx++);
            Cell tCell = tRow.createCell(0);
            tCell.setCellValue("COURSE INFO PANEL & COMPETITOR COMPARISON — BULK UPLOAD GUIDE");
            tCell.setCellStyle(instHeaderStyle);

            rIdx++; // blank
            String[] instructions = {
                    "1. OVERVIEW & PURPOSE:",
                    "   • This workbook allows administrators to upload and update Lead Info Panel data in bulk.",
                    "   • When counselors open a Lead, information is mapped dynamically based on the Lead's Interested Course.",
                    "",
                    "2. SHEET STRUCTURE:",
                    "   • Sheet 1 ('Course Info'): Contains primary Renaissance University course information.",
                    "     - Exactly ONE row per (Course Name + Academic Session).",
                    "   • Sheet 2 ('Other Colleges'): Contains competitor college comparison details.",
                    "     - Multiple rows can be provided for the same Course Name + Academic Session (e.g. multiple colleges or multiple branches per college).",
                    "",
                    "3. MANDATORY COLUMNS:",
                    "   • Sheet 1 ('Course Info'): 'Course Name' and 'Academic Session' are strictly required.",
                    "   • Sheet 2 ('Other Colleges'): 'Course Name', 'Academic Session', and 'College Name' are strictly required.",
                    "",
                    "4. COURSE RESOLUTION RULES:",
                    "   • Course names are matched against the canonical Course Master in the system (e.g., 'BBA', 'BCA', 'MBA', 'MCA', 'B.Tech CSE').",
                    "   • Case and leading/trailing spaces are normalized automatically.",
                    "   • If a course name matches multiple course records, the row will be flagged with 'AMBIGUOUS_COURSE'.",
                    "   • If a course name does not exist in the system, it will be flagged with 'INVALID_COURSE'.",
                    "",
                    "5. ACADEMIC SESSION RULES:",
                    "   • Academic session must follow the standard format: 'YYYY-YY' (e.g. '2026-27', '2025-26').",
                    "   • Invalid session formats will be flagged with 'INVALID_SESSION'.",
                    "",
                    "6. UPSERT & DUPLICATE BEHAVIOR:",
                    "   • Existing Info Panel records matching Course + Academic Session will be UPDATED.",
                    "   • Missing Info Panel records will be CREATED.",
                    "   • Competitor records matching College Name under the same Info Panel will have comparison data updated and new branches added.",
                    "   • Duplicate identical rows within the same uploaded Excel file will be detected and flagged with 'DUPLICATE_ROW'.",
                    "",
                    "7. SAMPLE DATA REMOVAL:",
                    "   • Delete the example rows before saving and uploading your production file.",
                    "   • Supported file formats: .xlsx or .xls (maximum file size: 10 MB)."
            };

            for (String line : instructions) {
                Row row = s3.createRow(rIdx++);
                Cell c = row.createCell(0);
                c.setCellValue(line);
                if (line.startsWith("1.") || line.startsWith("2.") || line.startsWith("3.") || line.startsWith("4.") || line.startsWith("5.") || line.startsWith("6.") || line.startsWith("7.")) {
                    c.setCellStyle(createBoldStyle(workbook));
                } else {
                    c.setCellStyle(textStyle);
                }
            }

            s3.autoSizeColumn(0);
            s3.setColumnWidth(0, 32000);

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate Course Info Panel bulk upload template", e);
            throw new RuntimeException("Error generating template file: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // 2. PARSE, VALIDATE & PREVIEW
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public CourseInfoPanelPreviewResponseDTO validateExcel(MultipartFile file) throws BadRequestException {
        validateFile(file);
        ParsedWorkbookData parsed = parseAndValidateWorkbook(file, false);

        cleanOldErrorFiles();
        UUID importId = UUID.randomUUID();
        boolean errorFileAvailable = false;

        if (!parsed.errors.isEmpty()) {
            byte[] errorFile = generateErrorSheet(file, parsed.errors);
            errorFileCache.put(importId, new StoredErrorFile(errorFile, Instant.now()));
            errorFileAvailable = true;
        }

        int totalRows = parsed.sheet1RowCount + parsed.sheet2RowCount;
        int errorRows = parsed.errors.size();
        int validRows = Math.max(0, totalRows - errorRows);

        return CourseInfoPanelPreviewResponseDTO.builder()
                .success(true)
                .message("Workbook parsed successfully.")
                .totalRows(totalRows)
                .validRows(validRows)
                .errorRows(errorRows)
                .newRecords(parsed.newRecordsCount)
                .recordsToUpdate(parsed.recordsToUpdateCount)
                .sheet1RowCount(parsed.sheet1RowCount)
                .sheet2RowCount(parsed.sheet2RowCount)
                .canImport(validRows > 0)
                .errorFileAvailable(errorFileAvailable)
                .importId(importId.toString())
                .errors(parsed.errors)
                .build();
    }

    // =========================================================================
    // 3. BULK UPLOAD EXECUTION
    // =========================================================================

    @Override
    @Transactional
    public CourseInfoPanelBulkUploadResponseDTO bulkUpload(MultipartFile file) throws BadRequestException {
        validateFile(file);
        ParsedWorkbookData parsed = parseAndValidateWorkbook(file, true);

        int totalRows = parsed.sheet1RowCount + parsed.sheet2RowCount;
        int failedRows = parsed.errors.size();
        int successfulRows = 0;
        int createdRecords = 0;
        int updatedRecords = 0;

        // Map to hold resolved panels in this import batch: (courseId + "::" + session) -> CourseInfoPanel
        Map<String, CourseInfoPanel> resolvedPanels = new HashMap<>();

        // 1. Process Valid Course Info Rows (Sheet 1)
        for (CourseInfoRow rowData : parsed.validSheet1Rows) {
            String panelKey = rowData.course.getId() + "::" + rowData.academicSession;
            CourseInfoPanel panel = infoPanelRepository.findByCourseIdAndAcademicSessionAndIsDeletedFalse(rowData.course.getId(), rowData.academicSession)
                    .orElse(null);

            boolean isNew = false;
            if (panel == null) {
                panel = CourseInfoPanel.builder()
                        .course(rowData.course)
                        .academicSession(rowData.academicSession)
                        .active(true)
                        .build();
                isNew = true;
            }

            // Apply updates
            if (rowData.school != null) panel.setSchool(rowData.school);
            if (rowData.courseNameInRow != null) panel.setCourseName(rowData.courseNameInRow);
            if (rowData.courseFee != null) panel.setCourseFee(rowData.courseFee);
            if (rowData.duration != null) panel.setDuration(rowData.duration);
            if (rowData.eligibility != null) panel.setEligibility(rowData.eligibility);
            if (rowData.jobOpportunities != null) panel.setJobOpportunities(rowData.jobOpportunities);
            if (rowData.hostelFee != null) panel.setHostelFee(rowData.hostelFee);
            if (rowData.courseDetails != null) panel.setCourseDetails(rowData.courseDetails);
            if (rowData.courseSpecialities != null) panel.setCourseSpecialities(rowData.courseSpecialities);
            if (rowData.renaissanceUniversityUsps != null) panel.setRenaissanceUniversityUsps(rowData.renaissanceUniversityUsps);
            if (rowData.howWeAreDifferent != null) panel.setHowWeAreDifferent(rowData.howWeAreDifferent);
            if (rowData.callerGuidance != null) panel.setCallerGuidance(rowData.callerGuidance);

            CourseInfoPanel savedPanel = infoPanelRepository.save(panel);
            resolvedPanels.put(panelKey, savedPanel);

            if (isNew) {
                createdRecords++;
            } else {
                updatedRecords++;
            }
            successfulRows++;
        }

        // 2. Process Valid Competitor Rows (Sheet 2)
        // Group by (course + session + collegeName) to aggregate branches
        Map<String, List<CompetitorRow>> competitorGroups = parsed.validSheet2Rows.stream()
                .collect(Collectors.groupingBy(r -> r.course.getId() + "::" + r.academicSession + "::" + normalizeText(r.collegeName)));

        for (Map.Entry<String, List<CompetitorRow>> entry : competitorGroups.entrySet()) {
            List<CompetitorRow> rows = entry.getValue();
            if (rows.isEmpty()) continue;
            CompetitorRow first = rows.get(0);
            String panelKey = first.course.getId() + "::" + first.academicSession;

            // Resolve or find parent CourseInfoPanel
            CourseInfoPanel parentPanel = resolvedPanels.get(panelKey);
            if (parentPanel == null) {
                parentPanel = infoPanelRepository.findByCourseIdAndAcademicSessionAndIsDeletedFalse(first.course.getId(), first.academicSession)
                        .orElseGet(() -> {
                            CourseInfoPanel newPanel = CourseInfoPanel.builder()
                                    .course(first.course)
                                    .academicSession(first.academicSession)
                                    .courseName(first.course.getCourseName())
                                    .active(true)
                                    .build();
                            return infoPanelRepository.save(newPanel);
                        });
                resolvedPanels.put(panelKey, parentPanel);
            }

            // Resolve competitor college
            CourseInfoPanelCompetitor competitor = competitorRepository.findByInfoPanelIdAndCollegeNameIgnoreCaseAndIsDeletedFalse(
                    parentPanel.getId(), first.collegeName).orElse(null);

            if (competitor == null) {
                competitor = CourseInfoPanelCompetitor.builder()
                        .infoPanel(parentPanel)
                        .collegeName(first.collegeName.trim())
                        .active(true)
                        .displayOrder(0)
                        .build();
                competitor = competitorRepository.save(competitor);
            }
            final CourseInfoPanelCompetitor targetComp = competitor;

            // Upsert comparison data
            CompetitorCourseComparison comparison = comparisonRepository.findByCompetitorIdAndIsDeletedFalse(targetComp.getId())
                    .orElseGet(() -> CompetitorCourseComparison.builder().competitor(targetComp).build());

            if (first.courseFeePerYear != null) comparison.setCourseFeePerYear(first.courseFeePerYear);
            if (first.duration != null) comparison.setDuration(first.duration);
            if (first.odds != null) comparison.setOdds(first.odds);
            if (first.eligibility != null) comparison.setEligibility(first.eligibility);
            if (first.hostel != null) comparison.setHostel(first.hostel);
            if (first.distanceFromCity != null) comparison.setDistanceFromCity(first.distanceFromCity);
            if (first.registrationFee != null) comparison.setRegistrationFee(first.registrationFee);
            if (first.averagePlacements != null) comparison.setAveragePlacements(first.averagePlacements);
            if (first.highestPlacement != null) comparison.setHighestPlacement(first.highestPlacement);

            comparisonRepository.save(comparison);

            // Add branches
            for (CompetitorRow compRow : rows) {
                if (compRow.branch != null && !compRow.branch.isBlank()) {
                    String bName = compRow.branch.trim();
                    if (!branchRepository.existsByCompetitorIdAndBranchNameIgnoreCaseAndIsDeletedFalse(competitor.getId(), bName)) {
                        branchRepository.save(CourseInfoPanelCompetitorBranch.builder()
                                .competitor(competitor)
                                .branchName(bName)
                                .build());
                    }
                }
                successfulRows++;
            }
        }

        // Cache error file if there are failed rows
        cleanOldErrorFiles();
        UUID importId = UUID.randomUUID();
        boolean errorFileAvailable = false;
        if (!parsed.errors.isEmpty()) {
            byte[] errorFile = generateErrorSheet(file, parsed.errors);
            errorFileCache.put(importId, new StoredErrorFile(errorFile, Instant.now()));
            errorFileAvailable = true;
        }

        return CourseInfoPanelBulkUploadResponseDTO.builder()
                .success(true)
                .message("Course Info Panel bulk import completed successfully.")
                .totalRows(totalRows)
                .successfulRows(successfulRows)
                .failedRows(failedRows)
                .createdRecords(createdRecords)
                .updatedRecords(updatedRecords)
                .skippedRows(failedRows)
                .errorFileAvailable(errorFileAvailable)
                .importId(importId.toString())
                .errors(parsed.errors)
                .build();
    }

    // =========================================================================
    // 4. ERROR FILE RETRIEVAL
    // =========================================================================

    @Override
    public byte[] getErrorFile(UUID importId) {
        StoredErrorFile stored = errorFileCache.get(importId);
        if (stored == null) {
            throw new ResourcesNotFoundException("Error file not found or has expired for import ID: " + importId);
        }
        return stored.getContent();
    }

    // =========================================================================
    // 5. INTERNAL PARSER, VALIDATOR & DATA STRUCTURES
    // =========================================================================

    private static class CourseInfoRow {
        int rowNumber;
        Course course;
        String academicSession;
        String school;
        String courseNameInRow;
        String courseFee;
        String duration;
        String eligibility;
        String jobOpportunities;
        String hostelFee;
        String courseDetails;
        String courseSpecialities;
        String renaissanceUniversityUsps;
        String howWeAreDifferent;
        String callerGuidance;
    }

    private static class CompetitorRow {
        int rowNumber;
        Course course;
        String academicSession;
        String collegeName;
        String branch;
        String courseFeePerYear;
        String duration;
        String odds;
        String eligibility;
        String hostel;
        String distanceFromCity;
        String registrationFee;
        String averagePlacements;
        String highestPlacement;
    }

    private static class ParsedWorkbookData {
        int sheet1RowCount = 0;
        int sheet2RowCount = 0;
        int newRecordsCount = 0;
        int recordsToUpdateCount = 0;
        List<CourseInfoRow> validSheet1Rows = new ArrayList<>();
        List<CompetitorRow> validSheet2Rows = new ArrayList<>();
        List<CourseInfoPanelBulkUploadRowErrorDTO> errors = new ArrayList<>();
    }

    private ParsedWorkbookData parseAndValidateWorkbook(MultipartFile file, boolean persist) throws BadRequestException {
        ParsedWorkbookData result = new ParsedWorkbookData();

        // 1. Preload active courses from DB for batch resolution
        List<Course> allCourses = courseRepository.findAllByIsDeletedFalse();
        Map<String, List<Course>> nameMap = new HashMap<>();
        Map<String, List<Course>> codeMap = new HashMap<>();

        for (Course c : allCourses) {
            if (c.getCourseName() != null) {
                String normName = normalizeText(c.getCourseName());
                nameMap.computeIfAbsent(normName, k -> new ArrayList<>()).add(c);
            }
            if (c.getCourseCode() != null) {
                String normCode = normalizeText(c.getCourseCode());
                codeMap.computeIfAbsent(normCode, k -> new ArrayList<>()).add(c);
            }
        }

        // Preload active authorities for field RBAC validation
        Set<String> authorities = getCurrentUserAuthorities();
        boolean isAdmin = isUserAdmin(authorities);

        try (InputStream is = file.getInputStream(); Workbook workbook = WorkbookFactory.create(is)) {
            DataFormatter formatter = new DataFormatter();

            // Locate Sheet 1: Course Info
            Sheet s1 = findSheet(workbook, "Course Info", "CourseInfo", "Courses", "Course Information", 0);
            // Locate Sheet 2: Other Colleges
            Sheet s2 = findSheet(workbook, "Other Colleges", "OtherColleges", "Competitors", "Competitor Comparison", 1);

            if (s1 == null && s2 == null) {
                throw new BadRequestException("Uploaded workbook must contain at least 'Course Info' or 'Other Colleges' sheet.");
            }

            // ----------------------------------------------------
            // Validate Sheet 1 (Course Info)
            // ----------------------------------------------------
            Set<String> sheet1SeenKeys = new HashSet<>();

            if (s1 != null) {
                Row headerRow = s1.getRow(0);
                if (headerRow == null) {
                    throw new BadRequestException("Sheet '" + s1.getSheetName() + "' is empty or missing headers.");
                }
                Map<String, Integer> hMap = parseHeaders(headerRow, formatter);
                validateRequiredHeader(hMap, "courseName", "Course Name", s1.getSheetName());
                validateRequiredHeader(hMap, "academicSession", "Academic Session", s1.getSheetName());

                for (int r = 1; r <= s1.getLastRowNum(); r++) {
                    Row row = s1.getRow(r);
                    if (row == null || isRowEmpty(row, formatter)) continue;
                    result.sheet1RowCount++;
                    int rowNum = r + 1; // 1-indexed for user display

                    String rawCourseName = getCellValue(row, hMap, "courseName", formatter);
                    String rawSession = getCellValue(row, hMap, "academicSession", formatter);

                    if (rawCourseName == null || rawCourseName.isBlank()) {
                        result.errors.add(buildError(s1.getSheetName(), rowNum, "", "", "MISSING_COURSE", "Course Name is required"));
                        continue;
                    }

                    if (rawSession == null || rawSession.isBlank()) {
                        result.errors.add(buildError(s1.getSheetName(), rowNum, rawCourseName, "", "MISSING_SESSION", "Academic Session is required"));
                        continue;
                    }

                    String rawDuration = getCellValue(row, hMap, "duration", formatter);
                    String rawFee = getCellValue(row, hMap, "courseFee", formatter);
                    String rawDetails = getCellValue(row, hMap, "courseDetails", formatter);

                    // Resolve or Auto-create Course
                    Course resolvedCourse = resolveCourse(rawCourseName, rawDuration, rawFee, rawDetails, nameMap, codeMap, s1.getSheetName(), rowNum, result.errors, persist);
                    if (resolvedCourse == null) {
                        continue;
                    }

                    // Validate Session
                    String session = rawSession.trim();
                    if (!SESSION_PATTERN.matcher(session).matches()) {
                        result.errors.add(buildError(s1.getSheetName(), rowNum, rawCourseName, "", "INVALID_SESSION",
                                "Academic session '" + session + "' is invalid. Expected format YYYY-YY (e.g. 2026-27)"));
                        continue;
                    }

                    // Duplicate detection within Sheet 1
                    String dupKey = resolvedCourse.getId() + "::" + session;
                    if (!sheet1SeenKeys.add(dupKey)) {
                        result.errors.add(buildError(s1.getSheetName(), rowNum, rawCourseName, "", "DUPLICATE_ROW",
                                "Duplicate entry for course '" + rawCourseName + "' and session '" + session + "' in Sheet 1"));
                        continue;
                    }

                    // Extract fields
                    CourseInfoRow cRow = new CourseInfoRow();
                    cRow.rowNumber = rowNum;
                    cRow.course = resolvedCourse;
                    cRow.academicSession = session;
                    cRow.school = getCellValue(row, hMap, "school", formatter);
                    cRow.courseNameInRow = getCellValue(row, hMap, "courseName", formatter);
                    cRow.courseFee = getCellValue(row, hMap, "courseFee", formatter);
                    cRow.duration = getCellValue(row, hMap, "duration", formatter);
                    cRow.eligibility = getCellValue(row, hMap, "eligibility", formatter);
                    cRow.jobOpportunities = getCellValue(row, hMap, "jobOpportunities", formatter);
                    cRow.hostelFee = getCellValue(row, hMap, "hostelFee", formatter);
                    cRow.courseDetails = getCellValue(row, hMap, "courseDetails", formatter);
                    cRow.courseSpecialities = getCellValue(row, hMap, "courseSpecialities", formatter);
                    cRow.renaissanceUniversityUsps = getCellValue(row, hMap, "renaissanceUniversityUsps", formatter);
                    cRow.howWeAreDifferent = getCellValue(row, hMap, "howWeAreDifferent", formatter);
                    cRow.callerGuidance = getCellValue(row, hMap, "callerGuidance", formatter);

                    // Field RBAC validation
                    if (!isAdmin) {
                        boolean fieldPermDenied = validateFieldLevelRBAC(cRow, authorities, s1.getSheetName(), rowNum, result.errors);
                        if (fieldPermDenied) continue;
                    }

                    result.validSheet1Rows.add(cRow);

                    // Check if new or update
                    boolean exists = infoPanelRepository.existsByCourseIdAndAcademicSessionAndIsDeletedFalse(resolvedCourse.getId(), session);
                    if (exists) {
                        result.recordsToUpdateCount++;
                    } else {
                        result.newRecordsCount++;
                    }
                }
            }

            // ----------------------------------------------------
            // Validate Sheet 2 (Other Colleges)
            // ----------------------------------------------------
            Set<String> sheet2SeenKeys = new HashSet<>();

            if (s2 != null) {
                Row headerRow = s2.getRow(0);
                if (headerRow == null) {
                    throw new BadRequestException("Sheet '" + s2.getSheetName() + "' is empty or missing headers.");
                }
                Map<String, Integer> hMap = parseHeaders(headerRow, formatter);
                validateRequiredHeader(hMap, "courseName", "Course Name", s2.getSheetName());
                validateRequiredHeader(hMap, "academicSession", "Academic Session", s2.getSheetName());
                validateRequiredHeader(hMap, "collegeName", "College Name", s2.getSheetName());

                for (int r = 1; r <= s2.getLastRowNum(); r++) {
                    Row row = s2.getRow(r);
                    if (row == null || isRowEmpty(row, formatter)) continue;
                    result.sheet2RowCount++;
                    int rowNum = r + 1;

                    String rawCourseName = getCellValue(row, hMap, "courseName", formatter);
                    String rawSession = getCellValue(row, hMap, "academicSession", formatter);
                    String rawCollegeName = getCellValue(row, hMap, "collegeName", formatter);
                    String rawBranch = getCellValue(row, hMap, "branch", formatter);

                    if (rawCourseName == null || rawCourseName.isBlank()) {
                        result.errors.add(buildError(s2.getSheetName(), rowNum, "", rawCollegeName, "MISSING_COURSE", "Course Name is required"));
                        continue;
                    }

                    if (rawSession == null || rawSession.isBlank()) {
                        result.errors.add(buildError(s2.getSheetName(), rowNum, rawCourseName, rawCollegeName, "MISSING_SESSION", "Academic Session is required"));
                        continue;
                    }

                    if (rawCollegeName == null || rawCollegeName.isBlank()) {
                        result.errors.add(buildError(s2.getSheetName(), rowNum, rawCourseName, "", "MISSING_COLLEGE", "College Name is required"));
                        continue;
                    }

                    String rawDuration = getCellValue(row, hMap, "duration", formatter);
                    String rawFee = getCellValue(row, hMap, "courseFeePerYear", formatter);

                    // Resolve or Auto-create Course
                    Course resolvedCourse = resolveCourse(rawCourseName, rawDuration, rawFee, null, nameMap, codeMap, s2.getSheetName(), rowNum, result.errors, persist);
                    if (resolvedCourse == null) {
                        continue;
                    }

                    // Validate Session
                    String session = rawSession.trim();
                    if (!SESSION_PATTERN.matcher(session).matches()) {
                        result.errors.add(buildError(s2.getSheetName(), rowNum, rawCourseName, rawCollegeName, "INVALID_SESSION",
                                "Academic session '" + session + "' is invalid. Expected format YYYY-YY (e.g. 2026-27)"));
                        continue;
                    }

                    // Duplicate detection within Sheet 2
                    String dupKey = resolvedCourse.getId() + "::" + session + "::" + normalizeText(rawCollegeName) + "::" + normalizeText(rawBranch != null ? rawBranch : "");
                    if (!sheet2SeenKeys.add(dupKey)) {
                        result.errors.add(buildError(s2.getSheetName(), rowNum, rawCourseName, rawCollegeName, "DUPLICATE_ROW",
                                "Duplicate competitor college and branch entry in Sheet 2"));
                        continue;
                    }

                    CompetitorRow compRow = new CompetitorRow();
                    compRow.rowNumber = rowNum;
                    compRow.course = resolvedCourse;
                    compRow.academicSession = session;
                    compRow.collegeName = rawCollegeName.trim();
                    compRow.branch = rawBranch != null ? rawBranch.trim() : null;
                    compRow.courseFeePerYear = getCellValue(row, hMap, "courseFeePerYear", formatter);
                    compRow.duration = getCellValue(row, hMap, "duration", formatter);
                    compRow.odds = getCellValue(row, hMap, "odds", formatter);
                    compRow.eligibility = getCellValue(row, hMap, "eligibility", formatter);
                    compRow.hostel = getCellValue(row, hMap, "hostel", formatter);
                    compRow.distanceFromCity = getCellValue(row, hMap, "distanceFromCity", formatter);
                    compRow.registrationFee = getCellValue(row, hMap, "registrationFee", formatter);
                    compRow.averagePlacements = getCellValue(row, hMap, "averagePlacements", formatter);
                    compRow.highestPlacement = getCellValue(row, hMap, "highestPlacement", formatter);

                    // Field RBAC check for competitor fields
                    if (!isAdmin) {
                        boolean compPermDenied = validateCompetitorFieldLevelRBAC(compRow, authorities, s2.getSheetName(), rowNum, result.errors);
                        if (compPermDenied) continue;
                    }

                    result.validSheet2Rows.add(compRow);
                }
            }

        } catch (BadRequestException bre) {
            throw bre;
        } catch (Exception e) {
            log.error("Failed to parse uploaded Excel file", e);
            throw new BadRequestException("Unable to read Excel file: " + e.getMessage());
        }

        return result;
    }

    private Course resolveCourse(String rawCourseName, String durationStr, String feeStr, String detailsStr,
                                 Map<String, List<Course>> nameMap, Map<String, List<Course>> codeMap,
                                 String sheetName, int rowNum, List<CourseInfoPanelBulkUploadRowErrorDTO> errors,
                                 boolean persist) {
        String norm = normalizeText(rawCourseName);

        List<Course> byName = nameMap.get(norm);
        if (byName != null && !byName.isEmpty()) {
            if (byName.size() == 1) {
                return byName.get(0);
            } else if (byName.size() > 1) {
                errors.add(buildError(sheetName, rowNum, rawCourseName, "", "AMBIGUOUS_COURSE",
                        "Course name '" + rawCourseName + "' matches multiple course records. Please disambiguate."));
                return null;
            }
        }

        List<Course> byCode = codeMap.get(norm);
        if (byCode != null && !byCode.isEmpty()) {
            if (byCode.size() == 1) {
                return byCode.get(0);
            } else if (byCode.size() > 1) {
                errors.add(buildError(sheetName, rowNum, rawCourseName, "", "AMBIGUOUS_COURSE",
                        "Course code '" + rawCourseName + "' matches multiple course records. Please disambiguate."));
                return null;
            }
        }

        // Auto-create missing course so users do not have to create courses one-by-one
        try {
            return getOrCreateCourse(rawCourseName, durationStr, feeStr, detailsStr, nameMap, codeMap, persist);
        } catch (Exception e) {
            log.warn("Failed to auto-create course '{}': {}", rawCourseName, e.getMessage());
            errors.add(buildError(sheetName, rowNum, rawCourseName, "", "COURSE_CREATE_FAILED",
                    "Course '" + rawCourseName + "' was not found and could not be auto-created: " + e.getMessage()));
            return null;
        }
    }

    private Course getOrCreateCourse(String rawCourseName, String durationStr, String feeStr, String detailsStr,
                                     Map<String, List<Course>> nameMap, Map<String, List<Course>> codeMap,
                                     boolean persist) {
        String trimmedName = rawCourseName.trim();
        String norm = normalizeText(trimmedName);

        List<Course> existing = nameMap.get(norm);
        if (existing != null && !existing.isEmpty()) {
            return existing.get(0);
        }

        String baseCode = trimmedName.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        if (baseCode.length() > 20) {
            baseCode = baseCode.substring(0, 20);
        }
        if (baseCode.isEmpty()) {
            baseCode = "CRS" + (System.currentTimeMillis() % 10000);
        }
        String finalCode = baseCode;
        int counter = 1;
        while (courseRepository.existsByCourseCodeIgnoreCase(finalCode) || codeMap.containsKey(normalizeText(finalCode))) {
            finalCode = baseCode + "_" + counter++;
        }

        int duration = 3;
        String durationUnit = "Years";
        if (durationStr != null && !durationStr.isBlank()) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(durationStr);
            if (m.find()) {
                try {
                    duration = Integer.parseInt(m.group(1));
                } catch (Exception ignored) {}
            }
            if (durationStr.toLowerCase().contains("month")) {
                durationUnit = "Months";
            } else if (durationStr.toLowerCase().contains("sem")) {
                durationUnit = "Semesters";
            }
        }

        double fee = 0.0;
        if (feeStr != null && !feeStr.isBlank()) {
            String digitsOnly = feeStr.replaceAll("[^0-9.]", "");
            if (!digitsOnly.isEmpty()) {
                try {
                    fee = Double.parseDouble(digitsOnly);
                } catch (Exception ignored) {}
            }
        }

        CourseType courseType = null;
        if (courseTypeRepository != null) {
            courseType = courseTypeRepository.findAll().stream()
                    .filter(ct -> !ct.isDeleted() && ct.getStatus() == com.app.datadistribution.enums.Status.ACTIVE)
                    .findFirst()
                    .orElse(null);

            if (courseType == null && persist) {
                CourseType newType = CourseType.builder()
                        .name("General")
                        .description("Default course type auto-created during bulk upload")
                        .status(com.app.datadistribution.enums.Status.ACTIVE)
                        .build();
                courseType = courseTypeRepository.save(newType);
            }
        }

        Course course = Course.builder()
                .courseName(trimmedName)
                .courseCode(finalCode)
                .courseType(courseType)
                .duration(duration)
                .durationUnit(durationUnit)
                .fees(fee)
                .description(detailsStr != null && !detailsStr.isBlank() ? detailsStr.trim() : trimmedName)
                .status(com.app.datadistribution.enums.Status.ACTIVE)
                .build();

        if (persist) {
            course = courseRepository.save(course);
            log.info("Auto-created missing course '{}' ({}) during Info Panel bulk upload", trimmedName, finalCode);
        } else {
            course.setId(UUID.randomUUID());
        }

        Course finalCourse = course;
        nameMap.computeIfAbsent(norm, k -> new ArrayList<>()).add(finalCourse);
        codeMap.computeIfAbsent(normalizeText(finalCode), k -> new ArrayList<>()).add(finalCourse);

        return finalCourse;
    }

    private boolean validateFieldLevelRBAC(CourseInfoRow row, Set<String> authorities, String sheetName, int rowNum,
                                           List<CourseInfoPanelBulkUploadRowErrorDTO> errors) {
        if (row.courseFee != null && !authorities.contains(PermissionType.INFO_PANEL_FIELD_COURSE_FEE_WRITE.name())) {
            errors.add(buildError(sheetName, rowNum, row.course.getCourseName(), "", "FIELD_PERMISSION_DENIED", "You do not have permission to modify Course Fee"));
            return true;
        }
        if (row.duration != null && !authorities.contains(PermissionType.INFO_PANEL_FIELD_DURATION_WRITE.name())) {
            errors.add(buildError(sheetName, rowNum, row.course.getCourseName(), "", "FIELD_PERMISSION_DENIED", "You do not have permission to modify Duration"));
            return true;
        }
        if (row.eligibility != null && !authorities.contains(PermissionType.INFO_PANEL_FIELD_ELIGIBILITY_WRITE.name())) {
            errors.add(buildError(sheetName, rowNum, row.course.getCourseName(), "", "FIELD_PERMISSION_DENIED", "You do not have permission to modify Eligibility"));
            return true;
        }
        if (row.jobOpportunities != null && !authorities.contains(PermissionType.INFO_PANEL_FIELD_JOB_OPPORTUNITIES_WRITE.name())) {
            errors.add(buildError(sheetName, rowNum, row.course.getCourseName(), "", "FIELD_PERMISSION_DENIED", "You do not have permission to modify Job Opportunities"));
            return true;
        }
        if (row.hostelFee != null && !authorities.contains(PermissionType.INFO_PANEL_FIELD_HOSTEL_FEE_WRITE.name())) {
            errors.add(buildError(sheetName, rowNum, row.course.getCourseName(), "", "FIELD_PERMISSION_DENIED", "You do not have permission to modify Hostel Fee"));
            return true;
        }
        if (row.courseDetails != null && !authorities.contains(PermissionType.INFO_PANEL_FIELD_COURSE_DETAILS_WRITE.name())) {
            errors.add(buildError(sheetName, rowNum, row.course.getCourseName(), "", "FIELD_PERMISSION_DENIED", "You do not have permission to modify Course Details"));
            return true;
        }
        if (row.courseSpecialities != null && !authorities.contains(PermissionType.INFO_PANEL_FIELD_COURSE_SPECIALITIES_WRITE.name())) {
            errors.add(buildError(sheetName, rowNum, row.course.getCourseName(), "", "FIELD_PERMISSION_DENIED", "You do not have permission to modify Course Specialities"));
            return true;
        }
        if (row.renaissanceUniversityUsps != null && !authorities.contains(PermissionType.INFO_PANEL_FIELD_RU_USPS_WRITE.name())) {
            errors.add(buildError(sheetName, rowNum, row.course.getCourseName(), "", "FIELD_PERMISSION_DENIED", "You do not have permission to modify RU USPs"));
            return true;
        }
        if (row.howWeAreDifferent != null && !authorities.contains(PermissionType.INFO_PANEL_FIELD_HOW_WE_ARE_DIFFERENT_WRITE.name())) {
            errors.add(buildError(sheetName, rowNum, row.course.getCourseName(), "", "FIELD_PERMISSION_DENIED", "You do not have permission to modify How We Are Different"));
            return true;
        }
        if (row.callerGuidance != null && !authorities.contains(PermissionType.INFO_PANEL_FIELD_CALLER_GUIDANCE_WRITE.name())) {
            errors.add(buildError(sheetName, rowNum, row.course.getCourseName(), "", "FIELD_PERMISSION_DENIED", "You do not have permission to modify Caller Guidance"));
            return true;
        }
        return false;
    }

    private boolean validateCompetitorFieldLevelRBAC(CompetitorRow row, Set<String> authorities, String sheetName, int rowNum,
                                                     List<CourseInfoPanelBulkUploadRowErrorDTO> errors) {
        if (!authorities.contains(PermissionType.INFO_PANEL_FIELD_COLLEGE_NAME_WRITE.name())) {
            errors.add(buildError(sheetName, rowNum, row.course.getCourseName(), row.collegeName, "FIELD_PERMISSION_DENIED", "You do not have permission to modify Competitor College Name"));
            return true;
        }
        if (row.branch != null && !authorities.contains(PermissionType.INFO_PANEL_FIELD_BRANCHES_WRITE.name())) {
            errors.add(buildError(sheetName, rowNum, row.course.getCourseName(), row.collegeName, "FIELD_PERMISSION_DENIED", "You do not have permission to modify Competitor Branches"));
            return true;
        }
        if (row.courseFeePerYear != null && !authorities.contains(PermissionType.INFO_PANEL_FIELD_COMPETITOR_FEE_WRITE.name())) {
            errors.add(buildError(sheetName, rowNum, row.course.getCourseName(), row.collegeName, "FIELD_PERMISSION_DENIED", "You do not have permission to modify Competitor Course Fee"));
            return true;
        }
        return false;
    }

    // =========================================================================
    // 6. ERROR SHEET GENERATOR
    // =========================================================================

    private byte[] generateErrorSheet(MultipartFile originalFile, List<CourseInfoPanelBulkUploadRowErrorDTO> errors) {
        try (InputStream is = originalFile.getInputStream();
             Workbook origWorkbook = WorkbookFactory.create(is);
             Workbook errorWorkbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            CellStyle errHeaderStyle = createHeaderStyle(errorWorkbook, IndexedColors.DARK_RED.getIndex());
            CellStyle badgeStyle = errorWorkbook.createCellStyle();
            Font badgeFont = errorWorkbook.createFont();
            badgeFont.setBold(true);
            badgeFont.setColor(IndexedColors.RED.getIndex());
            badgeStyle.setFont(badgeFont);

            CellStyle textStyle = createDataStyle(errorWorkbook, false);

            // Sheet 1: Error Summary
            Sheet sumSheet = errorWorkbook.createSheet("Error Summary");
            sumSheet.createFreezePane(0, 1);

            String[] sumHeaders = {"Row #", "Sheet Name", "Course Name", "College Name", "Error Code", "Error Message", "Status"};
            Row sumHeaderRow = sumSheet.createRow(0);
            sumHeaderRow.setHeightInPoints(22);
            for (int i = 0; i < sumHeaders.length; i++) {
                Cell cell = sumHeaderRow.createCell(i);
                cell.setCellValue(sumHeaders[i]);
                cell.setCellStyle(errHeaderStyle);
            }

            for (int r = 0; r < errors.size(); r++) {
                CourseInfoPanelBulkUploadRowErrorDTO err = errors.get(r);
                Row row = sumSheet.createRow(r + 1);
                row.createCell(0).setCellValue(err.getRowNumber());
                row.createCell(1).setCellValue(err.getSheetName());
                row.createCell(2).setCellValue(err.getCourseName() != null ? err.getCourseName() : "-");
                row.createCell(3).setCellValue(err.getCollegeName() != null ? err.getCollegeName() : "-");
                Cell codeCell = row.createCell(4);
                codeCell.setCellValue(err.getErrorCode());
                codeCell.setCellStyle(badgeStyle);

                row.createCell(5).setCellValue(err.getMessage());
                Cell stCell = row.createCell(6);
                stCell.setCellValue(err.getStatus());
                stCell.setCellStyle(badgeStyle);

                for (int c = 0; c < 7; c++) {
                    if (c != 4 && c != 6) {
                        row.getCell(c).setCellStyle(textStyle);
                    }
                }
            }

            for (int i = 0; i < sumHeaders.length; i++) {
                sumSheet.autoSizeColumn(i);
                int w = sumSheet.getColumnWidth(i);
                sumSheet.setColumnWidth(i, Math.max(w + 1000, 4000));
            }

            // Copy original sheets with error columns attached
            DataFormatter formatter = new DataFormatter();
            Map<String, Map<Integer, CourseInfoPanelBulkUploadRowErrorDTO>> errorMapBySheet = new HashMap<>();
            for (CourseInfoPanelBulkUploadRowErrorDTO err : errors) {
                errorMapBySheet.computeIfAbsent(err.getSheetName().toLowerCase(), k -> new HashMap<>())
                        .put(err.getRowNumber(), err);
            }

            for (int sIdx = 0; sIdx < origWorkbook.getNumberOfSheets(); sIdx++) {
                Sheet origSheet = origWorkbook.getSheetAt(sIdx);
                String sName = origSheet.getSheetName();
                if ("Instructions".equalsIgnoreCase(sName)) continue;

                Sheet errSheet = errorWorkbook.createSheet(sName + " (With Errors)");
                errSheet.createFreezePane(0, 1);

                Map<Integer, CourseInfoPanelBulkUploadRowErrorDTO> sheetErrors = errorMapBySheet.getOrDefault(sName.toLowerCase(), Collections.emptyMap());

                // Copy Header Row with error prepended
                Row origHRow = origSheet.getRow(0);
                if (origHRow != null) {
                    Row newHRow = errSheet.createRow(0);
                    newHRow.setHeightInPoints(22);
                    newHRow.createCell(0).setCellValue("Row #");
                    newHRow.getCell(0).setCellStyle(errHeaderStyle);
                    newHRow.createCell(1).setCellValue("Status");
                    newHRow.getCell(1).setCellStyle(errHeaderStyle);
                    newHRow.createCell(2).setCellValue("Error Code");
                    newHRow.getCell(2).setCellStyle(errHeaderStyle);
                    newHRow.createCell(3).setCellValue("Error Message");
                    newHRow.getCell(3).setCellStyle(errHeaderStyle);

                    for (int c = 0; c < origHRow.getLastCellNum(); c++) {
                        Cell origC = origHRow.getCell(c);
                        Cell newC = newHRow.createCell(c + 4);
                        if (origC != null) {
                            newC.setCellValue(formatter.formatCellValue(origC));
                        }
                        newC.setCellStyle(errHeaderStyle);
                    }
                }

                // Copy Data Rows
                for (int r = 1; r <= origSheet.getLastRowNum(); r++) {
                    Row origRow = origSheet.getRow(r);
                    if (origRow == null || isRowEmpty(origRow, formatter)) continue;
                    int rowNum = r + 1;
                    Row newRow = errSheet.createRow(r);

                    CourseInfoPanelBulkUploadRowErrorDTO err = sheetErrors.get(rowNum);
                    newRow.createCell(0).setCellValue(rowNum);
                    newRow.createCell(1).setCellValue(err != null ? "ERROR" : "VALID");
                    newRow.createCell(2).setCellValue(err != null ? err.getErrorCode() : "-");
                    newRow.createCell(3).setCellValue(err != null ? err.getMessage() : "OK");

                    if (err != null) {
                        newRow.getCell(1).setCellStyle(badgeStyle);
                        newRow.getCell(2).setCellStyle(badgeStyle);
                    } else {
                        newRow.getCell(1).setCellStyle(textStyle);
                        newRow.getCell(2).setCellStyle(textStyle);
                    }
                    newRow.getCell(0).setCellStyle(textStyle);
                    newRow.getCell(3).setCellStyle(textStyle);

                    for (int c = 0; c < origRow.getLastCellNum(); c++) {
                        Cell origC = origRow.getCell(c);
                        Cell newC = newRow.createCell(c + 4);
                        if (origC != null) {
                            newC.setCellValue(formatter.formatCellValue(origC));
                        }
                        newC.setCellStyle(textStyle);
                    }
                }

                int totalCols = (origHRow != null ? origHRow.getLastCellNum() : 10) + 4;
                for (int i = 0; i < totalCols; i++) {
                    errSheet.autoSizeColumn(i);
                    int w = errSheet.getColumnWidth(i);
                    errSheet.setColumnWidth(i, Math.max(w + 1000, 3800));
                }
            }

            errorWorkbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate error sheet", e);
            return new byte[0];
        }
    }

    // =========================================================================
    // 7. HELPER UTILITIES
    // =========================================================================

    private void validateFile(MultipartFile file) throws BadRequestException {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Uploaded file is missing or empty.");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || (!originalFilename.toLowerCase().endsWith(".xlsx") && !originalFilename.toLowerCase().endsWith(".xls"))) {
            throw new BadRequestException("Invalid file format. Only Excel files (.xlsx or .xls) are supported.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BadRequestException("File size exceeds maximum allowed limit of 10 MB.");
        }
    }

    private Sheet findSheet(Workbook workbook, String primaryName, String alt1, String alt2, String alt3, int fallbackIndex) {
        Sheet s = workbook.getSheet(primaryName);
        if (s != null) return s;
        if (alt1 != null && (s = workbook.getSheet(alt1)) != null) return s;
        if (alt2 != null && (s = workbook.getSheet(alt2)) != null) return s;
        if (alt3 != null && (s = workbook.getSheet(alt3)) != null) return s;

        // Try case-insensitive search
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            String name = workbook.getSheetName(i).trim().toLowerCase();
            if (name.equalsIgnoreCase(primaryName) || (alt1 != null && name.equalsIgnoreCase(alt1))
                    || (alt2 != null && name.equalsIgnoreCase(alt2)) || (alt3 != null && name.equalsIgnoreCase(alt3))) {
                return workbook.getSheetAt(i);
            }
        }

        // Try partial keyword match
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            String name = workbook.getSheetName(i).trim().toLowerCase();
            if (primaryName.toLowerCase().contains("course") && name.contains("course")) {
                return workbook.getSheetAt(i);
            }
            if (primaryName.toLowerCase().contains("other") && (name.contains("other") || name.contains("college") || name.contains("competitor"))) {
                return workbook.getSheetAt(i);
            }
        }

        // Fallback by index if in bounds and not instructions
        if (fallbackIndex >= 0 && fallbackIndex < workbook.getNumberOfSheets()) {
            Sheet candidate = workbook.getSheetAt(fallbackIndex);
            if (!candidate.getSheetName().toLowerCase().contains("instruct")) {
                return candidate;
            }
        }
        return null;
    }

    private Map<String, Integer> parseHeaders(Row headerRow, DataFormatter formatter) {
        Map<String, Integer> map = new HashMap<>();
        for (int c = 0; c < headerRow.getLastCellNum(); c++) {
            Cell cell = headerRow.getCell(c);
            if (cell != null) {
                String raw = formatter.formatCellValue(cell).trim().toLowerCase().replaceAll("[_*\\s\\-]+", "");

                if (raw.contains("coursename") || raw.equals("course") || raw.equals("program")) {
                    map.put("courseName", c);
                } else if (raw.contains("academicsession") || raw.equals("session") || raw.equals("year")) {
                    map.put("academicSession", c);
                } else if (raw.contains("collegename") || raw.equals("college") || raw.equals("competitor") || raw.equals("competitorcollege")) {
                    map.put("collegeName", c);
                } else if (raw.contains("branch") || raw.equals("stream") || raw.equals("specialization")) {
                    map.put("branch", c);
                } else if (raw.contains("coursefeeperyear") || raw.contains("feeperyear") || raw.equals("yearlyfee")) {
                    map.put("courseFeePerYear", c);
                } else if (raw.contains("coursefee") || raw.equals("fee") || raw.equals("fees")) {
                    map.put("courseFee", c);
                } else if (raw.contains("duration")) {
                    map.put("duration", c);
                } else if (raw.contains("school") || raw.contains("department") || raw.contains("coursetype")) {
                    map.put("school", c);
                } else if (raw.contains("eligibility")) {
                    map.put("eligibility", c);
                } else if (raw.contains("jobopportunities") || raw.contains("job") || raw.contains("career")) {
                    map.put("jobOpportunities", c);
                } else if (raw.contains("hostelfee")) {
                    map.put("hostelFee", c);
                } else if (raw.equals("hostel")) {
                    map.put("hostel", c);
                } else if (raw.contains("coursedetails") || raw.equals("details") || raw.equals("syllabus")) {
                    map.put("courseDetails", c);
                } else if (raw.contains("coursespecialities") || raw.contains("coursespeciality") || raw.contains("courseusp")) {
                    map.put("courseSpecialities", c);
                } else if (raw.contains("renaissanceuniversityusps") || raw.contains("ruusps") || raw.contains("renaissanceusps")) {
                    map.put("renaissanceUniversityUsps", c);
                } else if (raw.contains("howwearedifferent") || raw.contains("different") || raw.contains("usps")) {
                    map.put("howWeAreDifferent", c);
                } else if (raw.contains("callerguidance") || raw.contains("caller") || raw.contains("guidance")) {
                    map.put("callerGuidance", c);
                } else if (raw.contains("odds") || raw.contains("drawback")) {
                    map.put("odds", c);
                } else if (raw.contains("distancefromcity") || raw.contains("distance")) {
                    map.put("distanceFromCity", c);
                } else if (raw.contains("registrationfee") || raw.contains("regfee")) {
                    map.put("registrationFee", c);
                } else if (raw.contains("averageplacement") || raw.contains("avgplacement")) {
                    map.put("averagePlacements", c);
                } else if (raw.contains("highestplacement")) {
                    map.put("highestPlacement", c);
                }
            }
        }
        return map;
    }

    private void validateRequiredHeader(Map<String, Integer> hMap, String key, String displayName, String sheetName)
            throws BadRequestException {
        if (!hMap.containsKey(key)) {
            throw new BadRequestException("Sheet '" + sheetName + "' is missing required column header: '" + displayName + "'.");
        }
    }

    private String getCellValue(Row row, Map<String, Integer> headerMap, String key, DataFormatter formatter) {
        Integer colIndex = headerMap.get(key);
        if (colIndex == null) return null;
        Cell cell = row.getCell(colIndex);
        if (cell == null || cell.getCellType() == CellType.BLANK) return null;
        String val = formatter.formatCellValue(cell).trim();
        return val.isEmpty() ? null : val;
    }

    private boolean isRowEmpty(Row row, DataFormatter formatter) {
        if (row == null) return true;
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String val = formatter.formatCellValue(cell);
                if (val != null && !val.trim().isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    private String normalizeText(String text) {
        if (text == null) return "";
        return text.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    private CourseInfoPanelBulkUploadRowErrorDTO buildError(String sheetName, int rowNum, String courseName,
                                                           String collegeName, String code, String message) {
        return CourseInfoPanelBulkUploadRowErrorDTO.builder()
                .sheetName(sheetName)
                .rowNumber(rowNum)
                .courseName(courseName)
                .collegeName(collegeName)
                .errorCode(code)
                .message(message)
                .status("ERROR")
                .build();
    }

    private Set<String> getCurrentUserAuthorities() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Collections.emptySet();
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(String::toUpperCase)
                .collect(Collectors.toSet());
    }

    private boolean isUserAdmin(Set<String> authorities) {
        return authorities.contains("ROLE_SUPER_ADMIN")
                || authorities.contains("ROLE_ADMIN")
                || authorities.contains("SUPER_ADMIN")
                || authorities.contains("ADMIN");
    }

    private CellStyle createHeaderStyle(Workbook wb, short bgColor) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setFillForegroundColor(bgColor);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBottomBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
        return style;
    }

    private CellStyle createDataStyle(Workbook wb, boolean italic) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setItalic(italic);
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        style.setWrapText(true);
        return style;
    }

    private CellStyle createBoldStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        return style;
    }
}
