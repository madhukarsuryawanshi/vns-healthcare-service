package com.vns.healthcare.web;

import com.vns.healthcare.domain.SalaryPayStatus;
import com.vns.healthcare.entity.SalaryPayment;

import java.time.YearMonth;

public class SalaryMonthView {

    private final YearMonth month;
    private final SalaryPayStatus status;
    private final SalaryPayment payment;
    private final boolean currentMonth;

    public SalaryMonthView(YearMonth month, SalaryPayStatus status, SalaryPayment payment, boolean currentMonth) {
        this.month = month;
        this.status = status;
        this.payment = payment;
        this.currentMonth = currentMonth;
    }

    public YearMonth getMonth() {
        return month;
    }

    public int getMonthValue() {
        return month.getMonthValue();
    }

    public int getYear() {
        return month.getYear();
    }

    public String getLabel() {
        return month.getMonth().name().substring(0, 1)
                + month.getMonth().name().substring(1).toLowerCase();
    }

    public String getShortLabel() {
        return month.getMonth().name().substring(0, 3);
    }

    public SalaryPayStatus getStatus() {
        return status;
    }

    public SalaryPayment getPayment() {
        return payment;
    }

    public boolean isCurrentMonth() {
        return currentMonth;
    }

    public boolean isPaid() {
        return status == SalaryPayStatus.PAID;
    }

    public boolean isInProgress() {
        return status == SalaryPayStatus.IN_PROGRESS;
    }

    public boolean isUnpaid() {
        return status == SalaryPayStatus.UNPAID;
    }
}
