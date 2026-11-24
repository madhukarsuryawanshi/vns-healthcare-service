package com.vns.healthcare.service;

import com.vns.healthcare.entity.BusinessBankStatement;
import com.vns.healthcare.entity.BusinessBankStatementTable;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BankStatementExportServiceTest {

    private final BankStatementExportService service = new BankStatementExportService();

    @Test
    void excelHeadersAreBoldGrayAndBordered() throws Exception {
        BusinessBankStatementTable table = sampleTable();
        byte[] bytes = service.exportExcel(table);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheetAt(0);
            var header = sheet.getRow(3);

            for (int i = 0; i < 7; i++) {
                var style = header.getCell(i).getCellStyle();
                assertTrue(workbook.getFontAt(style.getFontIndex()).getBold());
                assertEquals(IndexedColors.GREY_25_PERCENT.getIndex(), style.getFillForegroundColor());
                assertEquals(BorderStyle.THIN, style.getBorderTop());
                assertEquals(BorderStyle.THIN, style.getBorderBottom());
                assertEquals(BorderStyle.THIN, style.getBorderLeft());
                assertEquals(BorderStyle.THIN, style.getBorderRight());
            }
        }
    }

    @Test
    void excelColumnsUseCompactCappedWidths() throws Exception {
        byte[] bytes = service.exportExcel(sampleTable());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheetAt(0);
            assertTrue(sheet.getColumnWidth(0) <= 1800);
            assertTrue(sheet.getColumnWidth(6) <= 10000);
        }
    }

    @Test
    void monthExcelContainsASeparateSheetForEachTable() throws Exception {
        BusinessBankStatementTable first = sampleTable();
        BusinessBankStatementTable second = new BusinessBankStatementTable();
        second.setTableName("VNS Healthcare");
        second.setOpeningBalance(new BigDecimal("500.00"));

        byte[] bytes = service.exportMonthExcel("November-2026", java.util.List.of(first, second));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertEquals(2, workbook.getNumberOfSheets());
            assertEquals("November Statement", workbook.getSheetAt(0).getSheetName());
            assertEquals("VNS Healthcare", workbook.getSheetAt(1).getSheetName());
        }
    }

    @Test
    void monthPdfStartsWithPdfHeaderAndContainsMonthTitle() throws Exception {
        byte[] bytes = service.exportMonthPdf("November-2026", java.util.List.of(sampleTable()));

        assertTrue(bytes.length > 4);
        assertEquals('%', (char) bytes[0]);
        assertEquals('P', (char) bytes[1]);
        assertEquals('D', (char) bytes[2]);
        assertEquals('F', (char) bytes[3]);
    }

    @Test
    void pdfExportStartsWithPdfHeader() throws Exception {
        byte[] bytes = service.exportPdf(sampleTable());

        assertTrue(bytes.length > 4);
        assertEquals('%', (char) bytes[0]);
        assertEquals('P', (char) bytes[1]);
        assertEquals('D', (char) bytes[2]);
        assertEquals('F', (char) bytes[3]);
    }

    private BusinessBankStatementTable sampleTable() {
        BusinessBankStatementTable table = new BusinessBankStatementTable();
        table.setTableName("November Statement");
        table.setOpeningBalance(new BigDecimal("100000.00"));

        BusinessBankStatement row = new BusinessBankStatement();
        row.setStatementTable(table);
        row.setTransactionDate(LocalDate.of(2026, 11, 1));
        row.setTransaction("Deposit");
        row.setCredit(new BigDecimal("25000.00"));
        row.setCurrentBalance(new BigDecimal("125000.00"));
        row.setDescription("Test transaction");
        table.getRows().add(row);
        return table;
    }
}
