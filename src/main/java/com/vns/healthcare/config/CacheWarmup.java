package com.vns.healthcare.config;

import com.vns.healthcare.service.CustomerService;
import com.vns.healthcare.service.EmployeeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
public class CacheWarmup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CacheWarmup.class);

    private final EmployeeService employeeService;
    private final CustomerService customerService;

    public CacheWarmup(EmployeeService employeeService, CustomerService customerService) {
        this.employeeService = employeeService;
        this.customerService = customerService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            log.info("Warming employee and customer caches at startup");
            employeeService.list("");
            employeeService.listPage(PageRequest.of(0, 25));
            employeeService.activeStaff();
            customerService.list("");
            customerService.listPage(PageRequest.of(0, 25));
            log.info("Application startup cache warmup completed");
        } catch (Exception ex) {
            log.warn("Startup cache warming failed: {}", ex.getMessage(), ex);
        }
    }
}
