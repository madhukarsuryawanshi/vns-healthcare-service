package com.vns.healthcare.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessagePreparator;
import org.springframework.util.StringUtils;

import javax.mail.internet.MimeMessage;
import java.io.InputStream;
import java.util.Properties;

@Configuration
public class MailConfig {

    private final Environment environment;

    public MailConfig(Environment environment) {
        this.environment = environment;
    }

    @Bean
    @ConditionalOnMissingBean(JavaMailSender.class)
    public JavaMailSender javaMailSender() {
        String configuredHost = environment.getProperty("spring.mail.host");
        if (!StringUtils.hasText(configuredHost)) {
            return new NoOpJavaMailSender();
        }

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(configuredHost);
        sender.setPort(Integer.parseInt(environment.getProperty("spring.mail.port", "587")));

        String username = environment.getProperty("spring.mail.username");
        if (StringUtils.hasText(username)) {
            sender.setUsername(username);
        }

        String password = environment.getProperty("spring.mail.password");
        if (StringUtils.hasText(password)) {
            sender.setPassword(password);
        }

        Properties props = new Properties();
        props.put("mail.smtp.auth", environment.getProperty("spring.mail.properties.mail.smtp.auth", "false"));
        props.put("mail.smtp.starttls.enable", environment.getProperty("spring.mail.properties.mail.smtp.starttls.enable", "false"));
        sender.setJavaMailProperties(props);

        return sender;
    }

    public static class NoOpJavaMailSender implements JavaMailSender {
        @Override
        public MimeMessage createMimeMessage() {
            return null;
        }

        @Override
        public MimeMessage createMimeMessage(InputStream contentStream) {
            return null;
        }

        @Override
        public void send(MimeMessage mimeMessage) throws MailException {
        }

        @Override
        public void send(MimeMessage[] mimeMessages) throws MailException {
        }

        @Override
        public void send(MimeMessagePreparator mimeMessagePreparator) throws MailException {
        }

        @Override
        public void send(MimeMessagePreparator[] mimeMessagePreparators) throws MailException {
        }

        @Override
        public void send(SimpleMailMessage simpleMessage) throws MailException {
        }

        @Override
        public void send(SimpleMailMessage[] simpleMessages) throws MailException {
        }
    }
}
