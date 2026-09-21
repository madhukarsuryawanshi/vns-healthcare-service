package com.vns.healthcare.web;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import javax.servlet.http.HttpSession;
import java.util.Collection;

@ControllerAdvice
public class UserAccessAdvice {

    @ModelAttribute
    public void addAccessDeniedMessage(HttpSession session, Model model) {
        String message = (String) session.getAttribute("accessDeniedMessage");
        if (message != null && !message.trim().isEmpty()) {
            model.addAttribute("error", message);
            session.removeAttribute("accessDeniedMessage");
        }
    }

    @ModelAttribute("canReadDashboard")
    public boolean canReadDashboard(Authentication authentication) {
        return hasAccess(authentication, "dashboard:read") || hasRole(authentication, "ADMIN");
    }

    @ModelAttribute("canWriteEmployees")
    public boolean canWriteEmployees(Authentication authentication) {
        return hasAccess(authentication, "employees:write") || hasRole(authentication, "ADMIN");
    }

    @ModelAttribute("canReadEmployees")
    public boolean canReadEmployees(Authentication authentication) {
        return hasAccess(authentication, "employees:read") || canWriteEmployees(authentication);
    }

    @ModelAttribute("canWriteCustomers")
    public boolean canWriteCustomers(Authentication authentication) {
        return hasAccess(authentication, "customers:write") || hasRole(authentication, "ADMIN");
    }

    @ModelAttribute("canReadCustomers")
    public boolean canReadCustomers(Authentication authentication) {
        return hasAccess(authentication, "customers:read") || canWriteCustomers(authentication);
    }

    @ModelAttribute("canWriteAttendance")
    public boolean canWriteAttendance(Authentication authentication) {
        return hasAccess(authentication, "attendance:write") || hasRole(authentication, "ADMIN");
    }

    @ModelAttribute("canReadAttendance")
    public boolean canReadAttendance(Authentication authentication) {
        return hasAccess(authentication, "attendance:read") || canWriteAttendance(authentication);
    }

    @ModelAttribute("canWriteSalary")
    public boolean canWriteSalary(Authentication authentication) {
        return hasAccess(authentication, "salary:write") || hasRole(authentication, "ADMIN");
    }

    @ModelAttribute("canReadSalary")
    public boolean canReadSalary(Authentication authentication) {
        return hasAccess(authentication, "salary:read") || canWriteSalary(authentication);
    }

    private boolean hasAccess(Authentication authentication, String permission) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        for (GrantedAuthority authority : authorities) {
            if (permission.equalsIgnoreCase(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasRole(Authentication authentication, String role) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(a -> ("ROLE_" + role).equalsIgnoreCase(a.getAuthority()));
    }
}
