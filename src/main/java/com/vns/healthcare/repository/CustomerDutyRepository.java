package com.vns.healthcare.repository;

import com.vns.healthcare.entity.CustomerDuty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CustomerDutyRepository extends JpaRepository<CustomerDuty, Long> {

    Optional<CustomerDuty> findByCustomerIdAndDutyDate(Long customerId, LocalDate dutyDate);

    List<CustomerDuty> findByEmployeeId(Long employeeId);

    @Query("SELECT d FROM CustomerDuty d LEFT JOIN FETCH d.employee WHERE d.customer.id = :customerId AND d.dutyDate BETWEEN :fromDate AND :toDate ORDER BY d.dutyDate")
    List<CustomerDuty> findForCustomerInRange(@Param("customerId") Long customerId,
                                              @Param("fromDate") LocalDate fromDate,
                                              @Param("toDate") LocalDate toDate);

    @Query("SELECT d FROM CustomerDuty d LEFT JOIN FETCH d.employee LEFT JOIN FETCH d.customer WHERE d.dutyDate BETWEEN :fromDate AND :toDate")
    List<CustomerDuty> findInRange(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);
}
