package com.vns.healthcare.service;

import com.vns.healthcare.entity.CustomerDocument;
import com.vns.healthcare.entity.EmployeeDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private final File root;

    public FileStorageService(@Value("${app.upload-dir}") String uploadDir) {
        this.root = new File(uploadDir);
        if (!root.exists() && !root.mkdirs()) {
            throw new IllegalStateException("Cannot create upload directory: " + root.getAbsolutePath());
        }
        log.info("File storage initialized at [{}]", root.getAbsolutePath());
    }

    public String store(Long employeeId, MultipartFile file) throws IOException {
        File folder = new File(root, "employees/" + employeeId);
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IOException("Cannot create employee upload folder");
        }
        String original = file.getOriginalFilename() == null ? "document" : file.getOriginalFilename();
        String safe = original.replaceAll("[^a-zA-Z0-9._-]", "_");
        String stored = UUID.randomUUID().toString() + "_" + safe;
        File dest = new File(folder, stored);
        file.transferTo(dest);
        log.info("Stored employee document [{}] in [{}]", original, dest.getAbsolutePath());
        return stored;
    }

    public String storeCustomer(Long customerId, MultipartFile file) throws IOException {
        File folder = new File(root, "customers/" + customerId);
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IOException("Cannot create customer upload folder");
        }
        String original = file.getOriginalFilename() == null ? "document" : file.getOriginalFilename();
        String safe = original.replaceAll("[^a-zA-Z0-9._-]", "_");
        String stored = UUID.randomUUID().toString() + "_" + safe;
        File dest = new File(folder, stored);
        file.transferTo(dest);
        log.info("Stored customer document [{}] in [{}]", original, dest.getAbsolutePath());
        return stored;
    }

    public Resource load(EmployeeDocument document) {
        File file = new File(root, "employees/" + document.getEmployee().getId() + "/" + document.getStoredFilename());
        log.info("Loading employee document from [{}]", file.getAbsolutePath());
        return new FileSystemResource(file);
    }

    public Resource load(CustomerDocument document) {
        File file = new File(root, "customers/" + document.getCustomer().getId() + "/" + document.getStoredFilename());
        log.info("Loading customer document from [{}]", file.getAbsolutePath());
        return new FileSystemResource(file);
    }

    public void delete(EmployeeDocument document) {
        File file = new File(root, "employees/" + document.getEmployee().getId() + "/" + document.getStoredFilename());
        if (file.exists()) {
            file.delete();
            log.info("Deleted employee document [{}]", file.getAbsolutePath());
        }
    }

    public void delete(CustomerDocument document) {
        File file = new File(root, "customers/" + document.getCustomer().getId() + "/" + document.getStoredFilename());
        if (file.exists()) {
            file.delete();
            log.info("Deleted customer document [{}]", file.getAbsolutePath());
        }
    }
}
