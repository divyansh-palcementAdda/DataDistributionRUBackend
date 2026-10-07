package com.app.datadistribution.common.bulkupload;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;

public final class ExcelCellReader {

    private static final DataFormatter FORMATTER = new DataFormatter();

    private ExcelCellReader() {
    }

    /**
     * Safely reads cell value from row according to cell type.
     * Prevents scientific notation (e.g. 9.87654321E9) and unwanted decimals (.0) for phone numbers and IDs.
     */
    public static String readCellValue(Row row, Integer columnIndex) {
        if (row == null || columnIndex == null || columnIndex < 0) {
            return null;
        }

        Cell cell = row.getCell(columnIndex);
        if (cell == null) {
            return null;
        }

        CellType cellType = cell.getCellType();
        if (cellType == CellType.FORMULA) {
            cellType = cell.getCachedFormulaResultType();
        }

        switch (cellType) {
            case STRING:
                try {
                    String str = cell.getStringCellValue();
                    if (str != null && !str.isBlank()) {
                        return str.trim();
                    }
                } catch (Exception ignored) {
                }
                String formattedStr = FORMATTER.formatCellValue(cell);
                return (formattedStr != null && !formattedStr.isBlank()) ? formattedStr.trim() : null;

            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    try {
                        return new SimpleDateFormat("yyyy-MM-dd").format(cell.getDateCellValue());
                    } catch (Exception e) {
                        return FORMATTER.formatCellValue(cell).trim();
                    }
                } else {
                    double num = cell.getNumericCellValue();
                    // If whole integer (e.g. phone number 9876543210.0 or 1.0)
                    if (num == Math.floor(num) && !Double.isInfinite(num)) {
                        return BigDecimal.valueOf((long) num).toPlainString();
                    }
                    return BigDecimal.valueOf(num).stripTrailingZeros().toPlainString();
                }

            case BOOLEAN:
                return Boolean.toString(cell.getBooleanCellValue());

            case BLANK:
            case _NONE:
            case ERROR:
            default:
                try {
                    String formatted = FORMATTER.formatCellValue(cell);
                    return (formatted != null && !formatted.isBlank()) ? formatted.trim() : null;
                } catch (Exception ignored) {
                    return null;
                }
        }
    }

    public static boolean isRowEmpty(Row row) {
        if (row == null) {
            return true;
        }
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String val = readCellValue(row, c);
                if (val != null && !val.isBlank()) {
                    return false;
                }
            }
        }
        return true;
    }
}
