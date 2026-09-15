package com.vns.healthcare.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    @GetMapping("/login")
    public String login(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            log.info("Authenticated user [{}] already logged in; redirecting to dashboard", authentication.getName());
            return "redirect:/";
        }
        log.info("Rendering login page");
        return "login";
    }
}
