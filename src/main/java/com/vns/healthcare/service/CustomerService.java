package com.vns.healthcare.service;

import com.vns.healthcare.config.CacheRefreshService;
import com.vns.healthcare.domain.CustomerStatus;
import com.vns.healthcare.domain.Gender;
import com.vns.healthcare.domain.ServiceType;
import com.vns.healthcare.entity.Customer;
import com.vns.healthcare.entity.CustomerDocument;
import com.vns.healthcare.entity.Employee;
import com.vns.healthcare.exception.BusinessException;
import com.vns.healthcare.repository.CustomerDocumentRepository;
import com.vns.healthcare.repository.CustomerRepository;
import com.vns.healthcare.web.CustomerForm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);

    private final CustomerRepository customerRepository;
    private final CustomerDocumentRepository documentRepository;
    private final EmployeeService employeeService;
    private final CodeGeneratorService codeGeneratorService;
    private final FileStorageService fileStorageService;
    private final CacheManager cacheManager;
    private final CacheRefreshService cacheRefreshService;

    public CustomerService(CustomerRepository customerRepository,
                          CustomerDocumentRepository documentRepository,
                          EmployeeService employeeService,
                          CodeGeneratorService codeGeneratorService,
                          FileStorageService fileStorageService,
                          CacheManager cacheManager,
                          CacheRefreshService cacheRefreshService) {
        this.customerRepository = customerRepository;
        this.documentRepository = documentRepository;
        this.employeeService = employeeService;
        this.codeGeneratorService = codeGeneratorService;
        this.fileStorageService = fileStorageService;
        this.cacheManager = cacheManager;
        this.cacheRefreshService = cacheRefreshService;
    }

    @Transactional(readOnly = true)
    public List<Customer> list(String query) {
        String search = query == null ? "" : query.trim();
        String cacheKey = search.isEmpty() ? "all" : search;
        Cache cache = cacheManager.getCache("customer-lists");
        if (cache != null) {
            List<Customer> cached = cache.get(cacheKey, List.class);
            if (cached != null) {
                log.info("CACHE HIT customer-lists key=[{}] records={}", cacheKey, cached.size());
                return cached;
            }
        }
        log.info("CACHE MISS customer-lists key=[{}] -> querying DB", cacheKey);
        List<Customer> result;
        if (search.isEmpty()) {
            result = customerRepository.findPage(PageRequest.of(0, 20)).getContent();
        } else {
            result = customerRepository.findSuggestions(CustomerRepository.buildPrefix(search), PageRequest.of(0, 20));
        }
        if (cache != null) {
            cache.put(cacheKey, result);
        }
        log.info("LOADED {} customer records into customer-lists cache key=[{}]", result.size(), cacheKey);
        return result;
    }

    @Transactional(readOnly = true)
    public Page<Customer> listPage(Pageable pageable) {
        String cacheKey = pageable.getPageNumber() + ":" + pageable.getPageSize() + ":" + pageable.getSort();
        Cache cache = cacheManager.getCache("customer-pages");
        if (cache != null) {
            Page<Customer> cached = cache.get(cacheKey, Page.class);
            if (cached != null) {
                log.info("CACHE HIT customer-pages key=[{}] records={}", cacheKey, cached.getNumberOfElements());
                return cached;
            }
        }
        log.info("CACHE MISS customer-pages key=[{}] -> querying DB", cacheKey);
        Page<Customer> result = customerRepository.findPage(pageable);
        if (cache != null) {
            cache.put(cacheKey, result);
        }
        log.info("LOADED {} customer records into customer-pages cache key=[{}]", result.getNumberOfElements(), cacheKey);
        return result;
    }

    @Transactional(readOnly = true)
    public Page<Customer> search(String query, Pageable pageable) {
        return customerRepository.search(query == null ? "" : query.trim(), pageable);
    }

    @Transactional(readOnly = true)
    public Page<Customer> findByStatus(CustomerStatus status, Pageable pageable) {
        return customerRepository.findByStatusPage(status, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Customer> searchByStatus(String query, CustomerStatus status, Pageable pageable) {
        String search = query == null ? "" : query.trim();
        return customerRepository.searchByStatus(search, status, pageable);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "customerById", key = "#id")
    public Customer get(Long id) {
        Cache cache = cacheManager.getCache("customerById");
        if (cache != null && cache.get(id, Customer.class) != null) {
            log.info("CACHE HIT customerById key=[{}]", id);
        } else {
            log.info("CACHE MISS customerById key=[{}] -> querying DB", id);
        }
        Customer customer = customerRepository.findWithEmployee(id)
                .orElseThrow(() -> new BusinessException("Customer not found"));
        if (cache != null) {
            cache.put(id, customer);
        }
        return customer;
    }

    @Transactional
    public Customer create(CustomerForm form) {
        return create(form, null);
    }

    @Transactional
    @CacheEvict(value = {"customer-lists", "customer-pages", "customer-active", "customerById"}, allEntries = true)
    public Customer create(CustomerForm form, MultipartFile[] documents) {
        log.info("Creating customer with patient name [{}] and phone [{}]", form.getPatientName(), form.getMobileNo());
        Customer customer = new Customer();
        customer.setCustCode(codeGeneratorService.nextCustomerCode());
        applyForm(customer, form);
        customer = customerRepository.save(customer);
        storeDocumentsIfPresent(customer, documents);
        cacheRefreshService.refreshCustomerCaches();
        log.info("Customer created successfully with id [{}] and code [{}]", customer.getId(), customer.getCustCode());
        return customer;
    }

    @Transactional
    @CacheEvict(value = {"customer-lists", "customer-pages", "customer-active", "customerById"}, allEntries = true)
    public Customer update(Long id, CustomerForm form) {
        Customer customer = get(id);
        log.info("Updating customer id [{}] [{}]", id, customer.getPatientName());
        assertOpen(customer);
        applyForm(customer, form);
        Customer saved = customerRepository.save(customer);
        cacheRefreshService.refreshCustomerCaches();
        log.info("Customer id [{}] updated successfully", saved.getId());
        return saved;
    }

    @Transactional
    @CacheEvict(value = {"customer-lists", "customer-pages", "customer-active", "customerById"}, allEntries = true)
    public void delete(Long id) {
        Customer customer = get(id);
        log.info("Deleting customer id [{}] [{}]", id, customer.getPatientName());
        customerRepository.delete(customer);
        cacheRefreshService.refreshCustomerCaches();
        log.info("Customer id [{}] deleted successfully", id);
    }

    @Transactional
    @CacheEvict(value = {"customer-lists", "customer-pages", "customer-active", "customerById"}, allEntries = true)
    public void addDocuments(Long id, MultipartFile[] files) {
        if (files == null || files.length == 0) {
            log.warn("Customer document upload rejected for id [{}]: no files", id);
            throw new BusinessException("Choose one or more documents to upload");
        }
        Set<String> seen = new HashSet<String>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String fingerprint = fingerprint(file);
            if (!seen.add(fingerprint)) {
                continue;
            }
            long maxBytes = file.getContentType() != null && file.getContentType().toLowerCase().startsWith("image/")
                    ? FileStorageService.MAX_IMAGE_BYTES
                    : FileStorageService.MAX_DOCUMENT_BYTES;
            if (file.getSize() > maxBytes) {
                throw new BusinessException("File is too large. Maximum allowed size is " + (maxBytes / (1024 * 1024)) + " MB.");
            }
        }
        storeDocumentsIfPresent(get(id), files);
    }

    @Transactional(readOnly = true)
    public CustomerDocument getDocument(Long customerId, Long documentId) {
        CustomerDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException("Document not found"));
        if (!document.getCustomer().getId().equals(customerId)) {
            throw new BusinessException("Document does not belong to this customer");
        }
        return document;
    }

    @Transactional
    @CacheEvict(value = {"customer-lists", "customer-pages", "customer-active", "customerById"}, allEntries = true)
    public void deleteDocument(Long customerId, Long documentId) {
        CustomerDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException("Document not found"));
        if (!document.getCustomer().getId().equals(customerId)) {
            throw new BusinessException("Document does not belong to this customer");
        }
        try {
            fileStorageService.delete(document);
        } catch (Exception e) {
            log.warn("Failed to delete file from storage for customer document id [{}]: {}", documentId, e.getMessage());
        }
        documentRepository.delete(document);
        log.info("Deleted customer document id [{}] for customer [{}]", documentId, customerId);
    }

    @Transactional
    @CacheEvict(value = {"customer-lists", "customer-pages", "customer-active", "customerById"}, allEntries = true)
    public void assignEmployee(Long customerId, Long employeeId) {
        Customer customer = get(customerId);
        log.info("Processing employee assignment for customer [{}] with employee [{}]", customerId, employeeId);
        assertOpen(customer);
        if (employeeId == null) {
            customer.setAssignedEmployee(null);
            if (customer.getStatus() == CustomerStatus.ASSIGNED) {
                customer.setStatus(CustomerStatus.NEW);
            }
        } else {
            validateEmployeeAssignment(customer, employeeId);
            Employee employee = employeeService.get(employeeId);
            customer.setAssignedEmployee(employee);
            if (customer.getStatus() == CustomerStatus.NEW) {
                customer.setStatus(CustomerStatus.ASSIGNED);
            }
        }
        customerRepository.save(customer);
        cacheRefreshService.refreshCustomerCaches();
        log.info("Employee assignment saved for customer [{}]", customerId);
    }

    @Transactional
    @CacheEvict(value = {"customer-lists", "customer-pages", "customer-active", "customerById"}, allEntries = true)
    public Customer closeService(Long id, CustomerDutyService dutyService) {
        Customer customer = get(id);
        log.info("Closing service for customer id [{}]", id);
        assertOpen(customer);
        LocalDate end = LocalDate.now();
        LocalDate start = end.withDayOfMonth(1);
        customer.setBilledAmount(dutyService.calculateCharges(customer, start, end));
        customer.setServiceClosedDate(end);
        customer.setAssignedEmployee(null);
        customer.setStatus(CustomerStatus.CLOSED);
        Customer saved = customerRepository.save(customer);
        cacheRefreshService.refreshCustomerCaches();
        log.info("Service closed for customer id [{}] and employee assignment released with billed amount [{}]", id, saved.getBilledAmount());
        return saved;
    }

    @Transactional
    @CacheEvict(value = {"customer-lists", "customer-pages", "customer-active", "customerById"}, allEntries = true)
    public Customer applyAdvancePayment(Long customerId, java.math.BigDecimal amountUsed) {
        Customer customer = get(customerId);
        java.math.BigDecimal current = customer.getAdvancePayment() == null ? java.math.BigDecimal.ZERO : customer.getAdvancePayment();
        java.math.BigDecimal used = amountUsed == null ? java.math.BigDecimal.ZERO : amountUsed;
        if (used.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            return customer;
        }
        used = used.min(current);
        java.math.BigDecimal remaining = current.subtract(used);
        customer.setAdvancePayment(remaining.max(java.math.BigDecimal.ZERO));
        Customer saved = customerRepository.save(customer);
        log.info("Applied advance payment of [{}] for customer [{}]. Remaining advance [{}]", used, customerId, saved.getAdvancePayment());
        return saved;
    }

    @Transactional
    @CacheEvict(value = {"customer-lists", "customer-pages", "customer-active", "customerById"}, allEntries = true)
    public Customer recalculateBilledAmount(Long id, CustomerDutyService dutyService) {
        Customer customer = get(id);
        log.info("Recalculating billed amount for customer id [{}]", id);
        if (!customer.isClosed()) {
            throw new BusinessException("Close the service first, or use Close Service to calculate charges");
        }
        LocalDate end = customer.getServiceClosedDate() == null ? LocalDate.now() : customer.getServiceClosedDate();
        LocalDate start = end.withDayOfMonth(1);
        customer.setBilledAmount(dutyService.calculateCharges(customer, start, end));
        Customer saved = customerRepository.save(customer);
        log.info("Recalculated billed amount for customer id [{}]: [{}]", id, saved.getBilledAmount());
        return saved;
    }

    public void assertOpen(Customer customer) {
        if (customer.isClosed()) {
            throw new BusinessException("This service is closed and cannot be changed");
        }
    }

    private void validateEmployeeAssignment(Customer customer, Long employeeId) {
        if (employeeId == null) {
            return;
        }
        List<Customer> assignedCustomers = customerRepository.findByAssignedEmployeeId(employeeId);
        for (Customer assignedCustomer : assignedCustomers) {
            if (assignedCustomer == null || assignedCustomer.isClosed()) {
                continue;
            }
            if (!assignedCustomer.getId().equals(customer.getId())) {
                log.warn("Duplicate assignment attempted: employee [{}] already assigned to customer [{}]", employeeId, assignedCustomer.getId());
                throw new BusinessException("This employee is already assigned to " + assignedCustomer.getPatientName() + ". Please choose another caregiver.");
            }
        }
    }

    private void applyForm(Customer customer, CustomerForm form) {
        customer.setFullName(form.getFullName().trim());
        customer.setMobileNo(form.getMobileNo().trim());
        customer.setAddress(form.getAddress().trim());
        customer.setPatientName(form.getPatientName().trim());
        if (form.getGender() == null || form.getGender().trim().isEmpty()) {
            customer.setGender(null);
        } else {
            customer.setGender(Gender.valueOf(form.getGender()));
        }
        customer.setAge(form.getAge());
        customer.setMedicalHistory(blankToNull(form.getMedicalHistory()));
        customer.setServiceStartDate(form.getServiceStartDate());
        customer.setServiceType(ServiceType.valueOf(form.getServiceType()));
        customer.setCharges(form.getCharges());
        customer.setAdvancePayment(form.getAdvancePayment());
        customer.setEmail(blankToNull(form.getEmail()));
        if (form.getEmployeeId() != null) {
            validateEmployeeAssignment(customer, form.getEmployeeId());
            customer.setAssignedEmployee(employeeService.get(form.getEmployeeId()));
            if (customer.getStatus() == null || customer.getStatus() == CustomerStatus.NEW) {
                customer.setStatus(CustomerStatus.ASSIGNED);
            }
        } else {
            customer.setAssignedEmployee(null);
        }
        if (form.getStatus() != null && !form.getStatus().trim().isEmpty()) {
            customer.setStatus(CustomerStatus.valueOf(form.getStatus()));
        }
    }

    private void storeDocumentIfPresent(Customer customer, MultipartFile file) {
        // backwards compatibility
        if (file == null || file.isEmpty()) return;
        storeDocumentsIfPresent(customer, new MultipartFile[]{file});
    }

    private void storeDocumentsIfPresent(Customer customer, MultipartFile[] files) {
        if (files == null || files.length == 0) return;
        Set<String> seen = new HashSet<String>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) continue;
            String fingerprint = fingerprint(file);
            if (!seen.add(fingerprint)) {
                continue;
            }
            try {
                MultipartFile safeFile = fileStorageService.normalizeUpload(file, false);
                String stored = fileStorageService.storeCustomer(customer.getId(), safeFile);
                CustomerDocument document = new CustomerDocument();
                document.setCustomer(customer);
                document.setOriginalFilename(safeFile.getOriginalFilename());
                document.setStoredFilename(stored);
                document.setContentType(safeFile.getContentType());
                document.setFileSize(safeFile.getSize());
                document.setFileData(safeFile.getBytes());
                documentRepository.save(document);
                log.info("Stored customer document [{}] for customer id [{}]", safeFile.getOriginalFilename(), customer.getId());
            } catch (IOException ex) {
                log.error("Failed to store customer document for customer id [{}]", customer.getId(), ex);
                throw new BusinessException("Could not store one of the uploaded documents");
            }
        }
    }

    private String fingerprint(MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return file.getOriginalFilename() + "::" + sb.toString();
        } catch (Exception ex) {
            return file.getOriginalFilename() + "::" + file.getSize();
        }
    }

    private String blankToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
