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

    List<SalaryPayment> findByEmployeeIdAndPayYearBetweenOrderByPayYearAscPayMonthAsc(Long employeeId, int startYear, int endYear);

    @Query("SELECT p FROM SalaryPayment p WHERE p.employee.id IN :employeeIds AND p.payYear = :year ORDER BY p.employee.id ASC, p.payMonth ASC")
    List<SalaryPayment> findByEmployeeIdsAndPayYearOrderByEmployeeIdAscPayMonthAsc(
            @Param("employeeIds") List<Long> employeeIds,
            @Param("year") int year);

    @Query("SELECT p FROM SalaryPayment p WHERE p.employee.id IN :employeeIds AND p.payYear BETWEEN :startYear AND :endYear ORDER BY p.employee.id ASC, p.payYear ASC, p.payMonth ASC")
    List<SalaryPayment> findByEmployeeIdsAndPayYearBetweenOrderByEmployeeIdAscPayYearAscPayMonthAsc(
            @Param("employeeIds") List<Long> employeeIds,
            @Param("startYear") int startYear,
            @Param("endYear") int endYear);

    @Query("SELECT p FROM SalaryPayment p JOIN FETCH p.employee WHERE p.payYear = :year")
    List<SalaryPayment> findByYearWithEmployee(@Param("year") int year);
}
