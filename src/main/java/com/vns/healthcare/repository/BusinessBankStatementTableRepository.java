package com.vns.healthcare.repository;

import com.vns.healthcare.entity.BusinessBankStatementTable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BusinessBankStatementTableRepository extends JpaRepository<BusinessBankStatementTable, Long> {
    @EntityGraph(attributePaths = "rows")
    List<BusinessBankStatementTable> findAllByOrderByIdDesc();

    @EntityGraph(attributePaths = "rows")
    Optional<BusinessBankStatementTable> findWithRowsById(Long id);
}
