package com.vns.healthcare.repository;

import com.vns.healthcare.entity.BusinessBankStatementTable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BusinessBankStatementTableRepository extends JpaRepository<BusinessBankStatementTable, Long> {
    @EntityGraph(attributePaths = "rows")
    List<BusinessBankStatementTable> findAllByOrderByIdDesc();
}
