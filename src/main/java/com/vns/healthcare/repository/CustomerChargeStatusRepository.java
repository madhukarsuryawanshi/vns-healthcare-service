package com.vns.healthcare.repository;

import com.vns.healthcare.entity.CustomerChargeStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerChargeStatusRepository extends JpaRepository<CustomerChargeStatus, Long> {

    Optional<CustomerChargeStatus> findByCustomerIdAndPayYearAndPayMonth(Long customerId, int payYear, int payMonth);

    List<CustomerChargeStatus> findByCustomerIdAndPayYearOrderByPayMonthAsc(Long customerId, int payYear);

    List<CustomerChargeStatus> findByCustomerIdAndPayYearBetweenOrderByPayYearAscPayMonthAsc(Long customerId, int startYear, int endYear);

    @org.springframework.data.jpa.repository.Query("SELECT c FROM CustomerChargeStatus c WHERE c.customer.id IN :customerIds AND c.payYear BETWEEN :startYear AND :endYear ORDER BY c.customer.id ASC, c.payYear ASC, c.payMonth ASC")
    List<CustomerChargeStatus> findByCustomerIdsAndPayYearBetweenOrderByCustomerIdAscPayYearAscPayMonthAsc(
            @org.springframework.data.repository.query.Param("customerIds") List<Long> customerIds,
            @org.springframework.data.repository.query.Param("startYear") int startYear,
            @org.springframework.data.repository.query.Param("endYear") int endYear);
}
