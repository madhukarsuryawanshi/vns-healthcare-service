package com.vns.healthcare.entity;

import com.vns.healthcare.domain.SalaryPayStatus;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "salary_payments", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"employee_id", "pay_year", "pay_month"})
})
public class SalaryPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "pay_year", nullable = false)
    private int payYear;

    @Column(name = "pay_month", nullable = false)
    private int payMonth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SalaryPayStatus status;

    @Column(precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "paid_on")
    private LocalDate paidOn;

    @Column(length = 500)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public int getPayYear() {
        return payYear;
    }

    public void setPayYear(int payYear) {
        this.payYear = payYear;
    }

    public int getPayMonth() {
        return payMonth;
    }

    public void setPayMonth(int payMonth) {
        this.payMonth = payMonth;
    }

    public SalaryPayStatus getStatus() {
        return status;
    }

    public void setStatus(SalaryPayStatus status) {
        this.status = status;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDate getPaidOn() {
        return paidOn;
    }

    public void setPaidOn(LocalDate paidOn) {
        this.paidOn = paidOn;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
