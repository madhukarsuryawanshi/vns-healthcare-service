package com.vns.healthcare.service;

import com.vns.healthcare.domain.CustomerStatus;
import com.vns.healthcare.domain.Gender;
import com.vns.healthcare.domain.ServiceType;
import com.vns.healthcare.entity.Customer;
import com.vns.healthcare.entity.Employee;
import com.vns.healthcare.exception.BusinessException;
import com.vns.healthcare.repository.CustomerDutyRepository;
import com.vns.healthcare.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerDutyServiceTest {

    @Mock
    private CustomerDutyRepository dutyRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CustomerService customerService;

    @Mock
    private EmployeeService employeeService;

    @InjectMocks
    private CustomerDutyService customerDutyService;

    @Test
    void applyRange_rejectsDuplicateAssignmentForAnotherCustomer() {
        Customer current = customer(10L, "CUS-1001", "Sarla Malhotra");
        Employee employee = employee(20L, "EMP-1007", "Madhukar");

        when(customerService.get(10L)).thenReturn(current);
        when(employeeService.get(20L)).thenReturn(employee);

        Customer alreadyAssigned = customer(11L, "CUS-2002", "Ramesh");
        alreadyAssigned.setAssignedEmployee(employee);
        alreadyAssigned.setStatus(CustomerStatus.ASSIGNED);
        when(customerRepository.findByAssignedEmployeeId(20L)).thenReturn(Collections.singletonList(alreadyAssigned));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> customerDutyService.applyRange(10L, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10), 20L, false));

        assertTrue(ex.getMessage().contains("already assigned to Ramesh"));
    }

    @Test
    void applyRange_ignoresClosedCustomerAssignmentsDuringValidation() {
        Customer current = customer(10L, "CUS-1001", "Sarla Malhotra");
        Employee employee = employee(20L, "EMP-1007", "Madhukar");

        when(customerService.get(10L)).thenReturn(current);
        when(employeeService.get(20L)).thenReturn(employee);

        Customer closedCustomer = customer(12L, "CUS-3003", "Closed case");
        closedCustomer.setAssignedEmployee(employee);
        closedCustomer.setStatus(CustomerStatus.CLOSED);
        when(customerRepository.findByAssignedEmployeeId(20L)).thenReturn(Collections.singletonList(closedCustomer));
        when(dutyRepository.findByEmployeeIdAndDutyDateBetween(eq(20L), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());
        when(dutyRepository.findByCustomerIdAndDutyDate(10L, LocalDate.of(2026, 9, 10))).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> customerDutyService.applyRange(10L, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10), 20L, false));
    }

    @Test
    void payDaysDivisor_usesMonthLength_forSingleMonthRange() {
        assertEquals(30, customerDutyService.payDaysDivisor(LocalDate.of(2025, 9, 1), LocalDate.of(2025, 9, 30)));
    }

    @Test
    void payDaysDivisor_usesInclusiveDayCount_whenRangeCrossesMonthBoundary() {
        assertEquals(39, customerDutyService.payDaysDivisor(LocalDate.of(2025, 9, 1), LocalDate.of(2025, 10, 9)));
    }

    @Test
    void calculateCharges_keepsMonthlyChargeEquivalent_forCrossMonthRange() {
        Customer customer = customer(10L, "CUS-1001", "Sarla Malhotra");
        customer.setCharges(BigDecimal.valueOf(30000));

        LocalDate from = LocalDate.of(2025, 9, 1);
        LocalDate to = LocalDate.of(2025, 10, 9);
        Employee employee = employee(20L, "EMP-1007", "Madhukar");

        List<com.vns.healthcare.entity.CustomerDuty> duties = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            com.vns.healthcare.entity.CustomerDuty duty = new com.vns.healthcare.entity.CustomerDuty();
            duty.setCustomer(customer);
            duty.setDutyDate(day);
            duty.setEmployee(employee);
            duty.setHold(false);
            duties.add(duty);
        }

        when(dutyRepository.findForCustomerInRange(10L, from, to)).thenReturn(duties);

        BigDecimal total = customerDutyService.calculateCharges(customer, from, to);
        assertEquals(0, total.compareTo(BigDecimal.valueOf(30000).setScale(2, java.math.RoundingMode.HALF_UP)));
    }

    private Customer customer(Long id, String custCode, String patientName) {
        Customer customer = new Customer();
        customer.setId(id);
        customer.setCustCode(custCode);
        customer.setPatientName(patientName);
        customer.setFullName("Family contact");
        customer.setMobileNo("9999999999");
        customer.setAddress("Address");
        customer.setAge(52);
        customer.setServiceType(ServiceType.HOURS_24);
        customer.setCharges(BigDecimal.valueOf(25000));
        customer.setStatus(CustomerStatus.ASSIGNED);
        return customer;
    }

    private Employee employee(Long id, String empCode, String fullName) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setEmpCode(empCode);
        employee.setFullName(fullName);
        employee.setMobileNo("9876543210");
        employee.setJoiningDate(LocalDate.of(2025, 1, 1));
        employee.setGender(Gender.MALE);
        employee.setDateOfBirth(LocalDate.of(1990, 1, 1));
        employee.setFullAddress("Employee address");
        employee.setAadharNumber("123456789012");
        employee.setStatus(com.vns.healthcare.domain.EmployeeStatus.ACTIVE);
        return employee;
    }
}
