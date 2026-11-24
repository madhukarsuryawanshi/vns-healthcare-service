package com.vns.healthcare.service;

import com.vns.healthcare.entity.BusinessBankStatement;
import com.vns.healthcare.entity.BusinessBankStatementTable;
import com.vns.healthcare.repository.BusinessBankStatementTableRepository;
import com.vns.healthcare.security.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BankStatementMonthEndServiceTest {
    @Mock
    private BusinessBankStatementTableRepository tableRepository;
    @Mock
    private BankStatementExportService exportService;
    @Mock
    private EmailService emailService;

    private BankStatementMonthEndService service;

    @BeforeEach
    void setUp() {
        service = new BankStatementMonthEndService(tableRepository, exportService, emailService);
        ReflectionTestUtils.setField(service, "recipientsProperty", "a@example.com, b@example.com");
    }

    @Test
    void detectsMonthEndsAndRegularDays() {
        assertTrue(service.isMonthEnd(LocalDate.of(2026, 2, 28)));
        assertTrue(service.isMonthEnd(LocalDate.of(2024, 2, 29)));
        assertFalse(service.isMonthEnd(LocalDate.of(2026, 11, 24)));
        assertFalse(service.isMonthEnd(null));
    }

    @Test
    void parsesCommaSeparatedRecipientsAndSkipsBlanks() {
        List<String> recipients = service.parseRecipients("  one@example.com, two@example.com ,, ");
        assertEquals(List.of("one@example.com", "two@example.com"), recipients);
        assertEquals(List.of(), service.parseRecipients("  "));
    }

    @Test
    void monthEndGeneratesExcelPdfAndSendsBothAttachments() throws Exception {
        BusinessBankStatementTable table = new BusinessBankStatementTable();
        table.setTableName("November Statement");
        table.setOpeningBalance(BigDecimal.TEN);
        BusinessBankStatement row = new BusinessBankStatement();
        row.setTransactionDate(LocalDate.of(2026, 11, 30));
        row.setTransaction("Deposit");
        row.setStatementTable(table);
        table.getRows().add(row);

        when(tableRepository.findAll()).thenReturn(List.of(table));
        when(exportService.exportMonthExcel(anyString(), anyList())).thenReturn(new byte[]{1, 2});
        when(exportService.exportMonthPdf(anyString(), anyList())).thenReturn(new byte[]{3, 4});
        when(emailService.sendAttachments(anyList(), anyString(), anyString(), anyList())).thenReturn(true);

        service.run(LocalDate.of(2026, 11, 30));

        verify(exportService).exportMonthExcel(anyString(), anyList());
        verify(exportService).exportMonthPdf(anyString(), anyList());
        ArgumentCaptor<List<EmailService.Attachment>> attachmentCaptor = ArgumentCaptor.forClass(List.class);
        verify(emailService).sendAttachments(anyList(), anyString(), anyString(), attachmentCaptor.capture());
        assertEquals(2, attachmentCaptor.getValue().size());
        assertTrue(attachmentCaptor.getValue().get(0).filename().endsWith(".xlsx"));
        assertTrue(attachmentCaptor.getValue().get(1).filename().endsWith(".pdf"));
    }

    @Test
    void monthEndSkipsEmailWhenRecipientsAreEmpty() throws Exception {
        ReflectionTestUtils.setField(service, "recipientsProperty", "");
        service.run(LocalDate.of(2026, 11, 30));

        verify(tableRepository, org.mockito.Mockito.never()).findAll();
        verify(exportService, org.mockito.Mockito.never()).exportMonthExcel(anyString(), anyList());
        verify(emailService, org.mockito.Mockito.never()).sendAttachments(anyList(), anyString(), anyString(), anyList());
    }
}
