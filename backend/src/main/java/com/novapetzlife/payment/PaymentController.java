package com.novapetzlife.payment;

import com.novapetzlife.common.ApiResponse;
import com.novapetzlife.payment.dto.CreatePaymentOrderRequest;
import com.novapetzlife.payment.dto.PaymentOrderResponse;
import com.novapetzlife.payment.dto.PaymentStatusResponse;
import com.novapetzlife.payment.dto.VerifyPaymentRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments & Gateway", description = "Razorpay checkout order creation, HMAC-SHA256 signature verification, and webhook event processing")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/create-order")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Create Razorpay payment order", description = "Generates order reference for Razorpay Standard Checkout in paise")
    public ResponseEntity<ApiResponse<PaymentOrderResponse>> createPaymentOrder(
            Authentication authentication,
            @Valid @RequestBody CreatePaymentOrderRequest request) {
        PaymentOrderResponse response = paymentService.createPaymentOrder(authentication, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/verify")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Verify Razorpay payment signature", description = "Validates HMAC-SHA256 signature from Razorpay modal callback. Confirms order and triggers GST tax invoice generation.")
    public ResponseEntity<ApiResponse<PaymentStatusResponse>> verifyPayment(
            Authentication authentication,
            @Valid @RequestBody VerifyPaymentRequest request) {
        PaymentStatusResponse response = paymentService.verifyPayment(authentication, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/webhook")
    @Operation(summary = "Razorpay Webhook receiver", description = "Processes asynchronous payment events (payment.captured, payment.failed) with HMAC-SHA256 signature check")
    public ResponseEntity<Map<String, Object>> handleWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signatureHeader) {
        paymentService.handleWebhook(payload, signatureHeader);
        return ResponseEntity.ok(Map.of("status", "ok", "message", "Webhook event processed"));
    }

    @GetMapping("/order/{orderNumber}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get payment status for order", description = "Retrieves latest payment gateway status and transaction ID for an order")
    public ResponseEntity<ApiResponse<PaymentStatusResponse>> getPaymentStatus(
            Authentication authentication,
            @PathVariable String orderNumber) {
        PaymentStatusResponse response = paymentService.getPaymentStatus(authentication, orderNumber);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
