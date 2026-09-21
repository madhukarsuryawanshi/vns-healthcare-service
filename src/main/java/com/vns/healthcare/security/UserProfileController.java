package com.vns.healthcare.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/profile")
public class UserProfileController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserProfileController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/password")
    public String passwordForm(Model model) {
        model.addAttribute("page", "dashboard");
        return "profile/change-password";
    }

    @PostMapping("/password")
    public String changePassword(@RequestParam String currentPassword,
                                @RequestParam String newPassword,
                                @RequestParam String confirmPassword,
                                Authentication authentication,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        String username = authentication == null ? null : authentication.getName();
        if (username == null || username.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Your session is not active.");
            return "redirect:/login";
        }

        AppUser user = userRepository.findByUsername(username)
                .orElse(null);
        if (user == null) {
            redirectAttributes.addFlashAttribute("error", "User not found.");
            return "redirect:/";
        }

        model.addAttribute("page", "dashboard");

        boolean hasErrors = false;

        if (currentPassword == null || currentPassword.trim().isEmpty()) {
            model.addAttribute("currentPasswordError", "Current password is required.");
            hasErrors = true;
        }

        if (newPassword == null || newPassword.trim().isEmpty()) {
            model.addAttribute("newPasswordError", "New password is required.");
            hasErrors = true;
        } else if (newPassword.length() < 8) {
            model.addAttribute("newPasswordError", "Use at least 8 characters for a stronger password.");
            hasErrors = true;
        }

        if (confirmPassword == null || confirmPassword.trim().isEmpty()) {
            model.addAttribute("confirmPasswordError", "Please confirm your new password.");
            hasErrors = true;
        }

        if (!hasErrors) {
            if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
                model.addAttribute("currentPasswordError", "Current password is incorrect.");
                hasErrors = true;
            }

            if (!newPassword.equals(confirmPassword)) {
                model.addAttribute("confirmPasswordError", "New password and confirm password do not match.");
                hasErrors = true;
            }
        }

        if (hasErrors) {
            model.addAttribute("formError", "Please correct the highlighted fields and try again.");
            return "profile/change-password";
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        redirectAttributes.addFlashAttribute("success", "Password updated successfully.");
        return "redirect:/";
    }
}
