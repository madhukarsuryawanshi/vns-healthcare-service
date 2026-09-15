package com.vns.healthcare.repository;

import com.vns.healthcare.entity.EmployeeDocument;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeDocumentRepository extends JpaRepository<EmployeeDocument, Long> {
}
