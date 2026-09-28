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
    @Query("SELECT c FROM Customer c")
    List<Customer> findAllWithEmployee();

    @EntityGraph(attributePaths = {"assignedEmployee"})
    @Query("SELECT c FROM Customer c")
    List<Customer> findTop5WithEmployee();

    @EntityGraph(attributePaths = {"assignedEmployee"})
    @Query("SELECT c FROM Customer c")
    Page<Customer> findAllWithEmployeePage(Pageable pageable);

    @EntityGraph(attributePaths = {"assignedEmployee"})
    @Query("SELECT c FROM Customer c LEFT JOIN c.assignedEmployee e WHERE (:status IS NULL OR c.status = :status) AND (:search IS NULL OR :search = '' OR c.custCode LIKE :prefix OR c.mobileNo LIKE :prefix OR LOWER(c.fullName) LIKE LOWER(:prefix) OR LOWER(c.patientName) LIKE LOWER(:prefix) OR LOWER(e.fullName) LIKE LOWER(:prefix) OR LOWER(e.empCode) LIKE LOWER(:prefix))")
    Page<Customer> searchPage(@Param("status") CustomerStatus status,
                             @Param("search") String search,
                             @Param("prefix") String prefix,
                             Pageable pageable);

    @EntityGraph(attributePaths = {"assignedEmployee"})
    @Query("SELECT c FROM Customer c LEFT JOIN c.assignedEmployee e WHERE (:status IS NULL OR c.status = :status)")
    Page<Customer> findByStatusPage(@Param("status") CustomerStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"assignedEmployee"})
    @Query("SELECT c FROM Customer c WHERE c.assignedEmployee.id = :employeeId ORDER BY c.createdAt DESC")
    List<Customer> findByAssignedEmployee(@Param("employeeId") Long employeeId);

    @EntityGraph(attributePaths = {"assignedEmployee"})
    @Query("SELECT c FROM Customer c LEFT JOIN c.assignedEmployee e WHERE c.custCode LIKE :prefix OR c.mobileNo LIKE :prefix OR LOWER(c.fullName) LIKE LOWER(:prefix) OR LOWER(c.patientName) LIKE LOWER(:prefix) OR LOWER(e.fullName) LIKE LOWER(:prefix) OR LOWER(e.empCode) LIKE LOWER(:prefix) ORDER BY c.createdAt DESC")
    List<Customer> findSuggestions(@Param("prefix") String prefix, Pageable pageable);

    @Query("SELECT c FROM Customer c LEFT JOIN c.assignedEmployee e WHERE (:status IS NULL OR c.status = :status) AND (:search IS NULL OR :search = '' OR c.custCode LIKE :prefix OR c.mobileNo LIKE :prefix OR LOWER(c.fullName) LIKE LOWER(:prefix) OR LOWER(c.patientName) LIKE LOWER(:prefix) OR LOWER(e.fullName) LIKE LOWER(:prefix) OR LOWER(e.empCode) LIKE LOWER(:prefix))")
    long countFiltered(@Param("status") CustomerStatus status,
                       @Param("search") String search,
                       @Param("prefix") String prefix);

    default Page<Customer> search(String query, Pageable pageable) {
        String search = query == null ? "" : query.trim();
        return searchPage(null, search, buildPrefix(search), pageable);
    }

    default Page<Customer> searchByStatus(String query, CustomerStatus status, Pageable pageable) {
        String search = query == null ? "" : query.trim();
        return searchPage(status, search, buildPrefix(search), pageable);
    }

    default Page<Customer> findByStatus(CustomerStatus status, Pageable pageable) {
        return findByStatusPage(status, pageable);
    }

    static String buildPrefix(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? "%" : normalized + "%";
    }

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
