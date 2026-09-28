package com.vns.healthcare.service;

import com.vns.healthcare.domain.AttendanceStatus;
import com.vns.healthcare.entity.Attendance;
import com.vns.healthcare.entity.Employee;
import com.vns.healthcare.repository.AttendanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AttendanceService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceService.class);

    private final AttendanceRepository attendanceRepository;
    private final EmployeeService employeeService;

    public AttendanceService(AttendanceRepository attendanceRepository, EmployeeService employeeService) {
        this.attendanceRepository = attendanceRepository;
        this.employeeService = employeeService;
    }

    @Transactional(readOnly = true)
    public Map<Employee, Attendance> rosterFor(LocalDate date) {
        log.debug("Loading attendance roster for date [{}]", date);
        List<Employee> staff = employeeService.activeStaff();
        List<Attendance> marks = attendanceRepository.findByDateWithEmployee(date);
        Map<Long, Attendance> byEmp = new LinkedHashMap<Long, Attendance>();
        for (Attendance mark : marks) {
            byEmp.put(mark.getEmployee().getId(), mark);
        }
        Map<Employee, Attendance> roster = new LinkedHashMap<Employee, Attendance>();
        for (Employee employee : staff) {
            roster.put(employee, byEmp.get(employee.getId()));
        }
        log.debug("Roster built for {} employees on {} with {} attendance records", staff.size(), date, marks.size());
        return roster;
    }

    @Transactional(readOnly = true)
    public Map<Long, Map<String, Attendance>> monthlyRoster(LocalDate monthStart) {
        log.debug("Loading monthly roster for month start [{}]", monthStart);
        LocalDate monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth());
        List<Attendance> marks = attendanceRepository.findByDateRangeWithEmployee(monthStart, monthEnd);
        Map<Long, Map<String, Attendance>> byEmployee = new LinkedHashMap<Long, Map<String, Attendance>>();
        for (Attendance mark : marks) {
            Long employeeId = mark.getEmployee().getId();
            Map<String, Attendance> byDate = byEmployee.get(employeeId);
            if (byDate == null) {
                byDate = new LinkedHashMap<String, Attendance>();
                byEmployee.put(employeeId, byDate);
            }
            byDate.put(mark.getAttendanceDate().toString(), mark);
        }
        log.debug("Monthly roster built with {} employees and {} attendance entries", byEmployee.size(), marks.size());
        return byEmployee;
    }

    @Transactional
    public void mark(Long employeeId, LocalDate date, AttendanceStatus status, LocalTime checkIn, String notes) {
        log.info("Marking attendance for employee [{}] on [{}] as [{}]", employeeId, date, status);
        Employee employee = employeeService.get(employeeId);
        Attendance attendance = attendanceRepository.findByEmployeeIdAndAttendanceDate(employeeId, date)
                .orElse(new Attendance());
        attendance.setEmployee(employee);
        attendance.setAttendanceDate(date);
        attendance.setStatus(status);
        attendance.setCheckInTime(checkIn);
        attendance.setNotes(notes);
        attendanceRepository.save(attendance);
        log.info("Attendance saved for employee [{}] on [{}]", employeeId, date);
    }

    @Transactional
    public void saveMonthlyRow(Long employeeId, LocalDate monthStart, Map<LocalDate, AttendanceStatus> statuses, String notes) {
        log.info("Saving monthly attendance batch for employee [{}] in month [{}], entries [{}]", employeeId, monthStart, statuses.size());
        Employee employee = employeeService.get(employeeId);
        LocalDate monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth());
        for (Map.Entry<LocalDate, AttendanceStatus> entry : statuses.entrySet()) {
            LocalDate day = entry.getKey();
            if (day.isBefore(monthStart) || day.isAfter(monthEnd)) {
                continue;
            }
            Attendance attendance = attendanceRepository.findByEmployeeIdAndAttendanceDate(employeeId, day)
                    .orElse(new Attendance());
            attendance.setEmployee(employee);
            attendance.setAttendanceDate(day);
            attendance.setStatus(entry.getValue());
            attendance.setCheckInTime(null);
            attendance.setNotes(notes);
            attendanceRepository.save(attendance);
        }
        log.info("Monthly attendance batch saved for employee [{}]", employeeId);
    }

    @Transactional(readOnly = true)
    public java.util.Map<String, Integer> summaryForDate(LocalDate date) {
        log.debug("Computing attendance summary for date [{}]", date);
        java.util.List<Attendance> marks = attendanceRepository.findByDateWithEmployee(date);
        int present = 0, half = 0, absent = 0, leave = 0;
        for (Attendance a : marks) {
            if (a.getStatus() == AttendanceStatus.PRESENT) present++;
            else if (a.getStatus() == AttendanceStatus.HALF_DAY) half++;
            else if (a.getStatus() == AttendanceStatus.ABSENT) absent++;
            else if (a.getStatus() == AttendanceStatus.LEAVE) leave++;
        }
        int totalActive = employeeService.activeStaff().size();
        int unmarked = totalActive - marks.size();
        java.util.Map<String, Integer> map = new java.util.LinkedHashMap<>();
        map.put("present", present);
        map.put("half", half);
        map.put("absent", absent);
        map.put("leave", leave);
        map.put("unmarked", unmarked < 0 ? 0 : unmarked);
        map.put("total", totalActive);
        log.debug("Attendance summary for {}: {}", date, map);
        return map;
    }
}
