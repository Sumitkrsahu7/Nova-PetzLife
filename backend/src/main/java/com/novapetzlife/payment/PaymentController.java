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
    @Operation(summary = "Create Razorpay payment order", description = "Generates order reference for Razorpay Standard Checkout in paise")
    public ResponseEntity<ApiResponse<PaymentOrderResponse>> createPaymentOrder(
            @Valid @RequestBody CreatePaymentOrderRequest request) {
        PaymentOrderResponse response = paymentService.createPaymentOrder(request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/verify")
    @Operation(summary = "Verify Razorpay payment signature", description = "Validates HMAC-SHA256 signature from Razorpay modal callback. Confirms order and triggers GST tax invoice generation.")
    public ResponseEntity<ApiResponse<PaymentStatusResponse>> verifyPayment(
            @Valid @RequestBody VerifyPaymentRequest request) {
        PaymentStatusResponse response = paymentService.verifyPayment(request);
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
    @Operation(summary = "Get payment status for order", description = "Retrieves latest payment gateway status and transaction ID for an order")
    public ResponseEntity<ApiResponse<PaymentStatusResponse>> getPaymentStatus(
            @PathVariable String orderNumber) {
        PaymentStatusResponse response = paymentService.getPaymentStatus(orderNumber);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
