package com.vns.healthcare.service;

import com.vns.healthcare.domain.ChargePayStatus;
import com.vns.healthcare.entity.Customer;
import com.vns.healthcare.entity.CustomerChargeStatus;
import com.vns.healthcare.exception.BusinessException;
import com.vns.healthcare.repository.CustomerChargeStatusRepository;
import com.vns.healthcare.web.CustomerChargeMonthView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CustomerChargeStatusService {

    private final CustomerChargeStatusRepository customerChargeStatusRepository;
    private final CustomerService customerService;

    public CustomerChargeStatusService(CustomerChargeStatusRepository customerChargeStatusRepository,
                                      CustomerService customerService) {
        this.customerChargeStatusRepository = customerChargeStatusRepository;
        this.customerService = customerService;
    }

    @Transactional(readOnly = true)
    public List<CustomerChargeMonthView> monthsForCustomer(Customer customer, int year) {
        Map<Integer, CustomerChargeStatus> byMonth = new LinkedHashMap<Integer, CustomerChargeStatus>();
        for (CustomerChargeStatus status : customerChargeStatusRepository.findByCustomerIdAndPayYearOrderByPayMonthAsc(customer.getId(), year)) {
            byMonth.put(status.getPayMonth(), status);
        }
        List<CustomerChargeMonthView> months = new ArrayList<CustomerChargeMonthView>();
        YearMonth now = YearMonth.now();
        for (int m = 1; m <= 12; m++) {
            YearMonth ym = YearMonth.of(year, m);
            CustomerChargeStatus record = byMonth.get(m);
            months.add(new CustomerChargeMonthView(ym, resolveStatus(ym, record, now), record, customer.getServiceStartDate()));
        }
        return months;
    }

    @Transactional
    public void save(Long customerId, int year, int month, ChargePayStatus status, LocalDate paidOn, String remarks) {
        if (month < 1 || month > 12) {
            throw new BusinessException("Invalid month");
        }
        Customer customer = customerService.get(customerId);
        YearMonth targetMonth = YearMonth.of(year, month);
        LocalDate startDate = customer.getServiceStartDate();
        if (startDate != null && targetMonth.isBefore(YearMonth.from(startDate))) {
            throw new BusinessException("This month is before the service start date and cannot be edited.");
        }

        CustomerChargeStatus record = customerChargeStatusRepository
                .findByCustomerIdAndPayYearAndPayMonth(customerId, year, month)
                .orElse(new CustomerChargeStatus());

        record.setCustomer(customer);
        record.setPayYear(year);
        record.setPayMonth(month);
        record.setStatus(status == null ? ChargePayStatus.UNPAID : status);
        record.setRemarks(blankToNull(remarks));
        if (record.getStatus() == ChargePayStatus.PAID) {
            record.setPaidOn(paidOn == null ? LocalDate.now() : paidOn);
        } else {
            record.setPaidOn(null);
        }
        customerChargeStatusRepository.save(record);
    }

    public ChargePayStatus resolveStatus(YearMonth month, CustomerChargeStatus record, YearMonth now) {
        if (record != null && record.getStatus() != null) {
            return record.getStatus();
        }
        if (month.equals(now)) {
            return ChargePayStatus.IN_PROGRESS;
        }
        if (month.isAfter(now)) {
            return null;
        }
        return ChargePayStatus.UNPAID;
    }

    private String blankToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
