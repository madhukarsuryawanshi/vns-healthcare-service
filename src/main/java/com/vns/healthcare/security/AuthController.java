package com.vns.healthcare.security;

import com.vns.healthcare.entity.Employee;
import com.vns.healthcare.repository.EmployeeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpSession;
import java.security.SecureRandom;
import java.util.Optional;

@Controller
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private static final long OTP_TTL_MILLIS = 10L * 60L * 1000L;

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    public AuthController(UserRepository userRepository,
                          EmployeeRepository employeeRepository,
                          PasswordEncoder passwordEncoder,
                          EmailService emailService) {
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    @GetMapping("/login")
    public String login(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            log.info("Authenticated user [{}] already logged in; redirecting to dashboard", authentication.getName());
            return "redirect:/";
        }
        log.info("Rendering login page");
        return "login";
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordPage(Model model) {
        model.addAttribute("pageTitle", "Forgot password");
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String submitForgotPassword(@RequestParam("employeeId") String employeeId,
                                       @RequestParam("email") String email,
                                       HttpSession session,
                                       RedirectAttributes redirectAttributes) {
        String trimmedEmployeeId = employeeId == null ? "" : employeeId.trim();
        String trimmedEmail = email == null ? "" : email.trim();

        if (trimmedEmployeeId.isEmpty() || trimmedEmail.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please enter your employee ID and registered email.");
            return "redirect:/forgot-password";
        }

        Optional<Employee> employeeOptional = employeeRepository.findByEmpCode(trimmedEmployeeId);
        if (!employeeOptional.isPresent()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Employee ID not found. Please check the employee code.");
            return "redirect:/forgot-password";
        }

        Employee employee = employeeOptional.get();
        String employeeEmail = employee.getEmail() == null ? "" : employee.getEmail().trim();
        if (employeeEmail.isEmpty() || !employeeEmail.equalsIgnoreCase(trimmedEmail)) {
            redirectAttributes.addFlashAttribute("errorMessage", "The email does not match the registered email for employee ID " + trimmedEmployeeId + ".");
            return "redirect:/forgot-password";
        }

        Optional<AppUser> userOptional = userRepository.findByUsernameIgnoreCase(trimmedEmployeeId);
        if (!userOptional.isPresent()) {
            userOptional = userRepository.findByEmailIgnoreCase(trimmedEmail);
        }

        if (!userOptional.isPresent()) {
            redirectAttributes.addFlashAttribute("successMessage",
                    "If the employee ID and email match an active account, an OTP has been sent to the registered email.");
            return "redirect:/forgot-password";
        }

        AppUser user = userOptional.get();
        String resolvedEmail = user.getEmail() == null ? "" : user.getEmail().trim();
        if (resolvedEmail.isEmpty()) {
            if (!employeeEmail.isEmpty()) {
                user.setEmail(employeeEmail);
                userRepository.save(user);
                resolvedEmail = employeeEmail;
            } else {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "No registered email is available for this account. Please contact the administrator.");
                return "redirect:/forgot-password";
            }
        }

        String otp = generateOtp();
        session.setAttribute("resetUsername", user.getUsername());
        session.setAttribute("resetEmail", resolvedEmail);
        session.setAttribute("resetOtp", otp);
        session.setAttribute("resetOtpIssuedAt", System.currentTimeMillis());

        boolean delivered = emailService.sendOtp(resolvedEmail, otp);
        if (!delivered) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Unable to send an OTP to the registered email right now. Please contact your administrator.");
            return "redirect:/forgot-password";
        }

        redirectAttributes.addFlashAttribute("successMessage",
                "A password reset OTP has been sent to " + resolvedEmail + ".");
        return "redirect:/reset-password";
    }

    @GetMapping("/reset-password")
    public String resetPasswordPage(HttpSession session, Model model) {
        Object resetUsername = session.getAttribute("resetUsername");
        if (resetUsername == null) {
            return "redirect:/forgot-password";
        }
        model.addAttribute("pageTitle", "Reset password");
        model.addAttribute("resetEmail", session.getAttribute("resetEmail"));
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String submitResetPassword(@RequestParam("otp") String otp,
                                     @RequestParam("newPassword") String newPassword,
                                     @RequestParam("confirmPassword") String confirmPassword,
                                     HttpSession session,
                                     RedirectAttributes redirectAttributes) {
        String storedUsername = (String) session.getAttribute("resetUsername");
        String storedOtp = (String) session.getAttribute("resetOtp");
        Long issuedAt = (Long) session.getAttribute("resetOtpIssuedAt");

        if (storedUsername == null || storedOtp == null || issuedAt == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Your reset session has expired. Please request a new OTP.");
            return "redirect:/forgot-password";
        }

        if (System.currentTimeMillis() - issuedAt > OTP_TTL_MILLIS) {
            clearResetSession(session);
            redirectAttributes.addFlashAttribute("errorMessage", "The OTP has expired. Please request a new one.");
            return "redirect:/forgot-password";
        }

        if (otp == null || otp.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please enter the OTP sent to your registered email.");
            return "redirect:/reset-password";
        }

        if (!storedOtp.equals(otp.trim())) {
            redirectAttributes.addFlashAttribute("errorMessage", "The OTP you entered is incorrect.");
            return "redirect:/reset-password";
        }

        if (newPassword == null || newPassword.length() < 6) {
            redirectAttributes.addFlashAttribute("errorMessage", "Password must be at least 6 characters long.");
            return "redirect:/reset-password";
        }

        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Passwords do not match.");
            return "redirect:/reset-password";
        }

        AppUser user = userRepository.findByUsername(storedUsername).orElse(null);
        if (user == null) {
            clearResetSession(session);
            redirectAttributes.addFlashAttribute("errorMessage", "Account not found. Please request a new OTP.");
            return "redirect:/forgot-password";
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        log.info("Generated new password hash for user [{}]", user.getUsername());
        userRepository.save(user);
        log.info("Password reset successfully for user [{}]", user.getUsername());
        clearResetSession(session);
        redirectAttributes.addFlashAttribute("successMessage", "Password reset successfully. Please sign in with your new password.");
        return "redirect:/login";
    }

    private String generateOtp() {
        SecureRandom random = new SecureRandom();
        int otpNumber = 100000 + random.nextInt(900000);
        return String.valueOf(otpNumber);
    }

    private void clearResetSession(HttpSession session) {
        session.removeAttribute("resetUsername");
        session.removeAttribute("resetEmail");
        session.removeAttribute("resetOtp");
        session.removeAttribute("resetOtpIssuedAt");
    }
}
