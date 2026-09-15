package com.vns.healthcare.service;

import com.vns.healthcare.domain.AttendanceStatus;
import com.vns.healthcare.entity.Attendance;
import com.vns.healthcare.entity.Employee;
import com.vns.healthcare.repository.AttendanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final EmployeeService employeeService;

    public AttendanceService(AttendanceRepository attendanceRepository, EmployeeService employeeService) {
        this.attendanceRepository = attendanceRepository;
        this.employeeService = employeeService;
    }

    @Transactional(readOnly = true)
    public Map<Employee, Attendance> rosterFor(LocalDate date) {
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
        return roster;
    }

    @Transactional
    public void mark(Long employeeId, LocalDate date, AttendanceStatus status, LocalTime checkIn, String notes) {
        Employee employee = employeeService.get(employeeId);
        Attendance attendance = attendanceRepository.findByEmployeeIdAndAttendanceDate(employeeId, date)
                .orElse(new Attendance());
        attendance.setEmployee(employee);
        attendance.setAttendanceDate(date);
        attendance.setStatus(status);
        attendance.setCheckInTime(checkIn);
        attendance.setNotes(notes);
        attendanceRepository.save(attendance);
    }
}
