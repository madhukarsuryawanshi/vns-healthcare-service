package com.vns.healthcare.web;

import com.vns.healthcare.domain.SalaryPayStatus;
import com.vns.healthcare.domain.TrainingStatus;
import com.vns.healthcare.entity.Employee;
import com.vns.healthcare.entity.EmployeeDocument;
import com.vns.healthcare.exception.BusinessException;
import com.vns.healthcare.service.EmployeeService;
import com.vns.healthcare.service.FileStorageService;
import com.vns.healthcare.service.SalaryPaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.time.LocalDate;
import java.time.YearMonth;

@Controller
@RequestMapping("/employees")
public class EmployeeController {

    private static final Logger log = LoggerFactory.getLogger(EmployeeController.class);

    private final EmployeeService employeeService;
    private final FileStorageService fileStorageService;
    private final SalaryPaymentService salaryPaymentService;

    public EmployeeController(EmployeeService employeeService,
                              FileStorageService fileStorageService,
                              SalaryPaymentService salaryPaymentService) {
        this.employeeService = employeeService;
        this.fileStorageService = fileStorageService;
        this.salaryPaymentService = salaryPaymentService;
    }

    @GetMapping
    public String list(@RequestParam(value = "q", required = false) String query,
                       @RequestParam(value = "status", required = false) String status,
                       @RequestParam(value = "sort", required = false, defaultValue = "empCode") String sort,
                       @RequestParam(value = "dir", required = false, defaultValue = "asc") String dir,
                       Model model) {
        log.info("Listing employees with query [{}], status [{}], sort [{}], dir [{}]", query, status, sort, dir);
        List<Employee> employees = new ArrayList<Employee>(employeeService.list(query));
        if (status != null && !status.trim().isEmpty()) {
            final String normalized = status.trim();
            employees.removeIf(e -> e.getStatus() == null || !e.getStatus().name().equalsIgnoreCase(normalized));
        }
        employees = sortEmployees(employees, sort, dir);
        model.addAttribute("page", "employees");
        model.addAttribute("employees", employees);
        model.addAttribute("q", query == null ? "" : query);
        model.addAttribute("status", status == null ? "" : status);
        model.addAttribute("sort", sort);
        model.addAttribute("dir", dir);
        model.addAttribute("statuses", com.vns.healthcare.domain.EmployeeStatus.values());
        model.addAttribute("employeeSuggestions", employeeService.list(null).stream()
                .flatMap(e -> java.util.stream.Stream.of(
                        e.getFullName(),
                        e.getMobileNo(),
                        e.getEmpCode(),
                        e.getAadharNumber()))
                .distinct()
                .filter(v -> v != null && !v.trim().isEmpty())
                .sorted()
                .collect(java.util.stream.Collectors.toList()));
        return "employees/list";
    }

    private List<Employee> sortEmployees(List<Employee> employees, String sort, String dir) {
        Comparator<Employee> comparator = comparatorForEmployee(sort);
        if ("desc".equalsIgnoreCase(dir)) {
            comparator = comparator.reversed();
        }
        employees.sort(comparator);
        return employees;
    }

    private Comparator<Employee> comparatorForEmployee(String sort) {
        if (sort == null || sort.trim().isEmpty()) {
            sort = "empCode";
        }
        switch (sort) {
            case "fullName":
                return Comparator.comparing(Employee::getFullName, Comparator.nullsLast(String::compareToIgnoreCase));
            case "mobileNo":
                return Comparator.comparing(Employee::getMobileNo, Comparator.nullsLast(String::compareToIgnoreCase));
            case "joiningDate":
                return Comparator.comparing(Employee::getJoiningDate, Comparator.nullsLast(LocalDate::compareTo));
            case "trainingStatus":
                return Comparator.comparing(e -> e.getTrainingStatus() == null ? "" : e.getTrainingStatus().name(), Comparator.nullsLast(String::compareToIgnoreCase));
            case "salary":
                return Comparator.comparing(Employee::getSalary, Comparator.nullsLast(java.math.BigDecimal::compareTo));
            case "status":
                return Comparator.comparing(e -> e.getStatus() == null ? "" : e.getStatus().name(), Comparator.nullsLast(String::compareToIgnoreCase));
            case "onboarded":
                return Comparator.comparing(Employee::isOnboarded);
            case "empCode":
            default:
                return Comparator.comparing(Employee::getEmpCode, Comparator.nullsLast(String::compareToIgnoreCase));
        }
    }

