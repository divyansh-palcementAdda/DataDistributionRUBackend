package com.app.datadistribution.service.impl;

import com.app.datadistribution.common.bulkupload.BulkUploadHeaderMappingResult;
import com.app.datadistribution.common.bulkupload.BulkUploadHeaderNormalizer;
import com.app.datadistribution.common.bulkupload.BulkUploadHeaderParser;
import com.app.datadistribution.common.bulkupload.ExcelCellReader;
import com.app.datadistribution.dto.department.DepartmentBulkUploadColumnDefinition;
import com.app.datadistribution.dto.department.DepartmentBulkUploadPreviewResponseDTO;
import com.app.datadistribution.dto.department.DepartmentBulkUploadResponseDTO;
import com.app.datadistribution.dto.department.DepartmentBulkUploadRowDTO;
import com.app.datadistribution.dto.department.DepartmentBulkUploadRowErrorDTO;
import com.app.datadistribution.entity.Department;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.repository.DepartmentRepository;
import com.app.datadistribution.service.interfaces.IDepartmentBulkUploadService;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class DepartmentBulkUploadServiceImpl implements IDepartmentBulkUploadService {

    private final DepartmentRepository departmentRepository;

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB

    public static final String[] TEMPLATE_HEADERS;
    static {
        List<String> headers = new ArrayList<>();
        headers.add("S.No.");
        for (DepartmentBulkUploadColumnDefinition col : DepartmentBulkUploadColumnDefinition.values()) {
            headers.add(col.getHeaderName());
        }
        TEMPLATE_HEADERS = headers.toArray(new String[0]);
    }

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

    private String generateCorrelationId(UUID uuid) {
        return "IMP-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-"
                + uuid.toString().substring(0, 8).toUpperCase();
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
            // Sheet 1: Departments
            // ----------------------------------------------------
            Sheet sheet = workbook.createSheet("Departments");
            sheet.createFreezePane(0, 1);

            Row headerRow = sheet.createRow(0);
            headerRow.setHeightInPoints(24);
            for (int i = 0; i < TEMPLATE_HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(TEMPLATE_HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            // Sample Rows
            String[][] sampleData = {
                    {"1", "Computer Science & Engineering", "CSE", "Department of Computer Science and Engineering", "ACTIVE"},
                    {"2", "School of Management", "SOM", "Department of Management and Business Administration", "ACTIVE"},
                    {"3", "Information Technology", "IT", "Department of Information Technology & Software Systems", "ACTIVE"},
                    {"4", "Admissions & Outreach", "ADM", "Student Admissions, Counseling and Outreach", "ACTIVE"}
            };

            for (int r = 0; r < sampleData.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < sampleData[r].length; c++) {
                    Cell cell = row.createCell(c);
                    cell.setCellValue(sampleData[r][c]);
                    cell.setCellStyle(exampleStyle);
                }
            }

            for (int i = 0; i < TEMPLATE_HEADERS.length; i++) {
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
            tCell.setCellValue("DEPARTMENT BULK UPLOAD - INSTRUCTIONS & GUIDELINES");
            tCell.setCellStyle(instHeaderStyle);

            rIdx++; // blank line
            String[] instructions = {
                    "1. MANDATORY FIELDS (marked with *):",
                    "   • Department Name: Full name of the department (e.g. 'Computer Science & Engineering'). Min 2, max 150 chars.",
                    "   • Department Code: Unique identifier code (e.g. 'CSE', 'SOM', 'ADM'). Min 2, max 50 chars. Automatically converted to uppercase.",
                    "",
                    "2. OPTIONAL FIELDS:",
                    "   • Description: Detailed description of the department's mandate and operations.",
                    "   • Status: Either 'ACTIVE' or 'INACTIVE'. If left empty, defaults to 'ACTIVE'.",
                    "",
                    "3. UPSERT & DUPLICATE BEHAVIOR:",
                    "   • If a row has a Department Name or Department Code matching an existing active department, that department will be UPDATED.",
                    "   • If neither code nor name matches, a NEW department record will be CREATED.",
                    "   • If a row has a Department Name matching one department and a Department Code matching another department, a collision conflict is reported.",
                    "   • Internal database IDs are never required or exposed in this template.",
                    "",
                    "4. FILE FORMAT & LIMITS:",
                    "   • Maximum allowed file size: 10 MB.",
                    "   • Supported file formats: Excel (.xlsx, .xls).",
                    "   • Column positions are flexible; headers are mapped dynamically by name."
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
            log.error("Failed to generate department bulk upload template", e);
            throw new RuntimeException("Error generating template: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // 2. VALIDATION & PREVIEW
    // =========================================================================

    @Override
    public DepartmentBulkUploadPreviewResponseDTO validateExcel(MultipartFile file) throws BadRequestException {
        UUID importUuid = UUID.randomUUID();
        String correlationId = generateCorrelationId(importUuid);

        log.info("==================================================");
        log.info("BULK IMPORT START (VALIDATE / PREVIEW)");
        log.info("importId={} (uuid={})", correlationId, importUuid);
        log.info("type=DEPARTMENT");
        log.info("file={}", file != null ? file.getOriginalFilename() : "null");
        log.info("==================================================");

        try {
            ParsedDepartmentData parsed = parseWorkbook(file, false, correlationId);
            cleanOldErrorFiles();

            boolean errorFileAvailable = false;
            if (!parsed.errors.isEmpty()) {
                byte[] errorFile = generateErrorSheet(file, parsed.errors);
                errorFileCache.put(importUuid, new StoredErrorFile(errorFile, Instant.now()));
                errorFileAvailable = true;
            }

            int total = parsed.rows.size() + parsed.errors.size();
            int valid = parsed.rows.size();
            int errs = parsed.errors.size();
            int newDepartments = (int) parsed.rows.stream().filter(r -> !r.isUpdate()).count();
            int updateDepartments = (int) parsed.rows.stream().filter(DepartmentBulkUploadRowDTO::isUpdate).count();

            log.info("==================================================");
            log.info("BULK_UPLOAD_VALIDATION_COMPLETED");
            log.info("importId={}", correlationId);
            log.info("type=DEPARTMENT");
            log.info("totalRows={}", total);
            log.info("validRows={}", valid);
            log.info("errorRows={}", errs);
            log.info("newDepartments={}", newDepartments);
            log.info("updateDepartments={}", updateDepartments);
            log.info("==================================================");

            return DepartmentBulkUploadPreviewResponseDTO.builder()
                    .success(true)
                    .message(errs == 0 ? "All departments validated successfully." : "Validation completed with " + errs + " issue(s).")
                    .totalRows(total)
                    .validRows(valid)
                    .errorRows(errs)
                    .newDepartments(newDepartments)
                    .updateDepartments(updateDepartments)
                    .canImport(valid > 0)
                    .errorFileAvailable(errorFileAvailable)
                    .importId(importUuid.toString())
                    .rows(parsed.rows)
                    .errors(parsed.errors)
                    .build();
        } catch (BadRequestException bre) {
            log.warn("BULK_UPLOAD_FAILED importId={} type=DEPARTMENT reason=HEADER_OR_FILE_VALIDATION_FAILED: {}",
                    correlationId, bre.getMessage());
            throw bre;
        } catch (Exception e) {
            log.error("BULK_UPLOAD_FAILED importId={} type=DEPARTMENT reason=UNEXPECTED_ERROR", correlationId, e);
            throw new BadRequestException("Failed to validate Excel: " + e.getMessage());
        }
    }

    // =========================================================================
    // 3. EXECUTE BULK UPLOAD
    // =========================================================================

    @Override
    @Transactional
    public DepartmentBulkUploadResponseDTO bulkUpload(MultipartFile file) throws BadRequestException {
        UUID importUuid = UUID.randomUUID();
        String correlationId = generateCorrelationId(importUuid);

        log.info("==================================================");
        log.info("BULK IMPORT START (EXECUTION)");
        log.info("importId={} (uuid={})", correlationId, importUuid);
        log.info("type=DEPARTMENT");
        log.info("file={}", file != null ? file.getOriginalFilename() : "null");
        log.info("==================================================");

        ParsedDepartmentData parsed;
        try {
            parsed = parseWorkbook(file, true, correlationId);
        } catch (BadRequestException bre) {
            log.warn("BULK_UPLOAD_FAILED importId={} type=DEPARTMENT reason={}", correlationId, bre.getMessage());
            throw bre;
        }

        if (parsed.rows.isEmpty()) {
            log.warn("BULK_UPLOAD_FAILED importId={} type=DEPARTMENT reason=NO_VALID_ROWS", correlationId);
            throw new BadRequestException("No valid department rows found to import in the uploaded file.");
        }

        int created = 0;
        int updated = 0;

        for (DepartmentBulkUploadRowDTO rowDto : parsed.rows) {
            log.debug("BULK_UPLOAD_ENTITY_MAPPING importId={} row={}", correlationId, rowDto.getRowNumber());
            log.debug("Department: name={}, code={}, isUpdate={}, existingId={}",
                    rowDto.getName(), rowDto.getCode(), rowDto.isUpdate(), rowDto.getExistingId());

            if (rowDto.isUpdate() && rowDto.getExistingId() != null) {
                Department dept = departmentRepository.findById(rowDto.getExistingId())
                        .filter(d -> !d.isDeleted())
                        .orElse(null);

                if (dept != null) {
                    dept.setName(rowDto.getName());
                    dept.setCode(rowDto.getCode());
                    dept.setDescription(rowDto.getDescription());
                    dept.setActive(rowDto.isActive());
                    Department saved = departmentRepository.save(dept);
                    updated++;
                    log.info("DATABASE_SAVE_SUCCESS importId={} row={} action=UPDATE deptId={} name={} code={}",
                            correlationId, rowDto.getRowNumber(), saved.getId(), saved.getName(), saved.getCode());
                } else {
                    Department newDept = Department.builder()
                            .name(rowDto.getName())
                            .code(rowDto.getCode())
                            .description(rowDto.getDescription())
                            .active(rowDto.isActive())
                            .build();
                    Department saved = departmentRepository.save(newDept);
                    created++;
                    log.info("DATABASE_SAVE_SUCCESS importId={} row={} action=CREATE deptId={} name={} code={}",
                            correlationId, rowDto.getRowNumber(), saved.getId(), saved.getName(), saved.getCode());
                }
            } else {
                Department newDept = Department.builder()
                        .name(rowDto.getName())
                        .code(rowDto.getCode())
                        .description(rowDto.getDescription())
                        .active(rowDto.isActive())
                        .build();
                Department saved = departmentRepository.save(newDept);
                created++;
                log.info("DATABASE_SAVE_SUCCESS importId={} row={} action=CREATE deptId={} name={} code={}",
                        correlationId, rowDto.getRowNumber(), saved.getId(), saved.getName(), saved.getCode());
            }
        }

        cleanOldErrorFiles();
        boolean errorFileAvailable = false;
        if (!parsed.errors.isEmpty()) {
            byte[] errorFile = generateErrorSheet(file, parsed.errors);
            errorFileCache.put(importUuid, new StoredErrorFile(errorFile, Instant.now()));
            errorFileAvailable = true;
        }

        log.info("==================================================");
        log.info("BULK_UPLOAD_COMPLETED");
        log.info("importId={}", correlationId);
        log.info("type=DEPARTMENT");
        log.info("totalRows={}", parsed.rows.size() + parsed.errors.size());
        log.info("successfulRows={}", created + updated);
        log.info("failedRows={}", parsed.errors.size());
        log.info("databaseInserts={}", created);
        log.info("databaseUpdates={}", updated);
        log.info("==================================================");

        return DepartmentBulkUploadResponseDTO.builder()
                .success(true)
                .message("Bulk upload finished. Created: " + created + ", Updated: " + updated
                        + (parsed.errors.isEmpty() ? "." : ", with " + parsed.errors.size() + " issue(s)."))
                .totalRows(parsed.rows.size() + parsed.errors.size())
                .successfulRows(created + updated)
                .failedRows(parsed.errors.size())
                .createdRecords(created)
                .updatedRecords(updated)
                .errorFileAvailable(errorFileAvailable)
                .importId(importUuid.toString())
                .errors(parsed.errors)
                .build();
    }

    // =========================================================================
    // 4. ERROR FILE RETRIEVAL
    // =========================================================================

    @Override
    public byte[] getErrorFile(UUID importId) {
        cleanOldErrorFiles();
        StoredErrorFile stored = errorFileCache.get(importId);
        if (stored == null) {
            throw new ResourcesNotFoundException("Error file not found or expired for import: " + importId);
        }
        return stored.getContent();
    }

    // =========================================================================
    // 5. PARSING HELPER
    // =========================================================================

    private static class ParsedDepartmentData {
        List<DepartmentBulkUploadRowDTO> rows = new ArrayList<>();
        List<DepartmentBulkUploadRowErrorDTO> errors = new ArrayList<>();
    }

    private ParsedDepartmentData parseWorkbook(MultipartFile file, boolean isExecution, String correlationId) throws BadRequestException {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Uploaded file is empty or missing.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BadRequestException("File size exceeds maximum allowed limit of 10 MB.");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.toLowerCase().endsWith(".xlsx") && !filename.toLowerCase().endsWith(".xls"))) {
            throw new BadRequestException("Invalid file format. Please upload an Excel file (.xlsx or .xls).");
        }

        ParsedDepartmentData result = new ParsedDepartmentData();

        // Preload active non-deleted departments for O(1) matching
        List<Department> existingDepartments = departmentRepository.findAll().stream()
                .filter(d -> !d.isDeleted())
                .toList();

        Map<String, Department> nameToDept = new HashMap<>();
        Map<String, Department> codeToDept = new HashMap<>();
        for (Department d : existingDepartments) {
            if (d.getName() != null) {
                nameToDept.put(normalizeDeptName(d.getName()), d);
            }
            if (d.getCode() != null) {
                codeToDept.put(d.getCode().trim().toUpperCase(), d);
            }
        }

        // Duplicate tracking within sheet
        Set<String> seenNamesInSheet = new HashSet<>();
        Set<String> seenCodesInSheet = new HashSet<>();

        try (InputStream is = file.getInputStream(); Workbook workbook = WorkbookFactory.create(is)) {
            Sheet sheet = findDepartmentsSheet(workbook);
            if (sheet == null) {
                throw new BadRequestException("The uploaded workbook does not contain a 'Departments' sheet.");
            }

            int lastRowNum = sheet.getLastRowNum();
            if (lastRowNum < 1) {
                throw new BadRequestException("The uploaded sheet has no data rows.");
            }

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new BadRequestException("The header row in the 'Departments' sheet is missing.");
            }

            // Header-based exact canonical mapping
            BulkUploadHeaderMappingResult headerMapping = BulkUploadHeaderParser.parseHeaders(
                    headerRow,
                    DepartmentBulkUploadColumnDefinition.getAllFieldDefinitions(),
                    sheet.getSheetName()
            );

            // Log header mapping
            headerMapping.logHeaderMapping(log, "DEPARTMENT", correlationId);

            for (int r = 1; r <= lastRowNum; r++) {
                Row row = sheet.getRow(r);
                if (row == null || ExcelCellReader.isRowEmpty(row)) {
                    continue;
                }

                int rowNum = r + 1; // 1-indexed
                log.debug("ROW_START importId={} row={}", correlationId, rowNum);
                log.debug("HEADER_MAPPING_SUCCESS importId={} row={}", correlationId, rowNum);

                String rawName = ExcelCellReader.readCellValue(row, headerMapping.getColIndex("name"));
                String rawCode = ExcelCellReader.readCellValue(row, headerMapping.getColIndex("code"));
                String rawDesc = ExcelCellReader.readCellValue(row, headerMapping.getColIndex("description"));
                String rawStatus = ExcelCellReader.readCellValue(row, headerMapping.getColIndex("status"));

                log.debug("FIELD_VALUE_EXTRACTION importId={} row={}", correlationId, rowNum);
                log.debug("BULK_UPLOAD_ROW_MAPPING importId={} row={}\nExcel Values:\nName = {}\nCode = {}\nDescription = {}\nStatus = {}",
                        correlationId, rowNum, rawName, rawCode, rawDesc, rawStatus);

                // Department Name Validation
                if (rawName == null || rawName.isBlank()) {
                    log.debug("VALIDATION_FAILED importId={} row={} field=name reason=MISSING_NAME", correlationId, rowNum);
                    result.errors.add(buildError(rowNum, "", rawCode, "name", "MISSING_NAME", "Department Name is required"));
                    continue;
                }
                String deptName = rawName.trim().replaceAll("\\s+", " ");
                if (deptName.length() < 2 || deptName.length() > 150) {
                    log.debug("VALIDATION_FAILED importId={} row={} field=name reason=INVALID_NAME_LENGTH", correlationId, rowNum);
                    result.errors.add(buildError(rowNum, deptName, rawCode, "name", "INVALID_NAME_LENGTH", "Department Name must be between 2 and 150 characters"));
                    continue;
                }

                // Department Code Validation
                if (rawCode == null || rawCode.isBlank()) {
                    log.debug("VALIDATION_FAILED importId={} row={} field=code reason=MISSING_CODE", correlationId, rowNum);
                    result.errors.add(buildError(rowNum, deptName, "", "code", "MISSING_CODE", "Department Code is required"));
                    continue;
                }
                String deptCode = rawCode.trim().toUpperCase().replaceAll("\\s+", "");
                if (deptCode.length() < 2 || deptCode.length() > 50) {
                    log.debug("VALIDATION_FAILED importId={} row={} field=code reason=INVALID_CODE_LENGTH", correlationId, rowNum);
                    result.errors.add(buildError(rowNum, deptName, deptCode, "code", "INVALID_CODE_LENGTH", "Department Code must be between 2 and 50 characters"));
                    continue;
                }

                String normName = normalizeDeptName(deptName);

                // Check in-sheet duplicate name
                if (!seenNamesInSheet.add(normName)) {
                    log.debug("VALIDATION_FAILED importId={} row={} field=name reason=DUPLICATE_NAME_IN_SHEET", correlationId, rowNum);
                    result.errors.add(buildError(rowNum, deptName, deptCode, "name", "DUPLICATE_NAME_IN_SHEET", "Duplicate Department Name '" + deptName + "' found within this Excel file"));
                    continue;
                }

                // Check in-sheet duplicate code
                if (!seenCodesInSheet.add(deptCode)) {
                    log.debug("VALIDATION_FAILED importId={} row={} field=code reason=DUPLICATE_CODE_IN_SHEET", correlationId, rowNum);
                    result.errors.add(buildError(rowNum, deptName, deptCode, "code", "DUPLICATE_CODE_IN_SHEET", "Duplicate Department Code '" + deptCode + "' found within this Excel file"));
                    continue;
                }

                // Database matching (Upsert resolution)
                Department existingByName = nameToDept.get(normName);
                Department existingByCode = codeToDept.get(deptCode);

                boolean isUpdate = false;
                UUID existingId = null;

                if (existingByName != null && existingByCode != null) {
                    if (!existingByName.getId().equals(existingByCode.getId())) {
                        log.debug("VALIDATION_FAILED importId={} row={} field=code reason=DEPARTMENT_CONFLICT", correlationId, rowNum);
                        result.errors.add(buildError(rowNum, deptName, deptCode, "code", "DEPARTMENT_CONFLICT",
                                "Department Name matches '" + existingByName.getName() + "' (Code: " + existingByName.getCode()
                                        + ") but Code matches '" + existingByCode.getName() + "' (Code: " + existingByCode.getCode() + "). Cannot merge different departments."));
                        continue;
                    }
                    isUpdate = true;
                    existingId = existingByName.getId();
                } else if (existingByName != null) {
                    isUpdate = true;
                    existingId = existingByName.getId();
                } else if (existingByCode != null) {
                    isUpdate = true;
                    existingId = existingByCode.getId();
                }

                String desc = (rawDesc != null && !rawDesc.isBlank()) ? rawDesc.trim() : null;

                boolean active = true;
                if (rawStatus != null && !rawStatus.isBlank()) {
                    String normStatus = rawStatus.trim().toUpperCase();
                    if ("INACTIVE".equals(normStatus) || "FALSE".equals(normStatus) || "DISABLED".equals(normStatus)) {
                        active = false;
                    }
                }

                log.debug("DTO_MAPPING_SUCCESS importId={} row={} isUpdate={}", correlationId, rowNum, isUpdate);
                log.debug("VALIDATION_SUCCESS importId={} row={}", correlationId, rowNum);

                DepartmentBulkUploadRowDTO rowDto = DepartmentBulkUploadRowDTO.builder()
                        .rowNumber(rowNum)
                        .name(deptName)
                        .code(deptCode)
                        .description(desc)
                        .active(active)
                        .isUpdate(isUpdate)
                        .existingId(existingId)
                        .build();

                result.rows.add(rowDto);
            }
        } catch (BadRequestException bre) {
            throw bre;
        } catch (Exception e) {
            log.error("Failed to parse department Excel file", e);
            throw new BadRequestException("Unable to read Excel file: " + e.getMessage());
        }

        return result;
    }

    private String normalizeDeptName(String name) {
        if (name == null) return "";
        return BulkUploadHeaderNormalizer.normalize(name);
    }

    private Sheet findDepartmentsSheet(Workbook workbook) {
        Sheet s = workbook.getSheet("Departments");
        if (s == null) s = workbook.getSheet("Department");
        if (s == null) s = workbook.getSheet("Sheet1");
        if (s == null && workbook.getNumberOfSheets() > 0) s = workbook.getSheetAt(0);
        return s;
    }

    private DepartmentBulkUploadRowErrorDTO buildError(int rowNum, String deptName, String deptCode, String field, String code, String message) {
        return DepartmentBulkUploadRowErrorDTO.builder()
                .rowNumber(rowNum)
                .departmentName(deptName != null ? deptName : "")
                .departmentCode(deptCode != null ? deptCode : "")
                .field(field)
                .errorCode(code)
                .errorMessage(message)
                .build();
    }

    private byte[] generateErrorSheet(MultipartFile originalFile, List<DepartmentBulkUploadRowErrorDTO> errors) {
        try (InputStream is = originalFile.getInputStream();
             Workbook workbook = WorkbookFactory.create(is);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = findDepartmentsSheet(workbook);
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
            for (DepartmentBulkUploadRowErrorDTO err : errors) {
                rowErrors.computeIfAbsent(err.getRowNumber(), k -> new ArrayList<>())
                        .add("[" + err.getErrorCode() + "] " + err.getErrorMessage());
            }

            for (Map.Entry<Integer, List<String>> entry : rowErrors.entrySet()) {
                int rIdx = entry.getKey() - 1; // 0-indexed
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
            log.error("Failed to generate error file for department bulk upload", e);
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
