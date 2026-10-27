package com.vns.healthcare.service;

import com.vns.healthcare.domain.CustomerStatus;
import com.vns.healthcare.domain.Gender;
import com.vns.healthcare.domain.ServiceType;
import com.vns.healthcare.entity.Customer;
import com.vns.healthcare.entity.CustomerDuty;
import com.vns.healthcare.entity.Employee;
import com.vns.healthcare.repository.CustomerDutyRepository;
import com.vns.healthcare.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerDutyServiceBillingTest {

    @Mock
    private CustomerDutyRepository dutyRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CustomerService customerService;

    @Mock
    private EmployeeService employeeService;

    private CustomerDutyService service;

    @BeforeEach
    void setUp() {
        service = new CustomerDutyService(dutyRepository, customerRepository, customerService, employeeService);
    }

    @Test
    void payDaysDivisor_returnsMonthLength_forSingleMonthRange() {
        assertEquals(30, service.payDaysDivisor(LocalDate.of(2025, 9, 1), LocalDate.of(2025, 9, 30)));
    }

    @Test
    void payDaysDivisor_returnsInclusiveDays_forCrossMonthRange() {
        assertEquals(39, service.payDaysDivisor(LocalDate.of(2025, 9, 1), LocalDate.of(2025, 10, 9)));
    }

    @Test
    void calculateCharges_usesDailyChargeAcrossExactBillingSpan() {
        Customer customer = new Customer();
        customer.setId(10L);
        customer.setCharges(BigDecimal.valueOf(1000));
        customer.setStatus(CustomerStatus.ASSIGNED);

        LocalDate from = LocalDate.of(2025, 9, 1);
        LocalDate to = LocalDate.of(2025, 10, 9);
        Employee employee = new Employee();
        employee.setId(5L);
        employee.setFullName("Madhukar");
        employee.setGender(Gender.MALE);

        List<CustomerDuty> duties = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            CustomerDuty duty = new CustomerDuty();
            duty.setCustomer(customer);
            duty.setDutyDate(day);
            duty.setEmployee(employee);
            duty.setHold(false);
            duties.add(duty);
        }

        when(dutyRepository.findForCustomerInRange(10L, from, to)).thenReturn(duties);

        BigDecimal total = service.calculateCharges(customer, from, to);

        assertEquals(0, total.compareTo(BigDecimal.valueOf(39000).setScale(2, java.math.RoundingMode.HALF_UP)));
    }
}
