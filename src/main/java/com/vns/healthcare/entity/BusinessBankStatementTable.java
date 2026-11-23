package com.vns.healthcare.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@EntityListeners(AuditEntityListener.class)
@Table(name = "business_bank_statement_tables")
public class BusinessBankStatementTable extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "table_name", nullable = false, length = 200)
    private String tableName;

    @Column(name = "opening_balance", precision = 19, scale = 2)
    private BigDecimal openingBalance = BigDecimal.ZERO;

    @Column(name = "opening_balance_saved", nullable = false, columnDefinition = "boolean default false")
    private boolean openingBalanceSaved = false;

    @OneToMany(mappedBy = "statementTable", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    private List<BusinessBankStatement> rows = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public BigDecimal getOpeningBalance() { return openingBalance == null ? BigDecimal.ZERO : openingBalance; }
    public void setOpeningBalance(BigDecimal openingBalance) { this.openingBalance = openingBalance == null ? BigDecimal.ZERO : openingBalance; }
    public boolean isOpeningBalanceSaved() { return openingBalanceSaved; }
    public void setOpeningBalanceSaved(boolean openingBalanceSaved) { this.openingBalanceSaved = openingBalanceSaved; }
    public List<BusinessBankStatement> getRows() { return rows; }
    public void setRows(List<BusinessBankStatement> rows) { this.rows = rows; }
}
