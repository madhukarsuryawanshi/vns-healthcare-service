package com.vns.healthcare.service;

import com.vns.healthcare.domain.AttendanceStatus;
import com.vns.healthcare.entity.Employee;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceAutoMarkServiceTest {

    @Mock
    private AttendanceService attendanceService;

    @Mock
    private EmployeeService employeeService;

    private AttendanceAutoMarkService service;

    @BeforeEach
    void setUp() {
        service = new AttendanceAutoMarkService(attendanceService, employeeService);
    }

    @Test
    void setEnabled_turnsFeatureOnAndOff() {
        assertFalse(service.isEnabled());

        service.setEnabled(true);
        assertTrue(service.isEnabled());

        service.setEnabled(false);
        assertFalse(service.isEnabled());
    }

    @Test
    void runDailyAutoMark_marksActiveEmployeesWhenEnabled() {
        Employee employee = new Employee();
        employee.setId(11L);
        employee.setFullName("Asha");

        service.setEnabled(true);
        when(employeeService.activeStaff()).thenReturn(List.of(employee));
        when(employeeService.countActiveStaff()).thenReturn(1L);

        service.runDailyAutoMark();

        verify(attendanceService).markIfMissing(eq(11L), any(LocalDate.class), eq(AttendanceStatus.PRESENT), any(), any());
    }

    @Test
    void runDailyAutoMark_doesNothingWhenDisabled() {
        service.setEnabled(false);

        service.runDailyAutoMark();

        verify(attendanceService, never()).markIfMissing(any(Long.class), any(LocalDate.class), any(AttendanceStatus.class), any(), any());
    }
}
