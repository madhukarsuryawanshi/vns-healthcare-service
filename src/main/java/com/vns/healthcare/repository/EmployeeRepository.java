package com.vns.healthcare.repository;

import com.vns.healthcare.domain.EmployeeStatus;
import com.vns.healthcare.entity.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    List<Employee> findTop5ByOrderByCreatedAtDesc();


    Optional<Employee> findByEmpCode(String empCode);

    @EntityGraph(attributePaths = {"documents", "knownLanguages"})
    @Query("SELECT e FROM Employee e WHERE e.id = :id")
    Optional<Employee> findWithDocuments(@Param("id") Long id);

    boolean existsByAadharNumber(String aadharNumber);

    boolean existsByAadharNumberAndIdNot(String aadharNumber, Long id);

    long countByOnboardedTrue();

    long countByStatus(EmployeeStatus status);

    @Query("SELECT e FROM Employee e WHERE e.status = :status ORDER BY e.fullName")
    List<Employee> findAllActive(@Param("status") EmployeeStatus status);

    @Query("SELECT e FROM Employee e WHERE e.status = :status")
    Page<Employee> findActivePage(@Param("status") EmployeeStatus status, Pageable pageable);

    @Query("SELECT e FROM Employee e WHERE " +
            "LOWER(e.fullName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "e.mobileNo LIKE CONCAT('%', :q, '%') OR " +
            "e.aadharNumber LIKE CONCAT('%', :q, '%') OR " +
            "LOWER(e.empCode) LIKE LOWER(CONCAT('%', :q, '%'))")
    List<Employee> search(@Param("q") String query);

    @Query(value = "SELECT e FROM Employee e WHERE " +
            "LOWER(e.fullName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "e.mobileNo LIKE CONCAT('%', :q, '%') OR " +
            "e.aadharNumber LIKE CONCAT('%', :q, '%') OR " +
            "LOWER(e.empCode) LIKE LOWER(CONCAT('%', :q, '%'))",
            countQuery = "SELECT count(e) FROM Employee e WHERE " +
            "LOWER(e.fullName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "e.mobileNo LIKE CONCAT('%', :q, '%') OR " +
            "e.aadharNumber LIKE CONCAT('%', :q, '%') OR " +
            "LOWER(e.empCode) LIKE LOWER(CONCAT('%', :q, '%'))")
    Page<Employee> search(@Param("q") String query, Pageable pageable);

    @Query(value = "SELECT e FROM Employee e WHERE e.status = :status",
            countQuery = "SELECT count(e) FROM Employee e WHERE e.status = :status")
    Page<Employee> findByStatus(@Param("status") EmployeeStatus status, Pageable pageable);

    @Query(value = "SELECT e FROM Employee e WHERE " +
            "(LOWER(e.fullName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "e.mobileNo LIKE CONCAT('%', :q, '%') OR " +
            "e.aadharNumber LIKE CONCAT('%', :q, '%') OR " +
            "LOWER(e.empCode) LIKE LOWER(CONCAT('%', :q, '%'))) AND e.status = :status",
            countQuery = "SELECT count(e) FROM Employee e WHERE " +
            "(LOWER(e.fullName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "e.mobileNo LIKE CONCAT('%', :q, '%') OR " +
            "e.aadharNumber LIKE CONCAT('%', :q, '%') OR " +
            "LOWER(e.empCode) LIKE LOWER(CONCAT('%', :q, '%'))) AND e.status = :status")
    Page<Employee> searchByStatus(@Param("q") String query, @Param("status") EmployeeStatus status, Pageable pageable);
}
