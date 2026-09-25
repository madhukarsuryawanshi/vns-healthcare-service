package com.vns.healthcare.repository;

import com.vns.healthcare.entity.Customer;
import com.vns.healthcare.entity.CustomerInvoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerInvoiceRepository extends JpaRepository<CustomerInvoice, Long> {
    List<CustomerInvoice> findByCustomerOrderByFromDateDesc(Customer customer);
    List<CustomerInvoice> findByCustomerAndInvoiceYearAndInvoiceMonthOrderByFromDateDesc(Customer customer, Integer invoiceYear, Integer invoiceMonth);
}
