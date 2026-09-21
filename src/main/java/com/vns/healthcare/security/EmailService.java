package com.vns.healthcare.security;

import com.vns.healthcare.config.MailConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public boolean sendOtp(String toAddress, String otp) {
        if (toAddress == null || toAddress.trim().isEmpty()) {
            log.warn("Cannot send OTP because the email address is blank");
            return false;
        }

        if (mailSender instanceof MailConfig.NoOpJavaMailSender) {
            log.warn("Mail server is not configured. OTP sending is disabled. Set MAIL_HOST and SMTP credentials to enable email delivery.");
            return false;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toAddress);
            message.setSubject("VNS Healthcare Password Reset OTP");
            message.setText("Your password reset OTP is: " + otp + "\n\nThis code expires in 10 minutes.");
            mailSender.send(message);
            log.info("Sent password reset OTP to [{}]", toAddress);
            return true;
        } catch (MailException ex) {
            log.error("Failed to send OTP email to [{}]", toAddress, ex);
            return false;
        }
    }
}
