package com.novapetzlife.email;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Resend email configuration properties.
 */
@Component
@ConfigurationProperties(prefix = "resend")
public class ResendEmailProperties {

    private boolean enabled = false;
    private String apiKey = "";
    private String baseUrl = "https://api.resend.com";
    private String fromEmail = "";
    private String fromName = "NOVA PETZLIFE";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getFromEmail() {
        return fromEmail;
    }

    public void setFromEmail(String fromEmail) {
        this.fromEmail = fromEmail;
    }

    public String getFromName() {
        return fromName;
    }

    public void setFromName(String fromName) {
        this.fromName = fromName;
    }
}
