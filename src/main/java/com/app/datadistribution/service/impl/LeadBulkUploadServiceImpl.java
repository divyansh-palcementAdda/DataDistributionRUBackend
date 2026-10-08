package com.app.datadistribution.service.impl;

import com.app.datadistribution.common.bulkupload.BulkUploadHeaderMappingResult;
import com.app.datadistribution.common.bulkupload.BulkUploadHeaderNormalizer;
import com.app.datadistribution.common.bulkupload.BulkUploadHeaderParser;
import com.app.datadistribution.common.bulkupload.ExcelCellReader;
import com.app.datadistribution.dto.lead.BulkLeadUploadResponse;
import com.app.datadistribution.dto.lead.BulkLeadUploadRowError;
import com.app.datadistribution.dto.lead.BulkUploadMappingItemDTO;
import com.app.datadistribution.dto.lead.LeadBulkUploadColumnDefinition;
import com.app.datadistribution.dto.lead.LeadBulkUploadRowDTO;
import com.app.datadistribution.entity.Board;
import com.app.datadistribution.entity.Course;
import com.app.datadistribution.entity.CourseType;
import com.app.datadistribution.entity.Department;
import com.app.datadistribution.entity.Grade;
import com.app.datadistribution.entity.Lead;
import com.app.datadistribution.entity.LeadSource;
import com.app.datadistribution.entity.LeadStatus;
import com.app.datadistribution.entity.LeadStatusHistory;
import com.app.datadistribution.entity.Program;
import com.app.datadistribution.entity.Stream;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.enums.RoleType;
import com.app.datadistribution.exception.BadRequestException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.exception.UnauthorizedException;
import com.app.datadistribution.repository.BoardRepository;
import com.app.datadistribution.repository.CourseRepository;
import com.app.datadistribution.repository.CourseTypeRepository;
import com.app.datadistribution.repository.DepartmentRepository;
import com.app.datadistribution.repository.GradeRepository;
import com.app.datadistribution.repository.LeadRepository;
import com.app.datadistribution.repository.LeadSourceRepository;
import com.app.datadistribution.repository.LeadStatusHistoryRepository;
import com.app.datadistribution.repository.LeadStatusRepository;
import com.app.datadistribution.repository.ProgramRepository;
import com.app.datadistribution.repository.StreamRepository;
import com.app.datadistribution.repository.UserRepository;
import com.app.datadistribution.service.dto.LeadAcademicResolutionResult;
import com.app.datadistribution.service.dto.UserDataScope;
import com.app.datadistribution.service.interfaces.ILeadBulkUploadService;
import com.app.datadistribution.service.interfaces.ILeadDataScopeService;
import com.app.datadistribution.service.util.LeadAcademicResolver;
import com.app.datadistribution.service.util.LeadDepartmentResolver;
import com.app.datadistribution.service.util.ProgramCourseResolver;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
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
    private final StreamRepository streamRepository;
    private final GradeRepository gradeRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final CourseTypeRepository courseTypeRepository;
    private final CourseRepository courseRepository;
    private final ProgramRepository programRepository;
    private final ProgramCourseResolver programCourseResolver;
    private final LeadAcademicResolver leadAcademicResolver;
    private final LeadStatusHistoryRepository leadStatusHistoryRepository;
    private final ILeadDataScopeService leadDataScopeService;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[+]?[0-9\\s\\-]{7,20}$");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static final Set<String> SUPPORTED_CANONICAL_TARGET_FIELDS = Set.of(
            "fullName",
            "phoneNumber",
            "alternatePhoneNumber",
            "email",
            "course",
            "program",
            "courseType",
            "leadSource",
            "sourceDetails",
            "board",
            "grade",
            "stream",
            "department",
            "city",
            "state",
            "country",
            "remarks"
    );

    @Override
    @Transactional
    public BulkLeadUploadResponse bulkUploadLeads(
            MultipartFile file,
            UUID programId,
            UUID courseTypeId,
            UUID streamId,
            UUID gradeId,
            UUID boardId,
            UUID leadSourceId,
            List<UUID> leadSourceIds,
            UUID statusId,
            UUID departmentId,
            UUID assignedToUserId) throws BadRequestException, UnauthorizedException {
        return bulkUploadLeads(file, programId, courseTypeId, streamId, gradeId, boardId, leadSourceId, leadSourceIds, statusId, departmentId, assignedToUserId, null);
    }

    @Override
    @Transactional
    public BulkLeadUploadResponse bulkUploadLeads(
            MultipartFile file,
            UUID programId,
            UUID courseTypeId,
            UUID streamId,
            UUID gradeId,
            UUID boardId,
            UUID leadSourceId,
            List<UUID> leadSourceIds,
            UUID statusId,
            UUID departmentId,
            UUID assignedToUserId,
            String mappingJson) throws BadRequestException, UnauthorizedException {

        String importId = "IMP-LEAD-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        // Structured Log: IMPORT_START (per spec §9)
        log.info("IMPORT_START\nimportId={}\nfileName={}\nselectedProgramId={}\nselectedCourseTypeId={}\nselectedStreamId={}\nselectedGradeId={}\nselectedBoardId={}\nselectedLeadSourceId={}\nselectedStatusId={}\nselectedDepartmentId={}\nselectedAssignedToUserId={}",
                importId,
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown",
                programId != null ? programId.toString() : "null",
                courseTypeId != null ? courseTypeId.toString() : "null",
                streamId != null ? streamId.toString() : "null",
                gradeId != null ? gradeId.toString() : "null",
                boardId != null ? boardId.toString() : "null",
                leadSourceId != null ? leadSourceId.toString() : "null",
                statusId != null ? statusId.toString() : "null",
                departmentId != null ? departmentId.toString() : "null",
                assignedToUserId != null ? assignedToUserId.toString() : "null");
        log.info("LEAD_BULK_IMPORT_STARTED\nimportId={}", importId);

        // 1. Validate Uploaded File
        validateFile(file);

        // 2. Validate Current User Context & Scope
        User currentUser = getCurrentUserEntity();
        UserDataScope dataScope = leadDataScopeService.getCurrentUserScope();

        if (dataScope.isSelfScope() && assignedToUserId != null && !assignedToUserId.equals(currentUser.getId())) {
            throw new BadRequestException("Counselors can only assign uploaded leads to themselves or leave unassigned.");
        }
        if (dataScope.isDepartmentScope()) {
            if (departmentId != null && dataScope.getDepartmentIds() != null && !dataScope.getDepartmentIds().contains(departmentId)) {
                throw new BadRequestException("HOD can only upload leads for their mapped department(s).");
            }
            if (assignedToUserId != null && dataScope.getDepartmentUserIds() != null && !dataScope.getDepartmentUserIds().contains(assignedToUserId)) {
                throw new BadRequestException("HOD can only assign leads to members of their assigned department(s).");
            }
        }

        // 3. Preload & Validate UI Selected Master Data Entities (Fast Fail)
        Program selectedProgram = validateAndFetchProgram(programId);
        CourseType selectedCourseType = validateAndFetchCourseType(courseTypeId);

        // Structured Log: IMPORT_START post-validation (resolved names)
        log.info("IMPORT_START_RESOLVED\nimportId={}\nselectedCourseTypeId={}\nselectedCourseTypeName={}",
                importId,
                selectedCourseType != null ? selectedCourseType.getId().toString() : "null",
                selectedCourseType != null ? selectedCourseType.getName() : "null");
        Stream selectedStream = validateAndFetchStream(streamId);
        Grade selectedGrade = validateAndFetchGrade(gradeId);
        Board selectedBoard = validateAndFetchBoard(boardId);
        Set<LeadSource> selectedLeadSources = validateAndFetchLeadSources(leadSourceId, leadSourceIds);
        LeadStatus selectedStatus = validateAndFetchLeadStatus(statusId);
        Department selectedDepartment = validateAndFetchDepartment(departmentId);
        User selectedAssignedTo = validateAndFetchAssignedUser(assignedToUserId, selectedDepartment);
        // Synchronize Department with assigned user (Source of Truth)
        selectedDepartment = LeadDepartmentResolver.resolveDepartmentForUser(selectedAssignedTo, selectedDepartment);

        // 4. Preload Active Phone Numbers for Duplicate Detection
        List<String> activePhoneNumbers = leadRepository.findAllActivePhoneNumbers();
        Set<String> dbPhoneSet = activePhoneNumbers.stream()
                .filter(Objects::nonNull)
                .map(this::normalizePhoneNumber)
                .filter(p -> !p.isBlank())
                .collect(Collectors.toSet());
        Set<String> fileProcessedPhoneSet = new HashSet<>();

        // 5. Parse Excel Rows
        List<BulkLeadUploadRowError> failedRows = new ArrayList<>();
        List<Lead> leadsToSave = new ArrayList<>();
        List<Integer> savedRowNumbers = new ArrayList<>();

        int totalRows = 0;
        int successCount = 0;
        int failedCount = 0;
        int duplicateCount = 0;
        int skippedCount = 0;

        int courseResolvedCount = 0;
        int programResolvedCount = 0;
        int courseTypeResolvedCount = 0;
        int programAutoMappedFromCourseCount = 0;
        int courseTypeAutoMappedFromCourseCount = 0;

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

            // Extract all cell headers from row 0
            Map<Integer, String> colIndexToRawHeader = new LinkedHashMap<>();
            DataFormatter headerFormatter = new DataFormatter();
            int lastCellNum = headerRow.getLastCellNum();
            for (int c = 0; c < lastCellNum; c++) {
                Cell cell = headerRow.getCell(c);
                if (cell != null) {
                    String raw = headerFormatter.formatCellValue(cell);
                    if (raw != null && !raw.isBlank()) {
                        colIndexToRawHeader.put(c, raw.trim());
                    }
                }
            }

            // Determine Field -> Column Index mapping
            Map<String, Integer> fieldToCol = new LinkedHashMap<>();

            if (mappingJson != null && !mappingJson.isBlank()) {
                // Explicit Mapping Mode: Frontend provided specific Excel Column -> Selected Target Field mapping
                List<BulkUploadMappingItemDTO> explicitMappings = parseMappingJson(mappingJson);
                for (BulkUploadMappingItemDTO item : explicitMappings) {
                    if (item.getExcelColumn() == null || item.getTargetField() == null) continue;
                    String canonKey = normalizeTargetFieldName(item.getTargetField());
                    if (canonKey == null || !SUPPORTED_CANONICAL_TARGET_FIELDS.contains(canonKey)) {
                        log.error("UNSUPPORTED_IMPORT_FIELD\nimportId={}\ntargetField={}", importId, item.getTargetField());
                        throw new BadRequestException("UNSUPPORTED_IMPORT_FIELD: Target field '" + item.getTargetField()
                                + "' is not supported. Supported fields are: " + SUPPORTED_CANONICAL_TARGET_FIELDS);
                    }
                    String normExcel = BulkUploadHeaderNormalizer.normalize(item.getExcelColumn());

                    for (Map.Entry<Integer, String> entry : colIndexToRawHeader.entrySet()) {
                        if (BulkUploadHeaderNormalizer.normalize(entry.getValue()).equals(normExcel)) {
                            fieldToCol.put(canonKey, entry.getKey());
                            break;
                        }
                    }
                }
            }

            // Fallback or Merge with canonical alias parsing
            if (fieldToCol.isEmpty()) {
                BulkUploadHeaderMappingResult parsedHeaders = BulkUploadHeaderParser.parseHeaders(
                        headerRow,
                        LeadBulkUploadColumnDefinition.getAllFieldDefinitions(),
                        sheet.getSheetName(),
                        null,
                        (fieldMap, defs, sheetName) -> {
                            // Rule 1: Every Lead field is optional. No missing required column exceptions thrown here.
                        }
                );
                fieldToCol.putAll(parsedHeaders.getFieldToColIndex());
                colIndexToRawHeader.putAll(parsedHeaders.getColIndexToRawHeader());
            }

            // Structured Log: HEADER_MAPPING
            StringBuilder hmLog = new StringBuilder("HEADER_MAPPING\nimportId=").append(importId).append("\n");
            for (Map.Entry<String, Integer> entry : fieldToCol.entrySet()) {
                String rawName = colIndexToRawHeader.getOrDefault(entry.getValue(), "Col " + entry.getValue());
                hmLog.append(rawName).append(" → ").append(entry.getKey()).append("\n");
            }
            log.info(hmLog.toString().trim());

            int lastRowNum = sheet.getLastRowNum();

            for (int r = 1; r <= lastRowNum; r++) {
                Row row = sheet.getRow(r);
                if (ExcelCellReader.isRowEmpty(row)) {
                    skippedCount++;
                    continue;
                }

                totalRows++;
                int displayRowNumber = r + 1;

                // Read cell values by mapped column index into Intermediate DTO
                LeadBulkUploadRowDTO rowDto = LeadBulkUploadRowDTO.builder()
                        .rowNumber(displayRowNumber)
                        .fullNameRaw(ExcelCellReader.readCellValue(row, fieldToCol.get("fullName")))
                        .phoneNumberRaw(ExcelCellReader.readCellValue(row, fieldToCol.get("phoneNumber")))
                        .alternatePhoneNumberRaw(ExcelCellReader.readCellValue(row, fieldToCol.get("alternatePhoneNumber")))
                        .emailRaw(ExcelCellReader.readCellValue(row, fieldToCol.get("email")))
                        .cityRaw(ExcelCellReader.readCellValue(row, fieldToCol.get("city")))
                        .stateRaw(ExcelCellReader.readCellValue(row, fieldToCol.get("state")))
                        .countryRaw(ExcelCellReader.readCellValue(row, fieldToCol.get("country")))
                        .sourceDetailsRaw(ExcelCellReader.readCellValue(row, fieldToCol.get("sourceDetails")))
                        .programRaw(ExcelCellReader.readCellValue(row, fieldToCol.get("program")))
                        .streamRaw(ExcelCellReader.readCellValue(row, fieldToCol.get("stream")))
                        .courseRaw(ExcelCellReader.readCellValue(row, fieldToCol.get("course")))
                        .courseTypeRaw(ExcelCellReader.readCellValue(row, fieldToCol.get("courseType")))
                        .leadSourceRaw(ExcelCellReader.readCellValue(row, fieldToCol.get("leadSource")))
                        .boardRaw(ExcelCellReader.readCellValue(row, fieldToCol.get("board")))
                        .gradeRaw(ExcelCellReader.readCellValue(row, fieldToCol.get("grade")))
                        .departmentRaw(ExcelCellReader.readCellValue(row, fieldToCol.get("department")))
                        .remarksRaw(ExcelCellReader.readCellValue(row, fieldToCol.get("remarks")))
                        .build();

                // Structured Log: ROW_FIELD_EXTRACTION for all mapped headers
                for (Map.Entry<String, Integer> entry : fieldToCol.entrySet()) {
                    String targetKey = entry.getKey();
                    int colIdx = entry.getValue();
                    String rawHeader = colIndexToRawHeader.getOrDefault(colIdx, "Col " + colIdx);
                    String rawVal = getRawValueByFieldKey(rowDto, targetKey);
                    log.info("ROW_FIELD_EXTRACTION\nimportId={}\nrow={}\nexcelHeader=\"{}\"\ntargetField=\"{}\"\ncolumnIndex={}\nrawValue=\"{}\"",
                            importId, displayRowNumber, rawHeader, targetKey, colIdx, rawVal != null ? rawVal : "");
                }

                // Structured Log: BULK_ROW_DTO + ROW_MAPPING courseType info (per spec §9)
                log.info("BULK_ROW_DTO\nrow={}\ncourseRaw={}\nprogramRaw={}\ncourseTypeRaw={}",
                        displayRowNumber,
                        rowDto.getCourseRaw() != null ? rowDto.getCourseRaw() : "",
                        rowDto.getProgramRaw() != null ? rowDto.getProgramRaw() : "",
                        rowDto.getCourseTypeRaw() != null ? rowDto.getCourseTypeRaw() : (selectedCourseType != null ? selectedCourseType.getName() : ""));
                log.info("ROW_MAPPING_COURSE_TYPE\nimportId={}\nrowNumber={}\nrawCourseTypeValue={}\nselectedCourseTypeId={}\nselectedCourseTypeName={}",
                        importId,
                        displayRowNumber,
                        rowDto.getCourseTypeRaw() != null ? rowDto.getCourseTypeRaw() : "",
                        selectedCourseType != null ? selectedCourseType.getId().toString() : "null",
                        selectedCourseType != null ? selectedCourseType.getName() : "null");

                // Row-Level Validation (All Lead Fields are strictly optional!)
                if (rowDto.getFullNameRaw() != null && rowDto.getFullNameRaw().length() > 150) {
                    failedCount++;
                    log.warn("LEAD_IMPORT_ROW_FAILED\nimportId={}\nrow={}\nfield=fullName\nrawValue={}\nerrorCode=FULL_NAME_TOO_LONG\nmessage=Full name must be less than 150 characters",
                            importId, displayRowNumber, rowDto.getFullNameRaw());
                    failedRows.add(BulkLeadUploadRowError.builder()
                            .rowNumber(displayRowNumber)
                            .field("fullName")
                            .value(rowDto.getFullNameRaw())
                            .reason("Full name must be less than 150 characters")
                            .build());
                    continue;
                }

                String normalizedPhone = null;
                if (rowDto.getPhoneNumberRaw() != null && !rowDto.getPhoneNumberRaw().isBlank()) {
                    if (!PHONE_PATTERN.matcher(rowDto.getPhoneNumberRaw().trim()).matches()) {
                        failedCount++;
                        log.warn("LEAD_IMPORT_ROW_FAILED\nimportId={}\nrow={}\nfield=phoneNumber\nrawValue={}\nerrorCode=INVALID_PHONE_FORMAT\nmessage=Invalid phone number format",
                                importId, displayRowNumber, rowDto.getPhoneNumberRaw());
                        failedRows.add(BulkLeadUploadRowError.builder()
                                .rowNumber(displayRowNumber)
                                .field("phoneNumber")
                                .value(rowDto.getPhoneNumberRaw())
                                .reason("Invalid phone number format")
                                .build());
                        continue;
                    }

                    normalizedPhone = normalizePhoneNumber(rowDto.getPhoneNumberRaw());
                    if (dbPhoneSet.contains(normalizedPhone) || fileProcessedPhoneSet.contains(normalizedPhone)) {
                        duplicateCount++;
                        log.warn("LEAD_IMPORT_ROW_FAILED\nimportId={}\nrow={}\nfield=phoneNumber\nrawValue={}\nerrorCode=DUPLICATE_PHONE_NUMBER\nmessage=Lead with this phone number already exists in system or batch",
                                importId, displayRowNumber, rowDto.getPhoneNumberRaw());
                        failedRows.add(BulkLeadUploadRowError.builder()
                                .rowNumber(displayRowNumber)
                                .field("phoneNumber")
                                .value(rowDto.getPhoneNumberRaw())
                                .reason("Lead with this phone number already exists in system or batch")
                                .build());
                        continue;
                    }
                }

                if (rowDto.getEmailRaw() != null && !rowDto.getEmailRaw().isBlank()) {
                    if (rowDto.getEmailRaw().length() > 100) {
                        failedCount++;
                        log.warn("LEAD_IMPORT_ROW_FAILED\nimportId={}\nrow={}\nfield=email\nrawValue={}\nerrorCode=EMAIL_TOO_LONG\nmessage=Email must be less than 100 characters",
                                importId, displayRowNumber, rowDto.getEmailRaw());
                        failedRows.add(BulkLeadUploadRowError.builder()
                                .rowNumber(displayRowNumber)
                                .field("email")
                                .value(rowDto.getEmailRaw())
                                .reason("Email must be less than 100 characters")
                                .build());
                        continue;
                    }
                    if (!EMAIL_PATTERN.matcher(rowDto.getEmailRaw().trim()).matches()) {
                        failedCount++;
                        log.warn("LEAD_IMPORT_ROW_FAILED\nimportId={}\nrow={}\nfield=email\nrawValue={}\nerrorCode=INVALID_EMAIL_FORMAT\nmessage=Invalid email address format",
                                importId, displayRowNumber, rowDto.getEmailRaw());
                        failedRows.add(BulkLeadUploadRowError.builder()
                                .rowNumber(displayRowNumber)
                                .field("email")
                                .value(rowDto.getEmailRaw())
                                .reason("Invalid email address format")
                                .build());
                        continue;
                    }
                }

                // Canonical Academic Entity Resolution (Single Domain Service)
                LeadAcademicResolutionResult academicResult;
                try {
                    academicResult = leadAcademicResolver.resolveLeadAcademicMappingsForUpload(
                            rowDto.getCourseRaw(),
                            null,
                            rowDto.getProgramRaw(),
                            programId,
                            rowDto.getCourseTypeRaw(),
                            courseTypeId,
                            importId,
                            displayRowNumber
                    );
                } catch (BadRequestException e) {
                    failedCount++;
                    String errorField = "interestedCourse";
                    if (e.getMessage().contains("PROGRAM_NOT_FOUND") || e.getMessage().contains("COURSE_PROGRAM_MISMATCH")) {
                        errorField = "program";
                    } else if (e.getMessage().contains("COURSE_TYPE")) {
                        errorField = "courseType";
                    }
                    failedRows.add(BulkLeadUploadRowError.builder()
                            .rowNumber(displayRowNumber)
                            .field(errorField)
                            .value(rowDto.getCourseRaw() != null ? rowDto.getCourseRaw() : rowDto.getProgramRaw())
                            .reason(e.getMessage())
                            .build());
                    continue;
                }

                // Resolve Stream (Optional)
                Stream rowStream = selectedStream;
                if (rowDto.getStreamRaw() != null && !rowDto.getStreamRaw().isBlank()) {
                    String cleanStream = rowDto.getStreamRaw().trim();
                    rowStream = streamRepository.findByNameIgnoreCaseAndIsDeletedFalse(cleanStream)
                            .or(() -> streamRepository.findByCodeIgnoreCaseAndIsDeletedFalse(cleanStream))
                            .orElse(null);
                    if (rowStream == null) {
                        failedCount++;
                        log.warn("LEAD_IMPORT_ROW_FAILED\nimportId={}\nrow={}\nfield=stream\nrawValue={}\nerrorCode=STREAM_NOT_FOUND\nmessage=Stream '{}' was not found",
                                importId, displayRowNumber, cleanStream);
                        failedRows.add(BulkLeadUploadRowError.builder()
                                .rowNumber(displayRowNumber)
                                .field("stream")
                                .value(rowDto.getStreamRaw())
                                .reason("Stream '" + rowDto.getStreamRaw() + "' was not found")
                                .build());
                        continue;
                    }
                }

                // Resolve Board (Optional)
                Board rowBoard = selectedBoard;
                if (rowDto.getBoardRaw() != null && !rowDto.getBoardRaw().isBlank()) {
                    String cleanBoard = rowDto.getBoardRaw().trim();
                    rowBoard = boardRepository.findByNameIgnoreCaseAndIsDeletedFalse(cleanBoard)
                            .or(() -> boardRepository.findByCodeIgnoreCaseAndIsDeletedFalse(cleanBoard))
                            .orElse(null);
                    if (rowBoard == null) {
                        failedCount++;
                        log.warn("LEAD_IMPORT_ROW_FAILED\nimportId={}\nrow={}\nfield=board\nrawValue={}\nerrorCode=BOARD_NOT_FOUND\nmessage=Board '{}' was not found",
                                importId, displayRowNumber, cleanBoard);
                        failedRows.add(BulkLeadUploadRowError.builder()
                                .rowNumber(displayRowNumber)
                                .field("board")
                                .value(rowDto.getBoardRaw())
                                .reason("Board '" + rowDto.getBoardRaw() + "' was not found")
                                .build());
                        continue;
                    }
                }

                // Resolve Grade (Optional)
                Grade rowGrade = selectedGrade;
                if (rowDto.getGradeRaw() != null && !rowDto.getGradeRaw().isBlank()) {
                    String cleanGrade = rowDto.getGradeRaw().trim();
                    rowGrade = gradeRepository.findByNameIgnoreCaseAndIsDeletedFalse(cleanGrade)
                            .or(() -> gradeRepository.findByCodeIgnoreCaseAndIsDeletedFalse(cleanGrade))
                            .orElse(null);
                    if (rowGrade == null) {
                        failedCount++;
                        log.warn("LEAD_IMPORT_ROW_FAILED\nimportId={}\nrow={}\nfield=grade\nrawValue={}\nerrorCode=GRADE_NOT_FOUND\nmessage=Grade '{}' was not found",
                                importId, displayRowNumber, cleanGrade);
                        failedRows.add(BulkLeadUploadRowError.builder()
                                .rowNumber(displayRowNumber)
                                .field("grade")
                                .value(rowDto.getGradeRaw())
                                .reason("Grade '" + rowDto.getGradeRaw() + "' was not found")
                                .build());
                        continue;
                    }
                }

                // Resolve Department (Optional)
                Department rowDept = selectedDepartment;
                if (rowDto.getDepartmentRaw() != null && !rowDto.getDepartmentRaw().isBlank()) {
                    String cleanDept = rowDto.getDepartmentRaw().trim();
                    rowDept = departmentRepository.findByNameIgnoreCaseAndIsDeletedFalse(cleanDept)
                            .or(() -> departmentRepository.findByCodeIgnoreCaseAndIsDeletedFalse(cleanDept))
                            .orElse(null);
                    if (rowDept == null) {
                        failedCount++;
                        log.warn("LEAD_IMPORT_ROW_FAILED\nimportId={}\nrow={}\nfield=department\nrawValue={}\nerrorCode=DEPARTMENT_NOT_FOUND\nmessage=Department '{}' was not found",
                                importId, displayRowNumber, cleanDept);
                        failedRows.add(BulkLeadUploadRowError.builder()
                                .rowNumber(displayRowNumber)
                                .field("department")
                                .value(rowDto.getDepartmentRaw())
                                .reason("Department '" + rowDto.getDepartmentRaw() + "' was not found")
                                .build());
                        continue;
                    }
                }

                // Resolve Lead Source (Optional)
                Set<LeadSource> rowSources = selectedLeadSources != null ? new HashSet<>(selectedLeadSources) : new HashSet<>();
                if (rowDto.getLeadSourceRaw() != null && !rowDto.getLeadSourceRaw().isBlank()) {
                    String cleanSource = rowDto.getLeadSourceRaw().trim();
                    LeadSource ls = leadSourceRepository.findByNameIgnoreCaseAndIsDeletedFalse(cleanSource).orElse(null);
                    if (ls == null) {
                        failedCount++;
                        log.warn("LEAD_IMPORT_ROW_FAILED\nimportId={}\nrow={}\nfield=leadSource\nrawValue={}\nerrorCode=LEAD_SOURCE_NOT_FOUND\nmessage=Lead Source '{}' was not found",
                                importId, displayRowNumber, cleanSource);
                        failedRows.add(BulkLeadUploadRowError.builder()
                                .rowNumber(displayRowNumber)
                                .field("leadSource")
                                .value(rowDto.getLeadSourceRaw())
                                .reason("Lead Source '" + rowDto.getLeadSourceRaw() + "' was not found")
                                .build());
                        continue;
                    }
                    rowSources.add(ls);
                }

                // Add to processed phone numbers
                if (normalizedPhone != null) {
                    fileProcessedPhoneSet.add(normalizedPhone);
                    dbPhoneSet.add(normalizedPhone);
                }

                // Build Lead Entity
                String leadCode = generateUniqueLeadCode();
                Lead lead = Lead.builder()
                        .leadCode(leadCode)
                        .fullName(rowDto.getFullNameRaw() != null ? rowDto.getFullNameRaw().trim() : null)
                        .phoneNumber(rowDto.getPhoneNumberRaw() != null ? rowDto.getPhoneNumberRaw().trim() : null)
                        .alternatePhoneNumber(rowDto.getAlternatePhoneNumberRaw() != null && !rowDto.getAlternatePhoneNumberRaw().isBlank() ? rowDto.getAlternatePhoneNumberRaw().trim() : null)
                        .email(rowDto.getEmailRaw() != null && !rowDto.getEmailRaw().isBlank() ? rowDto.getEmailRaw().trim() : null)
                        .city(rowDto.getCityRaw() != null && !rowDto.getCityRaw().isBlank() ? rowDto.getCityRaw().trim() : null)
                        .state(rowDto.getStateRaw() != null && !rowDto.getStateRaw().isBlank() ? rowDto.getStateRaw().trim() : null)
                        .country(rowDto.getCountryRaw() != null && !rowDto.getCountryRaw().isBlank() ? rowDto.getCountryRaw().trim() : null)
                        .sourceDetails(rowDto.getSourceDetailsRaw() != null && !rowDto.getSourceDetailsRaw().isBlank() ? rowDto.getSourceDetailsRaw().trim() : null)
                        .program(academicResult.getProgram())
                        .programs(academicResult.getPrograms())
                        .course(academicResult.getCourse())
                        .courseType(academicResult.getCourseType())
                        .interestedCourses(academicResult.getInterestedCourses())
                        .remarks(rowDto.getRemarksRaw() != null && !rowDto.getRemarksRaw().isBlank() ? rowDto.getRemarksRaw().trim() : null)
                        .leadSources(rowSources)
                        .currentStatus(selectedStatus)
                        .board(rowBoard)
                        .stream(rowStream)
                        .grade(rowGrade)
                        .department(rowDept)
                        .assignedTo(selectedAssignedTo)
                        .createdByUser(currentUser)
                        .active(true)
                        .build();

                // Structured Log: LEAD_ENTITY_MAPPING
                StringBuilder emLog = new StringBuilder("LEAD_ENTITY_MAPPING\nrow=").append(displayRowNumber).append("\n");
                if (lead.getFullName() != null) emLog.append("name=").append(lead.getFullName()).append("\n");
                if (academicResult.getCourse() != null) {
                    emLog.append("courseId=").append(academicResult.getCourse().getId()).append("\n");
                    emLog.append("courseName=").append(academicResult.getCourse().getCourseName()).append("\n");
                }
                if (!academicResult.getPrograms().isEmpty()) {
                    emLog.append("programIds=[").append(academicResult.getPrograms().stream().map(p -> p.getId().toString()).collect(Collectors.joining(", "))).append("]\n");
                    emLog.append("programNames=[").append(academicResult.getPrograms().stream().map(Program::getName).collect(Collectors.joining(", "))).append("]\n");
                }
                if (academicResult.getCourseType() != null) {
                    emLog.append("courseTypeId=").append(academicResult.getCourseType().getId()).append("\n");
                    emLog.append("courseTypeName=").append(academicResult.getCourseType().getName()).append("\n");
                }
                if (!rowSources.isEmpty()) {
                    emLog.append("sourceId=").append(rowSources.stream().map(s -> s.getId().toString()).collect(Collectors.joining(", "))).append("\n");
                    emLog.append("sourceName=").append(rowSources.stream().map(LeadSource::getName).collect(Collectors.joining(", "))).append("\n");
                }
                if (rowGrade != null) {
                    emLog.append("gradeId=").append(rowGrade.getId()).append("\n");
                    emLog.append("gradeName=").append(rowGrade.getName()).append("\n");
                }
                if (rowBoard != null) {
                    emLog.append("boardId=").append(rowBoard.getId()).append("\n");
                    emLog.append("boardName=").append(rowBoard.getName()).append("\n");
                }
                if (rowStream != null) {
                    emLog.append("streamId=").append(rowStream.getId()).append("\n");
                    emLog.append("streamName=").append(rowStream.getName()).append("\n");
                }
                log.info(emLog.toString().trim());

                // Structured Log: LEAD_ENTITY_BEFORE_SAVE
                log.info("LEAD_ENTITY_BEFORE_SAVE\nrow={}\nleadId={}\ncourseId={}\nprogramIds=[{}]\ncourseTypeId={}\ncourseTypeName={}",
                        displayRowNumber,
                        "null",
                        academicResult.getCourse() != null ? academicResult.getCourse().getId().toString() : "",
                        academicResult.getPrograms().stream().map(p -> p.getId().toString()).collect(Collectors.joining(", ")),
                        academicResult.getCourseType() != null ? academicResult.getCourseType().getId().toString() : "",
                        academicResult.getCourseType() != null ? academicResult.getCourseType().getName() : "");

                // Database Persistence & Flush
                Lead saved = leadRepository.save(lead);
                leadRepository.flush();

                // Reload from DB & Database-Level Relationship Verification
                Lead reloaded = leadRepository.findById(saved.getId()).orElse(saved);

                // --- Course Verification ---
                UUID expectedCourseId = academicResult.getCourse() != null ? academicResult.getCourse().getId() : null;
                UUID actualCourseId = null;
                try {
                    actualCourseId = leadRepository.findCourseIdByLeadId(saved.getId());
                } catch (Exception ignored) {}
                if (actualCourseId == null && reloaded.getCourse() != null) {
                    actualCourseId = reloaded.getCourse().getId();
                }
                boolean courseMatch = Objects.equals(expectedCourseId, actualCourseId);

                // --- Program Verification ---
                Set<UUID> expectedProgramIds = academicResult.getPrograms().stream().map(Program::getId).collect(Collectors.toSet());
                Set<UUID> actualProgramIds = new HashSet<>();
                try {
                    List<UUID> dbProgIds = leadRepository.findProgramIdsByLeadId(saved.getId());
                    if (dbProgIds != null) actualProgramIds.addAll(dbProgIds);
                } catch (Exception ignored) {}
                if (actualProgramIds.isEmpty() && reloaded.getPrograms() != null) {
                    actualProgramIds = reloaded.getPrograms().stream().map(Program::getId).collect(Collectors.toSet());
                }
                boolean programMatch = expectedProgramIds.equals(actualProgramIds);

                // --- CourseType Verification (DB-level via native query) ---
                UUID expectedCourseTypeId = academicResult.getCourseType() != null ? academicResult.getCourseType().getId() : null;
                UUID actualCourseTypeId = null;
                try {
                    actualCourseTypeId = leadRepository.findCourseTypeIdByLeadId(saved.getId());
                } catch (Exception ignored) {}
                if (actualCourseTypeId == null && reloaded.getCourseType() != null) {
                    actualCourseTypeId = reloaded.getCourseType().getId();
                }
                boolean courseTypeMatch = Objects.equals(expectedCourseTypeId, actualCourseTypeId);

                log.info("LEAD_IMPORT_PERSISTENCE_VERIFICATION\nrow={}\nleadId={}\nexpectedCourseId={}\nactualCourseId={}\ncourseMatch={}\nexpectedProgramIds=[{}]\nactualProgramIds=[{}]\nprogramMatch={}\nexpectedCourseTypeId={}\nactualCourseTypeId={}\ncourseTypeMatch={}",
                        displayRowNumber,
                        saved.getId(),
                        expectedCourseId != null ? expectedCourseId.toString() : "",
                        actualCourseId != null ? actualCourseId.toString() : "",
                        courseMatch,
                        expectedProgramIds.stream().map(UUID::toString).collect(Collectors.joining(", ")),
                        actualProgramIds.stream().map(UUID::toString).collect(Collectors.joining(", ")),
                        programMatch,
                        expectedCourseTypeId != null ? expectedCourseTypeId.toString() : "",
                        actualCourseTypeId != null ? actualCourseTypeId.toString() : "",
                        courseTypeMatch);

                if (!courseMatch || !programMatch || !courseTypeMatch) {
                    String failedFields = (!courseMatch ? "Course " : "") + (!programMatch ? "Program " : "") + (!courseTypeMatch ? "CourseType" : "");
                    log.error("LEAD_IMPORT_ROW_FAILED\nimportId={}\nrow={}\nerrorCode=PERSISTENCE_VERIFICATION_FAILED\nfailedFields={}\nmessage=Entity relationships failed database verification",
                            importId, displayRowNumber, failedFields.trim());
                    failedCount++;
                    failedRows.add(BulkLeadUploadRowError.builder()
                            .rowNumber(displayRowNumber)
                            .field("persistence")
                            .value(failedFields.trim())
                            .reason("PERSISTENCE_VERIFICATION_FAILED: [" + failedFields.trim() + "] relationship(s) were not persisted in database")
                            .build());
                    continue;
                }

                // Structured Log: LEAD_IMPORT_SAVE_SUCCESS (post-verification)
                log.info("LEAD_IMPORT_SAVE_SUCCESS\nimportId={}\nrow={}\nleadId={}\ncourseId={}\nprogramIds=[{}]\ncourseTypeId={}\ncourseTypeName={}",
                        importId,
                        displayRowNumber,
                        saved.getId(),
                        actualCourseId != null ? actualCourseId.toString() : "",
                        actualProgramIds.stream().map(UUID::toString).collect(Collectors.joining(", ")),
                        actualCourseTypeId != null ? actualCourseTypeId.toString() : "",
                        reloaded.getCourseType() != null ? reloaded.getCourseType().getName() : "");

                // Increment verified success counters (only after DB-level verification passes)
                successCount++;
                if (academicResult.getCourse() != null) courseResolvedCount++;
                if (!academicResult.getPrograms().isEmpty()) programResolvedCount++;
                if (academicResult.getCourseType() != null) courseTypeResolvedCount++;
                if ("COURSE".equals(academicResult.getProgramSource())) programAutoMappedFromCourseCount++;
                if ("COURSE".equals(academicResult.getCourseTypeSource())) courseTypeAutoMappedFromCourseCount++;

                // Record initial status history
                LeadStatusHistory history = LeadStatusHistory.builder()
                        .lead(saved)
                        .previousStatus(null)
                        .newStatus(selectedStatus)
                        .changedByUser(currentUser)
                        .feedback("Lead registered via Bulk Upload (" + importId + ").")
                        .build();
                leadStatusHistoryRepository.save(history);
            }

        } catch (Exception e) {
            log.error("Failed to parse Excel file for bulk lead upload", e);
            if (e instanceof BadRequestException) {
                throw (BadRequestException) e;
            }
            throw new BadRequestException("Error processing Excel file: " + e.getMessage());
        }

        // Structured Log: LEAD_BULK_IMPORT_COMPLETED
        log.info("LEAD_BULK_IMPORT_COMPLETED\nimportId={}\ntotalRows={}\nsuccessfulRows={}\nfailedRows={}\ncourseResolved={}\nprogramResolved={}\ncourseTypeResolved={}\nprogramAutoMappedFromCourse={}\ncourseTypeAutoMappedFromCourse={}",
                importId,
                totalRows,
                successCount,
                failedCount,
                courseResolvedCount,
                programResolvedCount,
                courseTypeResolvedCount,
                programAutoMappedFromCourseCount,
                courseTypeAutoMappedFromCourseCount);

        return BulkLeadUploadResponse.builder()
                .totalRows(totalRows)
                .successCount(successCount)
                .failedCount(failedCount)
                .duplicateCount(duplicateCount)
                .skippedCount(skippedCount)
                .failedRows(failedRows)
                .build();
    }

    private List<BulkUploadMappingItemDTO> parseMappingJson(String mappingJson) {
        if (mappingJson == null || mappingJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            com.fasterxml.jackson.databind.JsonNode root = OBJECT_MAPPER.readTree(mappingJson);
            if (root.isArray()) {
                return OBJECT_MAPPER.convertValue(root, new TypeReference<List<BulkUploadMappingItemDTO>>() {});
            } else if (root.isObject() && root.has("mapping") && root.get("mapping").isArray()) {
                return OBJECT_MAPPER.convertValue(root.get("mapping"), new TypeReference<List<BulkUploadMappingItemDTO>>() {});
            }
        } catch (Exception e) {
            log.warn("Failed to parse bulk upload mapping JSON: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    private String normalizeTargetFieldName(String target) {
        if (target == null || target.isBlank()) return null;
        String rawTrimmed = target.trim();
        if (SUPPORTED_CANONICAL_TARGET_FIELDS.contains(rawTrimmed)) {
            return rawTrimmed;
        }
        String norm = BulkUploadHeaderNormalizer.normalize(rawTrimmed);
        if (norm.equals("name") || norm.equals("fullname") || norm.equals("studentname") || norm.equals("student name") || norm.equals("candidate name")) {
            return "fullName";
        }
        if (norm.equals("mobile") || norm.equals("phone") || norm.equals("phonenumber") || norm.equals("mobilenumber") || norm.equals("phone number") || norm.equals("contact") || norm.equals("contact number") || norm.equals("contact no")) {
            return "phoneNumber";
        }
        if (norm.equals("altphone") || norm.equals("alternatephone") || norm.equals("alternatephonenumber") || norm.equals("alternate mobile") || norm.equals("alt mobile") || norm.equals("alternate phone") || norm.equals("alternate phone number")) {
            return "alternatePhoneNumber";
        }
        if (norm.equals("email") || norm.equals("emailid") || norm.equals("email address") || norm.equals("mail")) {
            return "email";
        }
        if (norm.equals("course") || norm.equals("interestedcourse") || norm.equals("courseinterested") || norm.equals("interested course") || norm.equals("course interested") || norm.equals("course name") || norm.equals("course code") || norm.equals("courseid")) {
            return "course";
        }
        if (norm.equals("program") || norm.equals("programs") || norm.equals("school") || norm.equals("faculty") || norm.equals("institute") || norm.equals("program name") || norm.equals("program code") || norm.equals("programid") || norm.equals("programids")) {
            return "program";
        }
        if (norm.equals("coursetype") || norm.equals("course type") || norm.equals("degree type") || norm.equals("type of course") || norm.equals("coursetypeid")) {
            return "courseType";
        }
        if (norm.equals("leadsource") || norm.equals("source") || norm.equals("lead source") || norm.equals("source name") || norm.equals("leadsourceid")) {
            return "leadSource";
        }
        if (norm.equals("sourcedetails") || norm.equals("source note") || norm.equals("source details") || norm.equals("campaign") || norm.equals("event")) {
            return "sourceDetails";
        }
        if (norm.equals("board") || norm.equals("education board") || norm.equals("board name")) return "board";
        if (norm.equals("grade") || norm.equals("class") || norm.equals("standard") || norm.equals("grade name")) return "grade";
        if (norm.equals("stream") || norm.equals("stream name") || norm.equals("discipline")) return "stream";
        if (norm.equals("department") || norm.equals("dept") || norm.equals("department name") || norm.equals("dept name")) return "department";
        if (norm.equals("city") || norm.equals("town")) return "city";
        if (norm.equals("state") || norm.equals("province")) return "state";
        if (norm.equals("country") || norm.equals("nation")) return "country";
        if (norm.equals("remarks") || norm.equals("remark") || norm.equals("notes") || norm.equals("note") || norm.equals("comment") || norm.equals("comments")) {
            return "remarks";
        }
        return null;
    }

    private String getRawValueByFieldKey(LeadBulkUploadRowDTO rowDto, String targetKey) {
        if (rowDto == null || targetKey == null) return null;
        switch (targetKey) {
            case "fullName": return rowDto.getFullNameRaw();
            case "phoneNumber": return rowDto.getPhoneNumberRaw();
            case "alternatePhoneNumber": return rowDto.getAlternatePhoneNumberRaw();
            case "email": return rowDto.getEmailRaw();
            case "course": return rowDto.getCourseRaw();
            case "program": return rowDto.getProgramRaw();
            case "courseType": return rowDto.getCourseTypeRaw();
            case "leadSource": return rowDto.getLeadSourceRaw();
            case "sourceDetails": return rowDto.getSourceDetailsRaw();
            case "board": return rowDto.getBoardRaw();
            case "grade": return rowDto.getGradeRaw();
            case "stream": return rowDto.getStreamRaw();
            case "department": return rowDto.getDepartmentRaw();
            case "city": return rowDto.getCityRaw();
            case "state": return rowDto.getStateRaw();
            case "country": return rowDto.getCountryRaw();
            case "remarks": return rowDto.getRemarksRaw();
            default: return null;
        }
    }

    private void validateFile(MultipartFile file) throws BadRequestException {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Please select an Excel file to upload");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || (!originalFilename.endsWith(".xlsx") && !originalFilename.endsWith(".xls"))) {
            throw new BadRequestException("Invalid file format. Only Excel files (.xlsx, .xls) are allowed");
        }
    }

    private User getCurrentUserEntity() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return userRepository.findByUsername(auth.getName()).orElse(null);
    }

    private Program validateAndFetchProgram(UUID programId) throws BadRequestException {
        if (programId == null) return null;
        Program program = programRepository.findById(programId)
                .filter(p -> !p.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Selected Program not found with ID: " + programId));
        if (!program.isActive()) {
            throw new BadRequestException("Selected Program '" + program.getName() + "' is inactive");
        }
        return program;
    }

    private CourseType validateAndFetchCourseType(UUID courseTypeId) throws BadRequestException {
        if (courseTypeId == null) return null;
        CourseType courseType = courseTypeRepository.findById(courseTypeId)
                .filter(ct -> !ct.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Selected Course Type not found with ID: " + courseTypeId));
        if (!courseType.isActive()) {
            throw new BadRequestException("Selected Course Type '" + courseType.getName() + "' is inactive");
        }
        return courseType;
    }

    private Stream validateAndFetchStream(UUID streamId) throws BadRequestException {
        if (streamId == null) return null;
        Stream stream = streamRepository.findById(streamId)
                .filter(s -> !s.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Selected Stream not found with ID: " + streamId));
        if (!stream.isActive()) {
            throw new BadRequestException("Selected Stream '" + stream.getName() + "' is inactive");
        }
        return stream;
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

    @Override
    public byte[] downloadTemplate() {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet uploadSheet = workbook.createSheet("Lead Upload");
            uploadSheet.createFreezePane(0, 1);

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            List<LeadBulkUploadColumnDefinition> columns = LeadBulkUploadColumnDefinition.getAllColumns();

            Row headerRow = uploadSheet.createRow(0);
            headerRow.setHeightInPoints(25);

            for (int i = 0; i < columns.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns.get(i).getHeaderName());
                cell.setCellStyle(headerStyle);
            }

            for (int i = 0; i < columns.size(); i++) {
                uploadSheet.autoSizeColumn(i);
                int currentWidth = uploadSheet.getColumnWidth(i);
                uploadSheet.setColumnWidth(i, Math.max(currentWidth + 1200, 5000));
            }

            Sheet instructionSheet = workbook.createSheet("Instructions");

            CellStyle instHeaderStyle = workbook.createCellStyle();
            Font instHeaderFont = workbook.createFont();
            instHeaderFont.setBold(true);
            instHeaderFont.setColor(IndexedColors.WHITE.getIndex());
            instHeaderStyle.setFont(instHeaderFont);
            instHeaderStyle.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
            instHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            instHeaderStyle.setAlignment(HorizontalAlignment.LEFT);

            CellStyle boldStyle = workbook.createCellStyle();
            Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            boldStyle.setFont(boldFont);

            int rowIdx = 0;

            Row titleRow = instructionSheet.createRow(rowIdx++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("LEAD BULK UPLOAD INSTRUCTIONS & BUSINESS RULES");
            titleCell.setCellStyle(instHeaderStyle);

            rowIdx++;

            Row sec1Title = instructionSheet.createRow(rowIdx++);
            Cell sec1Cell = sec1Title.createCell(0);
            sec1Cell.setCellValue("1. OPTIONAL FIELDS & AUTOMATIC MAPPINGS");
            sec1Cell.setCellStyle(boldStyle);

            String[] rules = {
                    "• All Lead fields are strictly OPTIONAL.",
                    "• Missing Excel cells or columns will be mapped to null without failing the upload.",
                    "• When 'Interested Course' is provided without 'Program', the Program is automatically derived from the Course.",
                    "• Course Type is also automatically mapped from the canonical Course relationship.",
                    "• If both Course and Program are provided, they are validated against each other.",
                    "• Course Type is independent and can be supplied without Course or Program."
            };

            for (String rule : rules) {
                Row r = instructionSheet.createRow(rowIdx++);
                r.createCell(0).setCellValue(rule);
            }

            instructionSheet.autoSizeColumn(0);
            workbook.write(out);
            return out.toByteArray();

        } catch (Exception e) {
            log.error("Failed to generate lead bulk upload template", e);
            throw new RuntimeException("Could not generate Excel template", e);
        }
    }
}
