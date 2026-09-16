package com.vns.healthcare.service;

import com.vns.healthcare.domain.SalaryPayStatus;
import com.vns.healthcare.entity.Employee;
import com.vns.healthcare.entity.SalaryPayment;
import com.vns.healthcare.repository.SalaryPaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalaryPaymentServiceTest {

    @Mock
    private SalaryPaymentRepository paymentRepository;

    @Mock
    private EmployeeService employeeService;

    @InjectMocks
    private SalaryPaymentService salaryPaymentService;

    @Test
    void mark_persistsNotesAndPaidOnForPaidStatus() {
        Employee employee = new Employee();
        employee.setId(15L);
        employee.setSalary(BigDecimal.valueOf(18000));

        when(employeeService.get(15L)).thenReturn(employee);
        when(paymentRepository.findByEmployeeIdAndPayYearAndPayMonth(15L, 2026, 5)).thenReturn(Optional.empty());

        salaryPaymentService.mark(15L, 2026, 5, SalaryPayStatus.PAID, null, "Approved by finance");

        ArgumentCaptor<SalaryPayment> captor = ArgumentCaptor.forClass(SalaryPayment.class);
        verify(paymentRepository).save(captor.capture());

        SalaryPayment saved = captor.getValue();
        assertEquals("Approved by finance", saved.getNotes());
        assertEquals(BigDecimal.valueOf(18000), saved.getAmount());
        assertNotNull(saved.getPaidOn());
        assertEquals(SalaryPayStatus.PAID, saved.getStatus());
    }
}
