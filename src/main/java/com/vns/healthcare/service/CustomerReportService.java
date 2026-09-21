package com.vns.healthcare.service;

import com.vns.healthcare.entity.Customer;
import com.vns.healthcare.entity.CustomerDuty;
import com.vns.healthcare.exception.BusinessException;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class CustomerReportService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final CustomerService customerService;
    private final CustomerDutyService dutyService;

    public CustomerReportService(CustomerService customerService, CustomerDutyService dutyService) {
        this.customerService = customerService;
        this.dutyService = dutyService;
    }

    @Transactional(readOnly = true)
    public byte[] buildExcel(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new BusinessException("Choose a from and to date for the report");
        }
        if (from.isAfter(to)) {
            throw new BusinessException("From date cannot be after to date");
        }
        if (from.plusDays(366).isBefore(to)) {
            throw new BusinessException("Report range cannot be more than 366 days");
        }

        List<LocalDate> days = dutyService.daysInRange(from, to);
        List<Customer> customers = customerService.list(null);
        Map<String, CustomerDuty> duties = dutyService.indexInRange(from, to);
        int daysForRate = dutyService.payDaysDivisor(from, to);

        try {
            Workbook workbook = new XSSFWorkbook();
            writeSheet(workbook, from, to, days, customers, duties, daysForRate);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            workbook.close();
            return out.toByteArray();
        } catch (IOException ex) {
            throw new BusinessException("Could not create the customer report");
        }
    }

    private void writeSheet(Workbook workbook, LocalDate from, LocalDate to, List<LocalDate> days,
                            List<Customer> customers, Map<String, CustomerDuty> duties, int daysForRate) {
        Sheet sheet = workbook.createSheet("Customer charges");
        CellStyle titleStyle = titleStyle(workbook);
        CellStyle headerStyle = headerStyle(workbook);
        CellStyle cellStyle = bodyStyle(workbook);
        CellStyle centerStyle = centerStyle(workbook);
        CellStyle moneyStyle = moneyStyle(workbook);
        CellStyle holdStyle = holdStyle(workbook);
        CellStyle[] employeeBlockStyles = new CellStyle[] {
                employeeBlockStyle(workbook, IndexedColors.LIGHT_GREEN),
                employeeBlockStyle(workbook, IndexedColors.LIGHT_TURQUOISE),
                employeeBlockStyle(workbook, IndexedColors.LIGHT_YELLOW),
                employeeBlockStyle(workbook, IndexedColors.LIGHT_ORANGE),
                employeeBlockStyle(workbook, IndexedColors.LIGHT_BLUE)
        };

        int totalPresentCol = 3 + days.size();
        int totalCol = totalPresentCol + 1;
        int r = 0;
        Row title = sheet.createRow(r++);
        Cell titleCell = title.createCell(0);
        titleCell.setCellValue("VNS Healthcare — Customer duty report");
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, totalCol));

        Row period = sheet.createRow(r++);
        period.createCell(0).setCellValue(
                "Period: " + from.format(DATE_FMT) + " to " + to.format(DATE_FMT)
                        + "  |  Per-day charge = monthly charges / " + daysForRate
                        + " days  |  Hold days are not billed");
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, totalCol));

        r++;
        Row header = sheet.createRow(r++);
        String[] fixed = {"Cust ID", "Patient", "Charges"};
        for (int i = 0; i < fixed.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(fixed[i]);
            cell.setCellStyle(headerStyle);
        }
        for (int i = 0; i < days.size(); i++) {
            Cell cell = header.createCell(3 + i);
            cell.setCellValue(dayHeader(days.get(i)));
            cell.setCellStyle(headerStyle);
        }
        Cell presentHeader = header.createCell(totalPresentCol);
        presentHeader.setCellValue("Total Days Present");
        presentHeader.setCellStyle(headerStyle);
        Cell totalHeader = header.createCell(totalCol);
        totalHeader.setCellValue("Total");
        totalHeader.setCellStyle(headerStyle);

        for (Customer customer : customers) {
            Row excelRow = sheet.createRow(r++);
            write(excelRow, 0, customer.getCustCode(), cellStyle);
            write(excelRow, 1, customer.getPatientName(), cellStyle);
            BigDecimal charges = customer.getCharges() == null ? BigDecimal.ZERO : customer.getCharges();
            writeMoney(excelRow, 2, charges, moneyStyle);
            int billableDays = 0;
            String currentEmployee = null;
            int employeeBlockIndex = 0;
            for (int i = 0; i < days.size(); i++) {
                CustomerDuty duty = duties.get(customer.getId() + "|" + days.get(i));
                Cell cell = excelRow.createCell(3 + i);
                if (duty == null) {
                    cell.setCellValue("");
                    cell.setCellStyle(centerStyle);
                    currentEmployee = null;
                    employeeBlockIndex = 0;
                } else if (duty.isHold()) {
                    cell.setCellValue("Hold");
                    cell.setCellStyle(holdStyle);
                    currentEmployee = "HOLD";
                    employeeBlockIndex = 0;
                } else if (duty.getEmployee() != null) {
                    String employeeName = duty.getEmployee().getFullName();
                    if (currentEmployee == null || !currentEmployee.equals(employeeName)) {
                        currentEmployee = employeeName;
                        employeeBlockIndex = (employeeBlockIndex + 1) % employeeBlockStyles.length;
                    }
                    cell.setCellValue(employeeName);
                    cell.setCellStyle(employeeBlockStyles[employeeBlockIndex]);
                    billableDays++;
                } else {
                    cell.setCellValue("");
                    cell.setCellStyle(centerStyle);
                    currentEmployee = null;
                    employeeBlockIndex = 0;
                }
            }
            writeNumber(excelRow, totalPresentCol, BigDecimal.valueOf(billableDays), centerStyle);
            BigDecimal total = dutyService.calculateCharges(customer, from, to);
            writeMoney(excelRow, totalCol, total, moneyStyle);
        }

        sheet.setColumnWidth(0, 12 * 256);
        sheet.setColumnWidth(1, 22 * 256);
        sheet.setColumnWidth(2, 12 * 256);
        for (int i = 0; i < days.size(); i++) {
            sheet.setColumnWidth(3 + i, 16 * 256);
        }
        sheet.setColumnWidth(totalPresentCol, 16 * 256);
        sheet.setColumnWidth(totalCol, 14 * 256);
        sheet.createFreezePane(3, 4);
    }

    private void write(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    private void writeNumber(Row row, int col, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value == null ? 0d : value.doubleValue());
        cell.setCellStyle(style);
    }

    private void writeMoney(Row row, int col, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value == null ? 0d : value.doubleValue());
        cell.setCellStyle(style);
    }

    private String dayHeader(LocalDate day) {
        return String.format("%02d", day.getDayOfMonth()) + "-" + monthShort(day);
    }

    private String monthShort(LocalDate day) {
        if (day.getMonthValue() == 9) {
            return "Sept";
        }
        return day.getMonth().name().substring(0, 3);
    }

    private CellStyle titleStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        return style;
    }

    private CellStyle headerStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.TEAL.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        applyBorder(style);
        return style;
    }

    private CellStyle bodyStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        applyBorder(style);
        return style;
    }

    private CellStyle centerStyle(Workbook workbook) {
        CellStyle style = bodyStyle(workbook);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setWrapText(true);
        return style;
    }

    private CellStyle holdStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.BROWN.getIndex());
        CellStyle style = centerStyle(workbook);
        style.setFont(font);
        return style;
    }

    private CellStyle moneyStyle(Workbook workbook) {
        CellStyle style = bodyStyle(workbook);
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("#,##0.00"));
        style.setAlignment(HorizontalAlignment.RIGHT);
        return style;
    }

    private CellStyle employeeBlockStyle(Workbook workbook, IndexedColors color) {
        CellStyle style = centerStyle(workbook);
        style.setFillForegroundColor(color.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private void applyBorder(CellStyle style) {
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }
}
