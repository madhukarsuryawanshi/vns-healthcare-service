package com.vns.healthcare.service;

import com.vns.healthcare.domain.AttendanceStatus;
import com.vns.healthcare.domain.EmployeeStatus;
import com.vns.healthcare.repository.AttendanceRepository;
import com.vns.healthcare.repository.CustomerRepository;
import com.vns.healthcare.repository.EmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Service
public class DashboardService {

    private final EmployeeRepository employeeRepository;
    private final CustomerRepository customerRepository;
    private final AttendanceRepository attendanceRepository;

    public DashboardService(EmployeeRepository employeeRepository,
                            CustomerRepository customerRepository,
                            AttendanceRepository attendanceRepository) {
        this.employeeRepository = employeeRepository;
        this.customerRepository = customerRepository;
        this.attendanceRepository = attendanceRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Long> stats() {
        Map<String, Long> stats = new HashMap<String, Long>();
        stats.put("employees", employeeRepository.count());
        stats.put("activeEmployees", employeeRepository.countByStatus(EmployeeStatus.ACTIVE));
        stats.put("onboarded", employeeRepository.countByOnboardedTrue());
        stats.put("customers", customerRepository.count());
        stats.put("assignedCases", customerRepository.countByAssignedEmployeeIsNotNull());
        stats.put("presentToday", attendanceRepository.countByAttendanceDateAndStatus(LocalDate.now(), AttendanceStatus.PRESENT));
        return stats;
    }
}
