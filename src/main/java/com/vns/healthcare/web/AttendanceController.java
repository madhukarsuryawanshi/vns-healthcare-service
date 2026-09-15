package com.vns.healthcare.web;

import com.vns.healthcare.domain.AttendanceStatus;
import com.vns.healthcare.entity.Attendance;
import com.vns.healthcare.entity.Employee;
import com.vns.healthcare.service.AttendanceReportService;
import com.vns.healthcare.service.AttendanceService;
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
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final AttendanceReportService attendanceReportService;

    public AttendanceController(AttendanceService attendanceService,
                                AttendanceReportService attendanceReportService) {
        this.attendanceService = attendanceService;
        this.attendanceReportService = attendanceReportService;
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
        Map<Employee, Attendance> roster = attendanceService.rosterFor(date);
        List<Map.Entry<Employee, Attendance>> rosterEntries = new ArrayList<Map.Entry<Employee, Attendance>>(roster.entrySet());
        if (status != null && !status.trim().isEmpty()) {
            final String normalized = status.trim();
            rosterEntries.removeIf(entry -> entry.getValue() == null || entry.getValue().getStatus() == null || !entry.getValue().getStatus().name().equalsIgnoreCase(normalized));
        }
        rosterEntries.sort(sortAttendanceEntries(sort, dir));
        model.addAttribute("page", "attendance");
        model.addAttribute("date", date);
        model.addAttribute("status", status == null ? "" : status);
        model.addAttribute("sort", sort);
        model.addAttribute("dir", dir);
        model.addAttribute("reportFrom", date.withDayOfMonth(1));
        model.addAttribute("reportTo", date);
        model.addAttribute("roster", rosterEntries);
        model.addAttribute("statuses", AttendanceStatus.values());
        return "attendance/list";
    }

    private Comparator<Map.Entry<Employee, Attendance>> sortAttendanceEntries(String sort, String dir) {
        Comparator<Map.Entry<Employee, Attendance>> comparator;
        if ("fullName".equalsIgnoreCase(sort)) {
            comparator = Comparator.comparing(e -> e.getKey().getFullName() == null ? "" : e.getKey().getFullName(), Comparator.nullsLast(String::compareToIgnoreCase));
        } else if ("onboarded".equalsIgnoreCase(sort)) {
            comparator = Comparator.comparing(e -> e.getKey().isOnboarded());
        } else {
            comparator = Comparator.comparing(e -> e.getKey().getEmpCode() == null ? "" : e.getKey().getEmpCode(), Comparator.nullsLast(String::compareToIgnoreCase));
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
