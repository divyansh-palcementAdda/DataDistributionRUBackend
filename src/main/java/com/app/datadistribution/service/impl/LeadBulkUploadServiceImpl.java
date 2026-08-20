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

            Sheet sheet = workbook.createSheet("Lead Bulk Upload Template");

            // Header Style
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            String[] headers = {
                    "Full Name *", "Phone Number *", "Alternate Phone Number", "Email",
                    "City", "State", "Country", "Source Details", "Course Interested", "Remarks"
            };

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Sample Row 1
            Row sampleRow1 = sheet.createRow(1);
            sampleRow1.createCell(0).setCellValue("John Doe");
            sampleRow1.createCell(1).setCellValue("+919876543210");
            sampleRow1.createCell(2).setCellValue("+919876543211");
            sampleRow1.createCell(3).setCellValue("john.doe@example.com");
            sampleRow1.createCell(4).setCellValue("Mumbai");
            sampleRow1.createCell(5).setCellValue("Maharashtra");
            sampleRow1.createCell(6).setCellValue("India");
            sampleRow1.createCell(7).setCellValue("Education Expo 2026");
            sampleRow1.createCell(8).setCellValue("Computer Science Engineering");
            sampleRow1.createCell(9).setCellValue("High intent lead, requested callback");

            // Sample Row 2
            Row sampleRow2 = sheet.createRow(2);
            sampleRow2.createCell(0).setCellValue("Jane Smith");
            sampleRow2.createCell(1).setCellValue("9812345678");
            sampleRow2.createCell(2).setCellValue("");
            sampleRow2.createCell(3).setCellValue("jane.smith@example.com");
            sampleRow2.createCell(4).setCellValue("Delhi");
            sampleRow2.createCell(5).setCellValue("Delhi");
            sampleRow2.createCell(6).setCellValue("India");
            sampleRow2.createCell(7).setCellValue("Organic Inquiry");
            sampleRow2.createCell(8).setCellValue("Business Administration");
            sampleRow2.createCell(9).setCellValue("Inquired about scholarship options");

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
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

        for (int c = 0; c < headerRow.getLastCellNum(); c++) {
            Cell cell = headerRow.getCell(c);
            if (cell != null) {
                String headerText = formatter.formatCellValue(cell).trim().toLowerCase().replaceAll("[_*\\s]+", "");
                if (headerText.contains("fullname") || headerText.equals("name") || headerText.contains("candidatename") || headerText.contains("studentname")) {
                    map.put("fullName", c);
                } else if (headerText.contains("phonenumber") || headerText.contains("phone") || headerText.contains("mobilenumber") || headerText.contains("mobile") || headerText.contains("contactno")) {
                    map.put("phoneNumber", c);
                } else if (headerText.contains("alternatephone") || headerText.contains("altphone") || headerText.contains("alternatemobile") || headerText.contains("altmobile")) {
                    map.put("alternatePhoneNumber", c);
                } else if (headerText.contains("email")) {
                    map.put("email", c);
                } else if (headerText.equals("city") || headerText.equals("town")) {
                    map.put("city", c);
                } else if (headerText.equals("state") || headerText.equals("province")) {
                    map.put("state", c);
                } else if (headerText.equals("country")) {
                    map.put("country", c);
                } else if (headerText.contains("sourcedetail") || headerText.contains("sourcenote")) {
                    map.put("sourceDetails", c);
                } else if (headerText.contains("courseinterested") || headerText.contains("interestedcourse")) {
                    map.put("courseInterested", c);
                } else if (headerText.contains("remark") || headerText.contains("note") || headerText.contains("comment")) {
                    map.put("remarks", c);
                }
            }
        }

        if (!map.containsKey("fullName")) {
            throw new BadRequestException("Missing required Excel header: 'Full Name' (or 'Name')");
        }
        if (!map.containsKey("phoneNumber")) {
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
