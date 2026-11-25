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

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    public record GatewayPaymentRefundItem(String refundId, long amountInPaise, String status, String receipt, String currency) {
        public GatewayPaymentRefundItem(String refundId, long amountInPaise, String status, String receipt) {
            this(refundId, amountInPaise, status, receipt, "INR");
        }
    }

    public static class GatewayConflictException extends IllegalStateException {
        public GatewayConflictException(String message) {
            super(message);
        }
    }

    public static class GatewayReconciliationException extends RuntimeException {
        public GatewayReconciliationException(String message) {
            super(message);
        }
        public GatewayReconciliationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

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
        return createRefund(gatewayPaymentId, refundAmountInPaise, reason, null);
    }

    /**
     * Issues a genuine gateway refund via POST https://api.razorpay.com/v1/payments/{paymentId}/refund
     *
     * @param gatewayPaymentId Original Razorpay payment ID (e.g. pay_123)
     * @param refundAmountInPaise Refund amount in paise
     * @param reason Refund reason
     * @param receipt Merchant tracking reference field (max 40 characters).
     * @return GatewayRefundResult with gateway refund ID and status
     */
    public GatewayRefundResult createRefund(String gatewayPaymentId, long refundAmountInPaise, String reason, String receipt) {
        return createRefund(gatewayPaymentId, refundAmountInPaise, reason, receipt, null);
    }

    /**
     * Issues a genuine gateway refund via POST https://api.razorpay.com/v1/payments/{paymentId}/refund
     * using the documented Razorpay X-Refund-Idempotency header to prevent duplicate refunds.
     *
     * @param gatewayPaymentId Original Razorpay payment ID (e.g. pay_123)
     * @param refundAmountInPaise Refund amount in paise
     * @param reason Refund reason
     * @param receipt Merchant tracking reference field (max 40 characters)
     * @param idempotencyKey Stable unique idempotency key persisted per refund intent
     * @return GatewayRefundResult with gateway refund ID and status
     */
    public GatewayRefundResult createRefund(String gatewayPaymentId, long refundAmountInPaise, String reason, String receipt, String idempotencyKey) {
        if (!isConfigured()) {
            log.warn("Razorpay refund API call skipped: credentials not configured. Falling back to internal manual refund.");
            throw new IllegalStateException("Payment gateway credentials not configured for automated refund.");
        }

        if (!StringUtils.hasText(gatewayPaymentId)) {
            throw new IllegalArgumentException("Gateway payment ID is required for Razorpay refund");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("amount", refundAmountInPaise);
        if (StringUtils.hasText(receipt)) {
            payload.put("receipt", receipt);
        }
        if (StringUtils.hasText(reason)) {
            payload.put("notes", Map.of("reason", reason));
        }

        String authHeader = buildBasicAuthHeader(razorpayConfig.getKeyId(), razorpayConfig.getKeySecret());

        try {
            log.info("Dispatching genuine Razorpay refund for payment: {}, amount: {} paise, receipt: {}, idempotencyKey: {}",
                    gatewayPaymentId, refundAmountInPaise, receipt, idempotencyKey);
            var requestSpec = restClient.post()
                    .uri("/payments/{paymentId}/refund", gatewayPaymentId)
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .contentType(MediaType.APPLICATION_JSON);

            if (StringUtils.hasText(idempotencyKey)) {
                requestSpec.header("X-Refund-Idempotency", idempotencyKey);
            }

            String responseBody = requestSpec
                    .body(payload)
                    .retrieve()
                    .onStatus(status -> status.value() == 409, (req, resp) -> {
                        String errBody = new String(resp.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        log.warn("Razorpay refund returned HTTP 409 Conflict for idempotency key {}: {}", idempotencyKey, errBody);
                        throw new GatewayConflictException("Gateway returned 409 Conflict: Refund request already in progress or conflicting idempotency key");
                    })
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
        } catch (GatewayConflictException gce) {
            throw gce;
        } catch (IllegalStateException ise) {
            throw ise;
        } catch (Exception ex) {
            log.error("Failed to process Razorpay refund for payment {}: {}", gatewayPaymentId, ex.getMessage());
            throw new IllegalStateException("Failed to communicate with payment gateway for refund: " + ex.getMessage(), ex);
        }
    }

    private String buildBasicAuthHeader(String keyId, String keySecret) {
        String auth = keyId + ":" + keySecret;
        return "Basic " + Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
    }

    public static final int MAX_SAFETY_RECORDS = 1000;
    public static final int RECONCILIATION_PAGE_SIZE = 50;

    /**
     * Reconciles payment refunds from Razorpay REST API with complete pagination support:
     * GET https://api.razorpay.com/v1/payments/{paymentId}/refunds?count={count}&skip={skip}
     * Used by crash recovery and durable dispatch sweeper to prevent duplicate refunds.
     * Enforces:
     * - Exhaustive pagination until the gateway indicates no more records
     * - Non-progressing / repeated page loop detection
     * - Operational safety limit (1000 records) to prevent unbounded processing
     * - Returns unresolved exception if safety limit is reached, NEVER interpreting incomplete pagination as absence.
     */
    public java.util.List<GatewayPaymentRefundItem> fetchRefundsForPayment(String gatewayPaymentId) {
        if (!isConfigured() || !StringUtils.hasText(gatewayPaymentId)) {
            return java.util.List.of();
        }

        String authHeader = buildBasicAuthHeader(razorpayConfig.getKeyId(), razorpayConfig.getKeySecret());
        int pageSize = RECONCILIATION_PAGE_SIZE;
        int skip = 0;
        java.util.List<GatewayPaymentRefundItem> allItems = new java.util.ArrayList<>();
        java.util.Set<String> seenRefundIds = new java.util.HashSet<>();

        while (true) {
            final int currentSkip = skip;
            try {
                log.info("Querying gateway refunds for payment: {} (count: {}, skip: {})", gatewayPaymentId, pageSize, currentSkip);
                String responseBody = restClient.get()
                        .uri("/payments/{paymentId}/refunds?count={count}&skip={skip}", gatewayPaymentId, pageSize, currentSkip)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (req, resp) -> {
                            String errBody = new String(resp.getBody().readAllBytes(), StandardCharsets.UTF_8);
                            log.warn("Razorpay query refunds returned HTTP {} on skip {}: {}", resp.getStatusCode(), currentSkip, errBody);
                            throw new GatewayReconciliationException("Gateway error querying refunds: HTTP " + resp.getStatusCode());
                        })
                        .body(String.class);

                if (!StringUtils.hasText(responseBody)) {
                    break;
                }

                JsonNode root = objectMapper.readTree(responseBody);
                JsonNode itemsNode = root.path("items");
                if (!itemsNode.isArray() || itemsNode.isEmpty()) {
                    break;
                }

                int pageCount = 0;
                int newItemsOnPage = 0;
                for (JsonNode item : itemsNode) {
                    pageCount++;
                    if (!item.isObject()) {
                        log.error("Malformed gateway refund record: item is not a JSON object for payment {}", gatewayPaymentId);
                        throw new GatewayReconciliationException("Malformed gateway refund record (non-object) for payment: " + gatewayPaymentId);
                    }

                    String rId = item.path("id").asText(null);
                    if (!StringUtils.hasText(rId) || rId.trim().isEmpty()) {
                        log.error("Malformed gateway refund record: missing or blank refund ID for payment {}", gatewayPaymentId);
                        throw new GatewayReconciliationException("Malformed gateway refund record: missing or blank refund ID for payment: " + gatewayPaymentId);
                    }

                    JsonNode amountNode = item.get("amount");
                    long amountInPaise = parseGatewayAmountInPaise(amountNode, rId, gatewayPaymentId);

                    JsonNode statusNode = item.get("status");
                    String status = (statusNode != null && !statusNode.isNull()) ? statusNode.asText(null) : null;
                    if (!StringUtils.hasText(status) || status.trim().isEmpty()) {
                        log.error("Malformed gateway refund record: missing or blank status for refund {} (payment {})", rId, gatewayPaymentId);
                        throw new GatewayReconciliationException("Malformed gateway refund record with missing status for refund: " + rId);
                    }

                    JsonNode currencyNode = item.get("currency");
                    String currency = (currencyNode != null && !currencyNode.isNull()) ? currencyNode.asText(null) : null;
                    if (!StringUtils.hasText(currency) || currency.trim().isEmpty()) {
                        log.error("Malformed gateway refund record: missing currency for refund {} (payment {})", rId, gatewayPaymentId);
                        throw new GatewayReconciliationException("Malformed gateway refund record with missing currency for refund: " + rId);
                    }

                    if (!seenRefundIds.add(rId)) {
                        log.warn("Duplicate refund ID {} encountered during pagination for payment {}. Skipping appending to result list.", rId, gatewayPaymentId);
                        continue;
                    }
                    newItemsOnPage++;
                    allItems.add(new GatewayPaymentRefundItem(
                            rId,
                            amountInPaise,
                            status,
                            item.path("receipt").asText(null),
                            currency
                    ));
                }

                // Detect repeated page or non-progressing cursor loop:
                // If page had items (pageCount > 0) but NONE of them were new (newItemsOnPage == 0),
                // the pagination cursor has looped/stalled. Abort reconciliation fail-closed.
                if (newItemsOnPage == 0 && pageCount > 0) {
                    log.error("Non-progressing pagination loop detected for payment {} at skip {}. Aborting reconciliation to prevent duplicate disbursement.",
                            gatewayPaymentId, currentSkip);
                    throw new GatewayReconciliationException("Non-progressing pagination loop detected for payment: " + gatewayPaymentId);
                }

                skip += pageCount;

                // If fewer items returned than requested page size, all items have been exhausted
                if (pageCount < pageSize) {
                    break;
                }

                // Enforce operational safety limit: if safety cap reached before exhaustion, fail-safe abort
                if (skip >= MAX_SAFETY_RECORDS) {
                    log.error("Operational safety limit of {} records reached during refund reconciliation for payment {}. Pagination incomplete. Aborting to prevent duplicate disbursement.",
                            MAX_SAFETY_RECORDS, gatewayPaymentId);
                    throw new GatewayReconciliationException("Reconciliation pagination exceeded operational safety limit of " + MAX_SAFETY_RECORDS + " records; reconciliation incomplete.");
                }

            } catch (GatewayReconciliationException gre) {
                throw gre;
            } catch (Exception ex) {
                log.warn("Failed to fetch gateway refunds for payment {} at skip {}: {}", gatewayPaymentId, currentSkip, ex.getMessage());
                throw new GatewayReconciliationException("Failed to communicate with payment gateway for reconciliation: " + ex.getMessage(), ex);
            }
        }
        return allItems;
    }

    /**
     * Parses and strictly validates a gateway refund amount in paise.
     * Enforces:
     * - Rejection of null, missing, or non-numeric JSON types (string, boolean, array, object)
     * - Rejection of zero or negative numbers
     * - Rejection of fractional amounts (e.g. 100.5, 100.9) without silent rounding/truncation
     * - Rejection of values exceeding 64-bit signed long range (overflow)
     * - Acceptance of mathematically integral positive values (e.g. 1000, 100.0, 100.00)
     */
    public static long parseGatewayAmountInPaise(JsonNode amountNode, String refundId, String paymentId) {
        if (amountNode == null || amountNode.isNull() || !amountNode.isNumber()) {
            log.error("Malformed gateway refund record: missing, null, or non-numeric amount for refund {} (payment {})", refundId, paymentId);
            throw new GatewayReconciliationException("Malformed gateway refund record: missing, null, or non-numeric amount for refund "
                    + refundId + " (payment " + paymentId + ")");
        }

        BigDecimal decimal;
        try {
            decimal = amountNode.decimalValue();
        } catch (Exception ex) {
            log.error("Malformed gateway refund record: unparseable numeric amount for refund {} (payment {})", refundId, paymentId);
            throw new GatewayReconciliationException("Malformed gateway refund record: unparseable numeric amount for refund "
                    + refundId + " (payment " + paymentId + ")");
        }

        if (decimal == null || decimal.compareTo(BigDecimal.ZERO) <= 0) {
            log.error("Malformed gateway refund record: non-positive or invalid amount ({}) for refund {} (payment {})", decimal, refundId, paymentId);
            throw new GatewayReconciliationException("Malformed gateway refund record: non-positive or invalid amount (" + decimal
                    + ") for refund " + refundId + " (payment " + paymentId + ")");
        }

        if (decimal.stripTrailingZeros().scale() > 0) {
            log.error("Malformed gateway refund record: fractional amount ({}) rejected for refund {} (payment {})", decimal, refundId, paymentId);
            throw new GatewayReconciliationException("Malformed gateway refund record: fractional amount (" + decimal
                    + ") rejected for refund " + refundId + " (payment " + paymentId + ")");
        }

        try {
            return decimal.setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        } catch (ArithmeticException ex) {
            log.error("Malformed gateway refund record: amount outside 64-bit long range ({}) for refund {} (payment {})", decimal, refundId, paymentId);
            throw new GatewayReconciliationException("Malformed gateway refund record: amount outside 64-bit long range (" + decimal
                    + ") for refund " + refundId + " (payment " + paymentId + ")");
        }
    }
}
