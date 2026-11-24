package com.vns.healthcare.security;

import com.vns.healthcare.config.MailConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.mail.javamail.MimeMessageHelper;
import jakarta.mail.internet.MimeMessage;
import java.util.List;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public boolean sendAttachments(List<String> recipients, String subject, String text,
                                   List<Attachment> attachments) {
        if (recipients == null || recipients.isEmpty()) {
            log.warn("Cannot send email because no recipients were provided");
            return false;
        }
        if (mailSender instanceof MailConfig.NoOpJavaMailSender) {
            log.warn("Mail server is not configured. Attachment email sending is disabled.");
            return false;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(recipients.toArray(new String[0]));
            helper.setSubject(subject);
            helper.setText(text, false);
            if (attachments != null) {
                for (Attachment attachment : attachments) {
                    helper.addAttachment(attachment.filename(), () -> new java.io.ByteArrayInputStream(attachment.data()), attachment.contentType());
                }
            }
            mailSender.send(message);
            log.info("Sent attachment email to [{}]", recipients);
            return true;
        } catch (Exception ex) {
            log.error("Failed to send attachment email to [{}]", recipients, ex);
            return false;
        }
    }

    public record Attachment(String filename, byte[] data, String contentType) {}

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
