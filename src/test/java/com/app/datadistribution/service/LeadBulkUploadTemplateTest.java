package com.app.datadistribution.service;

import static org.junit.jupiter.api.Assertions.*;

import com.app.datadistribution.dto.lead.LeadBulkUploadColumnDefinition;
import com.app.datadistribution.service.impl.LeadBulkUploadServiceImpl;
import java.io.ByteArrayInputStream;
import java.util.List;
import org.apache.poi.ss.usermodel.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class LeadBulkUploadTemplateTest {

    @InjectMocks
    private LeadBulkUploadServiceImpl leadBulkUploadService;

    @Test
    public void testDownloadTemplate_GeneratesValidTwoSheetWorkbook() throws Exception {
        byte[] templateBytes = leadBulkUploadService.downloadTemplate();
        assertNotNull(templateBytes);
        assertTrue(templateBytes.length > 0);

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(templateBytes))) {
            assertEquals(2, workbook.getNumberOfSheets());

            // 1. Verify Sheet 1: Lead Upload
            Sheet uploadSheet = workbook.getSheetAt(0);
            assertEquals("Lead Upload", uploadSheet.getSheetName());

            Row headerRow = uploadSheet.getRow(0);
            assertNotNull(headerRow);

            List<LeadBulkUploadColumnDefinition> columns = LeadBulkUploadColumnDefinition.getAllColumns();
            assertEquals(columns.size(), headerRow.getLastCellNum());

            DataFormatter formatter = new DataFormatter();
            for (int i = 0; i < columns.size(); i++) {
                Cell cell = headerRow.getCell(i);
                assertNotNull(cell);
                assertEquals(columns.get(i).getHeaderName(), formatter.formatCellValue(cell).trim());
            }

            // Verify no fake sample data rows in upload sheet
            assertEquals(0, uploadSheet.getLastRowNum(), "Upload sheet should only contain header row (row index 0)");

            // 2. Verify Sheet 2: Instructions
            Sheet instructionSheet = workbook.getSheetAt(1);
            assertEquals("Instructions", instructionSheet.getSheetName());
            assertTrue(instructionSheet.getPhysicalNumberOfRows() > 5);

            String firstTitle = formatter.formatCellValue(instructionSheet.getRow(0).getCell(0));
            assertTrue(firstTitle.contains("LEAD BULK UPLOAD INSTRUCTIONS"));
        }
    }

    @Test
    public void testColumnDefinition_Integrity() {
        List<LeadBulkUploadColumnDefinition> columns = LeadBulkUploadColumnDefinition.getAllColumns();
        assertEquals(10, columns.size());

        assertTrue(LeadBulkUploadColumnDefinition.FULL_NAME.isRequired());
        assertTrue(LeadBulkUploadColumnDefinition.PHONE_NUMBER.isRequired());
        assertFalse(LeadBulkUploadColumnDefinition.EMAIL.isRequired());

        assertEquals("Full Name *", LeadBulkUploadColumnDefinition.FULL_NAME.getHeaderName());
        assertEquals("fullName", LeadBulkUploadColumnDefinition.FULL_NAME.getFieldKey());
    }
}
