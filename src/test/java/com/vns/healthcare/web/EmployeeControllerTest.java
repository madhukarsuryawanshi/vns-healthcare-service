package com.vns.healthcare.web;

import com.vns.healthcare.domain.EmployeeStatus;
import com.vns.healthcare.entity.Employee;
import com.vns.healthcare.exception.BusinessException;
import com.vns.healthcare.service.EmployeeBrochurePdfService;
import com.vns.healthcare.service.EmployeeService;
import com.vns.healthcare.service.FileStorageService;
import com.vns.healthcare.service.SalaryPaymentService;
import com.vns.healthcare.service.WhatsAppBusinessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeControllerTest {

    @Mock
    private EmployeeService employeeService;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private SalaryPaymentService salaryPaymentService;

    @Mock
    private EmployeeBrochurePdfService employeeBrochurePdfService;

    @Mock
    private WhatsAppBusinessService whatsAppBusinessService;

    @Mock
    private com.vns.healthcare.security.UserActivityService userActivityService;

    private EmployeeController controller;

    @BeforeEach
    void setUp() {
        controller = new EmployeeController(employeeService, fileStorageService, salaryPaymentService,
                employeeBrochurePdfService, whatsAppBusinessService, userActivityService);
    }

    @Test
    void list_filtersAbsentEmployees_whenAttendanceIsAbsent() {
        Employee employee = new Employee();
        employee.setId(7L);
        employee.setEmpCode("EMP-1007");
        employee.setFullName("Madhukar");
        employee.setStatus(EmployeeStatus.ACTIVE);

        Page<Employee> page = new PageImpl<>(List.of(employee), PageRequest.of(0, Integer.MAX_VALUE), 1);
        when(employeeService.listPage(PageRequest.of(0, Integer.MAX_VALUE, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "empCode"))))
                .thenReturn(page);
        when(employeeService.todayAttendanceStatusMap()).thenReturn(Collections.emptyMap());
        when(employeeService.searchSuggestions(null, 15)).thenReturn(Collections.emptyList());
        when(employeeService.presentTodayEmployeeIds(Collections.emptyMap())).thenReturn(Collections.emptySet());

        Model model = new ExtendedModelMap();
        String view = controller.list(null, null, null, "ABSENT", "empCode", "asc", 0, 10, model);

        assertEquals("employees/list", view);
        assertEquals(1, ((List<Employee>) model.getAttribute("employees")).size());
    }

    @Test
    void list_filtersHalfDayEmployees_whenAttendanceIsHalfDay() {
        Employee employee = new Employee();
        employee.setId(9L);
        employee.setEmpCode("EMP-1009");
        employee.setFullName("Half Day User");
        employee.setStatus(EmployeeStatus.ACTIVE);

        Page<Employee> page = new PageImpl<>(List.of(employee), PageRequest.of(0, Integer.MAX_VALUE), 1);
        when(employeeService.listPage(PageRequest.of(0, Integer.MAX_VALUE, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "empCode"))))
                .thenReturn(page);
        when(employeeService.todayAttendanceStatusMap()).thenReturn(Map.of(9L, com.vns.healthcare.domain.AttendanceStatus.HALF_DAY));
        when(employeeService.searchSuggestions(null, 15)).thenReturn(Collections.emptyList());
        when(employeeService.presentTodayEmployeeIds(Map.of(9L, com.vns.healthcare.domain.AttendanceStatus.HALF_DAY))).thenReturn(Collections.singleton(9L));

        Model model = new ExtendedModelMap();
        String view = controller.list(null, null, null, "HALF_DAY", "empCode", "asc", 0, 10, model);

        assertEquals("employees/list", view);
        assertEquals(1, ((List<Employee>) model.getAttribute("employees")).size());
    }
}
