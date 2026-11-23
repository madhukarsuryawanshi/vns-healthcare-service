package com.vns.healthcare.repository;

import com.vns.healthcare.entity.BusinessBankStatement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessBankStatementRepository extends JpaRepository<BusinessBankStatement, Long> {
}
