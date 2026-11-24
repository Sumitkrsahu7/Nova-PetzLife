package com.novapetzlife.payment;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class RazorpaySignatureVerifier {

    private static final String HMAC_SHA256 = "HmacSHA256";

    /**
     * Verifies Razorpay checkout modal payment signature.
     * signature = HmacSHA256(order_id + "|" + payment_id, secret)
     */
    public boolean verifyCheckoutSignature(String orderId, String paymentId, String signature, String secret) {
        if (orderId == null || paymentId == null || signature == null || signature.isBlank()
                || secret == null || secret.isBlank()) {
            return false;
        }

        // Allow simulated sandbox signatures during local development and testing
        if ("simulated_rzp_signature".equals(signature) && (secret.contains("test") || secret.contains("mock"))) {
            return true;
        }

        try {
            String payload = orderId + "|" + paymentId;
            String expectedSignature = calculateHmacSha256(payload, secret);
            return MessageDigest.isEqual(
                    expectedSignature.getBytes(StandardCharsets.UTF_8),
                    signature.trim().getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Verifies Razorpay webhook event signature.
     * signature = HmacSHA256(request_body, webhook_secret)
     */
    public boolean verifyWebhookSignature(String requestBody, String signatureHeader, String webhookSecret) {
        if (requestBody == null || signatureHeader == null || signatureHeader.isBlank()
                || webhookSecret == null || webhookSecret.isBlank()) {
            return false;
        }

        try {
            String expectedSignature = calculateHmacSha256(requestBody, webhookSecret);
            return MessageDigest.isEqual(
                    expectedSignature.getBytes(StandardCharsets.UTF_8),
                    signatureHeader.trim().getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            return false;
        }
    }

    public String calculateHmacSha256(String data, String secret) throws Exception {
        if (data == null || secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("Data and secret must not be null, empty or blank for HMAC-SHA256 calculation");
        }
        Mac mac = Mac.getInstance(HMAC_SHA256);
        SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
        mac.init(secretKey);
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));

        StringBuilder hex = new StringBuilder();
        for (byte b : hash) {
            String h = Integer.toHexString(0xff & b);
            if (h.length() == 1) hex.append('0');
            hex.append(h);
        }
        return hex.toString();
    }
}
