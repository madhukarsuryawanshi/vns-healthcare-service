package com.vns.healthcare.service;

import com.vns.healthcare.entity.Customer;
import com.vns.healthcare.entity.CustomerDuty;
import com.vns.healthcare.entity.Employee;
import com.vns.healthcare.exception.BusinessException;
import com.vns.healthcare.repository.CustomerDutyRepository;
import com.vns.healthcare.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CustomerDutyService {

    private final CustomerDutyRepository dutyRepository;
    private final CustomerRepository customerRepository;
    private final CustomerService customerService;
    private final EmployeeService employeeService;

    public CustomerDutyService(CustomerDutyRepository dutyRepository,
                              CustomerRepository customerRepository,
                              CustomerService customerService,
                              EmployeeService employeeService) {
        this.dutyRepository = dutyRepository;
        this.customerRepository = customerRepository;
        this.customerService = customerService;
        this.employeeService = employeeService;
    }

    @Transactional(readOnly = true)
    public List<CustomerDuty> list(Long customerId, LocalDate from, LocalDate to) {
        return dutyRepository.findForCustomerInRange(customerId, from, to);
    }

    @Transactional
    public void applyRange(Long customerId, LocalDate from, LocalDate to, Long employeeId, boolean hold) {
        Customer customer = customerService.get(customerId);
        customerService.assertOpen(customer);
        if (from == null || to == null) {
            throw new BusinessException("Choose from and to dates");
        }
        if (from.isAfter(to)) {
            throw new BusinessException("From date cannot be after to date");
        }
        if (from.plusDays(93).isBefore(to)) {
            throw new BusinessException("Assign at most 93 days at a time");
        }
        if (!hold && employeeId == null) {
            throw new BusinessException("Select an employee, or mark the period as Hold");
        }

        Employee employee = hold ? null : employeeService.get(employeeId);
        validateEmployeeAvailability(customer, employee, from, to);

        LocalDate day = from;
        while (!day.isAfter(to)) {
            CustomerDuty duty = dutyRepository.findByCustomerIdAndDutyDate(customerId, day)
                    .orElse(new CustomerDuty());
            duty.setCustomer(customer);
            duty.setDutyDate(day);
            duty.setHold(hold);
            duty.setEmployee(employee);
            dutyRepository.save(duty);
            day = day.plusDays(1);
        }
        if (employee != null) {
            customer.setAssignedEmployee(employee);
        }
    }

    private void validateEmployeeAvailability(Customer customer, Employee employee, LocalDate from, LocalDate to) {
        if (employee == null) {
            return;
        }

        List<Customer> assignedCustomers = customerRepository.findByAssignedEmployeeId(employee.getId());
        for (Customer assignedCustomer : assignedCustomers) {
            if (assignedCustomer == null || assignedCustomer.isClosed()) {
                continue;
            }
            if (!assignedCustomer.getId().equals(customer.getId())) {
                throw new BusinessException("This employee is already assigned to "
                        + assignedCustomer.getPatientName() + ". Please choose another caregiver.");
            }
        }

        List<CustomerDuty> overlappingDuties = dutyRepository.findByEmployeeIdAndDutyDateBetween(employee.getId(), from, to);
        for (CustomerDuty duty : overlappingDuties) {
            if (duty.getCustomer() == null || duty.getCustomer().isClosed()) {
                continue;
            }
            if (!duty.getCustomer().getId().equals(customer.getId())) {
                String ownerName = duty.getCustomer() == null ? "another customer" : duty.getCustomer().getPatientName();
                throw new BusinessException("This employee is already assigned to "
                        + ownerName + " on " + duty.getDutyDate() + ". Please choose another caregiver.");
            }
        }
    }

    @Transactional(readOnly = true)
    public BigDecimal calculateCharges(Customer customer, LocalDate from, LocalDate to) {
        if (customer.getCharges() == null || from == null || to == null || from.isAfter(to)) {
            return BigDecimal.ZERO;
        }
        List<CustomerDuty> duties = dutyRepository.findForCustomerInRange(customer.getId(), from, to);
        Map<LocalDate, CustomerDuty> byDay = new LinkedHashMap<LocalDate, CustomerDuty>();
        for (CustomerDuty duty : duties) {
            byDay.put(duty.getDutyDate(), duty);
        }
        int billable = 0;
        LocalDate day = from;
        while (!day.isAfter(to)) {
            CustomerDuty duty = byDay.get(day);
            if (duty != null && !duty.isHold() && duty.getEmployee() != null) {
                billable++;
            }
            day = day.plusDays(1);
        }
        int divisor = payDaysDivisor(from, to);
        if (divisor <= 0 || billable == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return customer.getCharges()
                .multiply(BigDecimal.valueOf(billable))
                .divide(BigDecimal.valueOf(divisor), 2, RoundingMode.HALF_UP);
    }

    public int payDaysDivisor(LocalDate from, LocalDate to) {
        if (from.getYear() == to.getYear() && from.getMonth() == to.getMonth()) {
            return from.lengthOfMonth();
        }
        return (int) (to.toEpochDay() - from.toEpochDay() + 1);
    }

    public List<LocalDate> daysInRange(LocalDate from, LocalDate to) {
        List<LocalDate> days = new ArrayList<LocalDate>();
        LocalDate day = from;
        while (!day.isAfter(to)) {
            days.add(day);
            day = day.plusDays(1);
        }
        return days;
    }

    @Transactional(readOnly = true)
    public Map<String, CustomerDuty> indexInRange(LocalDate from, LocalDate to) {
        List<CustomerDuty> duties = dutyRepository.findInRange(from, to);
        Map<String, CustomerDuty> byKey = new LinkedHashMap<String, CustomerDuty>();
        for (CustomerDuty duty : duties) {
            byKey.put(duty.getCustomer().getId() + "|" + duty.getDutyDate(), duty);
        }
        return byKey;
    }
}
