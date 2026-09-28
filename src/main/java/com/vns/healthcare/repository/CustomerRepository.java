package com.vns.healthcare.repository;

import com.vns.healthcare.domain.CustomerStatus;
import com.vns.healthcare.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    @EntityGraph(attributePaths = {"assignedEmployee", "documents"})
    @Query("SELECT c FROM Customer c WHERE c.id = :id")
    Optional<Customer> findWithEmployee(@Param("id") Long id);

    List<Customer> findByAssignedEmployeeId(Long employeeId);

    long countByAssignedEmployeeIsNotNull();

    @EntityGraph(attributePaths = {"assignedEmployee"})
    @Query("SELECT c FROM Customer c ORDER BY c.createdAt DESC")
    List<Customer> findAllWithEmployee();

    @EntityGraph(attributePaths = {"assignedEmployee"})
    @Query("SELECT c FROM Customer c LEFT JOIN c.assignedEmployee e ORDER BY c.createdAt DESC")
    Page<Customer> findAllWithEmployee(Pageable pageable);

    @EntityGraph(attributePaths = {"assignedEmployee"})
    @Query("SELECT c FROM Customer c LEFT JOIN c.assignedEmployee e WHERE c.status = :status ORDER BY c.createdAt DESC")
    Page<Customer> findByStatus(@Param("status") CustomerStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"assignedEmployee"})
    @Query(value = "SELECT c FROM Customer c LEFT JOIN c.assignedEmployee e WHERE " +
            "LOWER(c.fullName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "c.mobileNo LIKE CONCAT('%', :q, '%') OR " +
            "LOWER(c.patientName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(c.custCode) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(e.fullName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(e.empCode) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "ORDER BY c.createdAt DESC",
            countQuery = "SELECT count(c) FROM Customer c LEFT JOIN c.assignedEmployee e WHERE " +
                    "LOWER(c.fullName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
                    "c.mobileNo LIKE CONCAT('%', :q, '%') OR " +
                    "LOWER(c.patientName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
                    "LOWER(c.custCode) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
                    "LOWER(e.fullName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
                    "LOWER(e.empCode) LIKE LOWER(CONCAT('%', :q, '%'))")
    Page<Customer> search(@Param("q") String query, Pageable pageable);

    @EntityGraph(attributePaths = {"assignedEmployee"})
    @Query(value = "SELECT c FROM Customer c LEFT JOIN c.assignedEmployee e WHERE " +
            "(LOWER(c.fullName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "c.mobileNo LIKE CONCAT('%', :q, '%') OR " +
            "LOWER(c.patientName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(c.custCode) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(e.fullName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(e.empCode) LIKE LOWER(CONCAT('%', :q, '%'))) AND c.status = :status " +
            "ORDER BY c.createdAt DESC",
            countQuery = "SELECT count(c) FROM Customer c LEFT JOIN c.assignedEmployee e WHERE " +
                    "(LOWER(c.fullName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
                    "c.mobileNo LIKE CONCAT('%', :q, '%') OR " +
                    "LOWER(c.patientName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
                    "LOWER(c.custCode) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
                    "LOWER(e.fullName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
                    "LOWER(e.empCode) LIKE LOWER(CONCAT('%', :q, '%'))) AND c.status = :status")
    Page<Customer> searchByStatus(@Param("q") String query, @Param("status") CustomerStatus status, Pageable pageable);

    @Query("SELECT c FROM Customer c LEFT JOIN FETCH c.assignedEmployee WHERE " +
            "LOWER(c.fullName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "c.mobileNo LIKE CONCAT('%', :q, '%') OR " +
            "LOWER(c.patientName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(c.custCode) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(c.assignedEmployee.fullName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(c.assignedEmployee.empCode) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "ORDER BY c.createdAt DESC")
    List<Customer> search(@Param("q") String query);
}
