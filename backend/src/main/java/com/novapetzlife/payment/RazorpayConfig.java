package com.novapetzlife.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RazorpayConfig {

    @Value("${razorpay.key-id:${RAZORPAY_KEY_ID:}}")
    private String keyId;

    @Value("${razorpay.key-secret:${RAZORPAY_KEY_SECRET:}}")
    private String keySecret;

    @Value("${razorpay.webhook-secret:${RAZORPAY_WEBHOOK_SECRET:}}")
    private String webhookSecret;

    @Value("${razorpay.currency:INR}")
    private String currency;

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public String getKeySecret() {
        return keySecret;
    }

    public void setKeySecret(String keySecret) {
        this.keySecret = keySecret;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}
