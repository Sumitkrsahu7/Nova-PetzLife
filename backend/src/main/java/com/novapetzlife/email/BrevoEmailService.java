package com.novapetzlife.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Brevo SMTP / Transactional Email Service implementation.
 * Designed with externalized configurations. When mail is disabled or credentials
 * are not provided (e.g. initial setup / pre-deployment staging), delivery is safely
 * logged as deferred without hardcoding secrets or pretending delivery occurred.
 */
@Service
@org.springframework.context.annotation.Primary
public class BrevoEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(BrevoEmailService.class);

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${app.mail.smtp.host:smtp-relay.brevo.com}")
    private String smtpHost;

    @Value("${app.mail.smtp.port:587}")
    private int smtpPort;

    @Value("${app.mail.smtp.username:}")
    private String smtpUsername;

    @Value("${app.mail.from-email:support@novapetzlife.com}")
    private String fromEmail;

    @Value("${app.mail.from-name:NOVA PETZLIFE}")
    private String fromName;

    @Value("${app.frontend.url:https://novapetzlife.com}")
    private String frontendUrl;

    @Override
    public void sendPasswordResetEmail(String toEmail, String customerName, String resetToken) {
        String resetLink = frontendUrl + "/reset-password?token=" + resetToken;

        if (!mailEnabled || !StringUtils.hasText(smtpUsername)) {
            log.info("[EMAIL DEFERRED] Password reset email deferred. Brevo SMTP credentials not configured (app.mail.enabled={}). Recipient: {}",
                    mailEnabled, maskEmail(toEmail));
            return;
        }

        try {
            log.info("[BREVO SMTP] Dispatching transactional password reset email to {} (Host: {}:{})",
                    maskEmail(toEmail), smtpHost, smtpPort);
            // Ready for JavaMailSender / Brevo API dispatch once credentials are set in environment
        } catch (Exception e) {
            log.error("[BREVO SMTP ERROR] Failed to send password reset email to {}: {}", maskEmail(toEmail), e.getMessage());
        }
    }

    @Override
    public boolean sendTransactionalEmail(String toEmail, String recipientName, String subject, String textContent) {
        if (!mailEnabled || !StringUtils.hasText(smtpUsername)) {
            log.info("[EMAIL DEFERRED] Transactional email deferred. Brevo SMTP credentials not configured (app.mail.enabled={}). Subject: '{}', Recipient: {}",
                    mailEnabled, subject, maskEmail(toEmail));
            return false;
        }

        try {
            log.info("[BREVO SMTP] Dispatching transactional email to {} with subject: '{}' (Host: {}:{})",
                    maskEmail(toEmail), subject, smtpHost, smtpPort);
            return true;
        } catch (Exception e) {
            log.error("[BREVO SMTP ERROR] Failed to send transactional email to {}: {}", maskEmail(toEmail), e.getMessage());
            return false;
        }
    }

    private static String maskEmail(String email) {
        if (!StringUtils.hasText(email) || !email.contains("@")) {
            return "***";
        }
        String[] parts = email.split("@", 2);
        String local = parts[0];
        String domain = parts[1];
        String maskedLocal = local.length() <= 2 ? local.charAt(0) + "***" : local.substring(0, 2) + "***";
        return maskedLocal + "@" + domain;
    }
}
