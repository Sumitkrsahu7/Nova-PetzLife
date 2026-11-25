package com.novapetzlife.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Resend Transactional Email Service implementation.
 * Official production transactional email provider for NOVA PETZLIFE.
 *
 * Utilizes the official Resend REST API (POST https://api.resend.com/emails).
 * Fail-Safe Design:
 * - When credentials or resend.enabled are not configured, dispatches are cleanly
 *   logged with [EMAIL DEFERRED] and return false (never falsely claims success).
 * - Email failures do not roll back active database transactions or block order checkout.
 * - Sensitive API keys, raw reset tokens, and full email addresses are masked in logs.
 */
@Service
@Primary
public class ResendEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Value("${resend.enabled:${RESEND_ENABLED:false}}")
    private boolean resendEnabled;

    @Value("${resend.api-key:${RESEND_API_KEY:}}")
    private String resendApiKey;

    @Value("${resend.base-url:${RESEND_BASE_URL:https://api.resend.com}}")
    private String resendBaseUrl;

    @Value("${resend.from-email:${RESEND_FROM_EMAIL:}}")
    private String fromEmail;

    @Value("${resend.from-name:${RESEND_FROM_NAME:NOVA PETZLIFE}}")
    private String fromName;

    @Value("${app.customer-base-url:${APP_CUSTOMER_BASE_URL:${app.frontend-base-url:${CUSTOMER_FRONTEND_URL:${FRONTEND_URL:http://localhost:4200}}}}}")
    private String customerBaseUrl;

    @Value("${app.password-reset-path:${APP_PASSWORD_RESET_PATH:/reset-password}}")
    private String passwordResetPath;

    @Value("${app.auth.password-reset-token-expiration-minutes:${PASSWORD_RESET_TOKEN_EXPIRATION_MINUTES:15}}")
    private long expirationMinutes;

    private final HttpClient httpClient;

    public ResendEmailService() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build());
    }

    public ResendEmailService(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public boolean isConfigured() {
        if (!resendEnabled) {
            return false;
        }
        if (!StringUtils.hasText(resendApiKey) || resendApiKey.isBlank()) {
            return false;
        }
        if (!StringUtils.hasText(fromEmail) || fromEmail.isBlank()
                || "onboarding@resend.dev".equalsIgnoreCase(fromEmail.trim())) {
            return false;
        }
        return fromEmail.contains("@") && fromEmail.contains(".");
    }

    @Override
    public void sendPasswordResetEmail(String toEmail, String customerName, String resetToken) {
        String cleanBaseUrl = customerBaseUrl != null ? customerBaseUrl.replaceAll("/+$", "") : "http://localhost:4200";
        String cleanPath = passwordResetPath != null ? "/" + passwordResetPath.replaceAll("^/+", "") : "/reset-password";
        String encodedToken = URLEncoder.encode(resetToken != null ? resetToken : "", StandardCharsets.UTF_8);
        String resetLink = cleanBaseUrl + cleanPath + "?token=" + encodedToken;

        if (!isConfigured()) {
            log.warn("[EMAIL DEFERRED] Resend email service is not configured (resend.enabled={}, RESEND_API_KEY). Password reset email deferred for recipient: {}",
                    resendEnabled, maskEmail(toEmail));
            return;
        }

        String displayName = StringUtils.hasText(customerName) ? customerName : "Valued Customer";
        String htmlContent = buildHtmlEmail(displayName, resetLink, expirationMinutes);
        String plainTextContent = buildPlainTextEmail(displayName, resetLink, expirationMinutes);

        boolean sent = sendEmailInternal(toEmail, "Reset Your NOVA PETZLIFE Account Password", htmlContent, plainTextContent);
        if (!sent) {
            log.error("[RESEND ERROR] Failed to dispatch password reset email to: {}", maskEmail(toEmail));
        }
    }

    @Override
    public boolean sendTransactionalEmail(String toEmail, String recipientName, String subject, String textContent) {
        if (!isConfigured()) {
            log.warn("[EMAIL DEFERRED] Resend transactional email deferred. Service not configured (resend.enabled={}). Subject: '{}', Recipient: {}",
                    resendEnabled, subject, maskEmail(toEmail));
            return false;
        }

        String htmlContent = (textContent != null && textContent.contains("<") && textContent.contains(">"))
                ? textContent
                : "<p>" + (textContent != null ? textContent.replace("\n", "<br/>") : "") + "</p>";
        String plainTextContent = textContent != null ? textContent.replaceAll("<[^>]*>", "") : "";

        return sendEmailInternal(toEmail, subject, htmlContent, plainTextContent);
    }

    /**
     * Genuine Resend API HTTP dispatcher with robust status code mapping and safety isolation.
     */
    private boolean sendEmailInternal(String toEmail, String subject, String htmlContent, String plainTextContent) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("from", fromName + " <" + fromEmail + ">");
            payload.put("to", new String[]{toEmail});
            payload.put("subject", subject);
            if (StringUtils.hasText(htmlContent)) {
                payload.put("html", htmlContent);
            }
            if (StringUtils.hasText(plainTextContent)) {
                payload.put("text", plainTextContent);
            }

            String requestBody = OBJECT_MAPPER.writeValueAsString(payload);
            String targetUrl = (StringUtils.hasText(resendBaseUrl) ? resendBaseUrl.replaceAll("/+$", "") : "https://api.resend.com") + "/emails";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .header("Authorization", "Bearer " + resendApiKey.trim())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(8))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();

            if (statusCode >= 200 && statusCode < 300) {
                log.info("[RESEND SUCCESS] Dispatched email to: {} (Subject: '{}')", maskEmail(toEmail), subject);
                return true;
            } else if (statusCode == 400) {
                log.error("[RESEND 400 BAD REQUEST] Invalid email request payload for recipient {}: {}", maskEmail(toEmail), response.body());
                return false;
            } else if (statusCode == 401) {
                log.error("[RESEND 401 UNAUTHORIZED] Invalid or revoked Resend API key");
                return false;
            } else if (statusCode == 403) {
                log.error("[RESEND 403 FORBIDDEN] Sender domain unverified or forbidden: {}", response.body());
                return false;
            } else if (statusCode == 429) {
                log.error("[RESEND 429 RATE LIMITED] Rate limit exceeded by Resend API for recipient {}", maskEmail(toEmail));
                return false;
            } else {
                log.error("[RESEND {} SERVER ERROR] Upstream failure from Resend API for recipient {}: {}", statusCode, maskEmail(toEmail), response.body());
                return false;
            }

        } catch (HttpConnectTimeoutException e) {
            log.error("[RESEND TIMEOUT] Connection timeout while connecting to Resend API for recipient {}", maskEmail(toEmail));
            return false;
        } catch (HttpTimeoutException e) {
            log.error("[RESEND TIMEOUT] Request timeout waiting for Resend API response for recipient {}", maskEmail(toEmail));
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[RESEND INTERRUPTED] Email dispatch interrupted for recipient {}", maskEmail(toEmail));
            return false;
        } catch (Exception e) {
            log.error("[RESEND EXCEPTION] Error occurred while attempting to dispatch email to {}: {}",
                    maskEmail(toEmail), e.getMessage());
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

    private static String buildHtmlEmail(String name, String resetLink, long expMinutes) {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
              <title>Reset Your Password — NOVA PETZLIFE</title>
              <style>
                body { margin: 0; padding: 0; background-color: #F8FAFC; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #0F172A; }
                .email-container { max-width: 580px; margin: 30px auto; background: #FFFFFF; border-radius: 12px; overflow: hidden; border: 1px solid #E2E8F0; }
                .email-header { background: #155C3B; padding: 24px; text-align: center; }
                .email-header h1 { margin: 0; color: #FFFFFF; font-size: 22px; font-weight: 800; letter-spacing: 0.5px; }
                .email-body { padding: 32px 28px; }
                .email-body h2 { color: #1E293B; font-size: 18px; margin-top: 0; }
                .email-body p { font-size: 15px; line-height: 1.6; color: #475569; margin: 16px 0; }
                .btn-wrap { text-align: center; margin: 28px 0; }
                .btn-reset { display: inline-block; background-color: #248B58; color: #FFFFFF !important; text-decoration: none; font-weight: 700; font-size: 15px; padding: 13px 30px; border-radius: 8px; }
                .notice-box { background: #F0FDF4; border-left: 4px solid #248B58; padding: 14px 18px; border-radius: 4px; font-size: 13.5px; color: #166534; margin: 24px 0; }
                .warning-text { font-size: 13px; color: #64748B; border-top: 1px solid #E2E8F0; padding-top: 20px; margin-top: 28px; }
                .email-footer { background: #F8FAFC; padding: 20px; text-align: center; font-size: 12px; color: #94A3B8; border-top: 1px solid #E2E8F0; }
              </style>
            </head>
            <body>
              <div class="email-container">
                <div class="email-header">
                  <h1>🐾 NOVA PETZLIFE</h1>
                </div>
                <div class="email-body">
                  <h2>Hello, %s</h2>
                  <p>We received a request to reset the password for your NOVA PETZLIFE customer account.</p>
                  <p>Click the button below to choose a new, secure password:</p>
                  <div class="btn-wrap">
                    <a href="%s" target="_blank" rel="noopener noreferrer" class="btn-reset">Reset My Password</a>
                  </div>
                  <div class="notice-box">
                    <strong>⏳ Security Notice:</strong> This password reset link will expire in <strong>%d minutes</strong> and can only be used once.
                  </div>
                  <p>If the button above does not work, copy and paste this link into your browser:</p>
                  <p style="word-break: break-all; font-size: 13px; color: #248B58;">%s</p>
                  <div class="warning-text">
                    <p><strong>Did not request this change?</strong> You can safely ignore this email. Your account password will remain unchanged.</p>
                  </div>
                </div>
                <div class="email-footer">
                  <p>&copy; NOVA PETZLIFE, Kuravankonam, Thiruvananthapuram, Kerala 695003</p>
                  <p>This is an automated security notification. Please do not reply directly to this email.</p>
                </div>
              </div>
            </body>
            </html>
            """.formatted(name, resetLink, expMinutes, resetLink);
    }

    private static String buildPlainTextEmail(String name, String resetLink, long expMinutes) {
        return """
            Hello %s,

            We received a request to reset the password for your NOVA PETZLIFE account.

            To choose a new password, please visit the following link:
            %s

            This link is valid for %d minutes and can only be used once.

            If you did not request this password reset, please ignore this email. Your password will remain unchanged.

            — NOVA PETZLIFE Team
            Kuravankonam, Thiruvananthapuram, Kerala
            """.formatted(name, resetLink, expMinutes);
    }
}
