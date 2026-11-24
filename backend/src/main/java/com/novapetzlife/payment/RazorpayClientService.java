package com.novapetzlife.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Genuine integration service for Razorpay REST API (v1/orders and v1/payments/{id}/refund).
 * Uses Spring 6 / Boot 3.3 RestClient with HTTP Basic Auth, strict timeouts, and safe error handling.
 */
@Service
public class RazorpayClientService {

    private static final Logger log = LoggerFactory.getLogger(RazorpayClientService.class);
    private static final String DEFAULT_BASE_URL = "https://api.razorpay.com/v1";

    private final RazorpayConfig razorpayConfig;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public RazorpayClientService(RazorpayConfig razorpayConfig, ObjectMapper objectMapper) {
        this(razorpayConfig, objectMapper, createDefaultRestClient());
    }

    public RazorpayClientService(RazorpayConfig razorpayConfig, ObjectMapper objectMapper, RestClient restClient) {
        this.razorpayConfig = razorpayConfig;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
    }

    private static RestClient createDefaultRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(15).toMillis());

        return RestClient.builder()
                .baseUrl(DEFAULT_BASE_URL)
                .requestFactory(factory)
                .build();
    }

    public boolean isConfigured() {
        return StringUtils.hasText(razorpayConfig.getKeyId())
                && StringUtils.hasText(razorpayConfig.getKeySecret());
    }

    public record GatewayOrderResult(String gatewayOrderId, long amountInPaise, String currency) {}
    public record GatewayRefundResult(String gatewayRefundId, String status, long amountInPaise) {}

    /**
     * Creates a genuine Razorpay payment order via POST https://api.razorpay.com/v1/orders
     *
     * @param receipt Internal order receipt number
     * @param amountInPaise Total amount in paise
     * @param currency Currency code (e.g. INR)
     * @param notes Optional metadata notes
     * @return GatewayOrderResult containing genuine gateway order ID
     */
    public GatewayOrderResult createOrder(String receipt, long amountInPaise, String currency, Map<String, String> notes) {
        if (!isConfigured()) {
            log.warn("Razorpay order creation rejected: credentials unconfigured (keyId={})",
                    StringUtils.hasText(razorpayConfig.getKeyId()) ? "present" : "missing");
            throw new IllegalStateException("Payment gateway is currently unavailable. Please select Cash on Delivery or contact store support.");
        }

        if (amountInPaise <= 0) {
            throw new IllegalArgumentException("Payment amount in paise must be positive: " + amountInPaise);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("amount", amountInPaise);
        payload.put("currency", currency != null ? currency : "INR");
        payload.put("receipt", receipt);
        if (notes != null && !notes.isEmpty()) {
            payload.put("notes", notes);
        }

        String authHeader = buildBasicAuthHeader(razorpayConfig.getKeyId(), razorpayConfig.getKeySecret());

        try {
            log.info("Dispatching genuine Razorpay order creation for receipt: {}, amount: {} paise", receipt, amountInPaise);
            String responseBody = restClient.post()
                    .uri("/orders")
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, resp) -> {
                        String errBody = new String(resp.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        log.error("Razorpay order creation rejected by gateway: HTTP {} - {}", resp.getStatusCode(), errBody);
                        throw new IllegalStateException("Payment gateway rejected order creation. Status: " + resp.getStatusCode());
                    })
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            String orderId = root.path("id").asText(null);

            if (!StringUtils.hasText(orderId) || !orderId.startsWith("order_")) {
                log.error("Malformed gateway response: missing valid order ID. Response: {}", responseBody);
                throw new IllegalStateException("Payment gateway returned an invalid order reference");
            }

            log.info("Successfully created genuine Razorpay order {} for receipt {}", orderId, receipt);
            return new GatewayOrderResult(orderId, amountInPaise, currency);
        } catch (IllegalStateException ise) {
            throw ise;
        } catch (Exception ex) {
            log.error("Network or parsing failure during Razorpay order creation for receipt {}: {}", receipt, ex.getMessage());
            throw new IllegalStateException("Unable to communicate with payment gateway. Please try again in a few moments.", ex);
        }
    }

    /**
     * Issues a genuine gateway refund via POST https://api.razorpay.com/v1/payments/{paymentId}/refund
     *
     * @param gatewayPaymentId Original Razorpay payment ID (e.g. pay_123)
     * @param refundAmountInPaise Refund amount in paise
     * @param reason Refund reason
     * @return GatewayRefundResult with gateway refund ID and status
     */
    public GatewayRefundResult createRefund(String gatewayPaymentId, long refundAmountInPaise, String reason) {
        if (!isConfigured()) {
            log.warn("Razorpay refund API call skipped: credentials not configured. Falling back to internal manual refund.");
            throw new IllegalStateException("Payment gateway credentials not configured for automated refund.");
        }

        if (!StringUtils.hasText(gatewayPaymentId)) {
            throw new IllegalArgumentException("Gateway payment ID is required for Razorpay refund");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("amount", refundAmountInPaise);
        if (StringUtils.hasText(reason)) {
            payload.put("notes", Map.of("reason", reason));
        }

        String authHeader = buildBasicAuthHeader(razorpayConfig.getKeyId(), razorpayConfig.getKeySecret());

        try {
            log.info("Dispatching genuine Razorpay refund for payment: {}, amount: {} paise", gatewayPaymentId, refundAmountInPaise);
            String responseBody = restClient.post()
                    .uri("/payments/{paymentId}/refund", gatewayPaymentId)
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, resp) -> {
                        String errBody = new String(resp.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        log.error("Razorpay refund rejected by gateway: HTTP {} - {}", resp.getStatusCode(), errBody);
                        throw new IllegalStateException("Gateway rejected refund: " + resp.getStatusCode());
                    })
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            String refundId = root.path("id").asText(null);
            String status = root.path("status").asText("processed");

            log.info("Successfully created Razorpay refund {} for payment {} (status: {})", refundId, gatewayPaymentId, status);
            return new GatewayRefundResult(refundId, status, refundAmountInPaise);
        } catch (Exception ex) {
            log.error("Failed to process Razorpay refund for payment {}: {}", gatewayPaymentId, ex.getMessage());
            throw new IllegalStateException("Failed to communicate with payment gateway for refund: " + ex.getMessage(), ex);
        }
    }

    private String buildBasicAuthHeader(String keyId, String keySecret) {
        String auth = keyId + ":" + keySecret;
        return "Basic " + Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
    }
}
