package com.vns.healthcare.service;

import com.vns.healthcare.entity.CustomerDocument;
import com.vns.healthcare.entity.EmployeeDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private final File root;
    private final boolean s3Enabled;
    private final boolean databaseEnabled;
    private final String bucketName;
    private final S3Client s3Client;

    public FileStorageService(@Value("${app.upload-dir:uploads}") String uploadDir,
                             @Value("${app.storage.type:database}") String storageType,
                             @Value("${aws.s3.bucket:}") String bucketName,
                             @Value("${aws.region:us-east-1}") String region,
                             @Value("${aws.s3.endpoint:}") String endpoint,
                             @Value("${aws.access-key:}") String accessKey,
                             @Value("${aws.secret-key:}") String secretKey) {
        this.root = new File(uploadDir);
        if (!this.root.exists() && !this.root.mkdirs()) {
            throw new IllegalStateException("Cannot create upload directory: " + this.root.getAbsolutePath());
        }
        this.databaseEnabled = "database".equalsIgnoreCase(storageType) || "db".equalsIgnoreCase(storageType);
        this.s3Enabled = "s3".equalsIgnoreCase(storageType) && bucketName != null && !bucketName.trim().isEmpty();
        this.bucketName = bucketName == null ? "" : bucketName.trim();
        if (this.s3Enabled) {
            S3ClientBuilder builder = S3Client.builder().region(Region.of(region));
            if (endpoint != null && !endpoint.trim().isEmpty()) {
                builder.endpointOverride(URI.create(endpoint.trim()));
            }
            builder.serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
            if (accessKey != null && !accessKey.trim().isEmpty() && secretKey != null && !secretKey.trim().isEmpty()) {
                builder.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey.trim(), secretKey.trim())));
            } else {
                builder.credentialsProvider(DefaultCredentialsProvider.create());
            }
            this.s3Client = builder.build();
            log.info("S3 storage enabled for bucket [{}] in region [{}]", this.bucketName, region);
        } else {
            this.s3Client = null;
            if (databaseEnabled) {
                log.info("Database BLOB storage enabled for employee/customer attachments");
            } else {
                log.info("File storage initialized at [{}]", this.root.getAbsolutePath());
            }
        }
    }

    public String store(Long employeeId, MultipartFile file) throws IOException {
        if (databaseEnabled) {
            return "db:" + UUID.randomUUID();
        }
        if (s3Enabled) {
            return storeToS3("employees", employeeId, file);
        }
        File folder = new File(root, "employees/" + employeeId);
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IOException("Cannot create employee upload folder");
        }
        String original = file.getOriginalFilename() == null ? "document" : file.getOriginalFilename();
        String safe = original.replaceAll("[^a-zA-Z0-9._-]", "_");
        String stored = UUID.randomUUID().toString() + "_" + safe;
        File dest = new File(folder, stored);
        Files.copy(file.getInputStream(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        try { dest.setWritable(true, false); } catch (Exception ignored) {}
        log.info("Stored employee document [{}] in [{}]", original, dest.getAbsolutePath());
        return stored;
    }

    public String storeCustomer(Long customerId, MultipartFile file) throws IOException {
        if (databaseEnabled) {
            return "db:" + UUID.randomUUID();
        }
        if (s3Enabled) {
            return storeToS3("customers", customerId, file);
        }
        File folder = new File(root, "customers/" + customerId);
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IOException("Cannot create customer upload folder");
        }
        String original = file.getOriginalFilename() == null ? "document" : file.getOriginalFilename();
        String safe = original.replaceAll("[^a-zA-Z0-9._-]", "_");
        String stored = UUID.randomUUID().toString() + "_" + safe;
        File dest = new File(folder, stored);
        Files.copy(file.getInputStream(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        try { dest.setWritable(true, false); } catch (Exception ignored) {}
        log.info("Stored customer document [{}] in [{}]", original, dest.getAbsolutePath());
        return stored;
    }

    public Resource load(EmployeeDocument document) {
        if (databaseEnabled && document.getFileData() != null) {
            return new ByteArrayResource(document.getFileData());
        }
        if (s3Enabled) {
            return loadFromS3(document.getStoredFilename(), "employees", document.getEmployee().getId(), document.getOriginalFilename(), document.getContentType());
        }
        File file = resolveLocalEmployeeFile(document);
        log.info("Loading employee document from [{}]", file.getAbsolutePath());
        return new FileSystemResource(file);
    }

    public Resource load(CustomerDocument document) {
        if (databaseEnabled && document.getFileData() != null) {
            return new ByteArrayResource(document.getFileData());
        }
        if (s3Enabled) {
            return loadFromS3(document.getStoredFilename(), "customers", document.getCustomer().getId(), document.getOriginalFilename(), document.getContentType());
        }
        File file = resolveLocalCustomerFile(document);
        log.info("Loading customer document from [{}]", file.getAbsolutePath());
        return new FileSystemResource(file);
    }

    public void delete(EmployeeDocument document) {
        if (databaseEnabled) {
            return;
        }
        if (s3Enabled) {
            deleteFromS3(resolveObjectKey("employees", document.getEmployee().getId(), document.getStoredFilename()));
            return;
        }
        File file = resolveLocalEmployeeFile(document);
        if (file.exists()) {
            file.delete();
            log.info("Deleted employee document [{}]", file.getAbsolutePath());
        }
    }

    public void delete(CustomerDocument document) {
        if (databaseEnabled) {
            return;
        }
        if (s3Enabled) {
            deleteFromS3(resolveObjectKey("customers", document.getCustomer().getId(), document.getStoredFilename()));
            return;
        }
        File file = resolveLocalCustomerFile(document);
        if (file.exists()) {
            file.delete();
            log.info("Deleted customer document [{}]", file.getAbsolutePath());
        }
    }

    private String storeToS3(String folder, Long entityId, MultipartFile file) throws IOException {
        String original = file.getOriginalFilename() == null ? "document" : file.getOriginalFilename();
        String safe = original.replaceAll("[^a-zA-Z0-9._-]", "_");
        String key = resolveObjectKey(folder, entityId, UUID.randomUUID().toString() + "_" + safe);
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(file.getContentType() == null ? "application/octet-stream" : file.getContentType())
                .build();
        try (java.io.InputStream inputStream = file.getInputStream()) {
            s3Client.putObject(request, RequestBody.fromInputStream(inputStream, file.getSize()));
        }
        log.info("Stored {} document [{}] in S3 key [{}]", folder, original, key);
        return key;
    }

    private Resource loadFromS3(String storedFilename, String folder, Long entityId, String originalFilename, String contentType) {
        String key = resolveObjectKey(folder, entityId, storedFilename);
        try {
            ResponseBytes<GetObjectResponse> object = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build());
            return new ByteArrayResource(object.asByteArray());
        } catch (Exception ex) {
            log.error("Failed to load document from S3 key [{}] for {} id [{}]", key, folder, entityId, ex);
            throw new IllegalStateException("Could not load document from storage", ex);
        }
    }

    private void deleteFromS3(String key) {
        if (key == null || key.trim().isEmpty()) {
            return;
        }
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(key).build());
        log.info("Deleted document from S3 key [{}]", key);
    }

    private String resolveObjectKey(String folder, Long entityId, String storedFilename) {
        if (storedFilename == null || storedFilename.trim().isEmpty()) {
            return folder + "/" + entityId + "/document";
        }
        if (storedFilename.startsWith(folder + "/")) {
            return storedFilename;
        }
        if (storedFilename.startsWith("/")) {
            return storedFilename.substring(1);
        }
        return folder + "/" + entityId + "/" + storedFilename;
    }

    private File resolveLocalEmployeeFile(EmployeeDocument document) {
        String stored = document.getStoredFilename();
        if (stored != null && stored.startsWith("employees/")) {
            return new File(root, stored);
        }
        return new File(root, "employees/" + document.getEmployee().getId() + "/" + stored);
    }

    private File resolveLocalCustomerFile(CustomerDocument document) {
        String stored = document.getStoredFilename();
        if (stored != null && stored.startsWith("customers/")) {
            return new File(root, stored);
        }
        return new File(root, "customers/" + document.getCustomer().getId() + "/" + stored);
    }
}
