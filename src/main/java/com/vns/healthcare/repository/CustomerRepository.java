package com.vns.healthcare.repository;

import com.vns.healthcare.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    @Query("SELECT DISTINCT c FROM Customer c LEFT JOIN FETCH c.assignedEmployee LEFT JOIN FETCH c.documents WHERE c.id = :id")
    Optional<Customer> findWithEmployee(@Param("id") Long id);

    List<Customer> findByAssignedEmployeeId(Long employeeId);

    long countByAssignedEmployeeIsNotNull();

    @Query("SELECT DISTINCT c FROM Customer c LEFT JOIN FETCH c.assignedEmployee LEFT JOIN FETCH c.documents ORDER BY c.createdAt DESC")
    List<Customer> findAllWithEmployee();

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