    @PreAuthorize("hasAuthority('employees:write') or hasRole('ADMIN')")
    @GetMapping("/new")
    public String createForm(Model model) {
        log.info("Opening employee create form");
        model.addAttribute("page", "employees");
        model.addAttribute("form", new EmployeeForm());
        model.addAttribute("mode", "create");
        return "employees/form";
    }

    @PreAuthorize("hasAuthority('employees:write') or hasRole('ADMIN')")
    @PostMapping
    public String create(@Valid @ModelAttribute("form") EmployeeForm form,
                         BindingResult bindingResult,
                         @RequestParam(value = "document", required = false) MultipartFile document,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            log.warn("Employee form validation failed for create request");
            model.addAttribute("page", "employees");
            model.addAttribute("mode", "create");
            return "employees/form";
        }
        try {
            Employee saved = employeeService.create(form, document);
            log.info("Created employee [{}] with employee code [{}]", form.getFullName(), saved.getEmpCode());
            redirectAttributes.addFlashAttribute("success", "Employee " + saved.getEmpCode() + " added.");
            return "redirect:/employees/" + saved.getId();
        } catch (BusinessException ex) {
            log.error("Failed to create employee [{}]", form.getFullName(), ex);
            model.addAttribute("page", "employees");
            model.addAttribute("mode", "create");
            model.addAttribute("error", ex.getMessage());
            return "employees/form";
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id,
                         @RequestParam(value = "year", required = false) Integer year,
                         Model model) {
        log.info("Viewing employee detail for id [{}] year [{}]", id, year);
        Employee employee = employeeService.get(id);
        int y = year == null ? YearMonth.now().getYear() : year;
        model.addAttribute("page", "employees");
        model.addAttribute("employee", employee);
        model.addAttribute("trainingStatuses", TrainingStatus.values());
        model.addAttribute("salaryYear", y);
        model.addAttribute("salaryMonths", salaryPaymentService.monthsForEmployee(employee, y));
        return "employees/detail";
    }

