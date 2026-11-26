package com.novapetzlife.email;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the temporary Google Apps Script Gmail Bridge
 * used exclusively for customer password reset emails on hosts that restrict SMTP ports.
 */
@Component
@ConfigurationProperties(prefix = "gmail-bridge")
public class GmailBridgeProperties {

    private boolean enabled = false;
    private String webhookUrl = "";
    private String secret = "";
    private String fromName = "NOVA PETZLIFE";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getFromName() {
        return fromName;
    }

    public void setFromName(String fromName) {
        this.fromName = fromName;
    }
}
