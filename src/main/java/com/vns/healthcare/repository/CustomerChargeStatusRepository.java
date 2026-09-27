package com.vns.healthcare.repository;

import com.vns.healthcare.entity.CustomerChargeStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerChargeStatusRepository extends JpaRepository<CustomerChargeStatus, Long> {

    Optional<CustomerChargeStatus> findByCustomerIdAndPayYearAndPayMonth(Long customerId, int payYear, int payMonth);

    List<CustomerChargeStatus> findByCustomerIdAndPayYearOrderByPayMonthAsc(Long customerId, int payYear);
}
