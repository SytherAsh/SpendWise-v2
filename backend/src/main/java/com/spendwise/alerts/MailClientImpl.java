package com.spendwise.alerts;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** E5-S3-T2 — {@code JavaMailSender} is auto-configured by spring-boot-starter-mail from {@code spring.mail.*} (EMAIL_SMTP_*). */
@Component
public class MailClientImpl implements MailClient {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public MailClientImpl(JavaMailSender mailSender, @Value("${app.mail.from-address:}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void send(String toEmail, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject(subject);
        message.setText(body);
        if (StringUtils.hasText(fromAddress)) {
            message.setFrom(fromAddress);
        }
        // JavaMailSender.send throws MailException (a RuntimeException) directly — no checked
        // exception to wrap, unlike FcmClientImpl.
        mailSender.send(message);
    }
}
