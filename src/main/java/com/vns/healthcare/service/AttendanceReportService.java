package com.vns.healthcare.service;

import com.vns.healthcare.domain.AttendanceStatus;
import com.vns.healthcare.domain.SalaryPayStatus;
import com.vns.healthcare.entity.Attendance;
import com.vns.healthcare.entity.Employee;
import com.vns.healthcare.entity.SalaryPayment;
import com.vns.healthcare.exception.BusinessException;
import com.vns.healthcare.repository.AttendanceRepository;
import com.vns.healthcare.repository.SalaryPaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AttendanceReportService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceReportService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final AttendanceRepository attendanceRepository;
    private final EmployeeService employeeService;
    private final SalaryPaymentRepository salaryPaymentRepository;

    public AttendanceReportService(AttendanceRepository attendanceRepository, EmployeeService employeeService,
                                  SalaryPaymentRepository salaryPaymentRepository) {
        this.attendanceRepository = attendanceRepository;
        this.employeeService = employeeService;
        this.salaryPaymentRepository = salaryPaymentRepository;
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

        List<LocalDate> days = daysInRange(from, to);
        List<Employee> staff = employeeService.activeStaff();
        Map<String, Attendance> marks = indexMarks(from, to);
        Map<Long, Map<Integer, SalaryPayStatus>> salaryStatusByEmployee = loadSalaryStatus(staff, from, to);
        int daysForRate = payDaysDivisor(from, to, days.size());

        try {
            Workbook workbook = new XSSFWorkbook();
            writeMatrixSheet(workbook, from, to, days, staff, marks, salaryStatusByEmployee, daysForRate);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            workbook.close();
            return out.toByteArray();
        } catch (IOException ex) {
            throw new BusinessException("Could not create the attendance report");
        }
    }

    private Map<String, Attendance> indexMarks(LocalDate from, LocalDate to) {
        List<Attendance> marks = attendanceRepository.findByDateRangeWithEmployee(from, to);
        Map<String, Attendance> byKey = new LinkedHashMap<String, Attendance>();
        for (Attendance mark : marks) {
            byKey.put(mark.getEmployee().getId() + "|" + mark.getAttendanceDate(), mark);
        }
        return byKey;
    }

    private void writeMatrixSheet(Workbook workbook, LocalDate from, LocalDate to, List<LocalDate> days,
                                  List<Employee> staff, Map<String, Attendance> marks,
                                  Map<Long, Map<Integer, SalaryPayStatus>> salaryStatusByEmployee, int daysForRate) {
        Sheet sheet = workbook.createSheet("Attendance");
        CellStyle titleStyle = titleStyle(workbook);
        CellStyle headerStyle = headerStyle(workbook);
        CellStyle cellStyle = bodyStyle(workbook);
        CellStyle centerStyle = centerStyle(workbook);
        CellStyle moneyStyle = moneyStyle(workbook);

        int totalPresentCol = 6 + days.size();
        int inHandCol = totalPresentCol + 1;
        int r = 0;

        Row title = sheet.createRow(r++);
        Cell titleCell = title.createCell(0);
        titleCell.setCellValue("VNS Healthcare — Attendance report");
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, inHandCol));

        Row period = sheet.createRow(r++);
        period.createCell(0).setCellValue(
                "Period: " + from.format(DATE_FMT) + " to " + to.format(DATE_FMT)
                        + "  |  Per-day salary = monthly salary / " + daysForRate
                        + " days  |  In-hand = (per day × P) + (half-day salary × Half-day)");
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, inHandCol));

        r++;
        Row header = sheet.createRow(r++);
        String[] fixed = {"Emp ID", "Name", "Mobile", "Onboarded", "Salary", "Salary Paid status"};
        for (int i = 0; i < fixed.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(fixed[i]);
            cell.setCellStyle(headerStyle);
        }
        for (int i = 0; i < days.size(); i++) {
            Cell cell = header.createCell(6 + i);
            cell.setCellValue(dayHeader(days.get(i)));
            cell.setCellStyle(headerStyle);
        }
        Cell totalHeader = header.createCell(totalPresentCol);
        totalHeader.setCellValue("Total Days Present");
        totalHeader.setCellStyle(headerStyle);
        Cell inHandHeader = header.createCell(inHandCol);
        inHandHeader.setCellValue("In-Hand Salary");
        inHandHeader.setCellStyle(headerStyle);

        for (Employee emp : staff) {
            Row excelRow = sheet.createRow(r++);
            write(excelRow, 0, emp.getEmpCode(), cellStyle);
            write(excelRow, 1, emp.getFullName(), cellStyle);
            write(excelRow, 2, emp.getMobileNo(), cellStyle);
            write(excelRow, 3, emp.isOnboarded() ? "Yes" : "No", centerStyle);

            BigDecimal salary = emp.getSalary() == null ? BigDecimal.ZERO : emp.getSalary();
            writeMoney(excelRow, 4, salary, moneyStyle);
            write(excelRow, 5, salaryStatusLabel(salaryStatusByEmployee.get(emp.getId())), centerStyle);

            BigDecimal paidUnits = BigDecimal.ZERO;
            for (int i = 0; i < days.size(); i++) {
                LocalDate day = days.get(i);
                Attendance mark = marks.get(emp.getId() + "|" + day);
                AttendanceStatus status = mark == null ? null : mark.getStatus();
                write(excelRow, 6 + i, markLabel(status), centerStyle);
                paidUnits = paidUnits.add(paidUnit(status));
            }
            writeNumber(excelRow, totalPresentCol, paidUnits, centerStyle);

            BigDecimal inHand = BigDecimal.ZERO;
            if (daysForRate > 0 && salary.compareTo(BigDecimal.ZERO) > 0) {
                inHand = salary.multiply(paidUnits)
                        .divide(BigDecimal.valueOf(daysForRate), 2, RoundingMode.HALF_UP);
            }
            writeMoney(excelRow, inHandCol, inHand, moneyStyle);
        }

        sheet.setColumnWidth(0, 12 * 256);
        sheet.setColumnWidth(1, 22 * 256);
        sheet.setColumnWidth(2, 14 * 256);
        sheet.setColumnWidth(3, 12 * 256);
        sheet.setColumnWidth(4, 12 * 256);
        sheet.setColumnWidth(5, 16 * 256);
        for (int i = 0; i < days.size(); i++) {
            sheet.setColumnWidth(6 + i, 11 * 256);
        }
        sheet.setColumnWidth(totalPresentCol, 14 * 256);
        sheet.setColumnWidth(inHandCol, 16 * 256);
        sheet.createFreezePane(6, 4);
    }

    private Map<Long, Map<Integer, SalaryPayStatus>> loadSalaryStatus(List<Employee> staff, LocalDate from, LocalDate to) {
        Map<Long, Map<Integer, SalaryPayStatus>> result = new LinkedHashMap<>();
        if (staff == null || staff.isEmpty()) {
            return result;
        }
        int fromYear = from.getYear();
        int toYear = to.getYear();
        for (Employee employee : staff) {
            if (employee == null || employee.getId() == null) {
                continue;
            }
            Map<Integer, SalaryPayStatus> statuses = new LinkedHashMap<>();
            for (SalaryPayment payment : salaryPaymentRepository.findByEmployeeIdAndPayYearBetweenOrderByPayYearAscPayMonthAsc(employee.getId(), fromYear, toYear)) {
                if (payment == null || payment.getStatus() == null) {
                    continue;
                }
                statuses.put(payment.getPayYear() * 100 + payment.getPayMonth(), payment.getStatus());
            }
            result.put(employee.getId(), statuses);
        }
        return result;
    }

    private String salaryStatusLabel(Map<Integer, SalaryPayStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return "Unpaid";
        }
        SalaryPayStatus latest = null;
        int latestKey = Integer.MIN_VALUE;
        for (Map.Entry<Integer, SalaryPayStatus> entry : statuses.entrySet()) {
            if (entry.getKey() > latestKey) {
                latestKey = entry.getKey();
                latest = entry.getValue();
            }
        }
        if (latest == null) {
            return "Unpaid";
        }
        if (latest == SalaryPayStatus.PAID) {
            return "Paid";
        }
        if (latest == SalaryPayStatus.IN_PROGRESS) {
            return "In progress";
        }
        return "Unpaid";
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

    private BigDecimal paidUnit(AttendanceStatus status) {
        if (status == AttendanceStatus.PRESENT) {
            return BigDecimal.ONE;
        }
        if (status == AttendanceStatus.HALF_DAY) {
            return new BigDecimal("0.5");
        }
        return BigDecimal.ZERO;
    }

    private String markLabel(AttendanceStatus status) {
        if (status == null) {
            return "";
        }
        if (status == AttendanceStatus.PRESENT) {
            return "P";
        }
        if (status == AttendanceStatus.ABSENT) {
            return "A";
        }
        if (status == AttendanceStatus.HALF_DAY) {
            return "Half-day";
        }
        if (status == AttendanceStatus.LEAVE) {
            return "L";
        }
        return status.name();
    }

    /**
     * Monthly salary is divided by calendar days of that month when the report stays in one month.
     * Example: salary 20000 in a 30-day month → per day 666.67; 2 P + 1 half-day → in-hand 1666.67.
     */
    private int payDaysDivisor(LocalDate from, LocalDate to, int rangeDays) {
        if (from.getYear() == to.getYear() && from.getMonth() == to.getMonth()) {
            return from.lengthOfMonth();
        }
        return rangeDays;
    }

    private List<LocalDate> daysInRange(LocalDate from, LocalDate to) {
        List<LocalDate> days = new ArrayList<LocalDate>();
        LocalDate day = from;
        while (!day.isAfter(to)) {
            days.add(day);
            day = day.plusDays(1);
        }
        return days;
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
        return style;
    }

    private CellStyle moneyStyle(Workbook workbook) {
        CellStyle style = bodyStyle(workbook);
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("#,##0.00"));
        style.setAlignment(HorizontalAlignment.RIGHT);
        return style;
    }

    private void applyBorder(CellStyle style) {
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }
}
