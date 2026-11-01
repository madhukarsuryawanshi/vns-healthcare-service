package com.vns.healthcare.service;

import com.vns.healthcare.domain.AttendanceStatus;
import com.vns.healthcare.entity.Employee;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class AttendanceAutoMarkService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceAutoMarkService.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final AttendanceService attendanceService;
    private final EmployeeService employeeService;
    private final AtomicBoolean enabled = new AtomicBoolean(false);

    public AttendanceAutoMarkService(AttendanceService attendanceService, EmployeeService employeeService) {
        this.attendanceService = attendanceService;
        this.employeeService = employeeService;
        this.enabled.set(false);
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    public void setEnabled(boolean enabled) {
        this.enabled.set(enabled);
        log.info("Attendance auto-mark changed to [{}]", enabled);
    }

    @Scheduled(cron = "0 0 10 * * *", zone = "Asia/Kolkata")
//    @Scheduled(cron = "0 15 21 * * *", zone = "Asia/Kolkata")
    public void runDailyAutoMark() {
        if (!enabled.get()) {
            log.debug("Attendance auto-mark is disabled; skipping scheduled run");
            return;
        }

        LocalDate today = LocalDate.now(IST);
        for (Employee employee : employeeService.activeStaff()) {
            if (employee == null || employee.getId() == null) {
                continue;
            }
            attendanceService.markIfMissing(employee.getId(), today, AttendanceStatus.PRESENT, null, "Auto-marked at 10:00 IST");
        }
        log.info("Attendance auto-mark completed for [{}] employees on [{}]", employeeService.countActiveStaff(), today);
    }
}
