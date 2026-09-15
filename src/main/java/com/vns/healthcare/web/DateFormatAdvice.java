package com.vns.healthcare.web;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.time.LocalDate;

@ControllerAdvice
public class DateFormatAdvice {

    @ModelAttribute("today")
    public LocalDate today() {
        return LocalDate.now();
    }

    @ModelAttribute("currentUser")
    public String currentUser(Authentication authentication) {
        return authentication == null ? "Guest" : authentication.getName();
    }
}
