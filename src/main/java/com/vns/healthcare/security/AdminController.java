package com.vns.healthcare.security;

import com.vns.healthcare.entity.BusinessBankAccount;
import com.vns.healthcare.entity.BusinessBankStatement;
import com.vns.healthcare.entity.BusinessBankStatementTable;
import com.vns.healthcare.repository.BusinessBankAccountRepository;
import com.vns.healthcare.repository.BusinessBankStatementRepository;
import com.vns.healthcare.repository.BusinessBankStatementTableRepository;
import com.vns.healthcare.repository.EmployeeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Base64;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.math.BigDecimal;
import org.springframework.transaction.annotation.Transactional;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private static final List<String> AVAILABLE_PERMISSIONS = Arrays.asList(
            "dashboard",
            "employees",
            "customers",
            "attendance",
            "salary",
            "bank-statements",
            "admin"
    );

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final BusinessBankAccountRepository businessBankAccountRepository;
    private final BusinessBankStatementRepository businessBankStatementRepository;
    private final BusinessBankStatementTableRepository businessBankStatementTableRepository;
    private final PasswordEncoder passwordEncoder;
    private final com.vns.healthcare.service.EmployeeService employeeService;
    private final UserActivityService userActivityService;
    private final CacheManager cacheManager;
    private final com.vns.healthcare.service.BankStatementExportService bankStatementExportService;

    public AdminController(RoleRepository roleRepository,
                          UserRepository userRepository,
                          EmployeeRepository employeeRepository,
                          BusinessBankAccountRepository businessBankAccountRepository,
                          BusinessBankStatementRepository businessBankStatementRepository,
                          BusinessBankStatementTableRepository businessBankStatementTableRepository,
                          PasswordEncoder passwordEncoder,
                          com.vns.healthcare.service.EmployeeService employeeService,
                          UserActivityService userActivityService,
                          CacheManager cacheManager,
                          com.vns.healthcare.service.BankStatementExportService bankStatementExportService) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.businessBankAccountRepository = businessBankAccountRepository;
        this.businessBankStatementRepository = businessBankStatementRepository;
        this.businessBankStatementTableRepository = businessBankStatementTableRepository;
        this.passwordEncoder = passwordEncoder;
        this.employeeService = employeeService;
        this.userActivityService = userActivityService;
        this.cacheManager = cacheManager;
        this.bankStatementExportService = bankStatementExportService;
    }

    @GetMapping({"", "/users"})
    public String users(@RequestParam(value = "status", required = false) String status,
                        @RequestParam(value = "sort", required = false, defaultValue = "username") String sort,
                        @RequestParam(value = "dir", required = false, defaultValue = "asc") String dir,
                        Model model) {
        List<AppUser> users = new ArrayList<AppUser>(userRepository.findAll());
        if (status != null && !status.trim().isEmpty()) {
            final String normalized = status.trim();
            users.removeIf(user -> user.isEnabled() != Boolean.parseBoolean(normalized));
        }
        users.sort(sortUsers(sort, dir));
        Map<Long, String> employeeNames = new LinkedHashMap<Long, String>();
        for (AppUser user : users) {
            if (user == null || user.getUsername() == null || user.getUsername().trim().isEmpty()) {
                continue;
            }
            String normalizedUsername = user.getUsername().trim();
            employeeRepository.findByEmpCode(normalizedUsername.toUpperCase(Locale.ROOT))
                    .ifPresent(employee -> employeeNames.put(user.getId(), employee.getFullName() == null ? employee.getEmpCode() : employee.getFullName()));
        }
        model.addAttribute("page", "admin");
        model.addAttribute("users", users);
        model.addAttribute("employeeNames", employeeNames);
        model.addAttribute("status", status == null ? "" : status);
        model.addAttribute("sort", sort);
        model.addAttribute("dir", dir);
        model.addAttribute("userStatuses", java.util.Arrays.asList("true", "false"));
        return "admin/users";
    }

    @GetMapping("/users/validate-employee")
    @ResponseBody
    public Map<String, Boolean> validateEmployee(@RequestParam(value = "code", required = false) String code) {
        String normalized = normalizeEmployeeCode(code);
        boolean valid = isValidEmployeeCode(normalized);
        Map<String, Boolean> result = new LinkedHashMap<String, Boolean>();
        result.put("valid", valid);
        return result;
    }

    @GetMapping("/cache-debug")
    @ResponseBody
    public Map<String, Object> cacheDebug() {
        List<String> cacheNames = Arrays.asList(
                "employee-lists",
                "employee-pages",
                "employee-active",
                "employee-active-pages",
                "employeeById",
                "customer-lists",
                "customer-pages",
                "customerById",
                "dashboard-stats",
                "attendance-monthly",
                "attendance-summary",
                "salary-register"
        );

        Map<String, Object> result = new LinkedHashMap<>();
        for (String cacheName : cacheNames) {
            Cache cache = cacheManager == null ? null : cacheManager.getCache(cacheName);
            if (cache == null) {
                Map<String, Object> info = new LinkedHashMap<String, Object>();
                info.put("size", 0);
                info.put("keys", new ArrayList<Object>());
                result.put(cacheName, info);
                continue;
            }

            if (cache instanceof CaffeineCache) {
                com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache = ((CaffeineCache) cache).getNativeCache();
                List<Object> keys = new ArrayList<Object>(nativeCache.asMap().keySet());
                Map<String, Object> info = new LinkedHashMap<String, Object>();
                info.put("size", nativeCache.estimatedSize());
                info.put("keys", keys);
                result.put(cacheName, info);
                log.info("CACHE DEBUG {} size={} keys={}", cacheName, nativeCache.estimatedSize(), keys);
            } else {
                Map<String, Object> info = new LinkedHashMap<String, Object>();
                info.put("size", "unknown");
                info.put("keys", "cache implementation not caffeine");
                result.put(cacheName, info);
            }
        }
        return result;
    }

    @GetMapping("/roles")
    public String roles(@RequestParam(value = "sort", required = false, defaultValue = "name") String sort,
                        @RequestParam(value = "dir", required = false, defaultValue = "asc") String dir,
                        Model model) {
        List<Role> roles = new ArrayList<Role>(roleRepository.findAll());
        roles.sort(sortRoles(sort, dir));
        model.addAttribute("page", "admin");
        model.addAttribute("roles", roles);
        model.addAttribute("sort", sort);
        model.addAttribute("dir", dir);
        return "admin/roles";
    }

    @GetMapping("/activity")
    public String activity(@RequestParam(value = "page", required = false, defaultValue = "0") int page,
                           @RequestParam(value = "size", required = false, defaultValue = "20") int size,
                           Model model) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        org.springframework.data.domain.Page<UserActivityLog> activityPage = userActivityService.recentPage(safePage, safeSize);
        Map<LocalDate, List<UserActivityLog>> grouped = new LinkedHashMap<>();
        for (UserActivityLog activity : activityPage.getContent()) {
            if (activity == null || activity.getCreatedAt() == null) {
                continue;
            }
            LocalDate date = activity.getCreatedAt().toLocalDate();
            grouped.computeIfAbsent(date, key -> new ArrayList<>()).add(activity);
        }
        model.addAttribute("page", "admin");
        model.addAttribute("activities", activityPage.getContent());
        model.addAttribute("groupedActivities", grouped.entrySet());
        model.addAttribute("currentDate", LocalDate.now());
        model.addAttribute("pagination", activityPage);
        model.addAttribute("currentPage", safePage);
        model.addAttribute("pageSize", safeSize);
        return "admin/activity";
    }

    @GetMapping("/bank-accounts")
    public String bankAccounts(Model model) {
        model.addAttribute("page", "admin");
        model.addAttribute("accounts", businessBankAccountRepository.findAll());
        return "admin/bank-accounts";
    }

    public String allBankStatements(Model model) {
        return allBankStatements(model, "desc");
    }

    @GetMapping("/bank-statements")
    @PreAuthorize("authentication.name == 'admin' or hasAuthority('bank-statements:read') or hasAuthority('bank-statements:write')")
    public String allBankStatements(Model model,
                                    @RequestParam(value = "monthSort", required = false, defaultValue = "desc") String monthSort) {
        List<BusinessBankStatementTable> tables = businessBankStatementTableRepository.findAllByOrderByIdDesc();
        YearMonth currentMonth = YearMonth.now();
        Map<YearMonth, List<BusinessBankStatementTable>> grouped = new LinkedHashMap<>();

        for (BusinessBankStatementTable table : tables) {
            YearMonth month = findTableMonth(table, currentMonth);
            grouped.computeIfAbsent(month, key -> new ArrayList<>()).add(table);
        }

        boolean chronological = "asc".equalsIgnoreCase(monthSort);
        List<Map.Entry<YearMonth, List<BusinessBankStatementTable>>> monthEntries = new ArrayList<>(grouped.entrySet());
        monthEntries.sort(chronological
                ? Map.Entry.comparingByKey()
                : Map.Entry.<YearMonth, List<BusinessBankStatementTable>>comparingByKey().reversed());

        List<Map<String, Object>> monthGroups = new ArrayList<>();
        for (Map.Entry<YearMonth, List<BusinessBankStatementTable>> entry : monthEntries) {
            Map<String, Object> group = new LinkedHashMap<>();
            YearMonth month = entry.getKey();
            group.put("month", month);
            group.put("label", month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + "-" + month.getYear());
            group.put("tables", entry.getValue());
            monthGroups.add(group);
        }

        model.addAttribute("page", "admin");
        model.addAttribute("tables", tables);
        model.addAttribute("monthGroups", monthGroups);
        model.addAttribute("monthSort", chronological ? "asc" : "desc");
        return "admin/bank-statements";
    }

    YearMonth findTableMonth(BusinessBankStatementTable table, YearMonth fallback) {
        if (table == null || table.getRows() == null) {
            return fallback;
        }
        for (BusinessBankStatement row : table.getRows()) {
            if (row != null && row.getTransactionDate() != null) {
                return YearMonth.from(row.getTransactionDate());
            }
        }
        return fallback;
    }

    @PostMapping("/bank-statements/tables")
    @PreAuthorize("authentication.name == 'admin' or hasAuthority('bank-statements:write')")
    public String addBankStatementTable(@RequestParam("tableName") String tableName,
                                        RedirectAttributes redirectAttributes) {
        String name = tableName == null ? "" : tableName.trim();
        if (name.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Table name is required.");
            return "redirect:/admin/bank-statements";
        }
        BusinessBankStatementTable table = new BusinessBankStatementTable();
        table.setTableName(name);
        BusinessBankStatementTable saved = businessBankStatementTableRepository.save(table);
        userActivityService.logCurrentUser("CREATE", "BANK_STATEMENT_TABLE", saved.getId(),
                "Created bank statement table " + name);
        redirectAttributes.addFlashAttribute("success", "Table created successfully.");
        return "redirect:/admin/bank-statements";
    }

    @GetMapping("/bank-statements/tables/{tableId}/download")
    @PreAuthorize("authentication.name == 'admin' or hasAuthority('bank-statements:read') or hasAuthority('bank-statements:write')")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> downloadBankStatementTable(@PathVariable Long tableId,
                                                              @RequestParam(value = "format", required = false, defaultValue = "excel") String format) {
        BusinessBankStatementTable table = businessBankStatementTableRepository.findWithRowsById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Bank statement table not found"));
        try {
            String normalized = format == null ? "excel" : format.trim().toLowerCase(java.util.Locale.ROOT);
            byte[] content;
            String contentType;
            String extension;
            if ("pdf".equals(normalized)) {
                content = bankStatementExportService.exportPdf(table);
                contentType = "application/pdf";
                extension = "pdf";
            } else if ("excel".equals(normalized) || "xlsx".equals(normalized)) {
                content = bankStatementExportService.exportExcel(table);
                contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                extension = "xlsx";
            } else {
                return ResponseEntity.badRequest().build();
            }
            String safeName = (table.getTableName() == null ? "bank-statement" : table.getTableName())
                    .replaceAll("[^a-zA-Z0-9_-]", "-");
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + safeName + "." + extension + "\"")
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(content);
        } catch (Exception ex) {
            log.error("Failed to export bank statement table [{}]", tableId, ex);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/bank-statements/tables/{tableId}/opening-balance")
    @PreAuthorize("authentication.name == 'admin' or hasAuthority('bank-statements:write')")
    @Transactional
    public Object saveBankStatementOpeningBalance(@PathVariable Long tableId,
                                                   @RequestParam("openingBalance") BigDecimal openingBalance,
                                                   RedirectAttributes redirectAttributes,
                                                   @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
                                                   @RequestHeader(value = "Accept", required = false) String accept) {
        BusinessBankStatementTable table = businessBankStatementTableRepository.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Bank statement table not found"));
        if (openingBalance == null) {
            redirectAttributes.addFlashAttribute("error", "Opening Balance is required.");
            if (isAjaxRequest(requestedWith, accept)) {
                return ResponseEntity.badRequest().body(Map.of("success", false,
                        "message", "Opening Balance is required."));
            }
            return "redirect:/admin/bank-statements";
        }
        table.setOpeningBalance(openingBalance);
        table.setOpeningBalanceSaved(true);
        businessBankStatementTableRepository.save(table);
        recalculateBankStatementBalances(table);
        userActivityService.logCurrentUser("UPDATE", "BANK_STATEMENT_TABLE", tableId,
                "Updated opening balance for bank statement table " + table.getTableName());
        redirectAttributes.addFlashAttribute("success", "Opening balance saved successfully.");
        if (isAjaxRequest(requestedWith, accept)) {
            Map<String, Object> balances = new LinkedHashMap<>();
            for (BusinessBankStatement row : table.getRows()) {
                balances.put(String.valueOf(row.getId()), row.getCurrentBalance());
            }
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", "Opening balance saved successfully.");
            response.put("openingBalance", table.getOpeningBalance());
            response.put("openingBalanceSaved", table.isOpeningBalanceSaved());
            response.put("rowBalances", balances);
            return ResponseEntity.ok(response);
        }
        return "redirect:/admin/bank-statements";
    }

    @PostMapping("/bank-statements/tables/{tableId}/rows")
    @PreAuthorize("authentication.name == 'admin' or hasAuthority('bank-statements:write')")
    @Transactional
    public Object addBankStatementRow(@PathVariable Long tableId,
                                      @RequestParam(value = "transactionDate", required = false) LocalDate transactionDate,
                                      @RequestParam(value = "transaction", required = false) String transaction,
                                      @RequestParam(value = "credit", required = false) BigDecimal credit,
                                      @RequestParam(value = "debit", required = false) BigDecimal debit,
                                      @RequestParam(value = "description", required = false) String description,
                                      RedirectAttributes redirectAttributes,
                                      @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
                                      @RequestHeader(value = "Accept", required = false) String accept) {
        BusinessBankStatementTable table = businessBankStatementTableRepository.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Bank statement table not found"));
        if (credit != null && credit.compareTo(BigDecimal.ZERO) < 0
                || debit != null && debit.compareTo(BigDecimal.ZERO) < 0) {
            redirectAttributes.addFlashAttribute("error", "Credit and Debit must be zero or positive.");
            if (isAjaxRequest(requestedWith, accept)) {
                return ResponseEntity.badRequest().body(Map.of("success", false,
                        "message", "Credit and Debit must be zero or positive."));
            }
            return "redirect:/admin/bank-statements";
        }
        BusinessBankStatement row = new BusinessBankStatement();
        row.setStatementTable(table);
        row.setTransactionDate(transactionDate);
        row.setTransaction(transaction == null ? null : transaction.trim());
        row.setCredit(credit);
        row.setDebit(debit);
        row.setDescription(description == null ? null : description.trim());
        table.getRows().add(row);
        businessBankStatementRepository.save(row);
        recalculateBankStatementBalances(table);
        userActivityService.logCurrentUser("CREATE", "BANK_STATEMENT_ROW", row.getId(),
                "Added row to bank statement table " + table.getTableName());
        redirectAttributes.addFlashAttribute("success", "Row added successfully.");
        if (isAjaxRequest(requestedWith, accept)) {
            Map<String, Object> rowData = new LinkedHashMap<>();
            rowData.put("id", row.getId());
            rowData.put("transactionDate", row.getTransactionDate());
            rowData.put("transaction", row.getTransaction());
            rowData.put("credit", row.getCredit());
            rowData.put("debit", row.getDebit());
            rowData.put("currentBalance", row.getCurrentBalance());
            rowData.put("description", row.getDescription());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", "Row added successfully.");
            response.put("openingBalance", table.getOpeningBalance());
            response.put("currentBalance", row.getCurrentBalance());
            response.put("rowCount", table.getRows().size());
            response.put("row", rowData);
            return ResponseEntity.ok(response);
        }
        return "redirect:/admin/bank-statements";
    }

    boolean isAjaxRequest(String requestedWith, String accept) {
        return "XMLHttpRequest".equalsIgnoreCase(requestedWith)
                || accept != null && accept.toLowerCase(Locale.ROOT).contains("application/json");
    }

    @PostMapping("/bank-statements/tables/{tableId}/rows/{rowId}")
    @PreAuthorize("authentication.name == 'admin' or hasAuthority('bank-statements:write')")
    @Transactional
    public Object updateBankStatementRow(@PathVariable Long tableId,
                                         @PathVariable Long rowId,
                                         @RequestParam(value = "transactionDate", required = false) LocalDate transactionDate,
                                         @RequestParam(value = "transaction", required = false) String transaction,
                                         @RequestParam(value = "credit", required = false) BigDecimal credit,
                                         @RequestParam(value = "debit", required = false) BigDecimal debit,
                                         @RequestParam(value = "description", required = false) String description,
                                         RedirectAttributes redirectAttributes,
                                         @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
                                         @RequestHeader(value = "Accept", required = false) String accept) {
        BusinessBankStatement row = businessBankStatementRepository.findById(rowId)
                .orElseThrow(() -> new IllegalArgumentException("Bank statement row not found"));
        if (row.getStatementTable() == null || !tableId.equals(row.getStatementTable().getId())) {
            throw new IllegalArgumentException("Bank statement row does not belong to this table");
        }
        if (transactionDate == null || transaction == null || transaction.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Date and Transaction are required.");
            if (isAjaxRequest(requestedWith, accept)) {
                return ResponseEntity.badRequest().body(Map.of("success", false,
                        "message", "Date and Transaction are required."));
            }
            return "redirect:/admin/bank-statements";
        }
        if (credit != null && credit.compareTo(BigDecimal.ZERO) < 0
                || debit != null && debit.compareTo(BigDecimal.ZERO) < 0) {
            redirectAttributes.addFlashAttribute("error", "Credit and Debit must be zero or positive.");
            if (isAjaxRequest(requestedWith, accept)) {
                return ResponseEntity.badRequest().body(Map.of("success", false,
                        "message", "Credit and Debit must be zero or positive."));
            }
            return "redirect:/admin/bank-statements";
        }

        row.setTransactionDate(transactionDate);
        row.setTransaction(transaction.trim());
        row.setCredit(credit);
        row.setDebit(debit);
        row.setDescription(description == null ? null : description.trim());
        businessBankStatementRepository.save(row);
        recalculateBankStatementBalances(row.getStatementTable());
        userActivityService.logCurrentUser("UPDATE", "BANK_STATEMENT_ROW", rowId,
                "Updated row in bank statement table " + row.getStatementTable().getTableName());
        redirectAttributes.addFlashAttribute("success", "Row updated successfully.");

        if (isAjaxRequest(requestedWith, accept)) {
            Map<String, Object> rowData = new LinkedHashMap<>();
            rowData.put("id", row.getId());
            rowData.put("transactionDate", row.getTransactionDate());
            rowData.put("transaction", row.getTransaction());
            rowData.put("credit", row.getCredit());
            rowData.put("debit", row.getDebit());
            rowData.put("currentBalance", row.getCurrentBalance());
            rowData.put("description", row.getDescription());

            Map<String, Object> balances = new LinkedHashMap<>();
            for (BusinessBankStatement statementRow : row.getStatementTable().getRows()) {
                balances.put(String.valueOf(statementRow.getId()), statementRow.getCurrentBalance());
            }
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", "Row updated successfully.");
            response.put("row", rowData);
            response.put("rowBalances", balances);
            return ResponseEntity.ok(response);
        }
        return "redirect:/admin/bank-statements";
    }

    private void recalculateBankStatementBalances(BusinessBankStatementTable table) {
        BigDecimal runningBalance = table.getOpeningBalance();
        for (BusinessBankStatement row : table.getRows()) {
            BigDecimal credit = row.getCredit() == null ? BigDecimal.ZERO : row.getCredit();
            BigDecimal debit = row.getDebit() == null ? BigDecimal.ZERO : row.getDebit();
            runningBalance = runningBalance.add(credit).subtract(debit);
            row.setCurrentBalance(runningBalance);
            businessBankStatementRepository.save(row);
        }
    }

    @PostMapping("/bank-statements/tables/{tableId}/delete")
    @PreAuthorize("authentication.name == 'admin' or hasAuthority('bank-statements:write')")
    public String deleteBankStatementTable(@PathVariable Long tableId,
                                           RedirectAttributes redirectAttributes) {
        BusinessBankStatementTable table = businessBankStatementTableRepository.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Bank statement table not found"));
        businessBankStatementTableRepository.delete(table);
        userActivityService.logCurrentUser("DELETE", "BANK_STATEMENT_TABLE", tableId,
                "Deleted bank statement table " + table.getTableName());
        redirectAttributes.addFlashAttribute("success", "Table deleted successfully.");
        return "redirect:/admin/bank-statements";
    }

    @PostMapping("/bank-statements/tables/{tableId}/rows/{rowId}/delete")
    @PreAuthorize("authentication.name == 'admin' or hasAuthority('bank-statements:write')")
    @Transactional
    public Object deleteBankStatementRow(@PathVariable Long tableId,
                                         @PathVariable Long rowId,
                                         RedirectAttributes redirectAttributes,
                                         @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
                                         @RequestHeader(value = "Accept", required = false) String accept) {
        if (tableId == null || rowId == null) {
            String message = "Table ID and row ID are required.";
            if (isAjaxRequest(requestedWith, accept)) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", message));
            }
            redirectAttributes.addFlashAttribute("error", message);
            return "redirect:/admin/bank-statements";
        }

        BusinessBankStatement row = businessBankStatementRepository.findById(rowId).orElse(null);
        if (row == null) {
            String message = "Bank statement row not found.";
            if (isAjaxRequest(requestedWith, accept)) {
                return ResponseEntity.status(404).body(Map.of("success", false, "message", message));
            }
            redirectAttributes.addFlashAttribute("error", message);
            return "redirect:/admin/bank-statements";
        }
        if (row.getStatementTable() == null || row.getStatementTable().getId() == null
                || !tableId.equals(row.getStatementTable().getId())) {
            String message = "Bank statement row does not belong to this table.";
            if (isAjaxRequest(requestedWith, accept)) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", message));
            }
            redirectAttributes.addFlashAttribute("error", message);
            return "redirect:/admin/bank-statements";
        }

        BusinessBankStatementTable table = row.getStatementTable();
        businessBankStatementRepository.delete(row);
        table.getRows().removeIf(candidate -> candidate.getId() != null && candidate.getId().equals(rowId));
        recalculateBankStatementBalances(table);
        userActivityService.logCurrentUser("DELETE", "BANK_STATEMENT_ROW", rowId,
                "Deleted row from bank statement table ID " + tableId);
        redirectAttributes.addFlashAttribute("success", "Row deleted successfully.");

        if (isAjaxRequest(requestedWith, accept)) {
            Map<String, Object> balances = new LinkedHashMap<>();
            for (BusinessBankStatement statementRow : table.getRows()) {
                balances.put(String.valueOf(statementRow.getId()), statementRow.getCurrentBalance());
            }
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Row deleted successfully.",
                    "rowId", rowId,
                    "rowBalances", balances));
        }
        return "redirect:/admin/bank-statements";
    }

    @GetMapping("/bank-accounts/new")
    public String newBankAccountForm(Model model) {
        model.addAttribute("page", "admin");
        model.addAttribute("bankAccount", new BusinessBankAccount());
        return "admin/bank-account-form";
    }

    @GetMapping("/bank-accounts/{id}/edit")
    public String editBankAccountForm(@PathVariable Long id, Model model) {
        BusinessBankAccount account = businessBankAccountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Bank account not found"));
        model.addAttribute("page", "admin");
        model.addAttribute("bankAccount", account);
        model.addAttribute("bankAccountId", id);
        return "admin/bank-account-form";
    }

    @PostMapping("/bank-accounts")
    public String saveBankAccount(@ModelAttribute("bankAccount") BusinessBankAccount form,
                                @RequestParam(value = "gpayQrImage", required = false) MultipartFile gpayQrImage,
                                RedirectAttributes redirectAttributes) {
        if (form.getAccountName() == null || form.getAccountName().trim().isEmpty()
                || form.getBankName() == null || form.getBankName().trim().isEmpty()
                || form.getAccountNo() == null || form.getAccountNo().trim().isEmpty()
                || form.getIfscCode() == null || form.getIfscCode().trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Account name, bank name, account no., and IFSC code are required.");
            return "redirect:/admin/bank-accounts/new";
        }

        BusinessBankAccount account = new BusinessBankAccount();
        account.setAccountName(form.getAccountName().trim());
        account.setBankName(form.getBankName().trim());
        account.setAccountNo(form.getAccountNo().trim());
        account.setIfscCode(form.getIfscCode().trim());
        account.setGpayPhonepe(form.getGpayPhonepe() == null ? null : form.getGpayPhonepe().trim());
        if (gpayQrImage != null && !gpayQrImage.isEmpty()) {
            try {
                account.setGpayPhonepeQrImage(gpayQrImage.getBytes());
                account.setGpayPhonepeQrContentType(gpayQrImage.getContentType());
            } catch (Exception ex) {
                redirectAttributes.addFlashAttribute("error", "Unable to store the QR image. Please try a smaller image.");
                return "redirect:/admin/bank-accounts/new";
            }
        }
        BusinessBankAccount saved = businessBankAccountRepository.save(account);
        userActivityService.logCurrentUser("CREATE", "BANK_ACCOUNT", saved.getId(),
                "Created bank account " + saved.getAccountName());

        redirectAttributes.addFlashAttribute("success", "Bank account saved successfully.");
        return "redirect:/admin/bank-accounts";
    }

    @GetMapping("/bank-accounts/{id}/qr")
    @ResponseBody
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> getBankAccountQr(@PathVariable Long id) {
        BusinessBankAccount account = businessBankAccountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Bank account not found"));
        if (account.getGpayPhonepeQrImage() == null || account.getGpayPhonepeQrImage().length == 0) {
            return ResponseEntity.noContent().build();
        }
        String contentType = account.getGpayPhonepeQrContentType() != null && !account.getGpayPhonepeQrContentType().trim().isEmpty()
                ? account.getGpayPhonepeQrContentType()
                : "image/png";
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(contentType))
                .body(account.getGpayPhonepeQrImage());
    }

    @PostMapping("/bank-accounts/{id}")
    public String updateBankAccount(@PathVariable Long id,
                                  @ModelAttribute("bankAccount") BusinessBankAccount form,
                                  @RequestParam(value = "gpayQrImage", required = false) MultipartFile gpayQrImage,
                                  RedirectAttributes redirectAttributes) {
        BusinessBankAccount account = businessBankAccountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Bank account not found"));

        if (form.getAccountName() == null || form.getAccountName().trim().isEmpty()
                || form.getBankName() == null || form.getBankName().trim().isEmpty()
                || form.getAccountNo() == null || form.getAccountNo().trim().isEmpty()
                || form.getIfscCode() == null || form.getIfscCode().trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Account name, bank name, account no., and IFSC code are required.");
            return "redirect:/admin/bank-accounts/" + id + "/edit";
        }

        account.setAccountName(form.getAccountName().trim());
        account.setBankName(form.getBankName().trim());
        account.setAccountNo(form.getAccountNo().trim());
        account.setIfscCode(form.getIfscCode().trim());
        account.setGpayPhonepe(form.getGpayPhonepe() == null ? null : form.getGpayPhonepe().trim());
        if (gpayQrImage != null && !gpayQrImage.isEmpty()) {
            try {
                account.setGpayPhonepeQrImage(gpayQrImage.getBytes());
                account.setGpayPhonepeQrContentType(gpayQrImage.getContentType());
            } catch (Exception ex) {
                redirectAttributes.addFlashAttribute("error", "Unable to store the QR image. Please try a smaller image.");
                return "redirect:/admin/bank-accounts/" + id + "/edit";
            }
        }
        BusinessBankAccount saved = businessBankAccountRepository.save(account);
        userActivityService.logCurrentUser("UPDATE", "BANK_ACCOUNT", saved.getId(),
                "Updated bank account " + saved.getAccountName());

        redirectAttributes.addFlashAttribute("success", "Bank account updated successfully.");
        return "redirect:/admin/bank-accounts";
    }

    @PostMapping("/bank-accounts/{id}/delete")
    public String deleteBankAccount(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        BusinessBankAccount account = businessBankAccountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Bank account not found"));
        businessBankAccountRepository.delete(account);
        userActivityService.logCurrentUser("DELETE", "BANK_ACCOUNT", id,
                "Deleted bank account " + account.getAccountName());
        redirectAttributes.addFlashAttribute("success", "Bank account deleted successfully.");
        return "redirect:/admin/bank-accounts";
    }

    private Comparator<AppUser> sortUsers(String sort, String dir) {
        Comparator<AppUser> comparator;
        if ("enabled".equalsIgnoreCase(sort)) {
            comparator = Comparator.comparing(AppUser::isEnabled);
        } else if ("updatedAt".equalsIgnoreCase(sort)) {
            comparator = Comparator.comparing(AppUser::getUpdatedAt, Comparator.nullsLast(java.time.LocalDateTime::compareTo));
        } else {
            comparator = Comparator.comparing(AppUser::getUsername, Comparator.nullsLast(String::compareToIgnoreCase));
        }
        if ("desc".equalsIgnoreCase(dir)) {
            comparator = comparator.reversed();
        }
        return comparator;
    }

    private Comparator<Role> sortRoles(String sort, String dir) {
        Comparator<Role> comparator;
        if ("description".equalsIgnoreCase(sort)) {
            comparator = Comparator.comparing(Role::getDescription, Comparator.nullsLast(String::compareToIgnoreCase));
        } else {
            comparator = Comparator.comparing(Role::getName, Comparator.nullsLast(String::compareToIgnoreCase));
        }
        if ("desc".equalsIgnoreCase(dir)) {
            comparator = comparator.reversed();
        }
        return comparator;
    }

    @GetMapping("/users/new")
    public String newUserForm(Model model) {
        UserForm form = new UserForm();
        form.setEnabled(true);
        model.addAttribute("page", "admin");
        model.addAttribute("userForm", form);
        model.addAttribute("roles", roleRepository.findAll());
        model.addAttribute("accessModules", AVAILABLE_PERMISSIONS);
        // provide employee suggestions for username autocomplete
        try {
            java.util.List<com.vns.healthcare.entity.Employee> employees = employeeService.activeStaffSuggestions(200);
            java.util.List<String> suggestions = new java.util.ArrayList<String>();
            for (com.vns.healthcare.entity.Employee e : employees) {
                if (e.getEmpCode() != null && !e.getEmpCode().trim().isEmpty()) {
                    suggestions.add(e.getEmpCode().trim() + " " + (e.getFullName() == null ? "" : e.getFullName()));
                }
            }
            model.addAttribute("employeeSuggestions", suggestions);
        } catch (Exception ex) {
            // silent fallback if service not available
            model.addAttribute("employeeSuggestions", java.util.Collections.emptyList());
        }
        return "admin/user-form";
    }

    @GetMapping("/users/{id}/edit")
    public String editUserForm(@PathVariable Long id, Model model) {
        AppUser user = userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found"));
        UserForm form = new UserForm();
        form.setUsername(user.getUsername());
        form.setEnabled(user.isEnabled());
        List<Long> roleIds = new ArrayList<Long>();
        for (Role role : user.getRoles()) {
            roleIds.add(role.getId());
        }
        form.setRoleIds(roleIds);

        List<String> readPermissions = new ArrayList<String>();
        List<String> writePermissions = new ArrayList<String>();
        for (String permission : user.getPermissions()) {
            if (permission == null || permission.trim().isEmpty()) {
                continue;
            }
            String normalized = permission.trim().toLowerCase();
            if (normalized.endsWith(":read")) {
                readPermissions.add(normalized.substring(0, normalized.lastIndexOf(":read")));
            } else if (normalized.endsWith(":write")) {
                writePermissions.add(normalized.substring(0, normalized.lastIndexOf(":write")));
            }
        }
        form.setReadPermissions(readPermissions);
        form.setWritePermissions(writePermissions);

        model.addAttribute("page", "admin");
        model.addAttribute("userForm", form);
        model.addAttribute("roles", roleRepository.findAll());
        model.addAttribute("accessModules", AVAILABLE_PERMISSIONS);
        model.addAttribute("userId", id);
        return "admin/user-form";
    }

    @PostMapping("/users")
    public String saveUser(@ModelAttribute("userForm") UserForm form,
                           RedirectAttributes redirectAttributes) {
        String username = normalizeEmployeeCode(form.getUsername());
        if (!isValidEmployeeCode(username)) {
            log.warn("Attempt to create user with non-employee username [{}]", form.getUsername());
            redirectAttributes.addFlashAttribute("error", "Employee not present");
            return "redirect:/admin/users/new";
        }
        if (userRepository.existsByUsername(username)) {
            log.warn("Attempt to create duplicate user [{}]", username);
            redirectAttributes.addFlashAttribute("error", "Username already exists.");
            return "redirect:/admin/users/new";
        }

        AppUser appUser = new AppUser();
        appUser.setUsername(username);
        appUser.setPassword(passwordEncoder.encode(form.getPassword()));
        appUser.setEnabled(form.isEnabled());

        List<Role> roles = roleRepository.findAllById(form.getRoleIds());
        appUser.setRoles(new HashSet<Role>(roles));
        appUser.setPermissions(new HashSet<String>(buildPermissions(form)));
        AppUser saved = userRepository.save(appUser);
        userActivityService.logCurrentUser("CREATE", "USER", saved.getId(),
                "Created user " + saved.getUsername());
        log.info("Created user [{}] with roles [{}] and permissions [{}]", username, roles.size(), appUser.getPermissions());

        redirectAttributes.addFlashAttribute("success", "User created successfully.");
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}")
    public String updateUser(@PathVariable Long id,
                             @ModelAttribute("userForm") UserForm form,
                             RedirectAttributes redirectAttributes) {
        AppUser user = userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found"));
        String requested = normalizeEmployeeCode(form.getUsername());
        if (!isValidEmployeeCode(requested)) {
            log.warn("Attempt to update user [{}] with non-employee username [{}]", user.getUsername(), form.getUsername());
            redirectAttributes.addFlashAttribute("error", "Employee not present");
            return "redirect:/admin/users/" + id + "/edit";
        }
        if (!requested.isEmpty() && !requested.equalsIgnoreCase(user.getUsername())
                && userRepository.existsByUsername(requested)) {
            log.warn("Attempt to update user [{}] to duplicate username [{}]", user.getUsername(), requested);
            redirectAttributes.addFlashAttribute("error", "Username already exists.");
            return "redirect:/admin/users/" + id + "/edit";
        }

        user.setUsername(requested);
        user.setEnabled(form.isEnabled());
        if (form.getPassword() != null && !form.getPassword().trim().isEmpty()) {
            user.setPassword(passwordEncoder.encode(form.getPassword()));
        }
        user.setRoles(new HashSet<Role>(roleRepository.findAllById(form.getRoleIds())));
        user.setPermissions(new HashSet<String>(buildPermissions(form)));
        AppUser saved = userRepository.save(user);
        userActivityService.logCurrentUser("UPDATE", "USER", saved.getId(),
                "Updated user " + saved.getUsername());
        log.info("Updated user [{}]", user.getUsername());

        redirectAttributes.addFlashAttribute("success", "User updated successfully.");
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/delete")
    public String deleteUser(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        AppUser user = userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found"));
        if ("admin".equalsIgnoreCase(user.getUsername())) {
            log.warn("Blocked deletion of protected admin user [{}]", user.getUsername());
            redirectAttributes.addFlashAttribute("error", "Default admin user cannot be deleted.");
            return "redirect:/admin/users";
        }
        userRepository.delete(user);
        userActivityService.logCurrentUser("DELETE", "USER", id,
                "Deleted user " + user.getUsername());
        log.info("Deleted user [{}]", user.getUsername());
        redirectAttributes.addFlashAttribute("success", "User deleted successfully.");
        return "redirect:/admin/users";
    }

    @GetMapping("/roles/new")
    public String newRoleForm(Model model) {
        RoleForm form = new RoleForm();
        model.addAttribute("page", "admin");
        model.addAttribute("roleForm", form);
        model.addAttribute("permissions", AVAILABLE_PERMISSIONS);
        return "admin/role-form";
    }

    @PostMapping("/roles")
    public String saveRole(@ModelAttribute("roleForm") RoleForm form,
                           RedirectAttributes redirectAttributes) {
        String normalized = form.getName() == null ? "" : form.getName().trim();
        if (normalized.isEmpty()) {
            log.warn("Attempted role save with empty name");
            redirectAttributes.addFlashAttribute("error", "Role name is required.");
            return "redirect:/admin/roles/new";
        }

        Role existing = roleRepository.findByName(normalized.toUpperCase())
                .orElseGet(() -> new Role());
        existing.setName(normalized.toUpperCase());
        existing.setDescription(form.getDescription());

        Set<String> permissions = new HashSet<String>();
        if (form.getPermissions() != null) {
            for (String permission : form.getPermissions()) {
                if (permission != null && !permission.trim().isEmpty()) {
                    permissions.add(permission.trim().toLowerCase());
                }
            }
        }
        existing.setPermissions(permissions);
        Role saved = roleRepository.save(existing);
        userActivityService.logCurrentUser("CREATE", "ROLE", saved.getId(),
                "Saved role " + saved.getName());
        log.info("Saved role [{}] with {} permissions", existing.getName(), permissions.size());

        redirectAttributes.addFlashAttribute("success", "Role saved successfully.");
        return "redirect:/admin/roles";
    }

    @GetMapping("/roles/{id}/edit")
    public String editRole(@PathVariable Long id, Model model) {
        Role role = roleRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Role not found"));
        RoleForm form = new RoleForm();
        form.setName(role.getName());
        form.setDescription(role.getDescription());
        form.setPermissions(new ArrayList<String>(role.getPermissions()));

        model.addAttribute("page", "admin");
        model.addAttribute("roleForm", form);
        model.addAttribute("permissions", AVAILABLE_PERMISSIONS);
        model.addAttribute("roleId", id);
        return "admin/role-form";
    }

    @PostMapping("/roles/{id}/edit")
    public String updateRole(@PathVariable Long id,
                             @ModelAttribute("roleForm") RoleForm form,
                             RedirectAttributes redirectAttributes) {
        Role role = roleRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Role not found"));
        role.setName(form.getName().trim().toUpperCase());
        role.setDescription(form.getDescription());

        Set<String> permissions = new HashSet<String>();
        for (String permission : form.getPermissions()) {
            if (permission != null && !permission.trim().isEmpty()) {
                permissions.add(permission.trim().toLowerCase());
            }
        }
        role.setPermissions(permissions);
        roleRepository.save(role);
        log.info("Updated role [{}] with {} permissions", role.getName(), permissions.size());

        redirectAttributes.addFlashAttribute("success", "Role updated successfully.");
        return "redirect:/admin/roles";
    }

    private boolean isValidEmployeeCode(String username) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }
        return employeeRepository.findByEmpCode(username.trim().toUpperCase(Locale.ROOT)).isPresent();
    }

    private String normalizeEmployeeCode(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String[] parts = trimmed.split("\\s+");
        return parts[0].trim().toUpperCase(Locale.ROOT);
    }

    private List<String> buildPermissions(UserForm userForm) {
        Set<String> permissions = new HashSet<String>();
        if (userForm.getReadPermissions() != null) {
            for (String module : userForm.getReadPermissions()) {
                if (module != null && !module.trim().isEmpty()) {
                    permissions.add(module.trim().toLowerCase() + ":read");
                }
            }
        }
        if (userForm.getWritePermissions() != null) {
            for (String module : userForm.getWritePermissions()) {
                if (module != null && !module.trim().isEmpty()) {
                    permissions.add(module.trim().toLowerCase() + ":write");
                }
            }
        }
        return new ArrayList<String>(permissions);
    }
}
