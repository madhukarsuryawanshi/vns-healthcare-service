package com.vns.healthcare.service;

import com.vns.healthcare.domain.CustomerStatus;
import com.vns.healthcare.entity.Customer;
import com.vns.healthcare.entity.Employee;
import com.vns.healthcare.repository.CustomerDocumentRepository;
import com.vns.healthcare.repository.CustomerRepository;
import com.vns.healthcare.web.CustomerForm;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CustomerDocumentRepository documentRepository;

    @Mock
    private EmployeeService employeeService;

    @Mock
    private CodeGeneratorService codeGeneratorService;

    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private CustomerService customerService;

    @Test
    void create_usesPatientNameAsCustomerIdentity() {
        CustomerForm form = new CustomerForm();
        form.setPatientName("Sarla Malhotra");
        form.setMobileNo("9876543210");
        form.setAddress("Bengaluru");
        form.setAge(52);
        form.setServiceStartDate(LocalDate.of(2026, 9, 1));
        form.setServiceType("HOURS_24");
        form.setCharges(BigDecimal.valueOf(25000));
        form.setAdvancePayment(BigDecimal.ZERO);

        when(codeGeneratorService.nextCustomerCode()).thenReturn("CUS-1001");
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Customer saved = customerService.create(form);

        assertEquals("CUS-1001", saved.getCustCode());
        assertEquals("Sarla Malhotra", saved.getPatientName());
    }

    @Test
    void assignEmployee_releasesAssignmentWhenUnassigned() {
        Employee employee = new Employee();
        employee.setId(7L);
        employee.setFullName("Madhukar");

        Customer customer = new Customer();
        customer.setId(5L);
        customer.setStatus(CustomerStatus.ASSIGNED);
        customer.setAssignedEmployee(employee);

        when(customerRepository.findWithEmployee(5L)).thenReturn(java.util.Optional.of(customer));

        customerService.assignEmployee(5L, null);

        assertNull(customer.getAssignedEmployee());
        assertEquals(CustomerStatus.NEW, customer.getStatus());
    }

    @Test
    void closeService_clearsAssignedEmployeeAndMarksCustomerClosed() {
        Employee employee = new Employee();
        employee.setId(9L);
        employee.setFullName("Harish");

        Customer customer = new Customer();
        customer.setId(15L);
        customer.setStatus(CustomerStatus.ASSIGNED);
        customer.setAssignedEmployee(employee);
        customer.setCharges(BigDecimal.valueOf(20000));

        CustomerDutyService dutyService = new CustomerDutyService(null, null, null, null);
        // use a mock from Mockito instead of a real instance with null dependencies
        CustomerDutyService dutyServiceMock = org.mockito.Mockito.mock(CustomerDutyService.class);
        org.mockito.Mockito.when(dutyServiceMock.calculateCharges(any(Customer.class), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(BigDecimal.valueOf(1500.00));

        when(customerRepository.findWithEmployee(15L)).thenReturn(java.util.Optional.of(customer));
        when(customerRepository.save(customer)).thenReturn(customer);

        Customer closed = customerService.closeService(15L, dutyServiceMock);

        assertEquals(CustomerStatus.CLOSED, closed.getStatus());
        assertNull(closed.getAssignedEmployee());
        assertEquals(BigDecimal.valueOf(1500.00), closed.getBilledAmount());
        assertEquals(LocalDate.now(), closed.getServiceClosedDate());
    }
}
