package com.vns.healthcare.security;

import com.vns.healthcare.entity.Employee;
import com.vns.healthcare.entity.BusinessBankStatement;
import com.vns.healthcare.entity.BusinessBankStatementTable;
import com.vns.healthcare.repository.BusinessBankAccountRepository;
import com.vns.healthcare.repository.BusinessBankStatementRepository;
import com.vns.healthcare.repository.BusinessBankStatementTableRepository;
import com.vns.healthcare.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock
    private RoleRepository roleRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private BusinessBankAccountRepository businessBankAccountRepository;
    @Mock
    private BusinessBankStatementRepository businessBankStatementRepository;
    @Mock
    private BusinessBankStatementTableRepository businessBankStatementTableRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private com.vns.healthcare.service.EmployeeService employeeService;
    @Mock
    private UserActivityService userActivityService;
    @Mock
    private CacheManager cacheManager;

    private AdminController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminController(
                roleRepository,
                userRepository,
                employeeRepository,
                businessBankAccountRepository,
                businessBankStatementRepository,
                businessBankStatementTableRepository,
                passwordEncoder,
                employeeService,
                userActivityService,
                cacheManager);
    }

    @Test
    void users_populatesEmployeeNameFromMatchingEmployeeCode() {
        AppUser user = new AppUser();
        user.setId(1L);
        user.setUsername("EMP01");
        user.setEnabled(true);

        Employee employee = new Employee();
        employee.setEmpCode("EMP01");
        employee.setFullName("Alice Johnson");

        when(userRepository.findAll()).thenReturn(List.of(user));
        when(employeeRepository.findByEmpCode("EMP01")).thenReturn(Optional.of(employee));

        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.users(null, "username", "asc", model);

        assertEquals("admin/users", view);
        @SuppressWarnings("unchecked")
        Map<Long, String> employeeNames = (Map<Long, String>) model.getAttribute("employeeNames");
        assertEquals("Alice Johnson", employeeNames.get(1L));
    }

    @Test
    void bankStatements_rendersStandaloneNamedTablePage() {
        when(businessBankStatementTableRepository.findAllByOrderByIdDesc()).thenReturn(List.of());
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.allBankStatements(model);

        assertEquals("admin/bank-statements", view);
        assertEquals(List.of(), model.getAttribute("tables"));
    }

    @Test
    void bankStatements_groupsPastAndFutureRowsByMonthAndUsesCurrentMonthForEmptyTables() {
        BusinessBankStatementTable november = new BusinessBankStatementTable();
        november.setId(1L);
        BusinessBankStatement novemberRow = new BusinessBankStatement();
        novemberRow.setTransactionDate(LocalDate.of(2026, 11, 5));
        november.getRows().add(novemberRow);

        BusinessBankStatementTable january = new BusinessBankStatementTable();
        january.setId(2L);
        BusinessBankStatement januaryRow = new BusinessBankStatement();
        januaryRow.setTransactionDate(LocalDate.of(2027, 1, 10));
        january.getRows().add(januaryRow);

        BusinessBankStatementTable empty = new BusinessBankStatementTable();
        empty.setId(3L);

        when(businessBankStatementTableRepository.findAllByOrderByIdDesc())
                .thenReturn(List.of(empty, january, november));
        ExtendedModelMap model = new ExtendedModelMap();

        controller.allBankStatements(model, "asc");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> groups = (List<Map<String, Object>>) model.getAttribute("monthGroups");
        assertEquals(YearMonth.now(), controller.findTableMonth(empty, YearMonth.now()));
        assertEquals(YearMonth.of(2026, 11), controller.findTableMonth(november, YearMonth.now()));
        assertEquals("November-2026", groups.get(0).get("label"));
        assertEquals("January-2027", groups.get(1).get("label"));
        assertEquals("asc", model.getAttribute("monthSort"));
    }

    @Test
    void bankStatements_usesFirstStatementRowMonthAndFallsBackToCurrentMonthForNullDates() {
        YearMonth fallback = YearMonth.of(2026, 12);
        BusinessBankStatementTable table = new BusinessBankStatementTable();
        table.setRows(List.of(new BusinessBankStatement()));

        assertEquals(fallback, controller.findTableMonth(table, fallback));

        BusinessBankStatement row = new BusinessBankStatement();
        row.setTransactionDate(LocalDate.of(2028, 3, 31));
        table.setRows(List.of(row));
        assertEquals(YearMonth.of(2028, 3), controller.findTableMonth(table, fallback));
    }

    @Test
    void newBankStatementTable_hasOpeningBalanceUnlockedByDefault() {
        BusinessBankStatementTable table = new BusinessBankStatementTable();

        assertEquals(false, table.isOpeningBalanceSaved());
        assertEquals(BigDecimal.ZERO, table.getOpeningBalance());
    }

    @Test
    void saveOpeningBalance_recalculatesRowsFromOpeningBalance() {
        BusinessBankStatementTable table = new BusinessBankStatementTable();
        table.setId(7L);
        table.setOpeningBalance(new BigDecimal("100.00"));

        BusinessBankStatement first = new BusinessBankStatement();
        first.setCredit(new BigDecimal("25.50"));
        first.setDebit(BigDecimal.ZERO);
        first.setStatementTable(table);
        table.getRows().add(first);

        BusinessBankStatement second = new BusinessBankStatement();
        second.setCredit(BigDecimal.ZERO);
        second.setDebit(new BigDecimal("10.25"));
        second.setStatementTable(table);
        table.getRows().add(second);

        when(businessBankStatementTableRepository.findById(7L)).thenReturn(Optional.of(table));

        Object view = controller.saveBankStatementOpeningBalance(7L, new BigDecimal("200.00"), new RedirectAttributesModelMap(), null, null);

        assertEquals("redirect:/admin/bank-statements", view);
        assertEquals(true, table.isOpeningBalanceSaved());
        assertEquals(new BigDecimal("225.50"), first.getCurrentBalance());
        assertEquals(new BigDecimal("215.25"), second.getCurrentBalance());
    }

    @Test
    void addRow_calculatesServerSideBalanceIgnoringAnyClientBalance() {
        BusinessBankStatementTable table = new BusinessBankStatementTable();
        table.setId(8L);
        table.setOpeningBalance(new BigDecimal("50.00"));
        when(businessBankStatementTableRepository.findById(8L)).thenReturn(Optional.of(table));

        Object view = controller.addBankStatementRow(8L, LocalDate.of(2026, 11, 23), "Deposit",
                new BigDecimal("30.00"), new BigDecimal("5.00"), "test", new RedirectAttributesModelMap(), null, null);

        assertEquals("redirect:/admin/bank-statements", view);
        assertEquals(1, table.getRows().size());
        assertEquals(new BigDecimal("75.00"), table.getRows().get(0).getCurrentBalance());
    }

    @Test
    void deleteRow_rejectsRowThatDoesNotBelongToTable() {
        BusinessBankStatementTable requestedTable = new BusinessBankStatementTable();
        requestedTable.setId(1L);
        BusinessBankStatementTable otherTable = new BusinessBankStatementTable();
        otherTable.setId(2L);
        BusinessBankStatement row = new BusinessBankStatement();
        row.setId(10L);
        row.setStatementTable(otherTable);
        when(businessBankStatementRepository.findById(10L)).thenReturn(Optional.of(row));

        Object result = controller.deleteBankStatementRow(1L, 10L,
                new RedirectAttributesModelMap(), "XMLHttpRequest", "application/json");

        ResponseEntity<?> response = (ResponseEntity<?>) result;
        assertEquals(400, response.getStatusCode().value());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals(false, body.get("success"));
    }

    @Test
    void deleteRow_ajaxDeletesMatchingRowAndReturnsRecalculatedBalances() {
        BusinessBankStatementTable table = new BusinessBankStatementTable();
        table.setId(1L);
        table.setOpeningBalance(new BigDecimal("100.00"));
        BusinessBankStatement row = new BusinessBankStatement();
        row.setId(10L);
        row.setStatementTable(table);
        row.setCredit(new BigDecimal("20.00"));
        table.getRows().add(row);
        when(businessBankStatementRepository.findById(10L)).thenReturn(Optional.of(row));

        Object result = controller.deleteBankStatementRow(1L, 10L,
                new RedirectAttributesModelMap(), "XMLHttpRequest", "application/json");

        ResponseEntity<?> response = (ResponseEntity<?>) result;
        assertEquals(200, response.getStatusCode().value());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals(true, body.get("success"));
        assertEquals(10L, body.get("rowId"));
        verify(businessBankStatementRepository).delete(row);
    }

    @Test
    void ajaxDetection_acceptsRequestedWithOrJsonAcceptHeader() {
        assertEquals(true, controller.isAjaxRequest("XMLHttpRequest", null));
        assertEquals(true, controller.isAjaxRequest(null, "text/html,application/json;q=0.9"));
        assertEquals(false, controller.isAjaxRequest(null, "text/html"));
    }

    @Test
    void saveOpeningBalance_ajaxReturnsUpdatedBalances() {
        BusinessBankStatementTable table = new BusinessBankStatementTable();
        table.setId(7L);
        table.setOpeningBalance(new BigDecimal("100.00"));
        BusinessBankStatement row = new BusinessBankStatement();
        row.setId(11L);
        row.setCredit(new BigDecimal("25.00"));
        row.setStatementTable(table);
        table.getRows().add(row);
        when(businessBankStatementTableRepository.findById(7L)).thenReturn(Optional.of(table));

        Object result = controller.saveBankStatementOpeningBalance(7L, new BigDecimal("200.00"),
                new RedirectAttributesModelMap(), "XMLHttpRequest", null);

        ResponseEntity<?> response = (ResponseEntity<?>) result;
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals(true, body.get("success"));
        assertEquals(new BigDecimal("200.00"), body.get("openingBalance"));
        assertEquals(true, body.get("openingBalanceSaved"));
        assertEquals(true, table.isOpeningBalanceSaved());
        Map<?, ?> rowBalances = (Map<?, ?>) body.get("rowBalances");
        assertEquals(new BigDecimal("225.00"), rowBalances.get("11"));
    }

    @Test
    void addRow_ajaxReturnsRowAndBalanceFields() {
        BusinessBankStatementTable table = new BusinessBankStatementTable();
        table.setId(8L);
        table.setOpeningBalance(new BigDecimal("50.00"));
        when(businessBankStatementTableRepository.findById(8L)).thenReturn(Optional.of(table));

        Object result = controller.addBankStatementRow(8L, LocalDate.of(2026, 11, 23), "Deposit",
                new BigDecimal("30.00"), BigDecimal.ZERO, "test", new RedirectAttributesModelMap(),
                null, "application/json");

        ResponseEntity<?> response = (ResponseEntity<?>) result;
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals(true, body.get("success"));
        assertEquals(new BigDecimal("80.00"), body.get("currentBalance"));
        assertEquals(1, body.get("rowCount"));
        Map<?, ?> row = (Map<?, ?>) body.get("row");
        assertEquals("Deposit", row.get("transaction"));
    }

    @Test
    void updateRow_changesValuesAndRecalculatesAllFollowingBalances() {
        BusinessBankStatementTable table = new BusinessBankStatementTable();
        table.setId(7L);
        table.setTableName("Main");
        table.setOpeningBalance(new BigDecimal("100.00"));

        BusinessBankStatement first = new BusinessBankStatement();
        first.setId(10L);
        first.setStatementTable(table);
        first.setCredit(new BigDecimal("20.00"));
        first.setDebit(BigDecimal.ZERO);
        table.getRows().add(first);

        BusinessBankStatement second = new BusinessBankStatement();
        second.setId(11L);
        second.setStatementTable(table);
        second.setCredit(BigDecimal.ZERO);
        second.setDebit(new BigDecimal("10.00"));
        table.getRows().add(second);

        when(businessBankStatementRepository.findById(10L)).thenReturn(Optional.of(first));

        Object result = controller.updateBankStatementRow(7L, 10L, LocalDate.of(2026, 11, 20),
                "Updated", new BigDecimal("50.00"), BigDecimal.ZERO, "edited",
                new RedirectAttributesModelMap(), null, null);

        assertEquals("redirect:/admin/bank-statements", result);
        assertEquals("Updated", first.getTransaction());
        assertEquals(new BigDecimal("150.00"), first.getCurrentBalance());
        assertEquals(new BigDecimal("140.00"), second.getCurrentBalance());
    }

    @Test
    void updateRow_ajaxReturnsUpdatedRowAndRunningBalances() {
        BusinessBankStatementTable table = new BusinessBankStatementTable();
        table.setId(7L);
        table.setOpeningBalance(new BigDecimal("100.00"));
        BusinessBankStatement first = new BusinessBankStatement();
        first.setId(10L);
        first.setStatementTable(table);
        first.setCredit(new BigDecimal("20.00"));
        table.getRows().add(first);
        BusinessBankStatement second = new BusinessBankStatement();
        second.setId(11L);
        second.setStatementTable(table);
        second.setDebit(new BigDecimal("10.00"));
        table.getRows().add(second);
        when(businessBankStatementRepository.findById(10L)).thenReturn(Optional.of(first));

        Object result = controller.updateBankStatementRow(7L, 10L, LocalDate.of(2026, 11, 20),
                "Updated", new BigDecimal("50.00"), BigDecimal.ZERO, "edited",
                new RedirectAttributesModelMap(), "XMLHttpRequest", null);

        ResponseEntity<?> response = (ResponseEntity<?>) result;
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals(true, body.get("success"));
        Map<?, ?> row = (Map<?, ?>) body.get("row");
        assertEquals("Updated", row.get("transaction"));
        Map<?, ?> balances = (Map<?, ?>) body.get("rowBalances");
        assertEquals(new BigDecimal("150.00"), balances.get("10"));
        assertEquals(new BigDecimal("140.00"), balances.get("11"));
    }

    @Test
    void addBankStatementTable_persistsTrimmedNameAndReturnsToPage() {
        BusinessBankStatementTable saved = new BusinessBankStatementTable();
        saved.setId(42L);
        saved.setTableName("Main Account");
        when(businessBankStatementTableRepository.save(any(BusinessBankStatementTable.class))).thenReturn(saved);

        String view = controller.addBankStatementTable("  Main Account  ", new RedirectAttributesModelMap());

        assertEquals("redirect:/admin/bank-statements", view);
        verify(businessBankStatementTableRepository).save(any(BusinessBankStatementTable.class));
    }
}
