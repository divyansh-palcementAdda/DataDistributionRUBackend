package com.app.datadistribution.service.impl;

import com.app.datadistribution.common.bulkupload.BulkUploadFieldDefinition;
import com.app.datadistribution.common.bulkupload.BulkUploadHeaderMappingResult;
import com.app.datadistribution.common.bulkupload.BulkUploadHeaderNormalizer;
import com.app.datadistribution.common.bulkupload.BulkUploadHeaderParser;
import com.app.datadistribution.common.bulkupload.ExcelCellReader;
import com.app.datadistribution.config.UserManagementProperties;
import com.app.datadistribution.dto.user.UserBulkUploadColumnDefinition;
import com.app.datadistribution.dto.user.UserBulkUploadPreviewResponseDTO;
import com.app.datadistribution.dto.user.UserBulkUploadResponseDTO;
import com.app.datadistribution.dto.user.UserBulkUploadRowDTO;
import com.app.datadistribution.dto.user.UserBulkUploadRowErrorDTO;
import com.app.datadistribution.entity.Department;
import com.app.datadistribution.entity.Role;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.ActivityType;
import com.app.datadistribution.enums.HodAccessType;
import com.app.datadistribution.enums.RoleType;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.repository.DepartmentRepository;
import com.app.datadistribution.repository.RoleRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.security.UserSecurityValidator;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.service.interfaces.IActivityLogService;
import com.app.datadistribution.service.interfaces.IUserBulkUploadService;
import com.app.datadistribution.service.interfaces.IUserDataScopeService;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserBulkUploadServiceImpl implements IUserBulkUploadService {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final IUserDataScopeService dataScopeService;
    private final UserSecurityValidator userSecurityValidator;
    private final UserManagementProperties userManagementProperties;
    private final IActivityLogService activityLogService;

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^(\\+?[0-9]{10,15})?$");

    public static final String[] TEMPLATE_HEADERS;
    static {
        List<String> list = new ArrayList<>();
        list.add("S.No.");
        for (UserBulkUploadColumnDefinition col : UserBulkUploadColumnDefinition.values()) {
            if (col != UserBulkUploadColumnDefinition.NAME) {
                list.add(col.getHeaderName());
            }
        }
        TEMPLATE_HEADERS = list.toArray(new String[0]);
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
            // Sheet 1: Users
            // ----------------------------------------------------
            Sheet sheet = workbook.createSheet("Users");
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
                    {"1", "Rahul", "Sharma", "rahul.sharma@example.com", "9876543210", "rahul.sharma", "COUNSELOR", "Computer Science & Engineering", "ACTIVE", "", ""},
                    {"2", "Priya", "Patel", "priya.patel@example.com", "9876543211", "priya.patel", "HOD", "School of Management", "ACTIVE", "", "FULL_ACCESS"},
                    {"3", "Amit", "Kumar", "amit.kumar@example.com", "9876543212", "amit.kumar", "COUNSELOR", "Admissions & Outreach", "ACTIVE", "", ""},
                    {"4", "Sneha", "Gupta", "sneha.gupta@example.com", "9876543213", "sneha.gupta", "COUNSELOR", "Computer Science & Engineering", "ACTIVE", "", ""}
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
            tCell.setCellValue("USER BULK UPLOAD - INSTRUCTIONS & GUIDELINES");
            tCell.setCellStyle(instHeaderStyle);

            rIdx++; // blank line
            String[] instructions = {
                    "1. MANDATORY FIELDS (marked with *):",
                    "   • Name / First Name: User's name. You can either supply separate 'First Name *' & 'Last Name *' columns, or a single 'Name' column.",
                    "   • Email: Unique email address for system login and notifications. Must be valid format.",
                    "   • Username: Unique login identifier. If omitted, will be derived automatically from email.",
                    "   • Role: System role name (e.g. 'COUNSELOR', 'HOD', 'ADMIN').",
                    "   • Department Name: Exact name of pre-created Department. Mandatory for Counselor and HOD roles.",
                    "",
                    "2. OPTIONAL FIELDS:",
                    "   • Mobile: Primary mobile number (10 to 15 digits).",
                    "   • Status: Either 'ACTIVE' or 'INACTIVE' (defaults to 'ACTIVE').",
                    "   • Password: Initial password. If blank, system sets secure default 'User@123'.",
                    "   • HOD Access Type: For HOD role, one of 'FULL_ACCESS', 'VIEW_ONLY', 'NO_ACCESS'. Defaults to 'FULL_ACCESS'.",
                    "",
                    "3. HEADER FLEXIBILITY & RESILIENCE:",
                    "   • Columns may appear in ANY order. The parser maps data based on header names, not fixed positions.",
                    "   • Leading and trailing whitespace or case differences in headers are safely normalized."
            };

            for (String line : instructions) {
                Row row = instSheet.createRow(rIdx++);
                Cell cell = row.createCell(0);
                cell.setCellValue(line);
                cell.setCellStyle(textStyle);
            }
            instSheet.autoSizeColumn(0);
            instSheet.setColumnWidth(0, Math.max(instSheet.getColumnWidth(0) + 2000, 16000));

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate user bulk upload template", e);
            throw new RuntimeException("Error generating template: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // 2. VALIDATION & PREVIEW
    // =========================================================================

    @Override
    public UserBulkUploadPreviewResponseDTO validateExcel(MultipartFile file) throws BadRequestException {
        UUID importUuid = UUID.randomUUID();
        String correlationId = generateCorrelationId(importUuid);

        log.info("==================================================");
        log.info("BULK IMPORT START (VALIDATE / PREVIEW)");
        log.info("importId={} (uuid={})", correlationId, importUuid);
        log.info("type=USER");
        log.info("file={}", file != null ? file.getOriginalFilename() : "null");
        log.info("==================================================");

        try {
            ParsedUserData parsed = parseWorkbook(file, false, correlationId);
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

            log.info("==================================================");
            log.info("BULK_UPLOAD_VALIDATION_COMPLETED");
            log.info("importId={}", correlationId);
            log.info("type=USER");
            log.info("totalRows={}", total);
            log.info("validRows={}", valid);
            log.info("errorRows={}", errs);
            log.info("==================================================");

            return UserBulkUploadPreviewResponseDTO.builder()
                    .success(true)
                    .message(errs == 0 ? "All users validated successfully." : "Validation completed with " + errs + " issue(s).")
                    .totalRows(total)
                    .validRows(valid)
                    .errorRows(errs)
                    .newUsers(valid)
                    .canImport(valid > 0)
                    .errorFileAvailable(errorFileAvailable)
                    .importId(importUuid.toString())
                    .rows(parsed.rows)
                    .errors(parsed.errors)
                    .build();
        } catch (BadRequestException bre) {
            log.warn("BULK_UPLOAD_FAILED importId={} type=USER reason=HEADER_OR_FILE_VALIDATION_FAILED: {}",
                    correlationId, bre.getMessage());
            throw bre;
        } catch (Exception e) {
            log.error("BULK_UPLOAD_FAILED importId={} type=USER reason=UNEXPECTED_ERROR", correlationId, e);
            throw new BadRequestException("Failed to validate Excel: " + e.getMessage());
        }
    }

    // =========================================================================
    // 3. EXECUTE BULK UPLOAD
    // =========================================================================

    @Override
    @Transactional
    public UserBulkUploadResponseDTO bulkUpload(MultipartFile file) throws BadRequestException {
        UUID importUuid = UUID.randomUUID();
        String correlationId = generateCorrelationId(importUuid);

        log.info("==================================================");
        log.info("BULK IMPORT START (EXECUTION)");
        log.info("importId={} (uuid={})", correlationId, importUuid);
        log.info("type=USER");
        log.info("file={}", file != null ? file.getOriginalFilename() : "null");
        log.info("==================================================");

        ParsedUserData parsed;
        try {
            parsed = parseWorkbook(file, true, correlationId);
        } catch (BadRequestException bre) {
            log.warn("BULK_UPLOAD_FAILED importId={} type=USER reason={}", correlationId, bre.getMessage());
            throw bre;
        }

        if (parsed.rows.isEmpty()) {
            log.warn("BULK_UPLOAD_FAILED importId={} type=USER reason=NO_VALID_ROWS", correlationId);
            throw new BadRequestException("No valid user rows found to import in the uploaded file.");
        }

        // Preload department and role maps
        Map<String, Department> deptMap = preloadDepartments();
        Map<String, Role> roleMap = preloadRoles();

        int created = 0;
        int deptsResolvedCount = 0;
        int rolesResolvedCount = 0;

        for (UserBulkUploadRowDTO rowDto : parsed.rows) {
            Role role = resolveRole(rowDto.getRoleName(), roleMap);
            if (role != null) rolesResolvedCount++;

            boolean isAdmin = isRoleAdmin(role);
            boolean isHod = isRoleHod(role);

            Set<Department> departments = new HashSet<>();
            if (!isAdmin && rowDto.getDepartmentName() != null && !rowDto.getDepartmentName().isBlank()) {
                Department dept = deptMap.get(normalizeDeptName(rowDto.getDepartmentName()));
                if (dept != null) {
                    departments.add(dept);
                    deptsResolvedCount++;
                }
            }

            HodAccessType hodAccessType = rowDto.getHodAccessType();
            if (isHod && hodAccessType == null) {
                hodAccessType = HodAccessType.FULL_ACCESS;
            }

            boolean passwordProvided = (rowDto.getPassword() != null && !rowDto.getPassword().isBlank());
            String rawPassword = passwordProvided ? rowDto.getPassword().trim() : "User@123";

            log.debug("BULK_UPLOAD_ENTITY_MAPPING importId={} row={}\nUser:\nname = {}\nemail = {}\nRole:\n{} → roleId={}\nDepartment:\n{} → departmentId={}\nStatus:\n{}",
                    correlationId, rowDto.getRowNumber(), rowDto.getResolvedFullName(), rowDto.getEmail(),
                    role != null ? role.getName() : "null", role != null ? role.getId() : "null",
                    rowDto.getDepartmentName(), departments.stream().findFirst().map(Department::getId).orElse(null),
                    rowDto.isActive() ? "ACTIVE" : "INACTIVE");

            log.debug("Password handling importId={} row={}: passwordProvided={}, passwordGenerated={}",
                    correlationId, rowDto.getRowNumber(), passwordProvided, !passwordProvided);

            User user = User.builder()
                    .firstName(rowDto.getFirstName())
                    .lastName(rowDto.getLastName())
                    .email(rowDto.getEmail())
                    .phone(rowDto.getPhone())
                    .username(rowDto.getUsername())
                    .password(passwordEncoder.encode(rawPassword))
                    .active(rowDto.isActive())
                    .locked(false)
                    .emailVerified(true)
                    .hodAccessType(isHod ? hodAccessType : null)
                    .roles(new HashSet<>(Set.of(role)))
                    .departments(departments)
                    .tokenVersion(1L)
                    .build();

            User saved = userRepository.save(user);
            created++;

            log.info("DATABASE_SAVE_SUCCESS importId={} row={} userId={} username={} email={} role={} departments={}",
                    correlationId, rowDto.getRowNumber(), saved.getId(), saved.getUsername(), saved.getEmail(),
                    role.getName(), departments.stream().map(Department::getName).toList());

            if (activityLogService != null) {
                try {
                    activityLogService.logActivity(ActivityType.USER_CREATED, "Bulk created user: " + saved.getUsername());
                } catch (Exception ex) {
                    log.warn("Activity log failed for user creation: {}", ex.getMessage());
                }
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
        log.info("type=USER");
        log.info("totalRows={}", parsed.rows.size() + parsed.errors.size());
        log.info("successfulRows={}", created);
        log.info("failedRows={}", parsed.errors.size());
        log.info("departmentsResolved={}", deptsResolvedCount);
        log.info("rolesResolved={}", rolesResolvedCount);
        log.info("databaseInserts={}", created);
        log.info("databaseUpdates=0");
        log.info("==================================================");

        return UserBulkUploadResponseDTO.builder()
                .success(true)
                .message("Bulk user import completed. Created: " + created
                        + (parsed.errors.isEmpty() ? "." : ", with " + parsed.errors.size() + " issue(s)."))
                .totalRows(parsed.rows.size() + parsed.errors.size())
                .successfulRows(created)
                .failedRows(parsed.errors.size())
                .createdRecords(created)
                .updatedRecords(0)
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
    // 5. PARSER & VALIDATOR
    // =========================================================================

    private static class ParsedUserData {
        List<UserBulkUploadRowDTO> rows = new ArrayList<>();
        List<UserBulkUploadRowErrorDTO> errors = new ArrayList<>();
    }

    private ParsedUserData parseWorkbook(MultipartFile file, boolean isExecution, String correlationId) throws BadRequestException {
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

        ParsedUserData result = new ParsedUserData();

        // 1. Efficient Bulk Preload (O(1) Map Lookups)
        Map<String, Department> deptMap = preloadDepartments();
        Map<String, Role> roleMap = preloadRoles();

        // 2. Caller Context & Department Scope
        UserDataScope callerScope = null;
        try {
            callerScope = dataScopeService.getScopeForCurrentUser();
        } catch (Exception e) {
            log.debug("Unable to retrieve caller data scope: {}", e.getMessage());
        }

        // 3. Duplicate Tracking
        Set<String> seenEmailsInSheet = new HashSet<>();
        Set<String> seenUsernamesInSheet = new HashSet<>();
        Set<String> seenPhonesInSheet = new HashSet<>();

        try (InputStream is = file.getInputStream(); Workbook workbook = WorkbookFactory.create(is)) {
            Sheet sheet = findUsersSheet(workbook);
            if (sheet == null) {
                throw new BadRequestException("The uploaded workbook does not contain a 'Users' sheet.");
            }

            int lastRowNum = sheet.getLastRowNum();
            if (lastRowNum < 1) {
                throw new BadRequestException("The uploaded sheet has no data rows.");
            }

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new BadRequestException("The header row in the 'Users' sheet is missing.");
            }

            // Header-based exact canonical mapping with custom validator for User requirements
            BulkUploadHeaderMappingResult headerMapping = BulkUploadHeaderParser.parseHeaders(
                    headerRow,
                    UserBulkUploadColumnDefinition.getAllFieldDefinitions(),
                    sheet.getSheetName(),
                    null,
                    (fieldToCol, definitions, sheetName) -> {
                        // Check Name requirement: Either 'name' or 'firstName' must be mapped
                        boolean hasName = fieldToCol.containsKey("name");
                        boolean hasFirstName = fieldToCol.containsKey("firstName");
                        if (!hasName && !hasFirstName) {
                            throw new BadRequestException(
                                    "Sheet '" + sheetName + "' is missing required column: 'Name' or 'First Name *'."
                            );
                        }

                        // Required: email, role, departmentName
                        if (!fieldToCol.containsKey("email")) {
                            throw new BadRequestException("Sheet '" + sheetName + "' is missing required column: 'Email *'.");
                        }
                        if (!fieldToCol.containsKey("role")) {
                            throw new BadRequestException("Sheet '" + sheetName + "' is missing required column: 'Role *'.");
                        }
                        if (!fieldToCol.containsKey("departmentName")) {
                            throw new BadRequestException("Sheet '" + sheetName + "' is missing required column: 'Department Name *'.");
                        }
                    }
            );

            // Log Header Mapping
            headerMapping.logHeaderMapping(log, "USER", correlationId);

            for (int r = 1; r <= lastRowNum; r++) {
                Row row = sheet.getRow(r);
                if (row == null || ExcelCellReader.isRowEmpty(row)) {
                    continue;
                }

                int rowNum = r + 1; // 1-indexed
                log.debug("ROW_START importId={} row={}", correlationId, rowNum);
                log.debug("HEADER_MAPPING_SUCCESS importId={} row={}", correlationId, rowNum);

                String rawName = ExcelCellReader.readCellValue(row, headerMapping.getColIndex("name"));
                String rawFirstName = ExcelCellReader.readCellValue(row, headerMapping.getColIndex("firstName"));
                String rawLastName = ExcelCellReader.readCellValue(row, headerMapping.getColIndex("lastName"));
                String rawEmail = ExcelCellReader.readCellValue(row, headerMapping.getColIndex("email"));
                String rawMobile = ExcelCellReader.readCellValue(row, headerMapping.getColIndex("mobile"));
                String rawUsername = ExcelCellReader.readCellValue(row, headerMapping.getColIndex("username"));
                String rawRole = ExcelCellReader.readCellValue(row, headerMapping.getColIndex("role"));
                String rawDeptName = ExcelCellReader.readCellValue(row, headerMapping.getColIndex("departmentName"));
                String rawStatus = ExcelCellReader.readCellValue(row, headerMapping.getColIndex("status"));
                String rawPassword = ExcelCellReader.readCellValue(row, headerMapping.getColIndex("password"));
                String rawHodAccess = ExcelCellReader.readCellValue(row, headerMapping.getColIndex("hodAccessType"));

                log.debug("FIELD_VALUE_EXTRACTION importId={} row={}", correlationId, rowNum);

                // --- 1. Name Resolution & Validation ---
                String firstName = null;
                String lastName = null;
                String fullName = null;

                if (rawFirstName != null && !rawFirstName.isBlank()) {
                    firstName = rawFirstName.trim();
                    lastName = (rawLastName != null && !rawLastName.isBlank()) ? rawLastName.trim() : "";
                    fullName = (firstName + (lastName.isEmpty() ? "" : " " + lastName)).trim();
                } else if (rawName != null && !rawName.isBlank()) {
                    String trimmedName = rawName.trim();
                    int spaceIdx = trimmedName.indexOf(' ');
                    if (spaceIdx > 0) {
                        firstName = trimmedName.substring(0, spaceIdx).trim();
                        lastName = trimmedName.substring(spaceIdx + 1).trim();
                    } else {
                        firstName = trimmedName;
                        lastName = "";
                    }
                    fullName = trimmedName;
                }

                if (firstName == null || firstName.isBlank()) {
                    log.debug("VALIDATION_FAILED importId={} row={} field=firstName reason=MISSING_FIRST_NAME", correlationId, rowNum);
                    result.errors.add(buildError(rowNum, "", rawEmail, rawUsername, rawRole, rawDeptName, "firstName", "MISSING_FIRST_NAME", "First Name or Name is required"));
                    continue;
                }
                if (firstName.length() < 2 || firstName.length() > 50) {
                    log.debug("VALIDATION_FAILED importId={} row={} field=firstName reason=INVALID_FIRST_NAME_LENGTH", correlationId, rowNum);
                    result.errors.add(buildError(rowNum, firstName, rawEmail, rawUsername, rawRole, rawDeptName, "firstName", "INVALID_FIRST_NAME_LENGTH", "First Name must be between 2 and 50 characters"));
                    continue;
                }
                if (lastName != null && !lastName.isEmpty() && (lastName.length() < 2 || lastName.length() > 50)) {
                    log.debug("VALIDATION_FAILED importId={} row={} field=lastName reason=INVALID_LAST_NAME_LENGTH", correlationId, rowNum);
                    result.errors.add(buildError(rowNum, fullName, rawEmail, rawUsername, rawRole, rawDeptName, "lastName", "INVALID_LAST_NAME_LENGTH", "Last Name must be between 2 and 50 characters"));
                    continue;
                }

                // --- 2. Email Validation ---
                if (rawEmail == null || rawEmail.isBlank()) {
                    log.debug("VALIDATION_FAILED importId={} row={} field=email reason=MISSING_EMAIL", correlationId, rowNum);
                    result.errors.add(buildError(rowNum, fullName, "", rawUsername, rawRole, rawDeptName, "email", "MISSING_EMAIL", "Email is required"));
                    continue;
                }
                String email = rawEmail.trim().toLowerCase();
                if (!EMAIL_PATTERN.matcher(email).matches() || email.length() > 100) {
                    log.debug("VALIDATION_FAILED importId={} row={} field=email reason=INVALID_EMAIL_FORMAT", correlationId, rowNum);
                    result.errors.add(buildError(rowNum, fullName, email, rawUsername, rawRole, rawDeptName, "email", "INVALID_EMAIL_FORMAT", "Email '" + rawEmail + "' is not a valid email address"));
                    continue;
                }
                if (!seenEmailsInSheet.add(email)) {
                    log.debug("VALIDATION_FAILED importId={} row={} field=email reason=DUPLICATE_EMAIL_IN_SHEET", correlationId, rowNum);
                    result.errors.add(buildError(rowNum, fullName, email, rawUsername, rawRole, rawDeptName, "email", "DUPLICATE_EMAIL_IN_SHEET", "Duplicate email '" + email + "' found within this Excel file"));
                    continue;
                }
                if (userRepository.existsByEmail(email)) {
                    log.debug("VALIDATION_FAILED importId={} row={} field=email reason=EMAIL_ALREADY_EXISTS", correlationId, rowNum);
                    result.errors.add(buildError(rowNum, fullName, email, rawUsername, rawRole, rawDeptName, "email", "EMAIL_ALREADY_EXISTS", "Email '" + email + "' is already registered in the system"));
                    continue;
                }

                // --- 3. Username Resolution & Validation ---
                String username;
                if (rawUsername != null && !rawUsername.isBlank()) {
                    username = rawUsername.trim();
                } else {
                    // Derive username automatically from email prefix or name
                    String baseUsername = email.contains("@")
                            ? email.substring(0, email.indexOf('@')).replaceAll("[^a-zA-Z0-9._-]", "")
                            : firstName.toLowerCase().replaceAll("[^a-zA-Z0-9._-]", "");
                    if (baseUsername.length() < 3) {
                        baseUsername = baseUsername + "123";
                    }
                    username = baseUsername;
                    int suffix = 1;
                    while (seenUsernamesInSheet.contains(username.toLowerCase()) || userRepository.existsByUsername(username)) {
                        username = baseUsername + suffix;
                        suffix++;
                    }
                }

                if (username.length() < 3 || username.length() > 50 || !USERNAME_PATTERN.matcher(username).matches()) {
                    log.debug("VALIDATION_FAILED importId={} row={} field=username reason=INVALID_USERNAME_FORMAT", correlationId, rowNum);
                    result.errors.add(buildError(rowNum, fullName, email, username, rawRole, rawDeptName, "username", "INVALID_USERNAME_FORMAT", "Username must be between 3 and 50 characters and contain only alphanumeric, dots, underscores, or hyphens"));
                    continue;
                }
                if (!seenUsernamesInSheet.add(username.toLowerCase())) {
                    log.debug("VALIDATION_FAILED importId={} row={} field=username reason=DUPLICATE_USERNAME_IN_SHEET", correlationId, rowNum);
                    result.errors.add(buildError(rowNum, fullName, email, username, rawRole, rawDeptName, "username", "DUPLICATE_USERNAME_IN_SHEET", "Duplicate username '" + username + "' found within this Excel file"));
                    continue;
                }
                if (userRepository.existsByUsername(username)) {
                    log.debug("VALIDATION_FAILED importId={} row={} field=username reason=USERNAME_ALREADY_EXISTS", correlationId, rowNum);
                    result.errors.add(buildError(rowNum, fullName, email, username, rawRole, rawDeptName, "username", "USERNAME_ALREADY_EXISTS", "Username '" + username + "' is already taken"));
                    continue;
                }

                // --- 4. Mobile Validation ---
                String phone = null;
                if (rawMobile != null && !rawMobile.isBlank()) {
                    phone = rawMobile.trim();
                    if (!PHONE_PATTERN.matcher(phone).matches()) {
                        log.debug("VALIDATION_FAILED importId={} row={} field=phone reason=INVALID_PHONE_FORMAT", correlationId, rowNum);
                        result.errors.add(buildError(rowNum, fullName, email, username, rawRole, rawDeptName, "phone", "INVALID_PHONE_FORMAT", "Phone number '" + rawMobile + "' is invalid (must be 10 to 15 digits)"));
                        continue;
                    }
                    if (!seenPhonesInSheet.add(phone)) {
                        log.debug("VALIDATION_FAILED importId={} row={} field=phone reason=DUPLICATE_PHONE_IN_SHEET", correlationId, rowNum);
                        result.errors.add(buildError(rowNum, fullName, email, username, rawRole, rawDeptName, "phone", "DUPLICATE_PHONE_IN_SHEET", "Duplicate phone number '" + phone + "' found within this Excel file"));
                        continue;
                    }
                }

                // --- 5. Role Resolution & RBAC Checks ---
                if (rawRole == null || rawRole.isBlank()) {
                    log.debug("VALIDATION_FAILED importId={} row={} field=role reason=MISSING_ROLE", correlationId, rowNum);
                    result.errors.add(buildError(rowNum, fullName, email, username, "", rawDeptName, "role", "MISSING_ROLE", "Role is required"));
                    continue;
                }

                log.debug("Role Resolution importId={} row={}\nExcel: {}\nNormalized: {}",
                        correlationId, rowNum, rawRole, rawRole.trim().toUpperCase());

                Role role = resolveRole(rawRole, roleMap);
                if (role == null) {
                    log.debug("Role Resolution Result: NOT_FOUND, Error: ROLE_NOT_FOUND importId={} row={}", correlationId, rowNum);
                    result.errors.add(buildError(rowNum, fullName, email, username, rawRole, rawDeptName, "role", "ROLE_NOT_FOUND", "Role '" + rawRole + "' was not found in the system"));
                    continue;
                }
                log.debug("Role Resolution Result: FOUND importId={} row={} roleId={} roleName={}",
                        correlationId, rowNum, role.getId(), role.getName());

                // RBAC & Privileged Role Policy Check
                boolean isPrivileged = userSecurityValidator.isPrivilegedRole(role.getName());
                if (isPrivileged) {
                    if (!userManagementProperties.isAllowAdminCreation()) {
                        log.debug("VALIDATION_FAILED importId={} row={} field=role reason=ADMIN_CREATION_DISABLED", correlationId, rowNum);
                        result.errors.add(buildError(rowNum, fullName, email, username, role.getName(), rawDeptName, "role", "ADMIN_CREATION_DISABLED", UserSecurityValidator.ADMIN_CREATION_DISABLED_MESSAGE));
                        continue;
                    }
                    try {
                        userSecurityValidator.validateRoleAssignment(Collections.singleton(role.getName()), Collections.emptySet());
                    } catch (Exception ex) {
                        log.debug("VALIDATION_FAILED importId={} row={} field=role reason=UNAUTHORIZED_ROLE_ASSIGNMENT", correlationId, rowNum);
                        result.errors.add(buildError(rowNum, fullName, email, username, role.getName(), rawDeptName, "role", "UNAUTHORIZED_ROLE_ASSIGNMENT", ex.getMessage()));
                        continue;
                    }
                }

                boolean isAdmin = isRoleAdmin(role);
                boolean isHod = isRoleHod(role);
                boolean requiresDepartment = isHod || RoleType.COUNSELOR.name().equalsIgnoreCase(role.getName())
                        || RoleType.USER.name().equalsIgnoreCase(role.getName())
                        || role.getName().toUpperCase().contains("HOD")
                        || role.getName().toUpperCase().contains("HEAD")
                        || role.getName().toUpperCase().contains("COUNSELOR");

                // --- 6. Department Resolution ---
                String deptName = (rawDeptName != null && !rawDeptName.isBlank()) ? rawDeptName.trim().replaceAll("\\s+", " ") : null;

                if (isAdmin) {
                    if (deptName != null && !deptName.isBlank()) {
                        log.debug("VALIDATION_FAILED importId={} row={} field=departmentName reason=INVALID_DEPARTMENT_MAPPING", correlationId, rowNum);
                        result.errors.add(buildError(rowNum, fullName, email, username, role.getName(), deptName, "departmentName", "INVALID_DEPARTMENT_MAPPING", "Admin and Super Admin users cannot be assigned to specific departments as they have system-wide access"));
                        continue;
                    }
                } else if (requiresDepartment) {
                    if (deptName == null || deptName.isBlank()) {
                        log.debug("VALIDATION_FAILED importId={} row={} field=departmentName reason=MISSING_DEPARTMENT", correlationId, rowNum);
                        result.errors.add(buildError(rowNum, fullName, email, username, role.getName(), "", "departmentName", "MISSING_DEPARTMENT", "Department Name is required for role '" + role.getName() + "'"));
                        continue;
                    }
                }

                Department resolvedDept = null;
                if (deptName != null && !deptName.isBlank()) {
                    String normDeptName = normalizeDeptName(deptName);
                    log.debug("Department Resolution importId={} row={}\nExcel Value: \"{}\"\nNormalized: \"{}\"",
                            correlationId, rowNum, deptName, normDeptName);

                    resolvedDept = deptMap.get(normDeptName);
                    if (resolvedDept == null) {
                        log.debug("Department Resolution Result: NOT_FOUND, Error: DEPARTMENT_NOT_FOUND importId={} row={}", correlationId, rowNum);
                        result.errors.add(buildError(rowNum, fullName, email, username, role.getName(), deptName, "departmentName", "DEPARTMENT_NOT_FOUND", "Department '" + deptName + "' was not found. Departments must be pre-created in Department Management."));
                        continue;
                    }
                    log.debug("Department Resolution Result: FOUND importId={} row={} deptId={} deptName={}",
                            correlationId, rowNum, resolvedDept.getId(), resolvedDept.getName());

                    // Enforce HOD / Server-side Department Scope
                    if (callerScope != null && !callerScope.isAdmin() && callerScope.getDepartmentIds() != null) {
                        if (!callerScope.getDepartmentIds().contains(resolvedDept.getId())) {
                            log.debug("VALIDATION_FAILED importId={} row={} field=departmentName reason=UNAUTHORIZED_DEPARTMENT_SCOPE", correlationId, rowNum);
                            result.errors.add(buildError(rowNum, fullName, email, username, role.getName(), deptName, "departmentName", "UNAUTHORIZED_DEPARTMENT_SCOPE", "You are not authorized to assign users to department '" + deptName + "'"));
                            continue;
                        }
                    }
                }

                // --- 7. Password Validation ---
                String password = (rawPassword != null && !rawPassword.isBlank()) ? rawPassword.trim() : null;
                if (password != null && (password.length() < 6 || password.length() > 100)) {
                    log.debug("VALIDATION_FAILED importId={} row={} field=password reason=INVALID_PASSWORD_LENGTH", correlationId, rowNum);
                    result.errors.add(buildError(rowNum, fullName, email, username, role.getName(), deptName, "password", "INVALID_PASSWORD_LENGTH", "Password must be between 6 and 100 characters"));
                    continue;
                }

                // --- 8. HOD Access Type Validation ---
                HodAccessType hodAccessType = null;
                if (rawHodAccess != null && !rawHodAccess.isBlank()) {
                    String normAccess = rawHodAccess.trim().toUpperCase();
                    try {
                        hodAccessType = HodAccessType.valueOf(normAccess);
                    } catch (IllegalArgumentException e) {
                        log.debug("VALIDATION_FAILED importId={} row={} field=hodAccessType reason=INVALID_HOD_ACCESS_TYPE", correlationId, rowNum);
                        result.errors.add(buildError(rowNum, fullName, email, username, role.getName(), deptName, "hodAccessType", "INVALID_HOD_ACCESS_TYPE", "HOD Access Type must be one of: FULL_ACCESS, READ_ONLY, NO_ACCESS"));
                        continue;
                    }
                } else if (isHod) {
                    hodAccessType = HodAccessType.FULL_ACCESS;
                }

                // --- 9. Status ---
                boolean active = true;
                if (rawStatus != null && !rawStatus.isBlank()) {
                    String normStatus = rawStatus.trim().toUpperCase();
                    if ("INACTIVE".equals(normStatus) || "FALSE".equals(normStatus) || "DISABLED".equals(normStatus)) {
                        active = false;
                    }
                }

                log.debug("BULK_UPLOAD_ROW_MAPPING importId={} row={}\nResolved:\nname = {}\nemail = {}\nmobile = {}\nrole = {}\ndepartmentName = {}",
                        correlationId, rowNum, fullName, email, phone, role.getName(), resolvedDept != null ? resolvedDept.getName() : "None");
                log.debug("DTO_MAPPING_SUCCESS importId={} row={}", correlationId, rowNum);
                log.debug("VALIDATION_SUCCESS importId={} row={}", correlationId, rowNum);

                UserBulkUploadRowDTO rowDto = UserBulkUploadRowDTO.builder()
                        .rowNumber(rowNum)
                        .name(fullName)
                        .firstName(firstName)
                        .lastName(lastName)
                        .email(email)
                        .phone(phone)
                        .username(username)
                        .roleName(role.getName())
                        .departmentName(resolvedDept != null ? resolvedDept.getName() : null)
                        .active(active)
                        .password(password)
                        .hodAccessType(hodAccessType)
                        .build();

                result.rows.add(rowDto);
            }
        } catch (BadRequestException bre) {
            throw bre;
        } catch (Exception e) {
            log.error("Failed to parse user Excel file", e);
            throw new BadRequestException("Unable to read Excel file: " + e.getMessage());
        }

        return result;
    }

    private Map<String, Department> preloadDepartments() {
        Map<String, Department> map = new HashMap<>();
        List<Department> departments = departmentRepository.findAll().stream()
                .filter(d -> !d.isDeleted())
                .toList();
        for (Department d : departments) {
            if (d.getName() != null) {
                map.put(normalizeDeptName(d.getName()), d);
            }
        }
        return map;
    }

    private Map<String, Role> preloadRoles() {
        Map<String, Role> map = new HashMap<>();
        List<Role> roles = roleRepository.findAll().stream()
                .filter(r -> !r.isDeleted())
                .toList();
        for (Role r : roles) {
            if (r.getName() != null) {
                map.put(r.getName().trim().toUpperCase(), r);
                if (r.getName().equalsIgnoreCase("COUNSELOR")) {
                    map.put("COUNSELLOR", r);
                }
            }
        }
        return map;
    }

    private Role resolveRole(String rawRole, Map<String, Role> roleMap) {
        if (rawRole == null) return null;
        String normalized = rawRole.trim().toUpperCase();
        if (roleMap.containsKey(normalized)) {
            return roleMap.get(normalized);
        }
        if (normalized.startsWith("ROLE_")) {
            String stripped = normalized.substring(5);
            if (roleMap.containsKey(stripped)) {
                return roleMap.get(stripped);
            }
        }
        return null;
    }

    private boolean isRoleAdmin(Role role) {
        if (role == null || role.getName() == null) return false;
        String name = role.getName().toUpperCase();
        return RoleType.SUPER_ADMIN.name().equalsIgnoreCase(name) || RoleType.ADMIN.name().equalsIgnoreCase(name);
    }

    private boolean isRoleHod(Role role) {
        if (role == null || role.getName() == null) return false;
        String name = role.getName().toUpperCase();
        return RoleType.HOD.name().equalsIgnoreCase(name) || name.contains("HOD") || name.contains("HEAD");
    }

    private String normalizeDeptName(String name) {
        if (name == null) return "";
        return BulkUploadHeaderNormalizer.normalize(name);
    }

    private Sheet findUsersSheet(Workbook workbook) {
        Sheet s = workbook.getSheet("Users");
        if (s == null) s = workbook.getSheet("User");
        if (s == null) s = workbook.getSheet("Sheet1");
        if (s == null && workbook.getNumberOfSheets() > 0) s = workbook.getSheetAt(0);
        return s;
    }

    private UserBulkUploadRowErrorDTO buildError(int rowNum, String name, String email, String username, String role, String deptName, String field, String code, String message) {
        return UserBulkUploadRowErrorDTO.builder()
                .rowNumber(rowNum)
                .name(name != null ? name : "")
                .email(email != null ? email : "")
                .username(username != null ? username : "")
                .role(role != null ? role : "")
                .departmentName(deptName != null ? deptName : "")
                .field(field)
                .errorCode(code)
                .errorMessage(message)
                .build();
    }

    private byte[] generateErrorSheet(MultipartFile originalFile, List<UserBulkUploadRowErrorDTO> errors) {
        try (InputStream is = originalFile.getInputStream();
             Workbook workbook = WorkbookFactory.create(is);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = findUsersSheet(workbook);
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
            for (UserBulkUploadRowErrorDTO err : errors) {
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
            log.error("Failed to generate error file for user bulk upload", e);
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
