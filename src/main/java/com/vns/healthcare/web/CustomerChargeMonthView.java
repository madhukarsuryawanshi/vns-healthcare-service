package com.vns.healthcare.web;

import com.vns.healthcare.domain.ChargePayStatus;
import com.vns.healthcare.entity.CustomerChargeStatus;

import java.time.LocalDate;
import java.time.YearMonth;

public class CustomerChargeMonthView {

    private final YearMonth month;
    private final ChargePayStatus status;
    private final CustomerChargeStatus record;
    private final boolean editable;

    public CustomerChargeMonthView(YearMonth month, ChargePayStatus status, CustomerChargeStatus record, LocalDate serviceStartDate) {
        this.month = month;
        this.status = status;
        this.record = record;
        this.editable = serviceStartDate == null || !month.isBefore(YearMonth.from(serviceStartDate));
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

    public ChargePayStatus getStatus() {
        return status;
    }

    public CustomerChargeStatus getRecord() {
        return record;
    }

    public boolean isPaid() {
        return status == ChargePayStatus.PAID;
    }

    public boolean isInProgress() {
        return status == ChargePayStatus.IN_PROGRESS;
    }

    public boolean isUnpaid() {
        return status == ChargePayStatus.UNPAID;
    }

    public boolean isEditable() {
        return editable;
    }
}
