package com.vns.healthcare.web;

import com.vns.healthcare.entity.CustomerDuty;

import java.time.LocalDate;

public class DutyDayView {

    private final LocalDate date;
    private final CustomerDuty duty;

    public DutyDayView(LocalDate date, CustomerDuty duty) {
        this.date = date;
        this.duty = duty;
    }

    public LocalDate getDate() {
        return date;
    }

    public boolean isHold() {
        return duty != null && duty.isHold();
    }

    public boolean hasStaff() {
        return duty != null && !duty.isHold() && duty.getEmployee() != null;
    }

    public String getStaffName() {
        if (!hasStaff()) {
            return "";
        }
        return duty.getEmployee().getFullName();
    }

    public boolean isEmpty() {
        return duty == null || (!duty.isHold() && duty.getEmployee() == null);
    }
}
