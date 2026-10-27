package com.vns.healthcare.web;

import com.vns.healthcare.domain.SalaryPayStatus;
import com.vns.healthcare.entity.Employee;
import com.vns.healthcare.service.SalaryPaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.ui.Model;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.List;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalaryControllerTest {

    @Mock
    private SalaryPaymentService salaryPaymentService;

    private SalaryController controller;

    @BeforeEach
    void setUp() {
        controller = new SalaryController(salaryPaymentService);
    }

    @Test
    void register_returnsSalaryPage_forUserWithReadPermission() {
        Employee employee = new Employee();
        employee.setId(10L);
        employee.setEmpCode("EMP-1001");
        employee.setFullName("Madhukar");
        employee.setSalary(BigDecimal.valueOf(25000));

        Map<Employee, List<SalaryMonthView>> rows = new LinkedHashMap<>();
        rows.put(employee, List.of(new SalaryMonthView(YearMonth.of(2026, 5), SalaryPayStatus.UNPAID, null, false)));
        when(salaryPaymentService.register(2026)).thenReturn(rows);

        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap flash = new RedirectAttributesModelMap();
        String view = controller.register(2026, null, "empCode", "asc", 0, 10, Map.of(), model,
                new TestingAuthenticationToken("admin", "pw", List.of(new SimpleGrantedAuthority("salary:read"))), flash);

        assertEquals("salary/list", view);
        assertEquals("salary", model.getAttribute("page"));
        assertEquals(2026, model.getAttribute("year"));
        assertNotNull(model.getAttribute("rows"));
        String error = (String) flash.getFlashAttributes().get("error");
        assertEquals(null, error);
    }

    @Test
    void register_redirects_whenUserHasNoSalaryAccess() {
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap flash = new RedirectAttributesModelMap();

        String view = controller.register(2026, null, "empCode", "asc", 0, 10, Map.of(), model,
                new TestingAuthenticationToken("user", "pw", List.of(new SimpleGrantedAuthority("customers:read"))), flash);

        assertEquals("redirect:/", view);
        assertEquals("You do not have permission, Please contact your Admin", flash.getFlashAttributes().get("error"));
    }

    @Test
    void mark_savesSalaryAndRedirects() {
        RedirectAttributesModelMap flash = new RedirectAttributesModelMap();
        doNothing().when(salaryPaymentService).mark(15L, 2026, 5, SalaryPayStatus.PAID, LocalDate.of(2026, 5, 28), "Approved");

        String view = controller.mark(15L, 2026, 5, SalaryPayStatus.PAID,
                LocalDate.of(2026, 5, 28), "Approved", null, "XMLHttpRequest", flash);

        assertEquals("redirect:/salary?year=2026", view);
        assertEquals("Salary status saved.", flash.getFlashAttributes().get("success"));
    }

    @Test
    void mark_redirectsWithError_whenServiceThrowsBusinessException() {
        RedirectAttributesModelMap flash = new RedirectAttributesModelMap();
        org.mockito.Mockito.doThrow(new com.vns.healthcare.exception.BusinessException("Invalid salary"))
                .when(salaryPaymentService).mark(15L, 2026, 5, SalaryPayStatus.PAID, null, "Approved");

        String view = controller.mark(15L, 2026, 5, SalaryPayStatus.PAID,
                null, "Approved", null, "XMLHttpRequest", flash);

        assertEquals("redirect:/salary?year=2026", view);
        assertEquals("Invalid salary", flash.getFlashAttributes().get("error"));
    }
}
