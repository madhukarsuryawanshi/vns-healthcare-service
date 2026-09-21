package com.vns.healthcare.web;

import com.vns.healthcare.domain.CustomerStatus;
import com.vns.healthcare.entity.Customer;
import com.vns.healthcare.entity.CustomerDocument;
import com.vns.healthcare.entity.CustomerDuty;
import com.vns.healthcare.exception.BusinessException;
import com.vns.healthcare.service.CustomerDutyService;
import com.vns.healthcare.service.CustomerReportService;
import com.vns.healthcare.service.CustomerService;
import com.vns.healthcare.service.EmployeeService;
import com.vns.healthcare.service.FileStorageService;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/customers")
public class CustomerController {

    private static final Logger log = LoggerFactory.getLogger(CustomerController.class);

    private final CustomerService customerService;
    private final EmployeeService employeeService;
    private final CustomerDutyService dutyService;
    private final CustomerReportService reportService;
    private final FileStorageService fileStorageService;

    public CustomerController(CustomerService customerService,
                              EmployeeService employeeService,
                              CustomerDutyService dutyService,
                              CustomerReportService reportService,
                              FileStorageService fileStorageService) {
        this.customerService = customerService;
        this.employeeService = employeeService;
        this.dutyService = dutyService;
        this.reportService = reportService;
        this.fileStorageService = fileStorageService;
    }

    @GetMapping
    public String list(@RequestParam(value = "q", required = false) String query,
                       @RequestParam(value = "status", required = false) String status,
                       @RequestParam(value = "sort", required = false, defaultValue = "custCode") String sort,
                       @RequestParam(value = "dir", required = false, defaultValue = "asc") String dir,
                       Model model) {
        log.info("Listing customers with query [{}], status [{}], sort [{}], dir [{}]", query, status, sort, dir);
        LocalDate today = LocalDate.now();
        List<Customer> customers = new ArrayList<Customer>(customerService.list(query));
        if (status != null && !status.trim().isEmpty()) {
            final String normalized = status.trim();
            customers.removeIf(c -> c.getStatus() == null || !c.getStatus().name().equalsIgnoreCase(normalized));
        }
        customers = sortCustomers(customers, sort, dir);
        model.addAttribute("page", "customers");
        model.addAttribute("customers", customers);
        model.addAttribute("q", query == null ? "" : query);
        model.addAttribute("status", status == null ? "" : status);
        model.addAttribute("sort", sort);
        model.addAttribute("dir", dir);
        model.addAttribute("statuses", CustomerStatus.values());
        model.addAttribute("reportFrom", today.withDayOfMonth(1));
        model.addAttribute("reportTo", today);
        model.addAttribute("customerSuggestions", customerService.list(null).stream()
                .flatMap(c -> java.util.stream.Stream.of(
                        c.getFullName(),
                        c.getMobileNo(),
                        c.getPatientName(),
                        c.getCustCode(),
                        c.getAssignedEmployee() != null ? c.getAssignedEmployee().getFullName() : null,
                        c.getAssignedEmployee() != null ? c.getAssignedEmployee().getEmpCode() : null))
                .distinct()
                .filter(v -> v != null && !v.trim().isEmpty())
                .sorted()
                .collect(java.util.stream.Collectors.toList()));
        return "customers/list";
    }

    private List<Customer> sortCustomers(List<Customer> customers, String sort, String dir) {
        Comparator<Customer> comparator = comparatorForCustomer(sort);
        if ("desc".equalsIgnoreCase(dir)) {
            comparator = comparator.reversed();
        }
        customers.sort(comparator);
        return customers;
    }

    private Comparator<Customer> comparatorForCustomer(String sort) {
        if (sort == null || sort.trim().isEmpty()) {
            sort = "custCode";
        }
        switch (sort) {
            case "fullName":
                return Comparator.comparing(c -> c.getFullName() == null ? "" : c.getFullName(), Comparator.nullsLast(String::compareToIgnoreCase));
            case "patientName":
                return Comparator.comparing(c -> c.getPatientName() == null ? "" : c.getPatientName(), Comparator.nullsLast(String::compareToIgnoreCase));
            case "serviceType":
                return Comparator.comparing(c -> c.getServiceType() == null ? "" : c.getServiceType().name(), Comparator.nullsLast(String::compareToIgnoreCase));
            case "charges":
                return Comparator.comparing(Customer::getCharges, Comparator.nullsLast(BigDecimal::compareTo));
            case "status":
                return Comparator.comparing(c -> c.getStatus() == null ? "" : c.getStatus().name(), Comparator.nullsLast(String::compareToIgnoreCase));
            case "billedAmount":
                return Comparator.comparing(Customer::getBilledAmount, Comparator.nullsLast(BigDecimal::compareTo));
            case "assignedEmployee":
                return Comparator.comparing(c -> c.getAssignedEmployee() == null ? "" : c.getAssignedEmployee().getFullName(), Comparator.nullsLast(String::compareToIgnoreCase));
            case "custCode":
            default:
                return Comparator.comparing(Customer::getCustCode, Comparator.nullsLast(String::compareToIgnoreCase));
        }
    }

