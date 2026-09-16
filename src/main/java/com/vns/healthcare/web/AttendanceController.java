package com.vns.healthcare.web;

import com.vns.healthcare.domain.AttendanceStatus;
import com.vns.healthcare.entity.Attendance;
import com.vns.healthcare.entity.Employee;
import com.vns.healthcare.service.AttendanceReportService;
import com.vns.healthcare.service.AttendanceService;
import com.vns.healthcare.service.EmployeeService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final AttendanceReportService attendanceReportService;
    private final EmployeeService employeeService;

    public AttendanceController(AttendanceService attendanceService,
                                AttendanceReportService attendanceReportService,
                                EmployeeService employeeService) {
        this.attendanceService = attendanceService;
        this.attendanceReportService = attendanceReportService;
        this.employeeService = employeeService;
    }

    @GetMapping
    public String roster(@RequestParam(value = "date", required = false)
                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                         @RequestParam(value = "status", required = false) String status,
                         @RequestParam(value = "sort", required = false, defaultValue = "empCode") String sort,
                         @RequestParam(value = "dir", required = false, defaultValue = "asc") String dir,
                         Model model) {
        if (date == null) {
            date = LocalDate.now();
        }
        LocalDate monthStart = date.withDayOfMonth(1);
        List<Employee> employees = employeeService.activeStaff();
        employees.sort(sortEmployees(sort, dir));
        Map<Long, Map<String, Attendance>> attendanceByEmployee = attendanceService.monthlyRoster(monthStart);

        List<LocalDate> monthDays = new ArrayList<LocalDate>();
        LocalDate current = monthStart;
        while (!current.isAfter(monthStart.withDayOfMonth(monthStart.lengthOfMonth()))) {
            monthDays.add(current);
            current = current.plusDays(1);
        }

        model.addAttribute("page", "attendance");
        model.addAttribute("date", monthStart);
        model.addAttribute("status", status == null ? "" : status);
        model.addAttribute("sort", sort);
        model.addAttribute("dir", dir);
        model.addAttribute("reportFrom", monthStart);
        model.addAttribute("reportTo", monthStart.withDayOfMonth(monthStart.lengthOfMonth()));
        model.addAttribute("employees", employees);
        model.addAttribute("monthDays", monthDays);
        model.addAttribute("attendanceByEmployee", attendanceByEmployee);
        model.addAttribute("statuses", AttendanceStatus.values());
        model.addAttribute("previousMonth", monthStart.minusMonths(1));
        model.addAttribute("nextMonth", monthStart.plusMonths(1));
        return "attendance/list";
    }

    private Comparator<Employee> sortEmployees(String sort, String dir) {
        Comparator<Employee> comparator;
        if ("fullName".equalsIgnoreCase(sort)) {
            comparator = Comparator.comparing(e -> e.getFullName() == null ? "" : e.getFullName(), Comparator.nullsLast(String::compareToIgnoreCase));
        } else {
            comparator = Comparator.comparing(e -> e.getEmpCode() == null ? "" : e.getEmpCode(), Comparator.nullsLast(String::compareToIgnoreCase));
        }
        if ("desc".equalsIgnoreCase(dir)) {
            comparator = comparator.reversed();
        }
        return comparator;
    }

    @GetMapping("/report")
    public ResponseEntity<byte[]> report(
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (to == null) {
            to = LocalDate.now();
        }
        if (from == null) {
            from = to.withDayOfMonth(1);
        }
        byte[] excel = attendanceReportService.buildExcel(from, to);
        String filename = "VNS-attendance-" + from + "-to-" + to + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(
                       "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excel);
    }

    @PostMapping("/bulk")
    public String saveMonthlyRow(@RequestParam Long employeeId,
                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate month,
                               @RequestParam(required = false) String notes,
                               @RequestParam Map<String, String> params,
                               RedirectAttributes redirectAttributes) {
        Map<LocalDate, AttendanceStatus> statuses = new LinkedHashMap<LocalDate, AttendanceStatus>();
        LocalDate monthStart = month.withDayOfMonth(1);
        for (LocalDate day : getDaysInMonth(monthStart)) {
            String key = "status_" + day.toString();
            String value = params.get(key);
            if (value != null && !value.trim().isEmpty()) {
                statuses.put(day, AttendanceStatus.valueOf(value));
            }
        }
        attendanceService.saveMonthlyRow(employeeId, monthStart, statuses, notes);
        redirectAttributes.addFlashAttribute("success", "Attendance saved for the month.");
        return "redirect:/attendance?date=" + monthStart;
    }

    private List<LocalDate> getDaysInMonth(LocalDate monthStart) {
        List<LocalDate> days = new ArrayList<LocalDate>();
        LocalDate current = monthStart;
        LocalDate monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth());
        while (!current.isAfter(monthEnd)) {
            days.add(current);
            current = current.plusDays(1);
        }
        return days;
    }

    @PostMapping
    public String mark(@RequestParam Long employeeId,
                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                       @RequestParam AttendanceStatus status,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime checkInTime,
                       @RequestParam(required = false) String notes,
                       RedirectAttributes redirectAttributes) {
        attendanceService.mark(employeeId, date, status, checkInTime, notes);
        redirectAttributes.addFlashAttribute("success", "Attendance saved.");
        return "redirect:/attendance?date=" + date;
    }
}
