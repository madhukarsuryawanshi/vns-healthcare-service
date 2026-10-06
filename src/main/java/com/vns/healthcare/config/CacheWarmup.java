package com.vns.healthcare.config;

import com.vns.healthcare.entity.Employee;
import com.vns.healthcare.service.AttendanceService;
import com.vns.healthcare.service.CustomerService;
import com.vns.healthcare.service.DashboardService;
import com.vns.healthcare.service.EmployeeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Component
public class CacheWarmup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CacheWarmup.class);

    private final EmployeeService employeeService;
    private final CustomerService customerService;
    private final DashboardService dashboardService;
    private final AttendanceService attendanceService;

    public CacheWarmup(EmployeeService employeeService,
                       CustomerService customerService,
                       DashboardService dashboardService,
                       AttendanceService attendanceService) {
        this.employeeService = employeeService;
        this.customerService = customerService;
        this.dashboardService = dashboardService;
        this.attendanceService = attendanceService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            log.info("Warming hot caches at startup");
            employeeService.list("");
            employeeService.listPage(PageRequest.of(0, 25));
            employeeService.activeStaff();
            List<Employee> activeStaff = employeeService.activeStaff();
            for (Employee employee : activeStaff) {
                if (employee != null && employee.getId() != null) {
                    employeeService.get(employee.getId());
                }
            }

            dashboardService.stats();
            LocalDate today = LocalDate.now();
            attendanceService.summaryForDate(today);
            attendanceService.monthlyRoster(YearMonth.from(today).atDay(1));

            customerService.list("");
            customerService.listPage(PageRequest.of(0, 25));
            log.info("Application startup cache warmup completed");
        } catch (Exception ex) {
            log.warn("Startup cache warming failed: {}", ex.getMessage(), ex);
        }
    }
}
