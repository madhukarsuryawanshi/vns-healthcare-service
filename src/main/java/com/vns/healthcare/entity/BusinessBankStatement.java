package com.vns.healthcare.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@EntityListeners(AuditEntityListener.class)
@Table(name = "business_bank_statement_rows")
public class BusinessBankStatement extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "statement_table_id", nullable = false)
    private BusinessBankStatementTable statementTable;

    @Column(name = "transaction_date")
    private LocalDate transactionDate;

    @Column(name = "transaction_name", length = 200)
    private String transaction;

    @Column(name = "credit", precision = 19, scale = 2)
    private BigDecimal credit;

    @Column(name = "debit", precision = 19, scale = 2)
    private BigDecimal debit;

    @Column(name = "current_balance", precision = 19, scale = 2)
    private BigDecimal currentBalance;

    @Column(name = "description", length = 1000)
    private String description;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public BusinessBankStatementTable getStatementTable() { return statementTable; }
    public void setStatementTable(BusinessBankStatementTable statementTable) { this.statementTable = statementTable; }
    public LocalDate getTransactionDate() { return transactionDate; }
    public void setTransactionDate(LocalDate transactionDate) { this.transactionDate = transactionDate; }
    public String getTransaction() { return transaction; }
    public void setTransaction(String transaction) { this.transaction = transaction; }
    public BigDecimal getCredit() { return credit; }
    public void setCredit(BigDecimal credit) { this.credit = credit; }
    public BigDecimal getDebit() { return debit; }
    public void setDebit(BigDecimal debit) { this.debit = debit; }
    public BigDecimal getCurrentBalance() { return currentBalance; }
    public void setCurrentBalance(BigDecimal currentBalance) { this.currentBalance = currentBalance; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
