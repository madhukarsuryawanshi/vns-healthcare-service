package com.vns.healthcare.repository;

import com.vns.healthcare.entity.EmployeeDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeeDocumentRepository extends JpaRepository<EmployeeDocument, Long> {

    List<EmployeeDocument> findByEmployeeIdAndDocumentType(Long employeeId, String documentType);

    boolean existsByEmployeeIdAndDocumentTypeAndOriginalFilenameAndFileSizeAndContentType(
            Long employeeId,
            String documentType,
            String originalFilename,
            long fileSize,
            String contentType);
}
