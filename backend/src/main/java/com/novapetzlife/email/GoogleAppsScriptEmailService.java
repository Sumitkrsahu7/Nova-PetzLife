package com.novapetzlife.email;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
 * Temporary HTTPS email bridge that dispatches customer password reset emails via a
 * Google Apps Script Web App ({@code GmailApp.sendEmail}) over Port 443.
 *
 * <p>Designed for environments like Render's free tier where outbound SMTP ports
 * (25, 465, 587) are blocked. Intentionally scoped to password-reset emails only;
 * general transactional notifications remain bound to the primary {@link EmailService}.</p>
 */
@Service("googleAppsScriptEmailService")
public class GoogleAppsScriptEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(GoogleAppsScriptEmailService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Value("${gmail-bridge.enabled:${GMAIL_BRIDGE_ENABLED:false}}")
    private boolean enabled;

    @Value("${gmail-bridge.webhook-url:${GMAIL_BRIDGE_WEBHOOK_URL:}}")
    private String webhookUrl;

    @Value("${gmail-bridge.secret:${GMAIL_BRIDGE_SECRET:}}")
    private String secret;

    @Value("${gmail-bridge.from-name:${GMAIL_BRIDGE_FROM_NAME:NOVA PETZLIFE}}")
    private String fromName;

    @Value("${app.customer-base-url:${APP_CUSTOMER_BASE_URL:${app.frontend-base-url:${CUSTOMER_FRONTEND_URL:${FRONTEND_URL:http://localhost:4200}}}}}")
    private String customerBaseUrl;

    @Value("${app.password-reset-path:${APP_PASSWORD_RESET_PATH:/reset-password}}")
    private String passwordResetPath;

    @Value("${app.auth.password-reset-token-expiration-minutes:${PASSWORD_RESET_TOKEN_EXPIRATION_MINUTES:15}}")
    private long expirationMinutes;

    private final HttpClient httpClient;

    @Autowired
    public GoogleAppsScriptEmailService() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    GoogleAppsScriptEmailService(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isConfigured() {
        if (!enabled) {
            return false;
        }
        if (!StringUtils.hasText(webhookUrl) || !StringUtils.hasText(secret)) {
            return false;
        }
        String trimmedUrl = webhookUrl.trim();
        if (!trimmedUrl.startsWith("https://")) {
            return false;
        }
        try {
            URI uri = URI.create(trimmedUrl);
            return StringUtils.hasText(uri.getHost());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public void sendPasswordResetEmail(String toEmail, String customerName, String resetToken) {
        if (!StringUtils.hasText(toEmail)) {
            log.warn("[GMAIL BRIDGE SKIPPED] Missing recipient email for password reset dispatch");
            return;
        }

        String cleanBaseUrl = StringUtils.hasText(customerBaseUrl)
                ? customerBaseUrl.trim().replaceAll("/+$", "")
                : "http://localhost:4200";
        String cleanPath = StringUtils.hasText(passwordResetPath)
                ? "/" + passwordResetPath.trim().replaceAll("^/+", "")
                : "/reset-password";
        String encodedToken = URLEncoder.encode(resetToken != null ? resetToken : "", StandardCharsets.UTF_8);
        String resetLink = cleanBaseUrl + cleanPath + "?token=" + encodedToken;

        if (!isConfigured()) {
            log.warn("[EMAIL DEFERRED] Gmail Apps Script bridge is not configured (gmail-bridge.enabled={}). Password reset email deferred for recipient: {}",
                    enabled, maskEmail(toEmail));
            return;
        }

        String htmlContent = PasswordResetEmailTemplate.buildHtml(customerName, resetLink, expirationMinutes);
        String plainTextContent = PasswordResetEmailTemplate.buildPlainText(customerName, resetLink, expirationMinutes);

        boolean sent = sendViaWebhook(toEmail.trim(), PasswordResetEmailTemplate.SUBJECT, htmlContent, plainTextContent);
        if (!sent) {
            log.error("[GMAIL BRIDGE ERROR] Failed to dispatch password reset email to: {}", maskEmail(toEmail));
        }
    }

    @Override
    public boolean sendTransactionalEmail(String toEmail, String recipientName, String subject, String textContent) {
        log.warn("[GMAIL BRIDGE SKIPPED] Temporary Gmail bridge is scoped strictly to password reset emails. Ignored transactional subject '{}' for {}",
                subject, maskEmail(toEmail));
        return false;
    }

    private boolean sendViaWebhook(String toEmail, String subject, String htmlContent, String plainTextContent) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("secret", secret.trim());
            payload.put("to", toEmail);
            payload.put("subject", subject);
            payload.put("html", htmlContent);
            payload.put("text", plainTextContent);
            payload.put("fromName", StringUtils.hasText(fromName) ? fromName.trim() : "NOVA PETZLIFE");

            String requestBody = OBJECT_MAPPER.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl.trim()))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(8))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();

            if (statusCode < 200 || statusCode >= 300) {
                log.error("[GMAIL BRIDGE HTTP {}] Webhook returned non-2xx status for recipient {}",
                        statusCode, maskEmail(toEmail));
                return false;
            }

            return parseWebhookAcknowledgement(response.body(), toEmail);
        } catch (HttpConnectTimeoutException e) {
            log.error("[GMAIL BRIDGE TIMEOUT] Connection timeout contacting Apps Script webhook for recipient {}", maskEmail(toEmail));
            return false;
        } catch (HttpTimeoutException e) {
            log.error("[GMAIL BRIDGE TIMEOUT] Request timeout waiting for Apps Script webhook for recipient {}", maskEmail(toEmail));
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[GMAIL BRIDGE INTERRUPTED] Email dispatch interrupted for recipient {}", maskEmail(toEmail));
            return false;
        } catch (Exception e) {
            log.error("[GMAIL BRIDGE EXCEPTION] Error dispatching email to {}: {}", maskEmail(toEmail), e.getMessage());
            return false;
        }
    }

    private boolean parseWebhookAcknowledgement(String responseBody, String toEmail) {
        if (!StringUtils.hasText(responseBody)) {
            log.error("[GMAIL BRIDGE ERROR] Empty response body from Apps Script webhook for recipient {}", maskEmail(toEmail));
            return false;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(responseBody);
            if (root.path("ok").asBoolean(false)) {
                log.info("[GMAIL BRIDGE SUCCESS] Dispatched password reset email to: {}", maskEmail(toEmail));
                return true;
            }
            String error = root.path("error").asText("Unknown webhook error");
            log.error("[GMAIL BRIDGE REJECTED] Apps Script webhook rejected request for {}: {}", maskEmail(toEmail), error);
            return false;
        } catch (Exception e) {
            // Usually indicates Web App permissions were not set to "Anyone", causing an HTML Google Sign-In response.
            log.error("[GMAIL BRIDGE INVALID RESPONSE] Non-JSON response from Apps Script webhook for {} (verify Web App access is set to 'Anyone')",
                    maskEmail(toEmail));
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
