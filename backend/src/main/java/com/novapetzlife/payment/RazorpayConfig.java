package com.novapetzlife.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RazorpayConfig {

    @Value("${razorpay.key-id:rzp_test_nova_petzlife_sample}")
    private String keyId;

    @Value("${razorpay.key-secret:nova_test_secret_key_12345}")
    private String keySecret;

    @Value("${razorpay.webhook-secret:nova_test_webhook_secret_67890}")
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
