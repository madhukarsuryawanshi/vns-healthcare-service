package com.vns.healthcare.repository;

import com.vns.healthcare.domain.EmployeeStatus;
import com.vns.healthcare.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    List<Employee> findAllByOrderByCreatedAtDesc();

    Optional<Employee> findByEmpCode(String empCode);

    @Query("SELECT DISTINCT e FROM Employee e LEFT JOIN FETCH e.documents WHERE e.id = :id")
    Optional<Employee> findWithDocuments(@Param("id") Long id);

    boolean existsByAadharNumber(String aadharNumber);

    boolean existsByAadharNumberAndIdNot(String aadharNumber, Long id);

    long countByOnboardedTrue();

    long countByStatus(EmployeeStatus status);

    @Query("SELECT e FROM Employee e WHERE e.status = :status ORDER BY e.fullName")
    List<Employee> findAllActive(@Param("status") EmployeeStatus status);

    @Query("SELECT e FROM Employee e WHERE " +
            "LOWER(e.fullName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "e.mobileNo LIKE CONCAT('%', :q, '%') OR " +
            "e.aadharNumber LIKE CONCAT('%', :q, '%') OR " +
            "LOWER(e.empCode) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "ORDER BY e.createdAt DESC")
    List<Employee> search(@Param("q") String query);
}
