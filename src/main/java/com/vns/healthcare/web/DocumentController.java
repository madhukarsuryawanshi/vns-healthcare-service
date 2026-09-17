package com.vns.healthcare.web;

import com.vns.healthcare.exception.BusinessException;
import com.vns.healthcare.service.CustomerService;
import com.vns.healthcare.service.EmployeeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class DocumentController {

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);

    private final EmployeeService employeeService;
    private final CustomerService customerService;

    public DocumentController(EmployeeService employeeService, CustomerService customerService) {
        this.employeeService = employeeService;
        this.customerService = customerService;
    }

    @PreAuthorize("hasAuthority('employees:write') or hasRole('ADMIN')")
    @PostMapping("/employees/{id}/documents/{docId}/delete")
    public String deleteEmployeeDocument(@PathVariable Long id, @PathVariable Long docId, RedirectAttributes redirectAttributes) {
        try {
            employeeService.deleteDocument(id, docId);
            redirectAttributes.addFlashAttribute("success", "Document deleted.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/employees/" + id;
    }

    @PreAuthorize("hasAuthority('customers:write') or hasRole('ADMIN')")
    @PostMapping("/customers/{id}/documents/{docId}/delete")
    public String deleteCustomerDocument(@PathVariable Long id, @PathVariable Long docId, RedirectAttributes redirectAttributes) {
        try {
            customerService.deleteDocument(id, docId);
            redirectAttributes.addFlashAttribute("success", "Document deleted.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/customers/" + id;
    }
}
