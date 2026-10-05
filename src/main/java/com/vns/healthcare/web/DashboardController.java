package com.vns.healthcare.web;

import com.vns.healthcare.repository.CustomerRepository;
import com.vns.healthcare.repository.EmployeeRepository;
import com.vns.healthcare.service.DashboardService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;

@Controller
public class DashboardController {

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);

    private final DashboardService dashboardService;
    private final EmployeeRepository employeeRepository;
    private final CustomerRepository customerRepository;

    public DashboardController(DashboardService dashboardService,
                               EmployeeRepository employeeRepository,
                               CustomerRepository customerRepository) {
        this.dashboardService = dashboardService;
        this.employeeRepository = employeeRepository;
        this.customerRepository = customerRepository;
    }

    @GetMapping("/")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('dashboard:read') or hasRole('ADMIN')")
    public String home(Model model) {
        log.info("Loading dashboard page");
        model.addAttribute("page", "dashboard");
        model.addAttribute("stats", dashboardService.stats());
        model.addAttribute("recentEmployees", java.util.Collections.emptyList());
        model.addAttribute("recentCustomers", java.util.Collections.emptyList());
        model.addAttribute("today", LocalDate.now());
        log.info("Dashboard page prepared successfully without recent staff/leads queries");
        return "dashboard";
    }

    private <T> java.util.List<T> take(java.util.List<T> source, int n) {
        if (source.size() <= n) {
            return source;
        }
        return source.subList(0, n);
    }
}
