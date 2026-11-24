package com.vns.healthcare.service;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import com.vns.healthcare.entity.BusinessBankStatement;
import com.vns.healthcare.entity.BusinessBankStatementTable;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

@Service
public class BankStatementExportService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    public byte[] exportExcel(BusinessBankStatementTable table) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(safeSheetName(table.getTableName()));
            sheet.setColumnWidth(0, 3000);
            sheet.setColumnWidth(1, 7000);
            sheet.setColumnWidth(2, 4000);
            sheet.setColumnWidth(3, 4000);
            sheet.setColumnWidth(4, 5000);
            sheet.setColumnWidth(5, 8000);
            sheet.setColumnWidth(6, 9000);

            org.apache.poi.ss.usermodel.Font boldFont = workbook.createFont();
            boldFont.setBold(true);

            org.apache.poi.ss.usermodel.Font titleFontExcel = workbook.createFont();
            titleFontExcel.setBold(true);
            titleFontExcel.setFontHeightInPoints((short) 14);

            CellStyle borderedStyle = workbook.createCellStyle();
            applyBorders(borderedStyle);

            CellStyle titleStyle = workbook.createCellStyle();
            titleStyle.setFont(titleFontExcel);
            applyBorders(titleStyle);

            CellStyle openingStyle = workbook.createCellStyle();
            openingStyle.setFont(boldFont);
            applyBorders(openingStyle);

            Row title = sheet.createRow(0);
            title.createCell(0).setCellValue(table.getTableName());
            title.getCell(0).setCellStyle(titleStyle);
            Row opening = sheet.createRow(1);
            opening.createCell(0).setCellValue("Opening Balance");
            opening.createCell(4).setCellValue(number(table.getOpeningBalance()));
            opening.getCell(0).setCellStyle(openingStyle);
            opening.getCell(4).setCellStyle(openingStyle);
            sheet.createFreezePane(0, 4);

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(boldFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            applyBorders(headerStyle);

            CellStyle bodyStyle = workbook.createCellStyle();
            applyBorders(bodyStyle);

            Row header = sheet.createRow(3);
            String[] headers = {"Sr No", "Date", "Transaction", "Credit", "Debit", "Current Balance", "Description"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
                header.getCell(i).setCellStyle(headerStyle);
            }

            int rowIndex = 4;
            int srNo = 1;
            for (BusinessBankStatement statement : table.getRows()) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(srNo++);
                row.createCell(1).setCellValue(statement.getTransactionDate() == null ? "" : DATE_FORMAT.format(statement.getTransactionDate()));
                row.createCell(2).setCellValue(statement.getTransaction() == null ? "" : statement.getTransaction());
                row.createCell(3).setCellValue(statement.getCredit() == null ? 0 : statement.getCredit().doubleValue());
                row.createCell(4).setCellValue(statement.getDebit() == null ? 0 : statement.getDebit().doubleValue());
                row.createCell(5).setCellValue(statement.getCurrentBalance() == null ? 0 : statement.getCurrentBalance().doubleValue());
                row.createCell(6).setCellValue(statement.getDescription() == null ? "" : statement.getDescription());
                for (int i = 0; i < 7; i++) {
                    row.getCell(i).setCellStyle(bodyStyle);
                }
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    public byte[] exportPdf(BusinessBankStatementTable table) throws Exception {
        Document document = new Document(PageSize.A4.rotate(), 36, 36, 36, 36);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PdfWriter.getInstance(document, output);
        document.open();

        Font titleFont = new Font(Font.FontFamily.HELVETICA, 16, Font.BOLD);
        com.itextpdf.text.Paragraph title = new com.itextpdf.text.Paragraph(table.getTableName(), titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        document.add(title);

        com.itextpdf.text.Paragraph opening = new com.itextpdf.text.Paragraph("Opening Balance: " + number(table.getOpeningBalance()));
        opening.setSpacingAfter(10);
        document.add(opening);

        PdfPTable pdfTable = new PdfPTable(7);
        pdfTable.setWidthPercentage(100);
        pdfTable.setWidths(new float[]{45, 75, 150, 75, 75, 90, 180});
        String[] headers = {"Sr No", "Date", "Transaction", "Credit", "Debit", "Current Balance", "Description"};
        Font pdfHeaderFont = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD);
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, pdfHeaderFont));
            cell.setBackgroundColor(BaseColor.LIGHT_GRAY);
            pdfTable.addCell(cell);
        }

        int srNo = 1;
        for (BusinessBankStatement statement : table.getRows()) {
            pdfTable.addCell(String.valueOf(srNo++));
            pdfTable.addCell(statement.getTransactionDate() == null ? "" : DATE_FORMAT.format(statement.getTransactionDate()));
            pdfTable.addCell(statement.getTransaction() == null ? "" : statement.getTransaction());
            pdfTable.addCell(statement.getCredit() == null ? "" : number(statement.getCredit()));
            pdfTable.addCell(statement.getDebit() == null ? "" : number(statement.getDebit()));
            pdfTable.addCell(statement.getCurrentBalance() == null ? "" : number(statement.getCurrentBalance()));
            pdfTable.addCell(statement.getDescription() == null ? "" : statement.getDescription());
        }
        document.add(pdfTable);
        document.close();
        return output.toByteArray();
    }

    private void applyBorders(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    private String safeSheetName(String name) {
        String safe = name == null ? "Bank Statement" : name.replaceAll("[\\\\/*?:\\[\\]]", " ").trim();
        if (safe.isEmpty()) safe = "Bank Statement";
        return safe.length() <= 31 ? safe : safe.substring(0, 31);
    }

    private String number(BigDecimal value) {
        return value == null ? "0.00" : value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
