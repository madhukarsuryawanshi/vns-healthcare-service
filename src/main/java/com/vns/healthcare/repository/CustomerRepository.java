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

    @Query("SELECT DISTINCT c.assignedEmployee.id FROM Customer c WHERE c.assignedEmployee IS NOT NULL")
    List<Long> findAssignedEmployeeIds();

    long countByAssignedEmployeeIsNotNull();

    @EntityGraph(attributePaths = {"assignedEmployee"})
    @Query("SELECT c FROM Customer c LEFT JOIN c.assignedEmployee e WHERE (:status IS NULL OR c.status = :status) AND (:search IS NULL OR :search = '' OR c.custCode LIKE :prefix OR c.mobileNo LIKE :prefix OR c.fullName LIKE :prefix OR c.patientName LIKE :prefix OR e.fullName LIKE :prefix OR e.empCode LIKE :prefix) ORDER BY c.createdAt DESC")
    Page<Customer> searchPage(@Param("status") CustomerStatus status,
                             @Param("search") String search,
                             @Param("prefix") String prefix,
                             Pageable pageable);

    @EntityGraph(attributePaths = {"assignedEmployee"})
    @Query("SELECT c FROM Customer c LEFT JOIN c.assignedEmployee e WHERE (:status IS NULL OR c.status = :status) ORDER BY c.createdAt DESC")
    Page<Customer> findByStatusPage(@Param("status") CustomerStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"assignedEmployee"})
    @Query("SELECT c FROM Customer c LEFT JOIN FETCH c.assignedEmployee e ORDER BY c.createdAt DESC")
    List<Customer> findTop5WithEmployee();

    @EntityGraph(attributePaths = {"assignedEmployee"})
    @Query("SELECT c FROM Customer c LEFT JOIN c.assignedEmployee e WHERE c.assignedEmployee.id = :employeeId ORDER BY c.createdAt DESC")
    List<Customer> findByAssignedEmployee(@Param("employeeId") Long employeeId);

    @EntityGraph(attributePaths = {"assignedEmployee"})
    @Query("SELECT c FROM Customer c LEFT JOIN c.assignedEmployee e WHERE c.custCode LIKE :prefix OR c.mobileNo LIKE :prefix OR c.fullName LIKE :prefix OR c.patientName LIKE :prefix OR e.fullName LIKE :prefix OR e.empCode LIKE :prefix ORDER BY c.createdAt DESC")
    List<Customer> findSuggestions(@Param("prefix") String prefix, Pageable pageable);

    @Query("SELECT c FROM Customer c LEFT JOIN c.assignedEmployee e WHERE (:status IS NULL OR c.status = :status) AND (:search IS NULL OR :search = '' OR c.custCode LIKE :prefix OR c.mobileNo LIKE :prefix OR c.fullName LIKE :prefix OR c.patientName LIKE :prefix OR e.fullName LIKE :prefix OR e.empCode LIKE :prefix)")
    long countFiltered(@Param("status") CustomerStatus status,
                       @Param("search") String search,
                       @Param("prefix") String prefix);

    @EntityGraph(attributePaths = {"assignedEmployee"})
    @Query("SELECT c FROM Customer c LEFT JOIN c.assignedEmployee e ORDER BY c.createdAt DESC")
    Page<Customer> findPage(Pageable pageable);

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
}
