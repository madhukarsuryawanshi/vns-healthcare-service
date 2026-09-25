package com.vns.healthcare.repository;

import com.vns.healthcare.entity.BusinessBankAccount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessBankAccountRepository extends JpaRepository<BusinessBankAccount, Long> {
}