    @GetMapping("/report")
    public ResponseEntity<byte[]> report(
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (to == null) {
            to = LocalDate.now();
        }
        if (from == null) {
            from = to.withDayOfMonth(1);
        }
        log.info("Generating customer report from [{}] to [{}]", from, to);
        byte[] excel = reportService.buildExcel(from, to);
        String filename = "VNS-customer-charges-" + from + "-to-" + to + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excel);
    }

    @PreAuthorize("hasAuthority('customers:write') or hasRole('ADMIN')")
    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("page", "customers");
        model.addAttribute("form", new CustomerForm());
        model.addAttribute("mode", "create");
        model.addAttribute("staff", employeeService.activeCareStaff());
        model.addAttribute("statuses", CustomerStatus.values());
        return "customers/form";
    }

    @PreAuthorize("hasAuthority('customers:write') or hasRole('ADMIN')")
    @PostMapping
    public String create(@Valid @ModelAttribute("form") CustomerForm form,
                         BindingResult bindingResult,
                         @RequestParam(value = "documents", required = false) MultipartFile[] documents,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            log.warn("Customer create validation failed for form [{}]", form.getPatientName());
            model.addAttribute("page", "customers");
            model.addAttribute("mode", "create");
            model.addAttribute("staff", employeeService.activeCareStaff());
            model.addAttribute("statuses", CustomerStatus.values());
            return "customers/form";
        }
        try {
            Customer saved = customerService.create(form, documents);
            log.info("Created customer [{}] with code [{}]", form.getPatientName(), saved.getCustCode());
            redirectAttributes.addFlashAttribute("success", "Lead " + saved.getCustCode() + " created.");
            return "redirect:/customers/" + saved.getId();
        } catch (BusinessException ex) {
            log.error("Failed to create customer [{}]", form.getPatientName(), ex);
            model.addAttribute("page", "customers");
            model.addAttribute("mode", "create");
            model.addAttribute("staff", employeeService.activeCareStaff());
            model.addAttribute("statuses", CustomerStatus.values());
            model.addAttribute("error", ex.getMessage());
            return "customers/form";
        }
    }

    @GetMapping("/{id:\\d+}")
    public String detail(@PathVariable Long id,
                         @RequestParam(value = "month", required = false) String month,
                         Model model) {
        Customer customer = customerService.get(id);
        YearMonth yearMonth = parseMonth(month);
        LocalDate from = yearMonth.atDay(1);
        LocalDate to = yearMonth.atEndOfMonth();
        Map<LocalDate, CustomerDuty> dutyMap = new LinkedHashMap<LocalDate, CustomerDuty>();
        for (CustomerDuty duty : dutyService.list(id, from, to)) {
            dutyMap.put(duty.getDutyDate(), duty);
        }
        List<DutyDayView> dayRows = new ArrayList<DutyDayView>();
        for (LocalDate day : dutyService.daysInRange(from, to)) {
            dayRows.add(new DutyDayView(day, dutyMap.get(day)));
        }
        LocalDate billTo = customer.isClosed() && customer.getServiceClosedDate() != null
                ? customer.getServiceClosedDate()
                : LocalDate.now();
        LocalDate billFrom = billTo.withDayOfMonth(1);

        model.addAttribute("page", "customers");
        model.addAttribute("customer", customer);
        model.addAttribute("staff", employeeService.activeCareStaff());
        model.addAttribute("month", yearMonth.toString());
        model.addAttribute("monthLabel", yearMonth);
        model.addAttribute("dayRows", dayRows);
        model.addAttribute("runningTotal", dutyService.calculateCharges(customer, billFrom, billTo));
        model.addAttribute("billFrom", billFrom);
        model.addAttribute("billTo", billTo);
        return "customers/detail";
    }

    @PreAuthorize("hasAuthority('customers:write') or hasRole('ADMIN')")
    @GetMapping("/{id:\\d+}/edit")
    public String editForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Customer customer = customerService.get(id);
        if (customer.isClosed()) {
            redirectAttributes.addFlashAttribute("error", "Closed services cannot be edited.");
            return "redirect:/customers/" + id;
        }
        model.addAttribute("page", "customers");
        model.addAttribute("form", toForm(customer));
        model.addAttribute("form", toForm(customer));
        model.addAttribute("mode", "edit");
        model.addAttribute("customerId", id);
        model.addAttribute("custCode", customer.getCustCode());
        model.addAttribute("staff", employeeService.activeCareStaff());
        model.addAttribute("statuses", CustomerStatus.values());
        return "customers/form";
    }

    @PreAuthorize("hasAuthority('customers:write') or hasRole('ADMIN')")
    @PostMapping("/{id:\\d+}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("form") CustomerForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("page", "customers");
            model.addAttribute("mode", "edit");
            model.addAttribute("customerId", id);
            model.addAttribute("staff", employeeService.activeStaff());
            model.addAttribute("statuses", CustomerStatus.values());
            return "customers/form";
        }
        try {
            customerService.update(id, form);
            redirectAttributes.addFlashAttribute("success", "Customer details updated.");
            return "redirect:/customers/" + id;
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/customers/" + id;
        }
    }

    @PreAuthorize("hasAuthority('customers:write') or hasRole('ADMIN')")
    @PostMapping("/{id:\\d+}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        customerService.delete(id);
        redirectAttributes.addFlashAttribute("success", "Lead removed.");
        return "redirect:/customers";
    }

    @PreAuthorize("hasAuthority('customers:write') or hasRole('ADMIN')")
    @PostMapping("/{id:\\d+}/assign")
    public String assign(@PathVariable Long id,
                         @RequestParam(required = false) Long employeeId,
                         RedirectAttributes redirectAttributes) {
        log.info("Assigning employee [{}] to customer [{}]", employeeId, id);
        try {
            customerService.assignEmployee(id, employeeId);
            redirectAttributes.addFlashAttribute("success", "Staff assignment updated.");
        } catch (BusinessException ex) {
            log.error("Failed to assign employee [{}] to customer [{}]", employeeId, id, ex);
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/customers/" + id;
    }

    @PreAuthorize("hasAuthority('customers:write') or hasRole('ADMIN')")
    @PostMapping("/{id:\\d+}/duties")
    public String applyDuty(@PathVariable Long id,
                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                            @RequestParam(required = false) Long employeeId,
                            @RequestParam(value = "hold", required = false) boolean hold,
                            RedirectAttributes redirectAttributes) {
        try {
            dutyService.applyRange(id, from, to, employeeId, hold);
            redirectAttributes.addFlashAttribute("success",
                    hold ? "Hold saved for the selected days." : "Employee assigned for the selected days.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        String month = from == null ? "" : from.getYear() + "-" + String.format("%02d", from.getMonthValue());
        return "redirect:/customers/" + id + (month.isEmpty() ? "" : "?month=" + month);
    }

    @PreAuthorize("hasAuthority('customers:write') or hasRole('ADMIN')")
    @PostMapping("/{id:\\d+}/close")
    public String close(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            Customer closed = customerService.closeService(id, dutyService);
            redirectAttributes.addFlashAttribute("success",
                    "Service closed. Billed amount for " + closed.getServiceClosedDate().withDayOfMonth(1)
                            + " to " + closed.getServiceClosedDate() + ": " + closed.getBilledAmount());
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/customers/" + id;
    }

    @PreAuthorize("hasAuthority('customers:write') or hasRole('ADMIN')")
    @PostMapping("/{id:\\d+}/recalculate")
    public String recalculate(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            Customer closed = customerService.recalculateBilledAmount(id, dutyService);
            redirectAttributes.addFlashAttribute("success",
                    "Billed amount updated: " + closed.getBilledAmount());
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/customers/" + id;
    }

    @PreAuthorize("hasAuthority('customers:write') or hasRole('ADMIN')")
    @PostMapping("/{id:\\d+}/documents")
    public String upload(@PathVariable Long id,
                         @RequestParam("documents") MultipartFile[] documents,
                         RedirectAttributes redirectAttributes) {
        log.info("Uploading customer documents for id [{}], count [{}]", id, documents == null ? 0 : documents.length);
        try {
            customerService.addDocuments(id, documents);
            redirectAttributes.addFlashAttribute("success", "Documents uploaded.");
        } catch (BusinessException ex) {
            log.error("Customer document upload failed for id [{}]", id, ex);
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/customers/" + id;
    }

    @GetMapping("/{id:\\d+}/documents/{docId}")
    public ResponseEntity<Resource> download(@PathVariable Long id, @PathVariable Long docId) {
        CustomerDocument document = customerService.getDocument(id, docId);
        Resource resource = fileStorageService.load(document);
        String filename = document.getOriginalFilename() == null ? "document" : document.getOriginalFilename();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(
                        document.getContentType() == null ? "application/octet-stream" : document.getContentType()))
                .body(resource);
    }

    private YearMonth parseMonth(String month) {
        if (month == null || month.trim().isEmpty()) {
            return YearMonth.from(LocalDate.now());
        }
        return YearMonth.parse(month.trim());
    }

    private CustomerForm toForm(Customer customer) {
        CustomerForm form = new CustomerForm();
        form.setFullName(customer.getFullName());
        form.setMobileNo(customer.getMobileNo());
        form.setAddress(customer.getAddress());
        form.setPatientName(customer.getPatientName());
        form.setGender(customer.getGender() == null ? "" : customer.getGender().name());
        form.setAge(customer.getAge());
        form.setMedicalHistory(customer.getMedicalHistory());
        form.setServiceStartDate(customer.getServiceStartDate());
        form.setServiceType(customer.getServiceType().name());
        form.setCharges(customer.getCharges());
        form.setEmployeeId(customer.getAssignedEmployee() == null ? null : customer.getAssignedEmployee().getId());
        form.setStatus(customer.getStatus().name());
        form.setEmail(customer.getEmail());
        return form;
    }
}
