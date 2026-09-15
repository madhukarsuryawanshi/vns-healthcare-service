package com.vns.healthcare.web;

import com.vns.healthcare.repository.CustomerRepository;
import com.vns.healthcare.repository.EmployeeRepository;
import com.vns.healthcare.service.DashboardService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;

@Controller
public class DashboardController {

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
    public String home(Model model) {
        model.addAttribute("page", "dashboard");
        model.addAttribute("stats", dashboardService.stats());
        model.addAttribute("recentEmployees", take(employeeRepository.findAllByOrderByCreatedAtDesc(), 5));
        model.addAttribute("recentCustomers", take(customerRepository.findAllWithEmployee(), 5));
        model.addAttribute("today", LocalDate.now());
        return "dashboard";
    }

    private <T> java.util.List<T> take(java.util.List<T> source, int n) {
        if (source.size() <= n) {
            return source;
        }
        return source.subList(0, n);
    }
}
