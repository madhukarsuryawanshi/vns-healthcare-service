package com.vns.healthcare.service;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@Service
public class CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);

    private final CustomerRepository customerRepository;
    private final CustomerDocumentRepository documentRepository;
    private final EmployeeService employeeService;
    private final CodeGeneratorService codeGeneratorService;
    private final FileStorageService fileStorageService;

    public CustomerService(CustomerRepository customerRepository,
                          CustomerDocumentRepository documentRepository,
                          EmployeeService employeeService,
                          CodeGeneratorService codeGeneratorService,
                          FileStorageService fileStorageService) {
        this.customerRepository = customerRepository;
        this.documentRepository = documentRepository;
        this.employeeService = employeeService;
        this.codeGeneratorService = codeGeneratorService;
        this.fileStorageService = fileStorageService;
    }

    @Transactional(readOnly = true)
    public List<Customer> list(String query) {
        if (query == null || query.trim().isEmpty()) {
            return customerRepository.findAllWithEmployee();
        }
        return customerRepository.search(query.trim());
    }

    @Transactional(readOnly = true)
    public Customer get(Long id) {
        return customerRepository.findWithEmployee(id)
                .orElseThrow(() -> new BusinessException("Customer not found"));
    }

    @Transactional
    public Customer create(CustomerForm form) {
        return create(form, null);
    }

    @Transactional
    public Customer create(CustomerForm form, MultipartFile[] documents) {
        log.info("Creating customer with patient name [{}] and phone [{}]", form.getPatientName(), form.getMobileNo());
        Customer customer = new Customer();
        customer.setCustCode(codeGeneratorService.nextCustomerCode());
        applyForm(customer, form);
        customer = customerRepository.save(customer);
        storeDocumentsIfPresent(customer, documents);
        log.info("Customer created successfully with id [{}] and code [{}]", customer.getId(), customer.getCustCode());
        return customer;
    }

    @Transactional
    public Customer update(Long id, CustomerForm form) {
        Customer customer = get(id);
        log.info("Updating customer id [{}] [{}]", id, customer.getPatientName());
        assertOpen(customer);
        applyForm(customer, form);
        Customer saved = customerRepository.save(customer);
        log.info("Customer id [{}] updated successfully", saved.getId());
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        Customer customer = get(id);
        log.info("Deleting customer id [{}] [{}]", id, customer.getPatientName());
        customerRepository.delete(customer);
        log.info("Customer id [{}] deleted successfully", id);
    }

    @Transactional
    public void addDocuments(Long id, MultipartFile[] files) {
        if (files == null || files.length == 0) {
            log.warn("Customer document upload rejected for id [{}]: no files", id);
            throw new BusinessException("Choose one or more documents to upload");
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
        log.info("Employee assignment saved for customer [{}]", customerId);
    }

    @Transactional
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
        log.info("Service closed for customer id [{}] and employee assignment released with billed amount [{}]", id, saved.getBilledAmount());
        return saved;
    }

    @Transactional
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
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) continue;
            try {
                String stored = fileStorageService.storeCustomer(customer.getId(), file);
                CustomerDocument document = new CustomerDocument();
                document.setCustomer(customer);
                document.setOriginalFilename(file.getOriginalFilename());
                document.setStoredFilename(stored);
                document.setContentType(file.getContentType());
                document.setFileSize(file.getSize());
                document.setFileData(file.getBytes());
                documentRepository.save(document);
                log.info("Stored customer document [{}] for customer id [{}]", file.getOriginalFilename(), customer.getId());
            } catch (IOException ex) {
                log.error("Failed to store customer document for customer id [{}]", customer.getId(), ex);
                throw new BusinessException("Could not store one of the uploaded documents");
            }
        }
    }

    private String blankToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
