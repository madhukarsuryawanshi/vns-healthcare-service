package com.vns.healthcare.service;

import com.vns.healthcare.domain.CustomerStatus;
import com.vns.healthcare.domain.EmployeeStatus;
import com.vns.healthcare.domain.Gender;
import com.vns.healthcare.entity.Customer;
import com.vns.healthcare.entity.CustomerDuty;
import com.vns.healthcare.entity.Employee;
import com.vns.healthcare.repository.AttendanceRepository;
import com.vns.healthcare.repository.CustomerDutyRepository;
import com.vns.healthcare.repository.CustomerRepository;
import com.vns.healthcare.repository.EmployeeDocumentRepository;
import com.vns.healthcare.repository.EmployeeRepository;
import com.vns.healthcare.repository.SalaryPaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private EmployeeDocumentRepository documentRepository;

    @Mock
    private AttendanceRepository attendanceRepository;

    @Mock
    private SalaryPaymentRepository salaryPaymentRepository;

    @Mock
    private CustomerDutyRepository customerDutyRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CodeGeneratorService codeGeneratorService;

    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private EmployeeService employeeService;

    @Test
    void resign_releasesAssignmentsAndMarksEmployeeResigned() {
        Employee employee = employee(7L, "EMP-1007", "Madhukar");
        Customer customer = customer(88L, "CUS-1001", "Sarla Malhotra");
        customer.setAssignedEmployee(employee);
        customer.setStatus(CustomerStatus.ASSIGNED);

        CustomerDuty duty = new CustomerDuty();
        duty.setCustomer(customer);
        duty.setDutyDate(LocalDate.of(2026, 9, 12));
        duty.setEmployee(employee);

        when(employeeRepository.findWithDocuments(7L)).thenReturn(java.util.Optional.of(employee));
        when(customerDutyRepository.findByEmployeeId(7L)).thenReturn(Collections.singletonList(duty));
        when(customerRepository.findByAssignedEmployeeId(7L)).thenReturn(Collections.singletonList(customer));
        when(employeeRepository.save(employee)).thenReturn(employee);

        employeeService.resign(7L);

        assertNull(duty.getEmployee());
        assertNull(customer.getAssignedEmployee());
        assertEquals(CustomerStatus.NEW, customer.getStatus());
        assertEquals(EmployeeStatus.RESIGNED, employee.getStatus());

        ArgumentCaptor<java.util.List<CustomerDuty>> dutyCaptor = ArgumentCaptor.forClass(java.util.List.class);
        verify(customerDutyRepository).saveAll(dutyCaptor.capture());
        assertEquals(1, dutyCaptor.getValue().size());

        ArgumentCaptor<java.util.List<Customer>> customerCaptor = ArgumentCaptor.forClass(java.util.List.class);
        verify(customerRepository).saveAll(customerCaptor.capture());
        assertEquals(1, customerCaptor.getValue().size());
    }

    private Employee employee(Long id, String empCode, String fullName) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setEmpCode(empCode);
        employee.setFullName(fullName);
        employee.setMobileNo("9900000000");
        employee.setJoiningDate(LocalDate.of(2025, 1, 1));
        employee.setGender(Gender.MALE);
        employee.setDateOfBirth(LocalDate.of(1990, 1, 1));
        employee.setFullAddress("Employee address");
        employee.setAadharNumber("123456789012");
        employee.setStatus(EmployeeStatus.ACTIVE);
        return employee;
    }

    private Customer customer(Long id, String custCode, String patientName) {
        Customer customer = new Customer();
        customer.setId(id);
        customer.setCustCode(custCode);
        customer.setPatientName(patientName);
        customer.setFullName("Contact person");
        customer.setMobileNo("9111111111");
        customer.setAddress("Address");
        customer.setAge(62);
        customer.setServiceType(com.vns.healthcare.domain.ServiceType.HOURS_24);
        customer.setCharges(java.math.BigDecimal.valueOf(25000));
        customer.setStatus(CustomerStatus.ASSIGNED);
        return customer;
    }
}
