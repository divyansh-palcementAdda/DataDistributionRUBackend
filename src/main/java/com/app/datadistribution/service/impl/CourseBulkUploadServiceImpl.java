package com.app.datadistribution.service.impl;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.app.datadistribution.dto.course.CourseBulkUploadPreviewResponseDTO;
import com.app.datadistribution.dto.course.CourseBulkUploadResponseDTO;
import com.app.datadistribution.dto.course.CourseBulkUploadRowDTO;
import com.app.datadistribution.dto.course.CourseBulkUploadRowErrorDTO;
import com.app.datadistribution.entity.Course;
import com.app.datadistribution.entity.CourseType;
import com.app.datadistribution.enums.Status;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.repository.CourseRepository;
import com.app.datadistribution.repository.CourseTypeRepository;
import com.app.datadistribution.service.interfaces.ICourseBulkUploadService;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CourseBulkUploadServiceImpl implements ICourseBulkUploadService {

    private final CourseRepository courseRepository;
    private final CourseTypeRepository courseTypeRepository;

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB

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

            CellStyle headerStyle = createHeaderStyle(workbook, IndexedColors.ROYAL_BLUE.getIndex());
            CellStyle textStyle = createDataStyle(workbook, false);
            CellStyle exampleStyle = createDataStyle(workbook, true);

            // ----------------------------------------------------
            // Sheet 1: Courses
            // ----------------------------------------------------
            Sheet sheet = workbook.createSheet("Courses");
            sheet.createFreezePane(0, 1);

            String[] headers = {
                    "Course Name *", "Course Code *", "Course Type *", "Duration *",
                    "Duration Unit *", "Fees *", "Description", "Status"
            };

            Row headerRow = sheet.createRow(0);
            headerRow.setHeightInPoints(24);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Sample Rows
            String[][] sampleData = {
                    {"Bachelor of Business Administration (BBA)", "BBA-001", "Undergraduate", "3", "Years", "75000", "Undergraduate business administration program focusing on management and marketing", "ACTIVE"},
                    {"Bachelor of Computer Applications (BCA)", "BCA-001", "Undergraduate", "3", "Years", "65000", "Undergraduate computer applications curriculum with full-stack development and AI", "ACTIVE"},
                    {"Master of Business Administration (MBA)", "MBA-001", "Postgraduate", "2", "Years", "120000", "Postgraduate management degree with finance, HR, and marketing specializations", "ACTIVE"},
                    {"B.Tech Computer Science & Engineering", "BT-CSE-001", "Undergraduate", "4", "Years", "95000", "Four-year engineering degree covering software, cloud, and systems architecture", "ACTIVE"}
            };

            for (int r = 0; r < sampleData.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < sampleData[r].length; c++) {
                    Cell cell = row.createCell(c);
                    cell.setCellValue(sampleData[r][c]);
                    cell.setCellStyle(exampleStyle);
                }
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                int currentWidth = sheet.getColumnWidth(i);
                sheet.setColumnWidth(i, Math.max(currentWidth + 1200, 4800));
            }

            // ----------------------------------------------------
            // Sheet 2: Instructions
            // ----------------------------------------------------
            Sheet instSheet = workbook.createSheet("Instructions");
            CellStyle instHeaderStyle = createHeaderStyle(workbook, IndexedColors.DARK_BLUE.getIndex());

            int rIdx = 0;
            Row tRow = instSheet.createRow(rIdx++);
            Cell tCell = tRow.createCell(0);
            tCell.setCellValue("COURSE BULK UPLOAD - INSTRUCTIONS & GUIDELINES");
            tCell.setCellStyle(instHeaderStyle);

            rIdx++; // blank
            String[] instructions = {
                    "1. MANDATORY FIELDS (marked with *):",
                    "   • Course Name: Full name of the course (e.g. 'Bachelor of Business Administration (BBA)'). Max 150 chars.",
                    "   • Course Code: Unique identifier for the course (e.g. 'BBA-001', 'BCA-01'). Max 50 chars.",
                    "   • Course Type: Category or level of course (e.g. 'Undergraduate', 'Postgraduate', 'Diploma', 'Certificate').",
                    "     If the Course Type does not already exist in the system, it will be automatically created.",
                    "   • Duration: Numeric duration of the course (e.g. 3, 4, 2, 6). Must be a positive whole number.",
                    "   • Duration Unit: Unit of duration (e.g. 'Years', 'Months', 'Semesters'). Max 50 chars.",
                    "   • Fees: Total course fee or yearly fee (e.g. 75000). Must be a non-negative number.",
                    "",
                    "2. OPTIONAL FIELDS:",
                    "   • Description: Summary or overview of the course and curriculum.",
                    "   • Status: Either 'ACTIVE' or 'INACTIVE'. If left empty, defaults to 'ACTIVE'.",
                    "",
                    "3. UPSERT BEHAVIOR:",
                    "   • If a row has a Course Code or Course Name that matches an existing course, that course will be UPDATED.",
                    "   • If neither code nor name matches, a NEW course record will be CREATED.",
                    "",
                    "4. FILE FORMAT & LIMITS:",
                    "   • Maximum allowed file size: 10 MB.",
                    "   • Supported file formats: Excel (.xlsx, .xls).",
                    "   • Please keep column headers intact and do not alter header row 1 in the 'Courses' sheet."
            };

            for (String line : instructions) {
                Row row = instSheet.createRow(rIdx++);
                Cell cell = row.createCell(0);
                cell.setCellValue(line);
                cell.setCellStyle(textStyle);
            }
            instSheet.autoSizeColumn(0);
            instSheet.setColumnWidth(0, Math.max(instSheet.getColumnWidth(0) + 2000, 15000));

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate course bulk upload template", e);
            throw new RuntimeException("Error generating template: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // 2. VALIDATION & PREVIEW
    // =========================================================================

    @Override
    public CourseBulkUploadPreviewResponseDTO validateExcel(MultipartFile file) throws BadRequestException {
        ParsedData parsed = parseWorkbook(file, false);
        cleanOldErrorFiles();

        UUID importId = UUID.randomUUID();
        boolean errorFileAvailable = false;
        if (!parsed.errors.isEmpty()) {
            byte[] errorFile = generateErrorSheet(file, parsed.errors);
            errorFileCache.put(importId, new StoredErrorFile(errorFile, Instant.now()));
            errorFileAvailable = true;
        }

        int total = parsed.rows.size() + parsed.errors.size();
        int valid = parsed.rows.size();
        int errs = parsed.errors.size();
        int newCourses = (int) parsed.rows.stream().filter(r -> !r.isUpdate()).count();
        int updateCourses = (int) parsed.rows.stream().filter(CourseBulkUploadRowDTO::isUpdate).count();

        return CourseBulkUploadPreviewResponseDTO.builder()
                .success(true)
                .message(errs == 0 ? "All courses validated successfully." : "Validation completed with " + errs + " issue(s).")
                .totalRows(total)
                .validRows(valid)
                .errorRows(errs)
                .newCourses(newCourses)
                .updateCourses(updateCourses)
                .canImport(valid > 0)
                .errorFileAvailable(errorFileAvailable)
                .importId(importId.toString())
                .rows(parsed.rows)
                .errors(parsed.errors)
                .build();
    }

    // =========================================================================
    // 3. EXECUTE BULK UPLOAD
    // =========================================================================

    @Override
    @Transactional
    public CourseBulkUploadResponseDTO bulkUpload(MultipartFile file) throws BadRequestException {
        ParsedData parsed = parseWorkbook(file, true);

        if (parsed.rows.isEmpty()) {
            throw new BadRequestException("No valid course rows found to import in the uploaded file.");
        }

        // Cache preloaded course types
        Map<String, CourseType> courseTypeMap = new HashMap<>();
        for (CourseType ct : courseTypeRepository.findAll()) {
            if (!ct.isDeleted() && ct.getName() != null) {
                courseTypeMap.put(ct.getName().trim().toLowerCase(), ct);
            }
        }

        int created = 0;
        int updated = 0;

        for (CourseBulkUploadRowDTO rowDto : parsed.rows) {
            String typeName = rowDto.getCourseTypeName().trim();
            String normType = typeName.toLowerCase();
            CourseType courseType = courseTypeMap.get(normType);

            if (courseType == null) {
                // Auto-create missing CourseType
                courseType = CourseType.builder()
                        .name(typeName)
                        .description("Auto-created from Course bulk upload")
                        .status(Status.ACTIVE)
                        .build();
                courseType = courseTypeRepository.save(courseType);
                courseTypeMap.put(normType, courseType);
                log.info("Auto-created course type '{}' during course bulk upload", typeName);
            }

            Course course;
            if (rowDto.isUpdate() && rowDto.getExistingCourseId() != null) {
                course = courseRepository.findById(rowDto.getExistingCourseId())
                        .orElse(null);
                if (course == null) {
                    course = new Course();
                }
            } else {
                course = new Course();
            }

            boolean isNew = (course.getId() == null);
            course.setCourseName(rowDto.getCourseName());
            course.setCourseCode(rowDto.getCourseCode());
            course.setCourseType(courseType);
            course.setDuration(rowDto.getDuration());
            course.setDurationUnit(rowDto.getDurationUnit());
            course.setFees(rowDto.getFees());
            course.setDescription(rowDto.getDescription());
            course.setStatus(rowDto.getStatus() != null ? rowDto.getStatus() : Status.ACTIVE);
            course.setDeleted(false);

            courseRepository.save(course);

            if (isNew) {
                created++;
            } else {
                updated++;
            }
        }

        cleanOldErrorFiles();
        UUID importId = UUID.randomUUID();
        boolean errorFileAvailable = false;
        if (!parsed.errors.isEmpty()) {
            byte[] errorFile = generateErrorSheet(file, parsed.errors);
            errorFileCache.put(importId, new StoredErrorFile(errorFile, Instant.now()));
            errorFileAvailable = true;
        }

        int total = parsed.rows.size() + parsed.errors.size();
        return CourseBulkUploadResponseDTO.builder()
                .success(true)
                .message("Course bulk upload completed successfully. (" + created + " created, " + updated + " updated)")
                .totalRows(total)
                .successfulRows(created + updated)
                .failedRows(parsed.errors.size())
                .createdRecords(created)
                .updatedRecords(updated)
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
    // 5. INTERNAL PARSER & HELPER METHODS
    // =========================================================================

    private static class ParsedData {
        List<CourseBulkUploadRowDTO> rows = new ArrayList<>();
        List<CourseBulkUploadRowErrorDTO> errors = new ArrayList<>();
    }

    private ParsedData parseWorkbook(MultipartFile file, boolean forExecution) throws BadRequestException {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Uploaded file is empty or missing.");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || (!originalFilename.toLowerCase().endsWith(".xlsx") && !originalFilename.toLowerCase().endsWith(".xls"))) {
            throw new BadRequestException("Invalid file format. Please upload an Excel file (.xlsx or .xls).");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BadRequestException("File size exceeds 10 MB limit.");
        }

        ParsedData result = new ParsedData();

        // Preload DB state for batch validation
        List<Course> allCourses = courseRepository.findAllByIsDeletedFalse();
        Map<String, Course> nameToCourse = new HashMap<>();
        Map<String, Course> codeToCourse = new HashMap<>();
        for (Course c : allCourses) {
            if (c.getCourseName() != null) nameToCourse.put(c.getCourseName().trim().toLowerCase(), c);
            if (c.getCourseCode() != null) codeToCourse.put(c.getCourseCode().trim().toLowerCase(), c);
        }

        Set<String> activeTypes = new HashSet<>();
        for (CourseType ct : courseTypeRepository.findAll()) {
            if (!ct.isDeleted() && ct.getName() != null) {
                activeTypes.add(ct.getName().trim().toLowerCase());
            }
        }

        Set<String> fileSeenCodes = new HashSet<>();
        Set<String> fileSeenNames = new HashSet<>();
        DataFormatter formatter = new DataFormatter();

        try (InputStream is = file.getInputStream(); Workbook workbook = WorkbookFactory.create(is)) {
            Sheet sheet = findCoursesSheet(workbook);
            if (sheet == null) {
                throw new BadRequestException("Workbook does not contain a 'Courses' sheet or valid course data.");
            }

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new BadRequestException("The Courses sheet is missing header row.");
            }

            Map<String, Integer> hMap = parseHeaders(headerRow, formatter);
            validateRequiredHeader(hMap, "courseName", "Course Name", sheet.getSheetName());
            validateRequiredHeader(hMap, "courseCode", "Course Code", sheet.getSheetName());

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isRowEmpty(row, formatter)) continue;
                int rowNum = r + 1; // 1-indexed

                String rawName = getCellValue(row, hMap, "courseName", formatter);
                String rawCode = getCellValue(row, hMap, "courseCode", formatter);
                String rawType = getCellValue(row, hMap, "courseType", formatter);
                String rawDuration = getCellValue(row, hMap, "duration", formatter);
                String rawDurationUnit = getCellValue(row, hMap, "durationUnit", formatter);
                String rawFees = getCellValue(row, hMap, "fees", formatter);
                String rawDesc = getCellValue(row, hMap, "description", formatter);
                String rawStatus = getCellValue(row, hMap, "status", formatter);

                // Validation checks
                if (rawName == null || rawName.isBlank()) {
                    result.errors.add(buildError(rowNum, "", rawCode, "courseName", "MISSING_NAME", "Course Name is required"));
                    continue;
                }
                String courseName = rawName.trim();
                if (courseName.length() > 150) {
                    result.errors.add(buildError(rowNum, courseName, rawCode, "courseName", "NAME_TOO_LONG", "Course Name must be 150 characters or fewer"));
                    continue;
                }

                if (rawCode == null || rawCode.isBlank()) {
                    result.errors.add(buildError(rowNum, courseName, "", "courseCode", "MISSING_CODE", "Course Code is required"));
                    continue;
                }
                String courseCode = rawCode.trim();
                if (courseCode.length() > 50) {
                    result.errors.add(buildError(rowNum, courseName, courseCode, "courseCode", "CODE_TOO_LONG", "Course Code must be 50 characters or fewer"));
                    continue;
                }

                // In-file uniqueness
                String normCode = courseCode.toLowerCase();
                String normName = courseName.toLowerCase();
                if (!fileSeenCodes.add(normCode)) {
                    result.errors.add(buildError(rowNum, courseName, courseCode, "courseCode", "DUPLICATE_CODE_IN_FILE", "Duplicate Course Code '" + courseCode + "' within uploaded file"));
                    continue;
                }
                if (!fileSeenNames.add(normName)) {
                    result.errors.add(buildError(rowNum, courseName, courseCode, "courseName", "DUPLICATE_NAME_IN_FILE", "Duplicate Course Name '" + courseName + "' within uploaded file"));
                    continue;
                }

                // Course Type
                String courseType = (rawType != null && !rawType.isBlank()) ? rawType.trim() : "General";
                boolean isNewType = !activeTypes.contains(courseType.toLowerCase());

                // Duration
                Integer duration = 1;
                if (rawDuration != null && !rawDuration.isBlank()) {
                    String trimmedDur = rawDuration.trim();
                    if (trimmedDur.startsWith("-")) {
                        result.errors.add(buildError(rowNum, courseName, courseCode, "duration", "INVALID_DURATION", "Duration must be greater than 0"));
                        continue;
                    }
                    try {
                        try {
                            duration = Integer.parseInt(trimmedDur);
                        } catch (NumberFormatException nfe) {
                            String digits = trimmedDur.replaceAll("[^0-9]", "");
                            if (!digits.isEmpty()) {
                                duration = Integer.parseInt(digits);
                            } else {
                                throw nfe;
                            }
                        }
                        if (duration <= 0) {
                            result.errors.add(buildError(rowNum, courseName, courseCode, "duration", "INVALID_DURATION", "Duration must be greater than 0"));
                            continue;
                        }
                    } catch (NumberFormatException e) {
                        result.errors.add(buildError(rowNum, courseName, courseCode, "duration", "INVALID_DURATION", "Duration '" + rawDuration + "' is not a valid number"));
                        continue;
                    }
                } else {
                    result.errors.add(buildError(rowNum, courseName, courseCode, "duration", "MISSING_DURATION", "Duration is required"));
                    continue;
                }

                // Duration Unit
                String durationUnit = (rawDurationUnit != null && !rawDurationUnit.isBlank()) ? rawDurationUnit.trim() : "Years";
                if (durationUnit.length() > 50) {
                    durationUnit = durationUnit.substring(0, 50);
                }

                // Fees
                Double fees = 0.0;
                if (rawFees != null && !rawFees.isBlank()) {
                    String trimmedFee = rawFees.trim();
                    if (trimmedFee.startsWith("-")) {
                        result.errors.add(buildError(rowNum, courseName, courseCode, "fees", "INVALID_FEES", "Fees cannot be negative"));
                        continue;
                    }
                    try {
                        try {
                            fees = Double.parseDouble(trimmedFee);
                        } catch (NumberFormatException nfe) {
                            String clean = trimmedFee.replaceAll("[^0-9.]", "");
                            if (!clean.isEmpty()) {
                                fees = Double.parseDouble(clean);
                            } else {
                                throw nfe;
                            }
                        }
                        if (fees < 0) {
                            result.errors.add(buildError(rowNum, courseName, courseCode, "fees", "INVALID_FEES", "Fees cannot be negative"));
                            continue;
                        }
                    } catch (NumberFormatException e) {
                        result.errors.add(buildError(rowNum, courseName, courseCode, "fees", "INVALID_FEES", "Fees '" + rawFees + "' is not a valid number"));
                        continue;
                    }
                } else {
                    result.errors.add(buildError(rowNum, courseName, courseCode, "fees", "MISSING_FEES", "Fees is required"));
                    continue;
                }

                // Status
                Status status = Status.ACTIVE;
                if (rawStatus != null && !rawStatus.isBlank()) {
                    String normStatus = rawStatus.trim().toUpperCase();
                    if ("INACTIVE".equals(normStatus) || "FALSE".equals(normStatus) || "DISABLED".equals(normStatus)) {
                        status = Status.INACTIVE;
                    }
                }

                // Description
                String description = (rawDesc != null && !rawDesc.isBlank()) ? rawDesc.trim() : courseName;

                // Check update vs new
                Course existingByCode = codeToCourse.get(normCode);
                Course existingByName = nameToCourse.get(normName);
                boolean isUpdate = false;
                UUID existingId = null;

                if (existingByCode != null) {
                    isUpdate = true;
                    existingId = existingByCode.getId();
                } else if (existingByName != null) {
                    isUpdate = true;
                    existingId = existingByName.getId();
                }

                CourseBulkUploadRowDTO rowDto = CourseBulkUploadRowDTO.builder()
                        .rowNumber(rowNum)
                        .courseName(courseName)
                        .courseCode(courseCode)
                        .courseTypeName(courseType)
                        .duration(duration)
                        .durationUnit(durationUnit)
                        .fees(fees)
                        .description(description)
                        .status(status)
                        .isUpdate(isUpdate)
                        .existingCourseId(existingId)
                        .isNewCourseType(isNewType)
                        .build();

                result.rows.add(rowDto);
            }
        } catch (BadRequestException bre) {
            throw bre;
        } catch (Exception e) {
            log.error("Failed to parse course excel file", e);
            throw new BadRequestException("Unable to read Excel file: " + e.getMessage());
        }

        return result;
    }

    private Sheet findCoursesSheet(Workbook workbook) {
        Sheet s = workbook.getSheet("Courses");
        if (s == null) s = workbook.getSheet("Course");
        if (s == null) s = workbook.getSheet("Sheet1");
        if (s == null && workbook.getNumberOfSheets() > 0) s = workbook.getSheetAt(0);
        return s;
    }

    private Map<String, Integer> parseHeaders(Row headerRow, DataFormatter formatter) {
        Map<String, Integer> map = new HashMap<>();
        for (int c = 0; c < headerRow.getLastCellNum(); c++) {
            Cell cell = headerRow.getCell(c);
            if (cell == null) continue;
            String text = formatter.formatCellValue(cell).trim().toLowerCase().replaceAll("[^a-z0-9]", "");
            if (text.contains("coursename") || text.equals("name")) map.put("courseName", c);
            else if (text.contains("coursecode") || text.equals("code")) map.put("courseCode", c);
            else if (text.contains("coursetype") || text.equals("type") || text.contains("category")) map.put("courseType", c);
            else if (text.equals("duration")) map.put("duration", c);
            else if (text.contains("durationunit") || text.equals("unit")) map.put("durationUnit", c);
            else if (text.contains("fee") || text.contains("fees") || text.contains("tuition")) map.put("fees", c);
            else if (text.contains("desc") || text.contains("description") || text.contains("about")) map.put("description", c);
            else if (text.equals("status") || text.contains("active")) map.put("status", c);
        }
        return map;
    }

    private void validateRequiredHeader(Map<String, Integer> hMap, String key, String displayName, String sheetName) throws BadRequestException {
        if (!hMap.containsKey(key)) {
            throw new BadRequestException("Sheet '" + sheetName + "' is missing required column: '" + displayName + "'.");
        }
    }

    private String getCellValue(Row row, Map<String, Integer> hMap, String key, DataFormatter formatter) {
        Integer idx = hMap.get(key);
        if (idx == null) return null;
        Cell cell = row.getCell(idx);
        if (cell == null) return null;
        String val = formatter.formatCellValue(cell);
        return val != null ? val.trim() : null;
    }

    private boolean isRowEmpty(Row row, DataFormatter formatter) {
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String val = formatter.formatCellValue(cell);
                if (val != null && !val.trim().isEmpty()) return false;
            }
        }
        return true;
    }

    private CourseBulkUploadRowErrorDTO buildError(int rowNum, String courseName, String courseCode, String field, String code, String message) {
        return CourseBulkUploadRowErrorDTO.builder()
                .rowNumber(rowNum)
                .courseName(courseName)
                .courseCode(courseCode)
                .field(field)
                .errorCode(code)
                .errorMessage(message)
                .build();
    }

    private byte[] generateErrorSheet(MultipartFile originalFile, List<CourseBulkUploadRowErrorDTO> errors) {
        try (InputStream is = originalFile.getInputStream();
             Workbook workbook = WorkbookFactory.create(is);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = findCoursesSheet(workbook);
            if (sheet == null) return new byte[0];

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) return new byte[0];

            int errColIdx = headerRow.getLastCellNum();
            CellStyle errHeaderStyle = createHeaderStyle(workbook, IndexedColors.RED.getIndex());
            Cell errHeaderCell = headerRow.createCell(errColIdx);
            errHeaderCell.setCellValue("Error Reason");
            errHeaderCell.setCellStyle(errHeaderStyle);

            CellStyle errCellStyle = workbook.createCellStyle();
            Font errFont = workbook.createFont();
            errFont.setColor(IndexedColors.RED.getIndex());
            errFont.setBold(true);
            errCellStyle.setFont(errFont);
            errCellStyle.setWrapText(true);

            Map<Integer, List<String>> rowErrors = new HashMap<>();
            for (CourseBulkUploadRowErrorDTO err : errors) {
                rowErrors.computeIfAbsent(err.getRowNumber(), k -> new ArrayList<>())
                        .add("[" + err.getErrorCode() + "] " + err.getErrorMessage());
            }

            for (Map.Entry<Integer, List<String>> entry : rowErrors.entrySet()) {
                int rIdx = entry.getKey() - 1; // 0-indexed in POI
                Row row = sheet.getRow(rIdx);
                if (row == null) row = sheet.createRow(rIdx);
                Cell cell = row.createCell(errColIdx);
                cell.setCellValue(String.join("; ", entry.getValue()));
                cell.setCellStyle(errCellStyle);
            }

            sheet.setColumnWidth(errColIdx, 15000);
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate error file", e);
            return new byte[0];
        }
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
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createDataStyle(Workbook wb, boolean isExample) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setFontHeightInPoints((short) 10);
        if (isExample) {
            font.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            font.setItalic(true);
        }
        style.setFont(font);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        return style;
    }
}