    @PreAuthorize("hasAuthority('employees:write') or hasRole('ADMIN')")
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Employee employee = employeeService.get(id);
        EmployeeForm form = toForm(employee);
        model.addAttribute("page", "employees");
        model.addAttribute("form", form);
        model.addAttribute("mode", "edit");
        model.addAttribute("employeeId", id);
        model.addAttribute("empCode", employee.getEmpCode());
        return "employees/form";
    }

    @PreAuthorize("hasAuthority('employees:write') or hasRole('ADMIN')")
    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("form") EmployeeForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("page", "employees");
            model.addAttribute("mode", "edit");
            model.addAttribute("employeeId", id);
            return "employees/form";
        }
        try {
            employeeService.update(id, form);
            redirectAttributes.addFlashAttribute("success", "Employee details updated.");
            return "redirect:/employees/" + id;
        } catch (BusinessException ex) {
            model.addAttribute("page", "employees");
            model.addAttribute("mode", "edit");
            model.addAttribute("employeeId", id);
            model.addAttribute("error", ex.getMessage());
            return "employees/form";
        }
    }

    @PreAuthorize("hasAuthority('employees:write') or hasRole('ADMIN')")
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.info("Deleting employee with id [{}]", id);
        employeeService.delete(id);
        redirectAttributes.addFlashAttribute("success", "Employee removed.");
        return "redirect:/employees";
    }

    @PreAuthorize("hasAuthority('employees:write') or hasRole('ADMIN')")
    @PostMapping("/{id}/training")
    public String training(@PathVariable Long id,
                           @RequestParam TrainingStatus trainingStatus,
                           @RequestParam(required = false) String trainingNotes,
                           RedirectAttributes redirectAttributes) {
        log.info("Updating training for employee id [{}] to [{}]", id, trainingStatus);
        employeeService.updateTraining(id, trainingStatus, trainingNotes);
        redirectAttributes.addFlashAttribute("success", "Training record saved.");
        return "redirect:/employees/" + id;
    }

    @PreAuthorize("hasAuthority('employees:write') or hasRole('ADMIN')")
    @PostMapping("/{id}/onboard")
    public String onboard(@PathVariable Long id,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate salaryStartDate,
                          RedirectAttributes redirectAttributes) {
        employeeService.onboard(id, salaryStartDate);
        redirectAttributes.addFlashAttribute("success", "Employee onboarded. Salary start recorded.");
        return "redirect:/employees/" + id;
    }

    @PreAuthorize("hasAuthority('employees:write') or hasRole('ADMIN')")
    @PostMapping("/{id}/resign")
    public String resign(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        employeeService.resign(id);
        redirectAttributes.addFlashAttribute("success", "Employee resigned and assignments released.");
        return "redirect:/employees/" + id;
    }

    @PreAuthorize("hasAuthority('employees:write') or hasRole('ADMIN')")
    @PostMapping("/{id}/salary")
    public String markSalary(@PathVariable Long id,
                             @RequestParam int year,
                             @RequestParam int month,
                             @RequestParam SalaryPayStatus status,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate paidOn,
                             @RequestParam(required = false) String notes,
                             RedirectAttributes redirectAttributes) {
        try {
            salaryPaymentService.mark(id, year, month, status, paidOn, notes);
            redirectAttributes.addFlashAttribute("success", "Salary status saved.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/employees/" + id + "?year=" + year;
    }

    @PreAuthorize("hasAuthority('employees:write') or hasRole('ADMIN')")
    @PostMapping("/{id}/documents")
    public String upload(@PathVariable Long id,
                         @RequestParam("document") MultipartFile document,
                         RedirectAttributes redirectAttributes) {
        log.info("Uploading employee document for id [{}], filename [{}]", id, document != null ? document.getOriginalFilename() : null);
        try {
            employeeService.addDocument(id, document);
            redirectAttributes.addFlashAttribute("success", "Document uploaded.");
        } catch (BusinessException ex) {
            log.error("Employee document upload failed for id [{}]", id, ex);
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/employees/" + id;
    }

    @GetMapping("/{id}/documents/{docId}")
    public ResponseEntity<Resource> download(@PathVariable Long id, @PathVariable Long docId) {
        EmployeeDocument document = employeeService.getDocument(id, docId);
        Resource resource = fileStorageService.load(document);
        String filename = document.getOriginalFilename() == null ? "document" : document.getOriginalFilename();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(
                        document.getContentType() == null ? "application/octet-stream" : document.getContentType()))
                .body(resource);
    }

    @GetMapping("/{id}/brochure")
    public String brochure(@PathVariable Long id, Model model) {
        model.addAttribute("employee", employeeService.get(id));
        return "employees/brochure";
    }

    private EmployeeForm toForm(Employee employee) {
        EmployeeForm form = new EmployeeForm();
        form.setFullName(employee.getFullName());
        form.setMobileNo(employee.getMobileNo());
        form.setJoiningDate(employee.getJoiningDate());
        form.setGender(employee.getGender().name());
        form.setDateOfBirth(employee.getDateOfBirth());
        form.setReferredBy(employee.getReferredBy());
        form.setFullAddress(employee.getFullAddress());
        form.setAadharNumber(employee.getAadharNumber());
        form.setSalary(employee.getSalary());
        return form;
    }
}
