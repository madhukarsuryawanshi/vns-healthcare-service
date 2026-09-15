package com.vns.healthcare.web;

import com.vns.healthcare.domain.SalaryPayStatus;
import com.vns.healthcare.entity.Employee;
import com.vns.healthcare.exception.BusinessException;
import com.vns.healthcare.service.SalaryPaymentService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/salary")
public class SalaryController {

    private final SalaryPaymentService salaryPaymentService;

    public SalaryController(SalaryPaymentService salaryPaymentService) {
        this.salaryPaymentService = salaryPaymentService;
    }

    @GetMapping
    public String register(@RequestParam(value = "year", required = false) Integer year, Model model) {
        int y = year == null ? YearMonth.now().getYear() : year;
        Map<Employee, List<SalaryMonthView>> rows = salaryPaymentService.register(y);
        List<String> monthNames = new ArrayList<String>();
        for (int m = 1; m <= 12; m++) {
            monthNames.add(YearMonth.of(y, m).getMonth().name().substring(0, 3));
        }
        model.addAttribute("page", "salary");
        model.addAttribute("year", y);
        model.addAttribute("prevYear", y - 1);
        model.addAttribute("nextYear", y + 1);
        model.addAttribute("monthNames", monthNames);
        model.addAttribute("rows", rows);
        model.addAttribute("today", LocalDate.now());
        return "salary/list";
    }

    @PostMapping
    public String mark(@RequestParam Long employeeId,
                       @RequestParam int year,
                       @RequestParam int month,
                       @RequestParam SalaryPayStatus status,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate paidOn,
                       @RequestParam(required = false) String notes,
                       RedirectAttributes redirectAttributes) {
        try {
            salaryPaymentService.mark(employeeId, year, month, status, paidOn, notes);
            redirectAttributes.addFlashAttribute("success", "Salary status saved.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/salary?year=" + year;
    }
}
