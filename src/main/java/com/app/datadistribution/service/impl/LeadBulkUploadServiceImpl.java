package com.app.datadistribution.service.impl;

import com.app.datadistribution.dto.lead.BulkLeadUploadResponse;
import com.app.datadistribution.dto.lead.BulkLeadUploadRowError;
import com.app.datadistribution.entity.*;
import com.app.datadistribution.enums.RoleType;
import com.app.datadistribution.enums.Status;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.repository.*;
import com.app.datadistribution.service.interfaces.ILeadBulkUploadService;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeadBulkUploadServiceImpl implements ILeadBulkUploadService {

    private final LeadRepository leadRepository;
    private final LeadSourceRepository leadSourceRepository;
    private final LeadStatusRepository leadStatusRepository;
    private final BoardRepository boardRepository;
    private final GradeRepository gradeRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final CourseTypeRepository courseTypeRepository;
    private final LeadStatusHistoryRepository leadStatusHistoryRepository;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[+]?[0-9\\s\\-]{7,20}$");

    @Override
    @Transactional
    public BulkLeadUploadResponse bulkUploadLeads(
            MultipartFile file,
            UUID courseTypeId,
            UUID gradeId,
            UUID boardId,
            UUID leadSourceId,
            List<UUID> leadSourceIds,
            UUID statusId,
            UUID departmentId,
            UUID assignedToUserId) throws BadRequestException, UnauthorizedException {

        log.info("Initiating lead bulk upload operation...");

        // 1. Validate Uploaded File
        validateFile(file);

        // 2. Validate Current User Context
        User currentUser = getCurrentUserEntity();

        // 3. Preload & Validate UI Selected Master Data Entities (Fast Fail)
        CourseType selectedCourseType = validateAndFetchCourseType(courseTypeId);
        Grade selectedGrade = validateAndFetchGrade(gradeId);
        Board selectedBoard = validateAndFetchBoard(boardId);
        Set<LeadSource> selectedLeadSources = validateAndFetchLeadSources(leadSourceId, leadSourceIds);
        LeadStatus selectedStatus = validateAndFetchLeadStatus(statusId);
        Department selectedDepartment = validateAndFetchDepartment(departmentId);
        User selectedAssignedTo = validateAndFetchAssignedUser(assignedToUserId, selectedDepartment);

        // 4. Preload Active Phone Numbers for Duplicate Detection
        List<String> activePhoneNumbers = leadRepository.findAllActivePhoneNumbers();
        Set<String> dbPhoneSet = activePhoneNumbers.stream()
                .filter(Objects::nonNull)
                .map(this::normalizePhoneNumber)
                .collect(Collectors.toSet());
        Set<String> fileProcessedPhoneSet = new HashSet<>();

        // 5. Parse Excel Rows
        List<BulkLeadUploadRowError> failedRows = new ArrayList<>();
        List<Lead> leadsToSave = new ArrayList<>();
        List<LeadStatusHistory> historiesToSave = new ArrayList<>();

        int totalRows = 0;
        int successCount = 0;
        int failedCount = 0;
        int duplicateCount = 0;
        int skippedCount = 0;

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null || sheet.getPhysicalNumberOfRows() == 0) {
                throw new BadRequestException("Uploaded Excel sheet is empty");
            }

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new BadRequestException("Excel sheet is missing header row");
            }

            Map<String, Integer> headerMap = parseHeaders(headerRow);

            DataFormatter formatter = new DataFormatter();
            int lastRowNum = sheet.getLastRowNum();

            for (int r = 1; r <= lastRowNum; r++) {
                Row row = sheet.getRow(r);
                if (isRowEmpty(row, formatter)) {
                    skippedCount++;
                    continue;
                }

                totalRows++;
                int displayRowNumber = r + 1;

                String fullName = getCellValue(row, headerMap, "fullName", formatter);
                String phoneNumber = getCellValue(row, headerMap, "phoneNumber", formatter);
                String alternatePhoneNumber = getCellValue(row, headerMap, "alternatePhoneNumber", formatter);
                String email = getCellValue(row, headerMap, "email", formatter);
                String city = getCellValue(row, headerMap, "city", formatter);
                String state = getCellValue(row, headerMap, "state", formatter);
                String country = getCellValue(row, headerMap, "country", formatter);
                String sourceDetails = getCellValue(row, headerMap, "sourceDetails", formatter);
                String courseInterested = getCellValue(row, headerMap, "courseInterested", formatter);
                String remarks = getCellValue(row, headerMap, "remarks", formatter);

                // Row-Level Validation
                if (fullName == null || fullName.isBlank()) {
                    failedCount++;
                    failedRows.add(BulkLeadUploadRowError.builder()
                            .rowNumber(displayRowNumber)
                            .field("fullName")
                            .value(fullName)
                            .reason("Full name is required")
                            .build());
                    continue;
                }

                if (fullName.length() > 150) {
                    failedCount++;
                    failedRows.add(BulkLeadUploadRowError.builder()
                            .rowNumber(displayRowNumber)
                            .field("fullName")
                            .value(fullName)
                            .reason("Full name must be less than 150 characters")
                            .build());
                    continue;
                }

                if (phoneNumber == null || phoneNumber.isBlank()) {
                    failedCount++;
                    failedRows.add(BulkLeadUploadRowError.builder()
                            .rowNumber(displayRowNumber)
                            .field("phoneNumber")
                            .value(phoneNumber)
                            .reason("Phone number is required")
                            .build());
                    continue;
                }

                if (!PHONE_PATTERN.matcher(phoneNumber.trim()).matches()) {
                    failedCount++;
                    failedRows.add(BulkLeadUploadRowError.builder()
                            .rowNumber(displayRowNumber)
                            .field("phoneNumber")
                            .value(phoneNumber)
                            .reason("Invalid phone number format")
                            .build());
                    continue;
                }

                String normalizedPhone = normalizePhoneNumber(phoneNumber);

                // Duplicate Check
                if (dbPhoneSet.contains(normalizedPhone) || fileProcessedPhoneSet.contains(normalizedPhone)) {
                    duplicateCount++;
                    failedRows.add(BulkLeadUploadRowError.builder()
                            .rowNumber(displayRowNumber)
                            .field("phoneNumber")
                            .value(phoneNumber)
                            .reason("Lead with this phone number already exists in system or batch")
                            .build());
                    continue;
                }

                if (email != null && !email.isBlank()) {
                    if (email.length() > 100) {
                        failedCount++;
                        failedRows.add(BulkLeadUploadRowError.builder()
                                .rowNumber(displayRowNumber)
                                .field("email")
                                .value(email)
                                .reason("Email must be less than 100 characters")
                                .build());
                        continue;
                    }
                    if (!EMAIL_PATTERN.matcher(email.trim()).matches()) {
                        failedCount++;
                        failedRows.add(BulkLeadUploadRowError.builder()
                                .rowNumber(displayRowNumber)
                                .field("email")
                                .value(email)
                                .reason("Invalid email address format")
                                .build());
                        continue;
                    }
                }

                // Add to processed phone numbers
                fileProcessedPhoneSet.add(normalizedPhone);
                dbPhoneSet.add(normalizedPhone);

                // Build Lead Entity
                String leadCode = generateUniqueLeadCode();
                Lead lead = Lead.builder()
                        .leadCode(leadCode)
                        .fullName(fullName.trim())
                        .phoneNumber(phoneNumber.trim())
                        .alternatePhoneNumber(alternatePhoneNumber != null && !alternatePhoneNumber.isBlank() ? alternatePhoneNumber.trim() : null)
                        .email(email != null && !email.isBlank() ? email.trim() : null)
                        .city(city != null && !city.isBlank() ? city.trim() : null)
                        .state(state != null && !state.isBlank() ? state.trim() : null)
                        .country(country != null && !country.isBlank() ? country.trim() : null)
                        .sourceDetails(sourceDetails != null && !sourceDetails.isBlank() ? sourceDetails.trim() : null)
                        .courseInterested(courseInterested != null && !courseInterested.isBlank() ? courseInterested.trim() : null)
                        .remarks(remarks != null && !remarks.isBlank() ? remarks.trim() : null)
                        .leadSources(selectedLeadSources)
                        .currentStatus(selectedStatus)
                        .board(selectedBoard)
                        .grade(selectedGrade)
                        .department(selectedDepartment)
                        .assignedTo(selectedAssignedTo)
                        .createdByUser(currentUser)
                        .active(true)
                        .build();

                leadsToSave.add(lead);
                successCount++;
            }

        } catch (Exception e) {
            log.error("Failed to parse Excel file for bulk lead upload", e);
            if (e instanceof BadRequestException) {
                throw (BadRequestException) e;
            }
            throw new BadRequestException("Error processing Excel file: " + e.getMessage());
        }

        // Save Batch Leads & Status Histories
        if (!leadsToSave.isEmpty()) {
            List<Lead> savedLeads = leadRepository.saveAll(leadsToSave);
            for (Lead saved : savedLeads) {
                LeadStatusHistory history = LeadStatusHistory.builder()
                        .lead(saved)
                        .previousStatus(null)
                        .newStatus(selectedStatus)
                        .changedByUser(currentUser)
                        .feedback("Lead registered via Bulk Upload.")
                        .build();
                historiesToSave.add(history);
            }
            leadStatusHistoryRepository.saveAll(historiesToSave);
            log.info("Successfully bulk created {} leads out of {} total rows", savedLeads.size(), totalRows);
        }

        return BulkLeadUploadResponse.builder()
                .totalRows(totalRows)
                .successCount(successCount)
                .failedCount(failedCount)
                .duplicateCount(duplicateCount)
                .skippedCount(skippedCount)
                .failedRows(failedRows)
                .build();
    }

    @Override
    public byte[] downloadTemplate() {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // --- Sheet 1: Lead Upload ---
            Sheet uploadSheet = workbook.createSheet("Lead Upload");
            uploadSheet.createFreezePane(0, 1);

            // Header Style
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            List<com.app.datadistribution.dto.lead.LeadBulkUploadColumnDefinition> columns =
                    com.app.datadistribution.dto.lead.LeadBulkUploadColumnDefinition.getAllColumns();

            Row headerRow = uploadSheet.createRow(0);
            headerRow.setHeightInPoints(25);

            for (int i = 0; i < columns.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns.get(i).getHeaderName());
                cell.setCellStyle(headerStyle);
            }

            // Auto-fit columns with safety width padding
            for (int i = 0; i < columns.size(); i++) {
                uploadSheet.autoSizeColumn(i);
                int currentWidth = uploadSheet.getColumnWidth(i);
                uploadSheet.setColumnWidth(i, Math.max(currentWidth + 1200, 5000));
            }

            // --- Sheet 2: Instructions ---
            Sheet instructionSheet = workbook.createSheet("Instructions");

            // Header Style for Instructions
            CellStyle instHeaderStyle = workbook.createCellStyle();
            Font instHeaderFont = workbook.createFont();
            instHeaderFont.setBold(true);
            instHeaderFont.setColor(IndexedColors.WHITE.getIndex());
            instHeaderStyle.setFont(instHeaderFont);
            instHeaderStyle.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
            instHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            instHeaderStyle.setAlignment(HorizontalAlignment.LEFT);

            // Data Cell Style
            CellStyle cellStyle = workbook.createCellStyle();
            cellStyle.setWrapText(true);
            cellStyle.setVerticalAlignment(VerticalAlignment.TOP);

            // Bold Cell Style
            CellStyle boldStyle = workbook.createCellStyle();
            Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            boldStyle.setFont(boldFont);

            int rowIdx = 0;

            // Title
            Row titleRow = instructionSheet.createRow(rowIdx++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("LEAD BULK UPLOAD INSTRUCTIONS & COLUMN GUIDE");
            titleCell.setCellStyle(instHeaderStyle);

            rowIdx++; // Empty spacing row

            // Section 1: UI Selected Common Master Data
            Row sec1Title = instructionSheet.createRow(rowIdx++);
            Cell sec1Cell = sec1Title.createCell(0);
            sec1Cell.setCellValue("1. MASTER DATA SELECTED FROM WEB UI (DO NOT INCLUDE IN EXCEL)");
            sec1Cell.setCellStyle(boldStyle);

            String[] uiFields = {
                    "• Course Type: Selected from Web UI during upload dialog.",
                    "• Grade: Selected from Web UI during upload dialog.",
                    "• Board: Selected from Web UI during upload dialog.",
                    "• Lead Source(s): Selected from Web UI during upload dialog.",
                    "• Lead Status: Selected from Web UI during upload dialog (Defaults to 'RAW').",
                    "• Department: Selected from Web UI during upload dialog.",
                    "• Assigned User: Selected from Web UI during upload dialog."
            };

            for (String uiField : uiFields) {
                Row r = instructionSheet.createRow(rowIdx++);
                r.createCell(0).setCellValue(uiField);
            }

            rowIdx++; // Spacing

            // Section 2: Excel Column Guidelines Table
            Row sec2Title = instructionSheet.createRow(rowIdx++);
            Cell sec2Cell = sec2Title.createCell(0);
            sec2Cell.setCellValue("2. EXCEL SHEET COLUMN FORMAT & VALIDATION RULES");
            sec2Cell.setCellStyle(boldStyle);

            Row tableHeader = instructionSheet.createRow(rowIdx++);
            tableHeader.setHeightInPoints(22);
            String[] instTableHeaders = {"Column Header", "Field Key", "Required?", "Data Format", "Sample Value", "Validation / Rule Description"};
            for (int i = 0; i < instTableHeaders.length; i++) {
                Cell c = tableHeader.createCell(i);
                c.setCellValue(instTableHeaders[i]);
                c.setCellStyle(instHeaderStyle);
            }

            for (com.app.datadistribution.dto.lead.LeadBulkUploadColumnDefinition colDef : columns) {
                Row r = instructionSheet.createRow(rowIdx++);
                r.createCell(0).setCellValue(colDef.getHeaderName());
                r.createCell(1).setCellValue(colDef.getFieldKey());
                r.createCell(2).setCellValue(colDef.isRequired() ? "REQUIRED *" : "Optional");
                r.createCell(3).setCellValue(colDef.getDataType());
                r.createCell(4).setCellValue(colDef.getSampleValue());
                r.createCell(5).setCellValue(colDef.getDescription());

                for (int i = 0; i < 6; i++) {
                    r.getCell(i).setCellStyle(cellStyle);
                }
            }

            rowIdx++; // Spacing

            // Section 3: General Upload Guidelines
            Row sec3Title = instructionSheet.createRow(rowIdx++);
            Cell sec3Cell = sec3Title.createCell(0);
            sec3Cell.setCellValue("3. SYSTEM LIMITS & DUPLICATE HANDLING RULES");
            sec3Cell.setCellStyle(boldStyle);

            String[] generalRules = {
                    "• Supported File Formats: .xlsx or .xls",
                    "• Maximum File Size: 10 MB per file.",
                    "• Recommended Maximum Records: 10,000 rows per batch upload.",
                    "• Duplicate Phone Numbers: System checks active phone numbers in database and batch rows. Duplicate phone numbers will be skipped and logged in error summary.",
                    "• Phone Number Format: 7 to 20 digits, optionally starting with '+' or containing spaces/dashes.",
                    "• Email Validation: Must be a valid email address format (max 100 characters).",
                    "• Required Fields: Rows missing Full Name or Phone Number will be marked as failed rows."
            };

            for (String rule : generalRules) {
                Row r = instructionSheet.createRow(rowIdx++);
                r.createCell(0).setCellValue(rule);
            }

            for (int i = 0; i < 6; i++) {
                instructionSheet.autoSizeColumn(i);
                int currentWidth = instructionSheet.getColumnWidth(i);
                instructionSheet.setColumnWidth(i, Math.max(currentWidth + 1000, 4500));
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate Excel template for lead bulk upload", e);
            throw new RuntimeException("Error generating template file: " + e.getMessage());
        }
    }

    // --- Helper Validation Methods ---

    private void validateFile(MultipartFile file) throws BadRequestException {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Uploaded file is missing or empty");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || (!originalFilename.toLowerCase().endsWith(".xlsx") && !originalFilename.toLowerCase().endsWith(".xls"))) {
            throw new BadRequestException("Invalid file format. Only Excel files (.xlsx or .xls) are supported");
        }

        long maxSizeBytes = 10 * 1024 * 1024; // 10 MB
        if (file.getSize() > maxSizeBytes) {
            throw new BadRequestException("File size exceeds maximum allowed limit of 10 MB");
        }
    }

    private User getCurrentUserEntity() throws UnauthorizedException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new UnauthorizedException("User is not authenticated");
        }
        String username = auth.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourcesNotFoundException("User not found with username: " + username));
    }

    private CourseType validateAndFetchCourseType(UUID courseTypeId) throws BadRequestException {
        if (courseTypeId == null) return null;
        CourseType courseType = courseTypeRepository.findById(courseTypeId)
                .filter(ct -> !ct.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Selected Course Type not found with ID: " + courseTypeId));
        if (courseType.getStatus() != Status.ACTIVE) {
            throw new BadRequestException("Selected Course Type '" + courseType.getName() + "' is inactive");
        }
        return courseType;
    }

    private Grade validateAndFetchGrade(UUID gradeId) throws BadRequestException {
        if (gradeId == null) return null;
        Grade grade = gradeRepository.findById(gradeId)
                .filter(g -> !g.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Selected Grade not found with ID: " + gradeId));
        if (!grade.isActive()) {
            throw new BadRequestException("Selected Grade '" + grade.getName() + "' is inactive");
        }
        return grade;
    }

    private Board validateAndFetchBoard(UUID boardId) throws BadRequestException {
        if (boardId == null) return null;
        Board board = boardRepository.findById(boardId)
                .filter(b -> !b.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Selected Board not found with ID: " + boardId));
        if (!board.isActive()) {
            throw new BadRequestException("Selected Board '" + board.getName() + "' is inactive");
        }
        return board;
    }

    private Set<LeadSource> validateAndFetchLeadSources(UUID singleSourceId, List<UUID> sourceIds) throws BadRequestException {
        Set<UUID> idsToFetch = new HashSet<>();
        if (sourceIds != null) {
            idsToFetch.addAll(sourceIds);
        }
        if (singleSourceId != null) {
            idsToFetch.add(singleSourceId);
        }
        if (idsToFetch.isEmpty()) {
            return new HashSet<>();
        }

        Set<LeadSource> sources = new HashSet<>();
        for (UUID id : idsToFetch) {
            LeadSource source = leadSourceRepository.findById(id)
                    .filter(s -> !s.isDeleted())
                    .orElseThrow(() -> new ResourcesNotFoundException("Selected Lead Source not found with ID: " + id));
            if (!source.isActive()) {
                throw new BadRequestException("Selected Lead Source '" + source.getName() + "' is inactive");
            }
            sources.add(source);
        }
        return sources;
    }

    private LeadStatus validateAndFetchLeadStatus(UUID statusId) throws BadRequestException {
        if (statusId != null) {
            LeadStatus status = leadStatusRepository.findById(statusId)
                    .filter(s -> !s.isDeleted())
                    .orElseThrow(() -> new ResourcesNotFoundException("Selected Lead Status not found with ID: " + statusId));
            if (!status.isActive()) {
                throw new BadRequestException("Selected Lead Status '" + status.getName() + "' is inactive");
            }
            return status;
        }

        // Fallback to RAW status
        return leadStatusRepository.findByCodeIgnoreCase("RAW")
                .or(() -> leadStatusRepository.findByNameIgnoreCase("Raw"))
                .orElseGet(() -> {
                    List<LeadStatus> all = leadStatusRepository.findAll();
                    return all.stream().filter(s -> !s.isDeleted() && s.isActive()).findFirst()
                            .orElseThrow(() -> new ResourcesNotFoundException("No active Lead Status configured in system"));
                });
    }

    private Department validateAndFetchDepartment(UUID departmentId) throws BadRequestException {
        if (departmentId == null) return null;
        Department department = departmentRepository.findById(departmentId)
                .filter(d -> !d.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Selected Department not found with ID: " + departmentId));
        if (!department.isActive()) {
            throw new BadRequestException("Selected Department '" + department.getName() + "' is inactive");
        }
        return department;
    }

    private User validateAndFetchAssignedUser(UUID assignedToUserId, Department department) throws BadRequestException {
        if (assignedToUserId == null) return null;
        User user = userRepository.findById(assignedToUserId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Selected Assigned User not found with ID: " + assignedToUserId));
        if (!user.isActive()) {
            throw new BadRequestException("Selected Assigned User '" + user.getUsername() + "' is inactive");
        }

        if (department != null) {
            boolean isSystemAdmin = user.getRoles() != null && user.getRoles().stream()
                    .anyMatch(r -> RoleType.SUPER_ADMIN.name().equalsIgnoreCase(r.getName()) || RoleType.ADMIN.name().equalsIgnoreCase(r.getName()));
            if (!isSystemAdmin && user.getDepartments() != null && !user.getDepartments().isEmpty()) {
                boolean belongsToDept = user.getDepartments().stream().anyMatch(d -> d.getId().equals(department.getId()));
                if (!belongsToDept) {
                    log.warn("Assigned user {} does not belong to lead department {}", user.getUsername(), department.getName());
                }
            }
        }
        return user;
    }

    private Map<String, Integer> parseHeaders(Row headerRow) throws BadRequestException {
        Map<String, Integer> map = new HashMap<>();
        DataFormatter formatter = new DataFormatter();

        List<com.app.datadistribution.dto.lead.LeadBulkUploadColumnDefinition> definitions =
                com.app.datadistribution.dto.lead.LeadBulkUploadColumnDefinition.getAllColumns();

        for (int c = 0; c < headerRow.getLastCellNum(); c++) {
            Cell cell = headerRow.getCell(c);
            if (cell != null) {
                String headerText = formatter.formatCellValue(cell).trim().toLowerCase().replaceAll("[_*\\s]+", "");

                for (com.app.datadistribution.dto.lead.LeadBulkUploadColumnDefinition def : definitions) {
                    String canonicalHeader = def.getHeaderName().trim().toLowerCase().replaceAll("[_*\\s]+", "");
                    if (headerText.equals(canonicalHeader) || headerText.equals(def.getFieldKey().toLowerCase())) {
                        map.put(def.getFieldKey(), c);
                        break;
                    }
                }

                // Fallback alias mappings for existing user variations
                if (!map.containsValue(c)) {
                    if (headerText.contains("fullname") || headerText.equals("name") || headerText.contains("candidatename") || headerText.contains("studentname")) {
                        map.put(com.app.datadistribution.dto.lead.LeadBulkUploadColumnDefinition.FULL_NAME.getFieldKey(), c);
                    } else if (headerText.contains("phonenumber") || headerText.contains("phone") || headerText.contains("mobilenumber") || headerText.contains("mobile") || headerText.contains("contactno")) {
                        map.put(com.app.datadistribution.dto.lead.LeadBulkUploadColumnDefinition.PHONE_NUMBER.getFieldKey(), c);
                    } else if (headerText.contains("alternatephone") || headerText.contains("altphone") || headerText.contains("alternatemobile") || headerText.contains("altmobile")) {
                        map.put(com.app.datadistribution.dto.lead.LeadBulkUploadColumnDefinition.ALTERNATE_PHONE_NUMBER.getFieldKey(), c);
                    } else if (headerText.contains("email")) {
                        map.put(com.app.datadistribution.dto.lead.LeadBulkUploadColumnDefinition.EMAIL.getFieldKey(), c);
                    } else if (headerText.equals("city") || headerText.equals("town")) {
                        map.put(com.app.datadistribution.dto.lead.LeadBulkUploadColumnDefinition.CITY.getFieldKey(), c);
                    } else if (headerText.equals("state") || headerText.equals("province")) {
                        map.put(com.app.datadistribution.dto.lead.LeadBulkUploadColumnDefinition.STATE.getFieldKey(), c);
                    } else if (headerText.equals("country")) {
                        map.put(com.app.datadistribution.dto.lead.LeadBulkUploadColumnDefinition.COUNTRY.getFieldKey(), c);
                    } else if (headerText.contains("sourcedetail") || headerText.contains("sourcenote")) {
                        map.put(com.app.datadistribution.dto.lead.LeadBulkUploadColumnDefinition.SOURCE_DETAILS.getFieldKey(), c);
                    } else if (headerText.contains("courseinterested") || headerText.contains("interestedcourse")) {
                        map.put(com.app.datadistribution.dto.lead.LeadBulkUploadColumnDefinition.COURSE_INTERESTED.getFieldKey(), c);
                    } else if (headerText.contains("remark") || headerText.contains("note") || headerText.contains("comment")) {
                        map.put(com.app.datadistribution.dto.lead.LeadBulkUploadColumnDefinition.REMARKS.getFieldKey(), c);
                    }
                }
            }
        }

        if (!map.containsKey(com.app.datadistribution.dto.lead.LeadBulkUploadColumnDefinition.FULL_NAME.getFieldKey())) {
            throw new BadRequestException("Missing required Excel header: 'Full Name' (or 'Name')");
        }
        if (!map.containsKey(com.app.datadistribution.dto.lead.LeadBulkUploadColumnDefinition.PHONE_NUMBER.getFieldKey())) {
            throw new BadRequestException("Missing required Excel header: 'Phone Number' (or 'Mobile')");
        }

        return map;
    }

    private String getCellValue(Row row, Map<String, Integer> headerMap, String key, DataFormatter formatter) {
        Integer colIndex = headerMap.get(key);
        if (colIndex == null) return null;
        Cell cell = row.getCell(colIndex);
        if (cell == null || cell.getCellType() == CellType.BLANK) return null;
        return formatter.formatCellValue(cell).trim();
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

    private String normalizePhoneNumber(String rawPhone) {
        if (rawPhone == null) return "";
        return rawPhone.replaceAll("[^0-9]", "");
    }

    private String generateUniqueLeadCode() {
        String candidate;
        do {
            candidate = "LEAD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } while (leadRepository.existsByLeadCode(candidate));
        return candidate;
    }
}
