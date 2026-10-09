package com.vns.healthcare.service;

import com.vns.healthcare.domain.SalaryPayStatus;
import com.vns.healthcare.entity.Employee;
import com.vns.healthcare.entity.SalaryPayment;
import com.vns.healthcare.exception.BusinessException;
import com.vns.healthcare.repository.SalaryPaymentRepository;
import com.vns.healthcare.web.SalaryMonthView;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SalaryPaymentService {

    private final SalaryPaymentRepository paymentRepository;
    private final EmployeeService employeeService;

    public SalaryPaymentService(SalaryPaymentRepository paymentRepository, EmployeeService employeeService) {
        this.paymentRepository = paymentRepository;
        this.employeeService = employeeService;
    }

    @Transactional(readOnly = true)
    public List<SalaryMonthView> monthsForEmployee(Employee employee, int year) {
        Map<Integer, SalaryPayment> byMonth = new LinkedHashMap<Integer, SalaryPayment>();
        for (SalaryPayment payment : paymentRepository.findByEmployeeIdAndPayYearOrderByPayMonthAsc(employee.getId(), year)) {
            byMonth.put(payment.getPayMonth(), payment);
        }
        List<SalaryMonthView> months = new ArrayList<SalaryMonthView>();
        YearMonth now = YearMonth.now();
        for (int m = 1; m <= 12; m++) {
            YearMonth ym = YearMonth.of(year, m);
            SalaryPayment payment = byMonth.get(m);
            months.add(new SalaryMonthView(ym, resolveStatus(employee, ym, payment, now), payment, ym.equals(now)));
        }
        return months;
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "salary-register", key = "#year")
    public Map<Employee, List<SalaryMonthView>> register(int year) {
        List<Employee> staff = employeeService.activeStaff();
        Map<Employee, List<SalaryMonthView>> rows = new LinkedHashMap<Employee, List<SalaryMonthView>>();
        if (staff == null || staff.isEmpty()) {
            return rows;
        }

        List<Long> employeeIds = new ArrayList<Long>();
        for (Employee employee : staff) {
            if (employee != null && employee.getId() != null) {
                employeeIds.add(employee.getId());
            }
        }
        Map<Long, Map<Integer, SalaryPayment>> byEmployeeMonth = new LinkedHashMap<Long, Map<Integer, SalaryPayment>>();
        for (SalaryPayment payment : paymentRepository.findByEmployeeIdsAndPayYearOrderByEmployeeIdAscPayMonthAsc(employeeIds, year)) {
            if (payment == null || payment.getEmployee() == null || payment.getEmployee().getId() == null) {
                continue;
            }
            Long employeeId = payment.getEmployee().getId();
            Map<Integer, SalaryPayment> byMonth = byEmployeeMonth.get(employeeId);
            if (byMonth == null) {
                byMonth = new LinkedHashMap<Integer, SalaryPayment>();
                byEmployeeMonth.put(employeeId, byMonth);
            }
            byMonth.put(payment.getPayMonth(), payment);
        }

        YearMonth now = YearMonth.now();
        for (Employee employee : staff) {
            if (employee == null) {
                continue;
            }
            Map<Integer, SalaryPayment> byMonth = byEmployeeMonth.get(employee.getId());
            List<SalaryMonthView> views = new ArrayList<SalaryMonthView>();
            for (int m = 1; m <= 12; m++) {
                YearMonth ym = YearMonth.of(year, m);
                SalaryPayment payment = byMonth == null ? null : byMonth.get(m);
                views.add(new SalaryMonthView(ym, resolveStatus(employee, ym, payment, now), payment, ym.equals(now)));
            }
            rows.put(detachEmployeeForCache(employee), views);
        }
        return rows;
    }

    @Transactional
    @CacheEvict(value = {"salary-register", "dashboard-stats"}, allEntries = true)
    public void mark(Long employeeId, int year, int month, SalaryPayStatus status, LocalDate paidOn, String notes) {
        if (month < 1 || month > 12) {
            throw new BusinessException("Invalid month");
        }
        Employee employee = employeeService.get(employeeId);
        SalaryPayment payment = paymentRepository
                .findByEmployeeIdAndPayYearAndPayMonth(employeeId, year, month)
                .orElse(new SalaryPayment());
        // Prevent modifications once a salary is marked PAID
        if (payment.getId() != null && payment.getStatus() == SalaryPayStatus.PAID) {
            throw new BusinessException("Salary for the selected month is already PAID and cannot be modified.");
        }
        payment.setEmployee(employee);
        payment.setPayYear(year);
        payment.setPayMonth(month);
        payment.setStatus(status);
        payment.setNotes(blankToNull(notes));
        if (status == SalaryPayStatus.PAID) {
            payment.setPaidOn(paidOn);
            if (payment.getAmount() == null) {
                payment.setAmount(employee.getSalary());
            }
        } else {
            payment.setPaidOn(null);
        }
        paymentRepository.save(payment);
    }

    public SalaryPayStatus resolveStatus(Employee employee, YearMonth month, SalaryPayment payment, YearMonth now) {
        if (payment != null && payment.getStatus() != null) {
            return payment.getStatus();
        }
        LocalDate start = employee.getSalaryStartDate() != null
                ? employee.getSalaryStartDate()
                : employee.getJoiningDate();
        if (start != null && month.isBefore(YearMonth.from(start))) {
            return null;
        }
        if (month.equals(now)) {
            return SalaryPayStatus.IN_PROGRESS;
        }
        if (month.isAfter(now)) {
            return null;
        }
        return SalaryPayStatus.UNPAID;
    }

    private Employee detachEmployeeForCache(Employee employee) {
        if (employee == null) {
            return null;
        }
        Employee detached = new Employee();
        detached.setId(employee.getId());
        detached.setEmpCode(employee.getEmpCode());
        detached.setFullName(employee.getFullName());
        detached.setMobileNo(employee.getMobileNo());
        detached.setJoiningDate(employee.getJoiningDate());
        detached.setDesignation(employee.getDesignation());
        detached.setStatus(employee.getStatus());
        detached.setSalary(employee.getSalary());
        detached.setSalaryStartDate(employee.getSalaryStartDate());
        detached.setKnownLanguages(employee.getKnownLanguages() == null ? new java.util.HashSet<String>() : new java.util.HashSet<String>(employee.getKnownLanguages()));
        detached.setDocuments(new java.util.ArrayList<com.vns.healthcare.entity.EmployeeDocument>());
        return detached;
    }

    private String blankToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
