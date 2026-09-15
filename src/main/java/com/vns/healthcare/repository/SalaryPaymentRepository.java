package com.vns.healthcare.repository;

import com.vns.healthcare.entity.SalaryPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SalaryPaymentRepository extends JpaRepository<SalaryPayment, Long> {

    Optional<SalaryPayment> findByEmployeeIdAndPayYearAndPayMonth(Long employeeId, int payYear, int payMonth);

    List<SalaryPayment> findByEmployeeId(Long employeeId);

    List<SalaryPayment> findByEmployeeIdAndPayYearOrderByPayMonthAsc(Long employeeId, int payYear);

    @Query("SELECT p FROM SalaryPayment p JOIN FETCH p.employee WHERE p.payYear = :year")
    List<SalaryPayment> findByYearWithEmployee(@Param("year") int year);
}
