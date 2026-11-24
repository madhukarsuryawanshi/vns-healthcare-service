package com.vns.healthcare.service;

import com.vns.healthcare.entity.BusinessBankStatement;
import com.vns.healthcare.entity.BusinessBankStatementTable;
import com.vns.healthcare.repository.BusinessBankStatementTableRepository;
import com.vns.healthcare.security.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class BankStatementMonthEndService {
    private static final Logger log = LoggerFactory.getLogger(BankStatementMonthEndService.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final BusinessBankStatementTableRepository tableRepository;
    private final BankStatementExportService exportService;
    private final EmailService emailService;

    @Value("${app.bank.statement.mail-to:}")
    private String recipientsProperty;

    public BankStatementMonthEndService(BusinessBankStatementTableRepository tableRepository,
                                        BankStatementExportService exportService,
                                        EmailService emailService) {
        this.tableRepository = tableRepository;
        this.exportService = exportService;
        this.emailService = emailService;
    }

    @Scheduled(cron = "0 0 10 * * *", zone = "Asia/Kolkata")
    public void scheduledRun() {
        run(LocalDate.now(IST));
    }

    @Transactional(readOnly = true)
    public void run(LocalDate date) {
        if (!isMonthEnd(date)) {
            return;
        }

        List<String> recipients = parseRecipients(recipientsProperty);
        if (recipients.isEmpty()) {
            log.warn("Bank statement month-end export skipped: APP_BANK_STATEMENT_MAIL_TO is empty");
            return;
        }

        YearMonth month = YearMonth.from(date);
        List<BusinessBankStatementTable> monthTables = monthTables(month);
        if (monthTables.isEmpty()) {
            log.warn("No bank statement rows found for month [{}]; no export email sent", month);
            return;
        }

        String monthLabel = month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + "-" + month.getYear();
        try {
            byte[] excel = exportService.exportMonthExcel(monthLabel, monthTables);
            byte[] pdf = exportService.exportMonthPdf(monthLabel, monthTables);
            String safeLabel = monthLabel.replace(' ', '-');
            emailService.sendAttachments(recipients,
                    "VNS Healthcare Bank Statements - " + monthLabel,
                    "Please find the bank statements for " + monthLabel + " attached.",
                    List.of(
                            new EmailService.Attachment(safeLabel + "-Bank-Statements.xlsx", excel,
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
                            new EmailService.Attachment(safeLabel + "-Bank-Statements.pdf", pdf,
                                    "application/pdf")));
        } catch (Exception ex) {
            log.error("Failed to generate or email bank statements for [{}]", monthLabel, ex);
        }
    }

    public boolean isMonthEnd(LocalDate date) {
        return date != null && date.equals(date.with(java.time.temporal.TemporalAdjusters.lastDayOfMonth()));
    }

    public List<String> parseRecipients(String value) {
        if (value == null || value.trim().isEmpty()) {
            return List.of();
        }
        List<String> recipients = new ArrayList<>();
        for (String recipient : value.split(",")) {
            String trimmed = recipient.trim();
            if (!trimmed.isEmpty()) {
                recipients.add(trimmed);
            }
        }
        return recipients;
    }

    private List<BusinessBankStatementTable> monthTables(YearMonth month) {
        List<BusinessBankStatementTable> result = new ArrayList<>();
        for (BusinessBankStatementTable source : tableRepository.findAll()) {
            if (source == null || source.getRows() == null) {
                continue;
            }
            BusinessBankStatementTable copy = new BusinessBankStatementTable();
            copy.setTableName(source.getTableName());
            copy.setOpeningBalance(source.getOpeningBalance());
            copy.setOpeningBalanceSaved(source.isOpeningBalanceSaved());
            for (BusinessBankStatement row : source.getRows()) {
                if (row != null && row.getTransactionDate() != null
                        && YearMonth.from(row.getTransactionDate()).equals(month)) {
                    BusinessBankStatement monthRow = new BusinessBankStatement();
                    monthRow.setTransactionDate(row.getTransactionDate());
                    monthRow.setTransaction(row.getTransaction());
                    monthRow.setCredit(row.getCredit());
                    monthRow.setDebit(row.getDebit());
                    monthRow.setCurrentBalance(row.getCurrentBalance());
                    monthRow.setDescription(row.getDescription());
                    monthRow.setStatementTable(copy);
                    copy.getRows().add(monthRow);
                }
            }
            if (!copy.getRows().isEmpty()) {
                result.add(copy);
            }
        }
        return result;
    }
}
