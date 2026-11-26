package com.vns.healthcare.service;

import com.vns.healthcare.entity.Customer;
import com.vns.healthcare.entity.CustomerDuty;
import com.vns.healthcare.entity.Employee;
import com.vns.healthcare.exception.BusinessException;
import com.vns.healthcare.repository.CustomerDutyRepository;
import com.vns.healthcare.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class CustomerDutyService {

    private static final Logger log = LoggerFactory.getLogger(CustomerDutyService.class);

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
        log.debug("Fetching duty list for customer [{}] from [{}] to [{}]", customerId, from, to);
        return dutyRepository.findForCustomerInRange(customerId, from, to);
    }

    @Transactional
    public void applyRange(Long customerId, LocalDate from, LocalDate to, Long employeeId, boolean hold) {
        log.info("Applying customer duty range for customer [{}], from [{}], to [{}], employee [{}], hold [{}]",
                customerId, from, to, employeeId, hold);
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

        dutyRepository.deleteForCustomerInRange(customerId, from, to);

        LocalDate day = from;
        while (!day.isAfter(to)) {
            CustomerDuty duty = new CustomerDuty();
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
        continueFromLatestRow(customer);
        log.info("Customer duty range applied successfully for customer [{}]", customerId);
    }

    @Transactional
    public void continueFromLatestRow(Customer customer) {
        if (customer == null || customer.getId() == null || customer.isClosed()) {
            return;
        }

        List<CustomerDuty> latestRows = dutyRepository.findByCustomerIdOrderByDutyDateDesc(customer.getId());
        Employee continuationEmployee = null;
        LocalDate latestDate = null;
        for (CustomerDuty row : latestRows) {
            if (row == null) {
                continue;
            }
            if (row.isHold()) {
                return;
            }
            if (row.getEmployee() != null) {
                continuationEmployee = row.getEmployee();
                latestDate = row.getDutyDate();
                break;
            }
        }
        if (continuationEmployee == null || latestDate == null) {
            return;
        }

        LocalDate end = LocalDate.now();
        if (customer.getServiceClosedDate() != null && customer.getServiceClosedDate().isBefore(end)) {
            end = customer.getServiceClosedDate();
        }
        if (customer.getServiceStartDate() != null && end.isBefore(customer.getServiceStartDate())) {
            return;
        }

        LocalDate day = latestDate.plusDays(1);
        while (!day.isAfter(end)) {
            Optional<CustomerDuty> existing = dutyRepository.findByCustomerIdAndDutyDate(customer.getId(), day);
            if (existing.isPresent()) {
                CustomerDuty existingRow = existing.get();
                if (existingRow.isHold() || existingRow.getEmployee() != null) {
                    day = day.plusDays(1);
                    continue;
                }
            }

            validateEmployeeAvailability(customer, continuationEmployee, day, day);
            CustomerDuty duty = existing.orElseGet(CustomerDuty::new);
            duty.setCustomer(customer);
            duty.setDutyDate(day);
            duty.setHold(false);
            duty.setEmployee(continuationEmployee);
            dutyRepository.save(duty);
            day = day.plusDays(1);
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
        log.debug("Calculating charges for customer [{}] from [{}] to [{}]", customer.getId(), from, to);
        if (customer.getCharges() == null || from == null || to == null || from.isAfter(to)) {
            return BigDecimal.ZERO;
        }
        int billable = countBillableDays(customer, from, to);
        if (billable <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal total = customer.getCharges()
                .multiply(BigDecimal.valueOf(billable))
                .setScale(2, RoundingMode.HALF_UP);
        log.debug("Calculated charge total for customer [{}]: {}", customer.getId(), total);
        return total;
    }

    @Transactional(readOnly = true)
    public Employee resolveCurrentAssignedEmployee(Customer customer) {
        if (customer == null || customer.getId() == null) {
            return null;
        }
        LocalDate from = customer.getServiceStartDate() != null ? customer.getServiceStartDate() : LocalDate.of(2000, 1, 1);
        LocalDate to = customer.isClosed() && customer.getServiceClosedDate() != null
                ? customer.getServiceClosedDate()
                : LocalDate.now();
        if (to.isBefore(from)) {
            return customer.getAssignedEmployee();
        }
        List<CustomerDuty> duties = dutyRepository.findForCustomerInRange(customer.getId(), from, to);
        for (int i = duties.size() - 1; i >= 0; i--) {
            CustomerDuty duty = duties.get(i);
            if (duty != null && !duty.isHold() && duty.getEmployee() != null) {
                return duty.getEmployee();
            }
        }
        return customer.getAssignedEmployee();
    }

    @Transactional(readOnly = true)
    public int countBillableDays(Customer customer, LocalDate from, LocalDate to) {
        if (customer == null || customer.getId() == null || from == null || to == null || from.isAfter(to)) {
            return 0;
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
            if (isBillable(duty)) {
                billable++;
            }
            day = day.plusDays(1);
        }
        return billable;
    }

    private boolean isBillable(CustomerDuty duty) {
        return duty != null && !duty.isHold() && duty.getEmployee() != null;
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
        log.debug("Indexing customer duties for range [{}] to [{}]", from, to);
        if (from == null || to == null || from.isAfter(to)) {
            return new LinkedHashMap<String, CustomerDuty>();
        }

        List<CustomerDuty> duties = dutyRepository.findInRange(from, to);
        Map<Long, Map<LocalDate, CustomerDuty>> explicitByCustomer = new LinkedHashMap<Long, Map<LocalDate, CustomerDuty>>();
        for (CustomerDuty duty : duties) {
            if (duty == null || duty.getCustomer() == null || duty.getDutyDate() == null) {
                continue;
            }
            Map<LocalDate, CustomerDuty> customerDuties = explicitByCustomer.computeIfAbsent(
                    duty.getCustomer().getId(), id -> new LinkedHashMap<LocalDate, CustomerDuty>()) ;
            customerDuties.put(duty.getDutyDate(), duty);
        }

        Map<String, CustomerDuty> byKey = new LinkedHashMap<String, CustomerDuty>();
        java.util.Set<Long> customerIds = new java.util.LinkedHashSet<Long>(explicitByCustomer.keySet());

        LocalDate day;
        for (Long customerId : customerIds) {
            Map<LocalDate, CustomerDuty> explicit = explicitByCustomer.get(customerId);
            CustomerDuty carry = null;
            List<CustomerDuty> priorRows = dutyRepository.findBeforeForCustomer(customerId, from);
            if (priorRows != null && !priorRows.isEmpty()) {
                carry = priorRows.get(0);
            }

            day = from;
            while (!day.isAfter(to)) {
                CustomerDuty explicitDuty = explicit.get(day);
                if (explicitDuty != null) {
                    carry = explicitDuty;
                } else if (carry != null) {
                    CustomerDuty carriedDuty = new CustomerDuty();
                    carriedDuty.setCustomer(carry.getCustomer());
                    carriedDuty.setDutyDate(day);
                    carriedDuty.setHold(carry.isHold());
                    carriedDuty.setEmployee(carry.isHold() ? null : carry.getEmployee());
                    carry = carriedDuty;
                }

                if (carry != null) {
                    String key = customerId + "|" + day.toString();
                    byKey.put(key, carry);
                }
                day = day.plusDays(1);
            }
        }

        log.debug("Indexed {} customer duties (including carry-forward) in range [{}] to [{}]", byKey.size(), from, to);
        return byKey;
    }
}
