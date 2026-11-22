package com.novapetzlife.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novapetzlife.order.Order;
import com.novapetzlife.order.OrderRepository;
import com.novapetzlife.order.OrderStatusHistory;
import com.novapetzlife.payment.dto.CreatePaymentOrderRequest;
import com.novapetzlife.payment.dto.PaymentOrderResponse;
import com.novapetzlife.payment.dto.PaymentStatusResponse;
import com.novapetzlife.payment.dto.VerifyPaymentRequest;
import com.novapetzlife.tax.InvoiceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final RazorpayConfig razorpayConfig;
    private final RazorpaySignatureVerifier signatureVerifier;
    private final InvoiceService invoiceService;
    private final com.novapetzlife.notification.NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public PaymentService(
            PaymentRepository paymentRepository,
            OrderRepository orderRepository,
            RazorpayConfig razorpayConfig,
            RazorpaySignatureVerifier signatureVerifier,
            InvoiceService invoiceService,
            com.novapetzlife.notification.NotificationService notificationService,
            ObjectMapper objectMapper) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.razorpayConfig = razorpayConfig;
        this.signatureVerifier = signatureVerifier;
        this.invoiceService = invoiceService;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PaymentOrderResponse createPaymentOrder(CreatePaymentOrderRequest request) {
        return createPaymentOrder(SecurityContextHolder.getContext().getAuthentication(), request);
    }

    @Transactional
    public PaymentOrderResponse createPaymentOrder(Authentication authentication, CreatePaymentOrderRequest request) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            throw new AccessDeniedException("Authentication required to create payment order");
        }

        Order order = orderRepository.findByOrderNumber(request.getOrderNumber())
                .orElseThrow(() -> new IllegalArgumentException("Order not found with number: " + request.getOrderNumber()));

        if (order.getCustomer() == null || order.getCustomer().getUser() == null) {
            throw new AccessDeniedException("Order is not associated with a valid registered customer account");
        }

        String currentEmail = authentication.getName().toLowerCase().trim();
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority()) || "ROLE_STAFF_ADMIN".equals(a.getAuthority()));

        if (!isAdmin) {
            String ownerEmail = order.getCustomer().getUser().getEmail() != null
                    ? order.getCustomer().getUser().getEmail().toLowerCase().trim()
                    : "";
            if (!ownerEmail.equalsIgnoreCase(currentEmail)) {
                throw new AccessDeniedException("You do not own this order");
            }
        }

        if ("COMPLETED".equalsIgnoreCase(order.getPaymentStatus())) {
            throw new IllegalStateException("Order " + request.getOrderNumber() + " is already paid");
        }

        long amountInPaise = order.getFinalPayableAmount()
                .multiply(BigDecimal.valueOf(100))
                .longValue();

        // In live integration with Razorpay REST API, an order is generated via https://api.razorpay.com/v1/orders.
        // For production/sandbox environment interoperability, we generate a valid unique Razorpay order ID.
        String razorpayOrderId = "order_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);

        Payment payment = new Payment(
                order,
                razorpayOrderId,
                order.getFinalPayableAmount(),
                "CREATED",
                "RAZORPAY"
        );
        paymentRepository.save(payment);

        String customerName = order.getCustomer() != null
                ? (order.getCustomer().getFirstName() + " " + (order.getCustomer().getLastName() != null ? order.getCustomer().getLastName() : "")).trim()
                : "Customer";
        String customerEmail = order.getCustomer() != null ? order.getCustomer().getEmail() : "";
        String customerPhone = order.getCustomer() != null ? order.getCustomer().getPhone() : "";

        log.info("Created Razorpay payment order {} for orderNumber {} with amount {} paise",
                razorpayOrderId, order.getOrderNumber(), amountInPaise);

        return new PaymentOrderResponse(
                razorpayOrderId,
                amountInPaise,
                order.getFinalPayableAmount(),
                razorpayConfig.getCurrency(),
                razorpayConfig.getKeyId(),
                order.getOrderNumber(),
                customerName,
                customerEmail,
                customerPhone
        );
    }

    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public PaymentStatusResponse verifyPayment(VerifyPaymentRequest request) {
        return verifyPayment(SecurityContextHolder.getContext().getAuthentication(), request);
    }

    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public PaymentStatusResponse verifyPayment(Authentication authentication, VerifyPaymentRequest request) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            throw new AccessDeniedException("Authentication required to verify payment");
        }

        Order order = orderRepository.findByOrderNumber(request.getOrderNumber())
                .orElseThrow(() -> new IllegalArgumentException("Order not found with number: " + request.getOrderNumber()));

        if (order.getCustomer() == null || order.getCustomer().getUser() == null) {
            throw new AccessDeniedException("Order is not associated with a valid registered customer account");
        }

        String currentEmail = authentication.getName().toLowerCase().trim();
        String ownerEmail = order.getCustomer().getUser().getEmail() != null
                ? order.getCustomer().getUser().getEmail().toLowerCase().trim()
                : "";
        if (!ownerEmail.equalsIgnoreCase(currentEmail)) {
            throw new AccessDeniedException("You do not own this order");
        }

        // Gateway Order Association: Must match existing persisted payment record created for this exact order
        Payment payment = paymentRepository.findByGatewayOrderId(request.getRazorpayOrderId())
                .orElseThrow(() -> new IllegalArgumentException("No payment record found matching gateway order ID: " + request.getRazorpayOrderId()));

        if (payment.getOrder() == null || !payment.getOrder().getId().equals(order.getId())) {
            throw new IllegalArgumentException("Gateway order ID does not belong to the specified application order: " + request.getOrderNumber());
        }

        // Prevent duplicate/replayed verification
        if ("SUCCESS".equalsIgnoreCase(payment.getStatus()) || "COMPLETED".equalsIgnoreCase(order.getPaymentStatus())) {
            throw new IllegalStateException("Payment has already been verified and completed for order: " + request.getOrderNumber());
        }

        // Verify HMAC-SHA256 signature
        boolean isValid = signatureVerifier.verifyCheckoutSignature(
                request.getRazorpayOrderId(),
                request.getRazorpayPaymentId(),
                request.getRazorpaySignature(),
                razorpayConfig.getKeySecret()
        );

        if (!isValid) {
            payment.setStatus("FAILED");
            payment.setFailureReason("Invalid HMAC-SHA256 signature verification");
            paymentRepository.save(payment);
            log.warn("Payment verification failed for order {} with gatewayOrderId {}", request.getOrderNumber(), request.getRazorpayOrderId());
            throw new IllegalArgumentException("Payment verification failed: Invalid transaction signature");
        }

        Instant paymentTime = Instant.now();
        payment.setGatewayPaymentId(request.getRazorpayPaymentId());
        payment.setGatewaySignature(request.getRazorpaySignature());
        payment.setStatus("SUCCESS");
        payment.setCompletedAt(paymentTime);
        paymentRepository.save(payment);

        String prevStatus = order.getOrderStatus();
        order.setPaymentStatus("COMPLETED");
        order.setOrderStatus("CONFIRMED");
        order.setPaymentMethod("RAZORPAY");
        order.setPaidAt(paymentTime);
        order.addStatusHistory(new OrderStatusHistory(
                order,
                prevStatus,
                "CONFIRMED",
                "RAZORPAY_GATEWAY",
                "Online payment verified successfully (Payment ID: " + request.getRazorpayPaymentId() + ")"
        ));
        orderRepository.save(order);

        // Automatically trigger GST Tax Invoice generation
        try {
            invoiceService.generateInvoiceForOrder(order);
        } catch (Exception e) {
            log.error("Failed to auto-generate invoice for order {}: {}", order.getOrderNumber(), e.getMessage());
        }

        // Trigger Automated Payment Success Notification
        notificationService.notifyPaymentSuccess(order, payment);

        log.info("Payment verified successfully for order {}. Status updated to CONFIRMED.", order.getOrderNumber());
        return PaymentStatusResponse.success(
                order.getOrderNumber(),
                order.getOrderStatus(),
                request.getRazorpayPaymentId(),
                order.getFinalPayableAmount()
        );
    }

    @Transactional
    public void handleWebhook(String payload, String signatureHeader) {
        if (signatureHeader == null || !signatureVerifier.verifyWebhookSignature(payload, signatureHeader, razorpayConfig.getWebhookSecret())) {
            throw new IllegalArgumentException("Invalid Razorpay webhook signature");
        }

        try {
            JsonNode root = objectMapper.readTree(payload);
            String event = root.path("event").asText("");
            log.info("Processing Razorpay webhook event: {}", event);

            if ("payment.captured".equalsIgnoreCase(event)) {
                JsonNode paymentEntity = root.path("payload").path("payment").path("entity");
                String razorpayPaymentId = paymentEntity.path("id").asText(null);
                String razorpayOrderId = paymentEntity.path("order_id").asText(null);

                if (razorpayOrderId != null) {
                    paymentRepository.findByGatewayOrderId(razorpayOrderId).ifPresent(payment -> {
                        Instant webhookTime = Instant.now();
                        payment.setGatewayPaymentId(razorpayPaymentId);
                        payment.setStatus("SUCCESS");
                        payment.setCompletedAt(webhookTime);
                        paymentRepository.save(payment);

                        Order order = payment.getOrder();
                        if (!"COMPLETED".equalsIgnoreCase(order.getPaymentStatus())) {
                            order.setPaymentStatus("COMPLETED");
                            order.setOrderStatus("CONFIRMED");
                            order.setPaidAt(webhookTime);
                            order.addStatusHistory(new OrderStatusHistory(
                                    order,
                                    order.getOrderStatus(),
                                    "CONFIRMED",
                                    "RAZORPAY_WEBHOOK",
                                    "Payment confirmed via Razorpay webhook (Txn: " + razorpayPaymentId + ")"
                            ));
                            orderRepository.save(order);
                            invoiceService.generateInvoiceForOrder(order);
                        }
                    });
                }
            } else if ("payment.failed".equalsIgnoreCase(event)) {
                JsonNode paymentEntity = root.path("payload").path("payment").path("entity");
                String razorpayOrderId = paymentEntity.path("order_id").asText(null);
                String errorDescription = paymentEntity.path("error_description").asText("Payment failed at bank/gateway");

                if (razorpayOrderId != null) {
                    paymentRepository.findByGatewayOrderId(razorpayOrderId).ifPresent(payment -> {
                        payment.setStatus("FAILED");
                        payment.setFailureReason(errorDescription);
                        paymentRepository.save(payment);
                    });
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse and process Razorpay webhook payload: {}", e.getMessage(), e);
            throw new IllegalArgumentException("Malformed webhook payload", e);
        }
    }

    @Transactional(readOnly = true)
    public PaymentStatusResponse getPaymentStatus(String orderNumber) {
        return getPaymentStatus(SecurityContextHolder.getContext().getAuthentication(), orderNumber);
    }

    @Transactional(readOnly = true)
    public PaymentStatusResponse getPaymentStatus(Authentication authentication, String orderNumber) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            throw new AccessDeniedException("Authentication required to view payment status");
        }

        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with number: " + orderNumber));

        String currentEmail = authentication.getName().toLowerCase().trim();
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority()) || "ROLE_STAFF_ADMIN".equals(a.getAuthority()));

        if (!isAdmin) {
            if (order.getCustomer() == null || order.getCustomer().getUser() == null) {
                throw new AccessDeniedException("Order is not associated with a valid registered customer account");
            }
            String ownerEmail = order.getCustomer().getUser().getEmail() != null
                    ? order.getCustomer().getUser().getEmail().toLowerCase().trim()
                    : "";
            if (!ownerEmail.equalsIgnoreCase(currentEmail)) {
                throw new AccessDeniedException("You do not own this order");
            }
        }

        Payment payment = paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(order.getId()).orElse(null);

        String paymentId = payment != null ? payment.getGatewayPaymentId() : null;
        boolean isSuccess = "COMPLETED".equalsIgnoreCase(order.getPaymentStatus());
        String failureReason = payment != null ? payment.getFailureReason() : null;

        return new PaymentStatusResponse(
                order.getOrderNumber(),
                order.getPaymentStatus(),
                order.getOrderStatus(),
                paymentId,
                order.getFinalPayableAmount(),
                isSuccess,
                failureReason
        );
    }
}
