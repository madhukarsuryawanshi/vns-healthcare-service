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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.time.LocalDate;
import java.time.YearMonth;

@Controller
@RequestMapping("/employees")
public class EmployeeController {

    private static final Logger log = LoggerFactory.getLogger(EmployeeController.class);

    private static final long SUGGESTION_CACHE_TTL_MS = 5000L;

    private final EmployeeService employeeService;
    private final FileStorageService fileStorageService;
    private final SalaryPaymentService salaryPaymentService;
    private final com.vns.healthcare.security.UserActivityService userActivityService;
    private final Map<String, SuggestionCacheEntry> suggestionCache = new ConcurrentHashMap<String, SuggestionCacheEntry>();

    public EmployeeController(EmployeeService employeeService,
                              FileStorageService fileStorageService,
                              SalaryPaymentService salaryPaymentService,
                              com.vns.healthcare.security.UserActivityService userActivityService) {
        this.employeeService = employeeService;
        this.fileStorageService = fileStorageService;
        this.salaryPaymentService = salaryPaymentService;
        this.userActivityService = userActivityService;
    }

    @GetMapping("/suggestions")
    @ResponseBody
    public List<String> suggestions(@RequestParam(value = "q", required = false) String query,
                                  @RequestParam(value = "limit", required = false, defaultValue = "10") int limit) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        int maxLimit = Math.max(1, Math.min(limit, 20));
        String cacheKey = (q + "|" + maxLimit).toLowerCase();
        long now = System.currentTimeMillis();
        SuggestionCacheEntry cached = suggestionCache.get(cacheKey);
        if (cached != null && now - cached.cachedAt < SUGGESTION_CACHE_TTL_MS) {
            return cached.values;
        }
        List<String> result = employeeService.searchSuggestions(q, maxLimit);
        suggestionCache.put(cacheKey, new SuggestionCacheEntry(result, now));
        return result;
    }

    @GetMapping
    public String list(@RequestParam(value = "q", required = false) String query,
                       @RequestParam(value = "status", required = false) String status,
                       @RequestParam(value = "designation", required = false) String designation,
                       @RequestParam(value = "attendance", required = false) String attendance,
                       @RequestParam(value = "sort", required = false, defaultValue = "empCode") String sort,
                       @RequestParam(value = "dir", required = false, defaultValue = "asc") String dir,
                       @RequestParam(value = "page", required = false, defaultValue = "0") int page,
                       @RequestParam(value = "size", required = false, defaultValue = "10") int size,
                       Model model) {
        log.info("Listing employees with query [{}], status [{}], sort [{}], dir [{}], page [{}], size [{}]", query, status, sort, dir, page, size);

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 10), 100);
        String normalizedSort = normalizeSortField(sort);
        Sort.Direction direction = "desc".equalsIgnoreCase(dir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(direction, normalizedSort));

        java.util.Map<Long, com.vns.healthcare.domain.AttendanceStatus> todayMap = employeeService.todayAttendanceStatusMap();
        java.util.List<String> employeeSuggestions = employeeService.searchSuggestions(query, 15);
        Page<Employee> employeePage;

        com.vns.healthcare.domain.EmployeeStatus normalizedStatus = parseEmployeeStatus(status);
        com.vns.healthcare.domain.Designation normalizedDesignation = parseDesignation(designation);

        if (normalizedStatus != null || normalizedDesignation != null || (query != null && !query.trim().isEmpty())) {
            employeePage = employeeService.filterPage(normalizedStatus, normalizedDesignation, query, pageable);
        } else {
            employeePage = employeeService.listPage(pageable);
        }

        List<Employee> employees = new ArrayList<Employee>(employeePage.getContent());
        if (attendance != null && !attendance.trim().isEmpty()) {
            List<Employee> attendanceFiltered = filterEmployeesByAttendance(employees, attendance.trim().toUpperCase(), todayMap);
            employeePage = new PageImpl<Employee>(attendanceFiltered, PageRequest.of(safePage, safeSize, Sort.by(direction, normalizedSort)), attendanceFiltered.size());
            employees = new ArrayList<Employee>(attendanceFiltered);
        }

        model.addAttribute("page", "employees");
        model.addAttribute("employees", employees);
        model.addAttribute("pagination", employeePage);
        model.addAttribute("filteredRecordCount", employeePage.getTotalElements());
        model.addAttribute("filterSummary", buildFilterSummary(query, status, designation, attendance));
        model.addAttribute("currentPage", safePage);
        model.addAttribute("pageSize", safeSize);
        model.addAttribute("q", query == null ? "" : query);
        model.addAttribute("status", status == null ? "" : status);
        model.addAttribute("designation", designation == null ? "" : designation);
        model.addAttribute("attendance", attendance == null ? "" : attendance);
        model.addAttribute("sort", sort);
        model.addAttribute("dir", dir);
        model.addAttribute("statuses", com.vns.healthcare.domain.EmployeeStatus.values());
        model.addAttribute("designations", com.vns.healthcare.domain.Designation.values());
        model.addAttribute("attendanceOptions", new String[]{"PRESENT","ABSENT","LEAVE","HALF_DAY"});
        model.addAttribute("employeeSuggestions", employeeSuggestions);
        model.addAttribute("presentTodayIds", employeeService.presentTodayEmployeeIds(todayMap));
        model.addAttribute("todayAttendance", todayMap);
        return "employees/list";
    }

    private static class SuggestionCacheEntry {
        private final List<String> values;
        private final long cachedAt;

        private SuggestionCacheEntry(List<String> values, long cachedAt) {
            this.values = values == null ? java.util.Collections.emptyList() : new ArrayList<String>(values);
            this.cachedAt = cachedAt;
        }
    }

    private List<Employee> filterEmployeesByAttendance(List<Employee> employees,
                                                      String attendanceValue,
                                                      java.util.Map<Long, com.vns.healthcare.domain.AttendanceStatus> todayMap) {
        if (employees == null || employees.isEmpty() || attendanceValue == null || attendanceValue.trim().isEmpty()) {
            return employees == null ? new ArrayList<Employee>() : new ArrayList<Employee>(employees);
        }
        List<Employee> filtered = new ArrayList<Employee>();
        for (Employee employee : employees) {
            com.vns.healthcare.domain.AttendanceStatus status = todayMap == null ? null : todayMap.get(employee.getId());
            switch (attendanceValue.trim().toUpperCase()) {
                case "PRESENT":
                    if (status == com.vns.healthcare.domain.AttendanceStatus.PRESENT || status == com.vns.healthcare.domain.AttendanceStatus.HALF_DAY) {
                        filtered.add(employee);
                    }
                    break;
                case "ABSENT":
                    if (status == com.vns.healthcare.domain.AttendanceStatus.ABSENT) {
                        filtered.add(employee);
                    }
                    break;
                case "LEAVE":
                    if (status == com.vns.healthcare.domain.AttendanceStatus.LEAVE) {
                        filtered.add(employee);
                    }
                    break;
                case "HALF_DAY":
                    if (status == com.vns.healthcare.domain.AttendanceStatus.HALF_DAY) {
                        filtered.add(employee);
                    }
                    break;
                default:
                    filtered.add(employee);
                    break;
            }
        }
        return filtered;
    }

    private String buildFilterSummary(String query, String status, String designation, String attendance) {
        if (status != null && !status.trim().isEmpty()) {
            return formatFilterLabel(status) + " records";
        }
        if (designation != null && !designation.trim().isEmpty()) {
            return formatFilterLabel(designation) + " records";
        }
        if (attendance != null && !attendance.trim().isEmpty()) {
            return formatFilterLabel(attendance) + " records";
        }
        if (query != null && !query.trim().isEmpty()) {
            return "Search results";
        }
        return "Total records";
    }

    private String formatFilterLabel(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "Total";
        }
        String normalized = value.trim().replace('_', ' ');
        String[] parts = normalized.split("\\s+");
        StringBuilder formatted = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (formatted.length() > 0) {
                formatted.append(' ');
            }
            formatted.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1).toLowerCase());
        }
        return formatted.length() == 0 ? "Total" : formatted.toString();
    }

    private String normalizeSortField(String sort) {
        if (sort == null || sort.trim().isEmpty()) {
            return "empCode";
        }
        switch (sort) {
            case "fullName":
                return "fullName";
            case "mobileNo":
                return "mobileNo";
            case "joiningDate":
                return "joiningDate";
            case "trainingStatus":
                return "trainingStatus";
            case "designation":
                return "designation";
            case "salary":
                return "salary";
            case "status":
                return "status";
            case "onboarded":
                return "onboarded";
            case "createdAt":
                return "createdAt";
            case "empCode":
            default:
                return "empCode";
        }
    }

    private com.vns.healthcare.domain.EmployeeStatus parseEmployeeStatus(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return com.vns.healthcare.domain.EmployeeStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private com.vns.healthcare.domain.Designation parseDesignation(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return com.vns.healthcare.domain.Designation.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
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
            case "designation":
                return Comparator.comparing(e -> e.getDesignation() == null ? "" : e.getDesignation().name(), Comparator.nullsLast(String::compareToIgnoreCase));
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
                         @RequestParam(value = "documents", required = false) MultipartFile[] documents,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            log.warn("Employee form validation failed for create request");
            model.addAttribute("page", "employees");
            model.addAttribute("mode", "create");
            return "employees/form";
        }
        try {
            Employee saved = employeeService.create(form, documents);
            userActivityService.logCurrentUser("CREATE", "EMPLOYEE", saved.getId(),
                    "Created employee " + saved.getEmpCode() + " - " + saved.getFullName());
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
        model.addAttribute("passportPhoto", employeeService.getPassportPhoto(employee));
        model.addAttribute("documentsForDisplay", employeeService.getDocuments(id, "DOCUMENT"));
        // compute age for brochure and templates (defensive)
        if (employee.getDateOfBirth() != null) {
            java.time.Period p = java.time.Period.between(employee.getDateOfBirth(), java.time.LocalDate.now());
            model.addAttribute("employeeAge", p.getYears());
        } else {
            model.addAttribute("employeeAge", null);
        }
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
            Employee updated = employeeService.update(id, form);
            userActivityService.logCurrentUser("UPDATE", "EMPLOYEE", updated.getId(),
                    "Updated employee " + updated.getEmpCode() + " - " + updated.getFullName());
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
        Employee employee = employeeService.get(id);
        log.info("Deleting employee with id [{}]", id);
        employeeService.delete(id);
        userActivityService.logCurrentUser("DELETE", "EMPLOYEE", id,
                "Deleted employee " + employee.getEmpCode() + " - " + employee.getFullName());
        redirectAttributes.addFlashAttribute("success", "Employee removed.");
        return "redirect:/employees";
    }

    @PreAuthorize("hasAuthority('employees:write') or hasRole('ADMIN')")
    @PostMapping("/{id}/training")
    public String training(@PathVariable Long id,
                           @RequestParam TrainingStatus trainingStatus,
                           @RequestParam(required = false) String trainingNotes,
                           @RequestParam(required = false) String anchor,
                           RedirectAttributes redirectAttributes) {
        log.info("Updating training for employee id [{}] to [{}]", id, trainingStatus);
        employeeService.updateTraining(id, trainingStatus, trainingNotes);
        redirectAttributes.addFlashAttribute("success", "Training record saved.");
        String redirect = "redirect:/employees/" + id;
        if (anchor != null && !anchor.trim().isEmpty()) {
            redirect += "#" + anchor.trim();
        }
        return redirect;
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
                             @RequestParam(required = false) String anchor,
                             RedirectAttributes redirectAttributes) {
        try {
            salaryPaymentService.mark(id, year, month, status, paidOn, notes);
            Employee employee = employeeService.get(id);
            userActivityService.logCurrentUser("UPDATE", "SALARY", id,
                    "Updated salary status for " + employee.getEmpCode() + " - " + employee.getFullName() + " for " + year + "/" + month + " to " + status);
            redirectAttributes.addFlashAttribute("success", "Salary status saved.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        String redirect = "redirect:/employees/" + id + "?year=" + year;
        if (anchor != null && !anchor.trim().isEmpty()) {
            redirect += "#" + anchor.trim();
        }
        return redirect;
    }

    @PreAuthorize("hasAuthority('employees:write') or hasRole('ADMIN')")
    @PostMapping("/{id}/documents")
    public String upload(@PathVariable Long id,
                         @RequestParam("documents") MultipartFile[] documents,
                         RedirectAttributes redirectAttributes) {
        log.info("Uploading employee documents for id [{}], count [{}]", id, documents == null ? 0 : documents.length);
        try {
            employeeService.addDocuments(id, documents);
            userActivityService.logCurrentUser("CREATE", "EMPLOYEE_DOCUMENTS", id,
                    "Uploaded employee documents for " + id + " (" + (documents == null ? 0 : documents.length) + " file(s))");
            redirectAttributes.addFlashAttribute("success", "Documents uploaded.");
        } catch (BusinessException ex) {
            log.error("Employee document upload failed for id [{}]", id, ex);
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/employees/" + id;
    }

    @PreAuthorize("hasAuthority('employees:write') or hasRole('ADMIN')")
    @PostMapping("/{id}/photo")
    public String uploadPhoto(@PathVariable Long id,
                            @RequestParam("photo") MultipartFile photo,
                            RedirectAttributes redirectAttributes) {
        try {
            employeeService.addPassportPhoto(id, photo);
            userActivityService.logCurrentUser("CREATE", "EMPLOYEE_PHOTO", id,
                    "Uploaded passport photo for employee " + id);
            redirectAttributes.addFlashAttribute("success", "Passport photo uploaded.");
        } catch (BusinessException ex) {
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
    public String brochure(@PathVariable Long id,
                          @RequestParam(value = "option", required = false) List<String> options,
                          @RequestParam(value = "catheterisationCare", required = false) String catheterisationCare,
                          @RequestParam(value = "includeAge", required = false) String includeAge,
                          @RequestParam(value = "age", required = false) String age,
                          Model model) {
        Employee employee = employeeService.get(id);
        List<String> brochureOptions = new java.util.ArrayList<>();
        if (options != null) {
            brochureOptions.addAll(options.stream()
                    .filter(value -> value != null && !value.trim().isEmpty())
                    .collect(java.util.stream.Collectors.toList()));
        }

        if (brochureOptions.contains("catheterisation-care") && catheterisationCare != null && !catheterisationCare.trim().isEmpty()) {
            brochureOptions.remove("catheterisation-care");
            String normalized = catheterisationCare.trim().toUpperCase();
            if ("ONLY_CARE".equals(normalized)) {
                brochureOptions.add("catheterisation-care-only-care");
            } else if ("EXPERT".equals(normalized)) {
                brochureOptions.add("catheterisation-care-expert");
            }
        }

        model.addAttribute("employee", employee);
        model.addAttribute("passportPhoto", employeeService.getPassportPhoto(employee));

        // determine age: use provided age when includeAge checked, else derive from DOB
        Integer resolvedAge = null;
        if (includeAge != null && includeAge.equalsIgnoreCase("true") && age != null && !age.trim().isEmpty()) {
            try {
                resolvedAge = Integer.parseInt(age.trim());
            } catch (NumberFormatException ex) {
                // ignore invalid age, fallback to DOB
            }
        }
        if (resolvedAge == null && employee.getDateOfBirth() != null) {
            resolvedAge = java.time.Period.between(employee.getDateOfBirth(), java.time.LocalDate.now()).getYears();
        }
        model.addAttribute("employeeAge", resolvedAge);

        model.addAttribute("brochureOptions", brochureOptions);
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
        form.setNoOfExperience(employee.getNoOfExperience());
        form.setSalary(employee.getSalary());
        form.setDesignation(employee.getDesignation() == null ? "" : employee.getDesignation().name());
        form.setEmail(employee.getEmail());
        form.setMaritalStatus(employee.getMaritalStatus() == null ? "" : employee.getMaritalStatus());
        java.util.List<String> knownLanguages = new java.util.ArrayList<String>();
        if (employee.getKnownLanguages() != null) {
            for (String language : employee.getKnownLanguages()) {
                if (language != null && !language.trim().isEmpty()) {
                    knownLanguages.add(language.trim().toUpperCase());
                }
            }
        }
        form.setKnownLanguages(knownLanguages);
        return form;
    }
}
