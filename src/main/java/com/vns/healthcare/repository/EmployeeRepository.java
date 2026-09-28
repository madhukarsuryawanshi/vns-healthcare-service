package com.vns.healthcare.repository;

import com.vns.healthcare.domain.Designation;
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

    @Query("SELECT e FROM Employee e WHERE (:status IS NULL OR e.status = :status) " +
            "AND (:designation IS NULL OR e.designation = :designation) " +
            "AND (:search IS NULL OR :search = '' OR e.empCode LIKE :prefix OR e.mobileNo LIKE :prefix OR LOWER(e.fullName) LIKE LOWER(:prefix))")
    Page<Employee> findFilteredPage(@Param("status") EmployeeStatus status,
                                  @Param("designation") Designation designation,
                                  @Param("search") String search,
                                  @Param("prefix") String prefix,
                                  Pageable pageable);

    @Query("SELECT e FROM Employee e WHERE e.status = :status " +
            "AND (:search IS NULL OR :search = '' OR e.empCode LIKE :prefix OR e.mobileNo LIKE :prefix OR LOWER(e.fullName) LIKE LOWER(:prefix))")
    Page<Employee> findFilteredByStatusPage(@Param("status") EmployeeStatus status,
                                          @Param("search") String search,
                                          @Param("prefix") String prefix,
                                          Pageable pageable);

    @Query("SELECT e FROM Employee e WHERE (:designation IS NULL OR e.designation = :designation)")
    Page<Employee> findByDesignationPage(@Param("designation") Designation designation, Pageable pageable);

    @Query("SELECT e FROM Employee e WHERE e.status = :status ORDER BY e.fullName ASC")
    List<Employee> findActiveStaff(@Param("status") EmployeeStatus status, Pageable pageable);

    @Query("SELECT e FROM Employee e WHERE e.empCode LIKE :prefix OR e.mobileNo LIKE :prefix OR LOWER(e.fullName) LIKE LOWER(:prefix) ORDER BY e.createdAt DESC")
    List<Employee> findSuggestions(@Param("prefix") String prefix, Pageable pageable);

    @Query("SELECT e FROM Employee e WHERE (:status IS NULL OR e.status = :status) AND (:designation IS NULL OR e.designation = :designation) AND (:search IS NULL OR :search = '' OR e.empCode LIKE :prefix OR e.mobileNo LIKE :prefix OR LOWER(e.fullName) LIKE LOWER(:prefix))")
    long countFiltered(@Param("status") EmployeeStatus status,
                       @Param("designation") Designation designation,
                       @Param("search") String search,
                       @Param("prefix") String prefix);

    default Page<Employee> search(String query, Pageable pageable) {
        String search = query == null ? "" : query.trim();
        return findFilteredPage(null, null, search, buildPrefix(search), pageable);
    }

    default Page<Employee> searchByStatus(String query, EmployeeStatus status, Pageable pageable) {
        String search = query == null ? "" : query.trim();
        return findFilteredByStatusPage(status, search, buildPrefix(search), pageable);
    }

    default Page<Employee> findByStatus(EmployeeStatus status, Pageable pageable) {
        return findFilteredByStatusPage(status, "", buildPrefix(""), pageable);
    }

    static String buildPrefix(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? "%" : normalized + "%";
    }
}
