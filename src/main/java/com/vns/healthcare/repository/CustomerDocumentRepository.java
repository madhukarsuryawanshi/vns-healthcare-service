package com.vns.healthcare.repository;

import com.vns.healthcare.entity.CustomerDocument;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerDocumentRepository extends JpaRepository<CustomerDocument, Long> {
}
