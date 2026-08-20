package com.app.datadistribution.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.app.datadistribution.dto.lead.BulkLeadUploadResponse;
import com.app.datadistribution.entity.LeadStatus;
import com.app.datadistribution.entity.User;
import com.app.datadistribution.repository.*;
import com.app.datadistribution.service.impl.LeadBulkUploadServiceImpl;
import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.Optional;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class LeadBulkUploadServiceImplTest {

    @Mock
    private LeadRepository leadRepository;
    @Mock
    private LeadSourceRepository leadSourceRepository;
    @Mock
    private LeadStatusRepository leadStatusRepository;
    @Mock
    private BoardRepository boardRepository;
    @Mock
    private GradeRepository gradeRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CourseTypeRepository courseTypeRepository;
    @Mock
    private LeadStatusHistoryRepository leadStatusHistoryRepository;

    @InjectMocks
    private LeadBulkUploadServiceImpl bulkUploadService;

    @Test
    void testDownloadTemplate_ReturnsValidBytes() {
        byte[] templateBytes = bulkUploadService.downloadTemplate();
        assertNotNull(templateBytes);
        assertTrue(templateBytes.length > 0);
    }

    @Test
    void testBulkUpload_Success() throws Exception {
        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        lenient().when(authentication.isAuthenticated()).thenReturn(true);
        lenient().when(authentication.getName()).thenReturn("admin");
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        User user = User.builder().username("admin").active(true).build();
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));

        LeadStatus status = LeadStatus.builder().name("Raw").code("RAW").active(true).build();
        when(leadStatusRepository.findByCodeIgnoreCase("RAW")).thenReturn(Optional.of(status));
        when(leadRepository.findAllActivePhoneNumbers()).thenReturn(Collections.emptyList());

        // Create Excel in memory
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Leads");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Full Name");
            header.createCell(1).setCellValue("Phone Number");
            header.createCell(2).setCellValue("Email");

            Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("Alice Brown");
            row1.createCell(1).setCellValue("+919988776655");
            row1.createCell(2).setCellValue("alice@example.com");

            workbook.write(out);
        }

        MockMultipartFile file = new MockMultipartFile(
                "file", "leads.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                out.toByteArray()
        );

        when(leadRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BulkLeadUploadResponse response = bulkUploadService.bulkUploadLeads(
                file, null, null, null, null, null, null, null, null
        );

        assertNotNull(response);
        assertEquals(1, response.getTotalRows());
        assertEquals(1, response.getSuccessCount());
        assertEquals(0, response.getFailedCount());
        assertEquals(0, response.getDuplicateCount());
    }
}
