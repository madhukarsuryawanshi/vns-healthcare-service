package com.vns.healthcare.service;

import com.vns.healthcare.domain.CustomerStatus;
import com.vns.healthcare.domain.EmployeeStatus;
import com.vns.healthcare.domain.Gender;
import com.vns.healthcare.domain.TrainingStatus;
import com.vns.healthcare.entity.Attendance;
import com.vns.healthcare.entity.Customer;
import com.vns.healthcare.entity.CustomerDuty;
import com.vns.healthcare.entity.Employee;
import com.vns.healthcare.entity.EmployeeDocument;
import com.vns.healthcare.entity.SalaryPayment;
import com.vns.healthcare.exception.BusinessException;
import com.vns.healthcare.repository.AttendanceRepository;
import com.vns.healthcare.repository.CustomerDutyRepository;
import com.vns.healthcare.repository.CustomerRepository;
import com.vns.healthcare.repository.EmployeeDocumentRepository;
import com.vns.healthcare.repository.EmployeeRepository;
import com.vns.healthcare.repository.SalaryPaymentRepository;
import com.vns.healthcare.web.EmployeeForm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@Service
public class EmployeeService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeService.class);

    private final EmployeeRepository employeeRepository;
    private final EmployeeDocumentRepository documentRepository;
    private final AttendanceRepository attendanceRepository;
    private final SalaryPaymentRepository salaryPaymentRepository;
    private final CustomerDutyRepository customerDutyRepository;
    private final CustomerRepository customerRepository;
    private final CodeGeneratorService codeGeneratorService;
    private final FileStorageService fileStorageService;

    public EmployeeService(EmployeeRepository employeeRepository,
                           EmployeeDocumentRepository documentRepository,
                           AttendanceRepository attendanceRepository,
                           SalaryPaymentRepository salaryPaymentRepository,
                           CustomerDutyRepository customerDutyRepository,
                           CustomerRepository customerRepository,
                           CodeGeneratorService codeGeneratorService,
                           FileStorageService fileStorageService) {
        this.employeeRepository = employeeRepository;
        this.documentRepository = documentRepository;
        this.attendanceRepository = attendanceRepository;
        this.salaryPaymentRepository = salaryPaymentRepository;
        this.customerDutyRepository = customerDutyRepository;
        this.customerRepository = customerRepository;
        this.codeGeneratorService = codeGeneratorService;
        this.fileStorageService = fileStorageService;
    }

    @Transactional(readOnly = true)
    public List<Employee> list(String query) {
        if (query == null || query.trim().isEmpty()) {
            return employeeRepository.findAllByOrderByCreatedAtDesc();
        }
        return employeeRepository.search(query.trim());
    }

    @Transactional(readOnly = true)
    public List<Employee> activeStaff() {
        return employeeRepository.findAllActive(EmployeeStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public Employee get(Long id) {
        return employeeRepository.findWithDocuments(id)
                .orElseThrow(() -> new BusinessException("Employee not found"));
    }

    @Transactional
    public Employee create(EmployeeForm form, MultipartFile[] documents) {
        log.info("Creating employee with Aadhar [{}] and name [{}]", form.getAadharNumber(), form.getFullName());
        if (employeeRepository.existsByAadharNumber(form.getAadharNumber())) {
            log.warn("Employee creation blocked: duplicate Aadhar [{}]", form.getAadharNumber());
            throw new BusinessException("An employee with this Aadhar number already exists");
        }
        Employee employee = new Employee();
        employee.setEmpCode(codeGeneratorService.nextEmployeeCode());
        applyForm(employee, form);
        employee = employeeRepository.save(employee);
        storeDocumentsIfPresent(employee, documents);
        log.info("Employee created successfully with id [{}] and code [{}]", employee.getId(), employee.getEmpCode());
        return employee;
    }

    @Transactional
    public Employee update(Long id, EmployeeForm form) {
        Employee employee = get(id);
        log.info("Updating employee id [{}] with Aadhar [{}]", id, form.getAadharNumber());
        if (employeeRepository.existsByAadharNumberAndIdNot(form.getAadharNumber(), id)) {
            log.warn("Employee update blocked for id [{}]: duplicate Aadhar [{}]", id, form.getAadharNumber());
            throw new BusinessException("An employee with this Aadhar number already exists");
        }
        applyForm(employee, form);
        Employee saved = employeeRepository.save(employee);
        log.info("Employee id [{}] updated successfully", saved.getId());
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        Employee employee = get(id);
        log.info("Deleting employee id [{}] [{}]", id, employee.getFullName());

        List<Attendance> attendanceRecords = attendanceRepository.findByEmployeeId(id);
        if (!attendanceRecords.isEmpty()) {
            attendanceRepository.deleteAll(attendanceRecords);
            log.info("Removed {} attendance records for employee [{}]", attendanceRecords.size(), id);
        }

        List<SalaryPayment> salaryPayments = salaryPaymentRepository.findByEmployeeId(id);
        if (!salaryPayments.isEmpty()) {
            salaryPaymentRepository.deleteAll(salaryPayments);
            log.info("Removed {} salary payment records for employee [{}]", salaryPayments.size(), id);
        }

        List<CustomerDuty> duties = customerDutyRepository.findByEmployeeId(id);
        if (!duties.isEmpty()) {
            for (CustomerDuty duty : duties) {
                duty.setEmployee(null);
            }
            customerDutyRepository.saveAll(duties);
            log.info("Cleared {} duty assignments for employee [{}]", duties.size(), id);
        }

        List<Customer> assignedCustomers = customerRepository.findByAssignedEmployeeId(id);
        if (!assignedCustomers.isEmpty()) {
            for (Customer customer : assignedCustomers) {
                customer.setAssignedEmployee(null);
                if (customer.getStatus() == CustomerStatus.ASSIGNED) {
                    customer.setStatus(CustomerStatus.NEW);
                }
            }
            customerRepository.saveAll(assignedCustomers);
            log.info("Unassigned employee [{}] from {} customer records", id, assignedCustomers.size());
        }

        employeeRepository.delete(employee);
        log.info("Employee id [{}] deleted successfully", id);
    }

    @Transactional
    public void updateTraining(Long id, TrainingStatus status, String notes) {
        Employee employee = get(id);
        log.info("Updating training for employee id [{}] to status [{}]", id, status);
        employee.setTrainingStatus(status);
        employee.setTrainingNotes(notes);
        employeeRepository.save(employee);
    }

    @Transactional
    public void onboard(Long id, LocalDate salaryStartDate) {
        Employee employee = get(id);
        log.info("Onboarding employee id [{}] with salary start date [{}]", id, salaryStartDate);
        employee.setOnboarded(true);
        employee.setSalaryStartDate(salaryStartDate == null ? LocalDate.now() : salaryStartDate);
        employee.setStatus(EmployeeStatus.ACTIVE);
        employeeRepository.save(employee);
    }

    @Transactional
    public void resign(Long id) {
        Employee employee = get(id);
        log.info("Resigning employee id [{}] [{}]", id, employee.getFullName());

        List<CustomerDuty> duties = customerDutyRepository.findByEmployeeId(id);
        if (!duties.isEmpty()) {
            for (CustomerDuty duty : duties) {
                duty.setEmployee(null);
            }
            customerDutyRepository.saveAll(duties);
            log.info("Cleared {} duty assignments for resigned employee [{}]", duties.size(), id);
        }

        List<Customer> assignedCustomers = customerRepository.findByAssignedEmployeeId(id);
        if (!assignedCustomers.isEmpty()) {
            for (Customer customer : assignedCustomers) {
                customer.setAssignedEmployee(null);
                if (customer.getStatus() == CustomerStatus.ASSIGNED) {
                    customer.setStatus(CustomerStatus.NEW);
                }
            }
            customerRepository.saveAll(assignedCustomers);
            log.info("Released employee [{}] from {} customer assignments", id, assignedCustomers.size());
        }

        employee.setStatus(EmployeeStatus.RESIGNED);
        employeeRepository.save(employee);
        log.info("Employee id [{}] marked as resigned", id);
    }

    @Transactional
    public void addDocuments(Long id, MultipartFile[] files) {
        if (files == null || files.length == 0) {
            log.warn("Employee document upload rejected for id [{}]: no files", id);
            throw new BusinessException("Choose one or more documents to upload");
        }
        storeDocumentsIfPresent(get(id), files);
    }

    @Transactional
    public void addPassportPhoto(Long id, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Choose a passport size photo to upload");
        }
        String contentType = file.getContentType();
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        boolean allowed = "image/png".equalsIgnoreCase(contentType)
                || "image/jpeg".equalsIgnoreCase(contentType)
                || filename.endsWith(".png")
                || filename.endsWith(".jpg")
                || filename.endsWith(".jpeg");
        if (!allowed) {
            throw new BusinessException("Only PNG or JPG/JPEG passport size photos are allowed");
        }
        Employee employee = get(id);
        storeDocumentIfPresent(employee, file);
    }

    @Transactional(readOnly = true)
    public EmployeeDocument getPassportPhoto(Employee employee) {
        if (employee == null || employee.getDocuments() == null || employee.getDocuments().isEmpty()) {
            return null;
        }
        for (EmployeeDocument document : employee.getDocuments()) {
            String contentType = document.getContentType();
            String filename = document.getOriginalFilename() == null ? "" : document.getOriginalFilename().toLowerCase();
            if ((contentType != null && ("image/png".equalsIgnoreCase(contentType) || "image/jpeg".equalsIgnoreCase(contentType)))
                    || filename.endsWith(".png") || filename.endsWith(".jpg") || filename.endsWith(".jpeg")) {
                return document;
            }
        }
        return null;
    }

    @Transactional(readOnly = true)
    public EmployeeDocument getDocument(Long employeeId, Long documentId) {
        EmployeeDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException("Document not found"));
        if (!document.getEmployee().getId().equals(employeeId)) {
            throw new BusinessException("Document does not belong to this employee");
        }
        return document;
    }

    @Transactional
    public void deleteDocument(Long employeeId, Long documentId) {
        EmployeeDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException("Document not found"));
        if (!document.getEmployee().getId().equals(employeeId)) {
            throw new BusinessException("Document does not belong to this employee");
        }
        // delete file and record
        fileStorageService.delete(document);
        documentRepository.delete(document);
        log.info("Deleted employee document id [{}] for employee [{}]", documentId, employeeId);
    }

    private void applyForm(Employee employee, EmployeeForm form) {
        employee.setFullName(form.getFullName().trim());
        employee.setMobileNo(form.getMobileNo().trim());
        employee.setJoiningDate(form.getJoiningDate());
        employee.setGender(Gender.valueOf(form.getGender()));
        employee.setDateOfBirth(form.getDateOfBirth());
        employee.setReferredBy(blankToNull(form.getReferredBy()));
        employee.setFullAddress(form.getFullAddress().trim());
        employee.setAadharNumber(form.getAadharNumber().trim());
        employee.setNoOfExperience(form.getNoOfExperience());
        employee.setSalary(form.getSalary());
    }

    private void storeDocumentIfPresent(Employee employee, MultipartFile file) {
        // kept for backward compatibility
        if (file == null || file.isEmpty()) return;
        storeDocumentsIfPresent(employee, new MultipartFile[]{file});
    }

    private void storeDocumentsIfPresent(Employee employee, MultipartFile[] files) {
        if (files == null || files.length == 0) return;
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) continue;
            try {
                String stored = fileStorageService.store(employee.getId(), file);
                EmployeeDocument document = new EmployeeDocument();
                document.setEmployee(employee);
                document.setOriginalFilename(file.getOriginalFilename());
                document.setStoredFilename(stored);
                document.setContentType(file.getContentType());
                document.setFileSize(file.getSize());
                documentRepository.save(document);
                log.info("Stored employee document [{}] for employee id [{}]", file.getOriginalFilename(), employee.getId());
            } catch (IOException ex) {
                log.error("Failed to store employee document for employee id [{}]", employee.getId(), ex);
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
