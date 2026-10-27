package com.vns.healthcare.web;

import com.vns.healthcare.domain.AttendanceStatus;
import com.vns.healthcare.entity.Employee;
import com.vns.healthcare.service.AttendanceReportService;
import com.vns.healthcare.service.AttendanceService;
import com.vns.healthcare.service.EmployeeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceControllerTest {

    @Mock
    private AttendanceService attendanceService;

    @Mock
    private AttendanceReportService attendanceReportService;

    @Mock
    private EmployeeService employeeService;

    private AttendanceController controller;

    @BeforeEach
    void setUp() {
        controller = new AttendanceController(attendanceService, attendanceReportService, employeeService);
    }

    @Test
    void roster_loadsMonthView_forAuthorizedUser() {
        Employee employee = new Employee();
        employee.setId(3L);
        employee.setEmpCode("EMP-1003");
        employee.setFullName("Annie");

        when(employeeService.activeStaffPage(0, 10)).thenReturn(new PageImpl<>(List.of(employee), org.springframework.data.domain.PageRequest.of(0, 10), 1));
        when(attendanceService.monthlyRoster(LocalDate.of(2026, 9, 1))).thenReturn(new LinkedHashMap<>());
        when(attendanceService.summaryForDate(any(LocalDate.class))).thenReturn(Map.of("PRESENT", 1));

        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap flash = new RedirectAttributesModelMap();
        String view = controller.roster(LocalDate.of(2026, 9, 12), null, "empCode", "asc", 0, 10, model,
                new TestingAuthenticationToken("user", "pw", "attendance:read"), flash);

        assertEquals("attendance/list", view);
        assertEquals("attendance", model.getAttribute("page"));
        assertEquals(LocalDate.of(2026, 9, 1), model.getAttribute("date"));
        assertNotNull(model.getAttribute("summary"));
    }

    @Test
    void roster_redirects_whenNoAttendanceAccess() {
        Model model = new ExtendedModelMap();
        RedirectAttributesModelMap flash = new RedirectAttributesModelMap();

        String view = controller.roster(LocalDate.of(2026, 9, 12), null, "empCode", "asc", 0, 10, model,
                new TestingAuthenticationToken("user", "pw", "dashboard:read"), flash);

        assertEquals("redirect:/", view);
        assertEquals("You do not have permission, Please contact your Admin", flash.getFlashAttributes().get("error"));
    }

    @Test
    void summary_returnsAttendanceMap() {
        when(attendanceService.summaryForDate(LocalDate.of(2026, 9, 12))).thenReturn(Map.of("PRESENT", 3, "ABSENT", 1));

        var response = controller.summary(LocalDate.of(2026, 9, 12));

        assertEquals(200, response.getStatusCode().value());
        assertEquals(3, response.getBody().get("PRESENT"));
    }

    @Test
    void bulkSave_returnsOkForAjaxRequest() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("status_2026-09-01", "PRESENT");
        params.put("status_2026-09-02", "ABSENT");

        doNothing().when(attendanceService).saveMonthlyRow(eq(7L), eq(LocalDate.of(2026, 9, 1)), any(Map.class), eq(null));

        jakarta.servlet.http.HttpServletRequest request = mock(jakarta.servlet.http.HttpServletRequest.class);
        when(request.getHeader("X-Requested-With")).thenReturn("XMLHttpRequest");

        var response = (org.springframework.http.ResponseEntity<?>) controller.saveMonthlyRow(7L, LocalDate.of(2026, 9, 1), null, params,
                new RedirectAttributesModelMap(), request);

        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void mark_returnsOkForAjaxRequest() {
        doNothing().when(attendanceService).mark(7L, LocalDate.of(2026, 9, 12), AttendanceStatus.PRESENT, LocalTime.of(9, 0), "Late");

        jakarta.servlet.http.HttpServletRequest request = mock(jakarta.servlet.http.HttpServletRequest.class);
        when(request.getHeader("X-Requested-With")).thenReturn("XMLHttpRequest");

        var response = (org.springframework.http.ResponseEntity<?>) controller.mark(7L, LocalDate.of(2026, 9, 12), AttendanceStatus.PRESENT,
                LocalTime.of(9, 0), "Late", new RedirectAttributesModelMap(), request);

        assertEquals(200, response.getStatusCode().value());
    }
}
