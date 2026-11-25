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
    private final RazorpayClientService razorpayClientService;
    private final InvoiceService invoiceService;
    private final com.novapetzlife.notification.NotificationService notificationService;
    private final com.novapetzlife.inventory.InventoryService inventoryService;
    private final ObjectMapper objectMapper;
    private final com.novapetzlife.refund.RefundService refundService;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    public PaymentService(
            PaymentRepository paymentRepository,
            OrderRepository orderRepository,
            RazorpayConfig razorpayConfig,
            RazorpaySignatureVerifier signatureVerifier,
            RazorpayClientService razorpayClientService,
            InvoiceService invoiceService,
            com.novapetzlife.notification.NotificationService notificationService,
            com.novapetzlife.inventory.InventoryService inventoryService,
            ObjectMapper objectMapper) {
        this(paymentRepository, orderRepository, razorpayConfig, signatureVerifier, razorpayClientService,
                invoiceService, notificationService, inventoryService, objectMapper, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public PaymentService(
            PaymentRepository paymentRepository,
            OrderRepository orderRepository,
            RazorpayConfig razorpayConfig,
            RazorpaySignatureVerifier signatureVerifier,
            RazorpayClientService razorpayClientService,
            InvoiceService invoiceService,
            com.novapetzlife.notification.NotificationService notificationService,
            com.novapetzlife.inventory.InventoryService inventoryService,
            ObjectMapper objectMapper,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.novapetzlife.refund.RefundService refundService) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.razorpayConfig = razorpayConfig;
        this.signatureVerifier = signatureVerifier;
        this.razorpayClientService = razorpayClientService;
        this.invoiceService = invoiceService;
        this.notificationService = notificationService;
        this.inventoryService = inventoryService;
        this.objectMapper = objectMapper;
        this.refundService = refundService;
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

        boolean hasSuccessPayment = paymentRepository.findByOrderId(order.getId()).stream()
                .anyMatch(p -> "SUCCESS".equalsIgnoreCase(p.getStatus()));
        if (hasSuccessPayment) {
            throw new IllegalStateException("Order " + request.getOrderNumber() + " already has a verified successful payment record");
        }

        if ("CANCELLED".equalsIgnoreCase(order.getOrderStatus())) {
            throw new IllegalStateException("Order " + request.getOrderNumber() + " is cancelled and ineligible for payment.");
        }

        if ("DELIVERED".equalsIgnoreCase(order.getOrderStatus())) {
            throw new IllegalStateException("Order " + request.getOrderNumber() + " is already delivered.");
        }

        BigDecimal finalPayable = order.getFinalPayableAmount();
        if (finalPayable == null || finalPayable.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Order final payable amount must be positive for online payment gateway: " + finalPayable);
        }

        // Strict financial precision: Reject amounts with fractional paise (more than 2 decimal places)
        if (finalPayable.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Order final payable amount cannot have fractional paise: " + finalPayable);
        }

        long amountInPaise;
        try {
            // Exact arithmetic: movePointRight(2) without rounding, setScale UNNECESSARY to ensure zero fractional paise,
            // and longValueExact() to detect integer overflow
            amountInPaise = finalPayable.movePointRight(2)
                    .setScale(0, java.math.RoundingMode.UNNECESSARY)
                    .longValueExact();
        } catch (ArithmeticException ae) {
            throw new IllegalArgumentException("Invalid payment amount precision or arithmetic overflow: " + finalPayable, ae);
        }

        if (amountInPaise <= 0) {
            throw new IllegalArgumentException("Converted payment amount in paise must be positive: " + amountInPaise);
        }

        String customerName = order.getCustomer() != null
                ? (order.getCustomer().getFirstName() + " " + (order.getCustomer().getLastName() != null ? order.getCustomer().getLastName() : "")).trim()
                : "Customer";
        String customerEmail = order.getCustomer() != null ? order.getCustomer().getEmail() : "";
        String customerPhone = order.getCustomer() != null ? order.getCustomer().getPhone() : "";

        String expectedCurrency = razorpayConfig.getCurrency() != null ? razorpayConfig.getCurrency() : "INR";

        // Idempotency: Reuse existing CREATED payment order ONLY if:
        // 1. Belongs to the same local order
        // 2. Status is strictly CREATED (not SUCCESS, FAILED, REFUNDED, or CANCELLED)
        // 3. Gateway order ID is non-blank and starts with "order_"
        // 4. Amount matches authoritative final payable amount exactly
        // 5. Currency matches configured gateway currency
        // 6. Record was created within the last 24 hours (within gateway TTL)
        Payment payment = paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(order.getId())
                .filter(p -> "CREATED".equalsIgnoreCase(p.getStatus())
                        && p.getOrder() != null
                        && p.getOrder().getId().equals(order.getId())
                        && org.springframework.util.StringUtils.hasText(p.getGatewayOrderId())
                        && p.getGatewayOrderId().startsWith("order_")
                        && p.getAmount() != null
                        && p.getAmount().compareTo(order.getFinalPayableAmount()) == 0
                        && (p.getCurrency() == null || expectedCurrency.equalsIgnoreCase(p.getCurrency()))
                        && p.getCreatedAt() != null
                        && p.getCreatedAt().isAfter(Instant.now().minus(java.time.Duration.ofHours(24))))
                .orElse(null);

        String razorpayOrderId;
        if (payment != null) {
            razorpayOrderId = payment.getGatewayOrderId();
            log.info("Reusing existing valid CREATED Razorpay payment order {} for orderNumber {}", razorpayOrderId, order.getOrderNumber());
        } else {
            // Supersede ONLY prior payments that are in CREATED status (never touch SUCCESS, FAILED, REFUNDED, or PARTIALLY_REFUNDED)
            paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(order.getId()).ifPresent(oldPayment -> {
                if ("CREATED".equalsIgnoreCase(oldPayment.getStatus())) {
                    oldPayment.setStatus("EXPIRED");
                    oldPayment.setFailureReason("Superseded by fresh payment order creation (amount mismatch, stale TTL, or replaced)");
                    paymentRepository.save(oldPayment);
                    log.info("Superseded stale/mismatched CREATED payment record {} for order {}", oldPayment.getId(), order.getOrderNumber());
                }
            });

            java.util.Map<String, String> notes = new java.util.HashMap<>();
            notes.put("orderNumber", order.getOrderNumber());
            if (!customerEmail.isBlank()) {
                notes.put("customerEmail", customerEmail);
            }

            RazorpayClientService.GatewayOrderResult gatewayResult = razorpayClientService.createOrder(
                    order.getOrderNumber(),
                    amountInPaise,
                    expectedCurrency,
                    notes
            );
            razorpayOrderId = gatewayResult.gatewayOrderId();

            payment = new Payment(
                    order,
                    razorpayOrderId,
                    order.getFinalPayableAmount(),
                    "CREATED",
                    "RAZORPAY"
            );
            payment.setCurrency(expectedCurrency);
            paymentRepository.save(payment);

            log.info("Created genuine Razorpay payment order {} for orderNumber {} with amount {} paise",
                    razorpayOrderId, order.getOrderNumber(), amountInPaise);
        }

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

        Order order = orderRepository.findByOrderNumberWithLock(request.getOrderNumber())
                .orElseThrow(() -> new IllegalArgumentException("Order not found with number: " + request.getOrderNumber()));
        if (entityManager != null && order.getId() != null && entityManager.contains(order)) {
            entityManager.refresh(order, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        }

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

        // 1. Verify HMAC-SHA256 signature strictly before accepting or processing payment
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

        // 2. Idempotent return for already verified / completed orders (e.g. concurrent webhook or page reload)
        if ("SUCCESS".equalsIgnoreCase(payment.getStatus())
                || "COMPLETED".equalsIgnoreCase(order.getPaymentStatus())
                || "PAYMENT_RECEIVED_OUT_OF_STOCK".equalsIgnoreCase(order.getPaymentStatus())) {
            log.info("Payment for order {} (gatewayOrderId: {}) is already processed (Status: {}). Returning idempotent success response.",
                    order.getOrderNumber(), request.getRazorpayOrderId(), order.getOrderStatus());
            return PaymentStatusResponse.success(
                    order.getOrderNumber(),
                    order.getOrderStatus(),
                    payment.getGatewayPaymentId() != null ? payment.getGatewayPaymentId() : request.getRazorpayPaymentId(),
                    order.getFinalPayableAmount()
            );
        }

        Instant paymentTime = Instant.now();
        payment.setGatewaySignature(request.getRazorpaySignature());
        applyCapturedPayment(payment, order, request.getRazorpayPaymentId(), paymentTime, "RAZORPAY_CLIENT_VERIFY");

        return PaymentStatusResponse.success(
                order.getOrderNumber(),
                order.getOrderStatus(),
                request.getRazorpayPaymentId(),
                order.getFinalPayableAmount()
        );
    }

    /**
     * Applies captured online payment to order state with strict atomicity and monotonic state transition guarantees.
     * Shared by both client return callback (verifyPayment) and asynchronous webhook (handleWebhook).
     *
     * Rules:
     * 1. Monotonic transitions: Never regresses PACKED, DISPATCHED, or DELIVERED orders to CONFIRMED.
     * 2. Cancelled order protection: If order was CANCELLED, attempts atomic all-or-nothing re-reservation.
     *    If stock is insufficient, records payment received but leaves order unfulfillable (PAYMENT_RECEIVED_OUT_OF_STOCK).
     * 3. Idempotent: Redundant capture events for already COMPLETED orders are safely ignored without duplicating inventory or invoices.
     */
    private void applyCapturedPayment(Payment payment, Order order, String razorpayPaymentId, Instant paymentTime, String source) {
        String existingPaymentStatus = payment.getStatus();
        String existingOrderPaymentStatus = order.getPaymentStatus();

        if ("REFUNDED".equalsIgnoreCase(existingPaymentStatus)
                || "REFUNDED".equalsIgnoreCase(existingOrderPaymentStatus)
                || "REFUND_PROCESSING".equalsIgnoreCase(existingOrderPaymentStatus)
                || "PARTIALLY_REFUNDED".equalsIgnoreCase(existingOrderPaymentStatus)
                || ("COMPLETED".equalsIgnoreCase(existingOrderPaymentStatus) && !"CANCELLED".equalsIgnoreCase(order.getOrderStatus()))
                || "PAYMENT_RECEIVED_OUT_OF_STOCK".equalsIgnoreCase(existingOrderPaymentStatus)) {
            log.info("Order {} payment is already captured/processed (PaymentStatus: {}, OrderPaymentStatus: {}). Ignoring duplicate/replayed capture from {}",
                    order.getOrderNumber(), existingPaymentStatus, existingOrderPaymentStatus, source);
            return;
        }

        payment.setGatewayPaymentId(razorpayPaymentId);
        payment.setStatus("SUCCESS");
        payment.setCompletedAt(paymentTime);
        paymentRepository.save(payment);

        String currentStatus = order.getOrderStatus();
        if ("CANCELLED".equalsIgnoreCase(currentStatus)) {
            // Check if this cancellation was specifically for switching to Cash on Delivery
            boolean isCodSwitch = order.getCancellationReason() != null
                    && (order.getCancellationReason().toLowerCase().contains("cash on delivery")
                    || order.getCancellationReason().toLowerCase().contains("cod"));

            if (isCodSwitch) {
                // Do NOT revive the order or re-reserve inventory.
                // Doing so would cause double fulfillment (both this order and the new COD order would be shipped).
                // Instead, record payment as SUCCESS and automatically issue a full refund to the customer.
                order.setPaymentStatus("REFUND_PROCESSING");
                order.setPaymentMethod("RAZORPAY");
                order.setPaidAt(paymentTime);
                order.addStatusHistory(new OrderStatusHistory(
                        order,
                        "CANCELLED",
                        "CANCELLED",
                        source,
                        "Late payment captured after order was cancelled for COD transition. Order remains CANCELLED to prevent duplicate fulfillment; initiating automatic refund (Payment ID: " + razorpayPaymentId + ")"
                ));
                order = orderRepository.save(order);

                if (refundService != null) {
                    try {
                        refundService.processRefund(
                                order,
                                order.getFinalPayableAmount(),
                                "Automatic refund: late payment received for order cancelled due to COD switch",
                                "SYSTEM"
                        );
                        log.info("Automatically processed refund for late payment on COD-cancelled order {}", order.getOrderNumber());
                    } catch (Exception e) {
                        log.error("Automatic refund initiation failed for COD-cancelled order {}: {}. Manual review required.",
                                order.getOrderNumber(), e.getMessage(), e);
                    }
                }
                notificationService.notifyPaymentSuccess(order, payment);
                return;
            }

            // Atomic re-reservation: all-or-nothing check across all items
            boolean stockReserved = inventoryService.tryReserveAllItems(order.getItems(), order.getOrderNumber());
            if (stockReserved) {
                order.setPaymentStatus("COMPLETED");
                order.setOrderStatus("CONFIRMED");
                order.setPaymentMethod("RAZORPAY");
                order.setPaidAt(paymentTime);
                order.addStatusHistory(new OrderStatusHistory(
                        order,
                        "CANCELLED",
                        "CONFIRMED",
                        source,
                        "Payment confirmed via " + source + " and inventory atomically re-reserved (Txn: " + razorpayPaymentId + ")"
                ));
                orderRepository.save(order);
                try {
                    invoiceService.generateInvoiceForOrder(order);
                } catch (Exception e) {
                    log.error("Failed to auto-generate invoice for order {}: {}", order.getOrderNumber(), e.getMessage());
                }
                notificationService.notifyPaymentSuccess(order, payment);
                log.info("Late payment captured for cancelled order {}. Stock successfully re-reserved, status set to CONFIRMED.", order.getOrderNumber());
            } else {
                // Stock unavailable: record payment received but keep order unfulfillable
                order.setPaymentStatus("PAYMENT_RECEIVED_OUT_OF_STOCK");
                order.setPaymentMethod("RAZORPAY");
                order.setPaidAt(paymentTime);
                order.addStatusHistory(new OrderStatusHistory(
                        order,
                        "CANCELLED",
                        "CANCELLED",
                        source,
                        "Payment captured after cancellation, but stock unavailable for atomic re-reservation. Marked PAYMENT_RECEIVED_OUT_OF_STOCK (Txn: " + razorpayPaymentId + ")"
                ));
                orderRepository.save(order);
                log.warn("Late payment captured for cancelled order {} but stock was unavailable. Status set to PAYMENT_RECEIVED_OUT_OF_STOCK", order.getOrderNumber());
            }
        } else {
            order.setPaymentStatus("COMPLETED");
            order.setPaymentMethod("RAZORPAY");
            order.setPaidAt(paymentTime);

            // Monotonic order state transition: Never regress PACKED, DISPATCHED, or DELIVERED to CONFIRMED
            if ("PENDING_PAYMENT".equalsIgnoreCase(currentStatus) || "PENDING".equalsIgnoreCase(currentStatus)) {
                order.setOrderStatus("CONFIRMED");
                order.addStatusHistory(new OrderStatusHistory(
                        order,
                        currentStatus,
                        "CONFIRMED",
                        source,
                        "Payment confirmed via " + source + " (Payment ID: " + razorpayPaymentId + ")"
                ));
            } else {
                order.addStatusHistory(new OrderStatusHistory(
                        order,
                        currentStatus,
                        currentStatus,
                        source,
                        "Payment confirmed via " + source + " for in-flight/completed order (Payment ID: " + razorpayPaymentId + ")"
                ));
            }
            orderRepository.save(order);
            try {
                invoiceService.generateInvoiceForOrder(order);
            } catch (Exception e) {
                log.error("Failed to auto-generate invoice for order {}: {}", order.getOrderNumber(), e.getMessage());
            }
            notificationService.notifyPaymentSuccess(order, payment);
            log.info("Payment confirmed via {} for order {}. Order status: {}", source, order.getOrderNumber(), order.getOrderStatus());
        }
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
                        Order rawOrder = payment.getOrder();
                        if (rawOrder != null) {
                            Order order = orderRepository.findByIdWithLock(rawOrder.getId())
                                    .orElse(rawOrder);
                            if (entityManager != null && order.getId() != null && entityManager.contains(order)) {
                                entityManager.refresh(order, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
                            }
                            Instant webhookTime = Instant.now();
                            applyCapturedPayment(payment, order, razorpayPaymentId, webhookTime, "RAZORPAY_WEBHOOK");
                        }
                    });
                }
            } else if ("payment.failed".equalsIgnoreCase(event)) {
                JsonNode paymentEntity = root.path("payload").path("payment").path("entity");
                String razorpayOrderId = paymentEntity.path("order_id").asText(null);
                String errorDescription = paymentEntity.path("error_description").asText("Payment failed at bank/gateway");

                if (razorpayOrderId != null) {
                    paymentRepository.findByGatewayOrderId(razorpayOrderId).ifPresent(payment -> {
                        boolean alreadyCaptured = "SUCCESS".equalsIgnoreCase(payment.getStatus())
                                || (payment.getOrder() != null && ("COMPLETED".equalsIgnoreCase(payment.getOrder().getPaymentStatus())
                                || "PAYMENT_RECEIVED_OUT_OF_STOCK".equalsIgnoreCase(payment.getOrder().getPaymentStatus())));
                        if (!alreadyCaptured) {
                            payment.setStatus("FAILED");
                            payment.setFailureReason(errorDescription);
                            paymentRepository.save(payment);
                            log.info("Recorded payment failure for order {} (Gateway Order: {}): {}",
                                    payment.getOrder() != null ? payment.getOrder().getOrderNumber() : "N/A", razorpayOrderId, errorDescription);
                        } else {
                            log.warn("Late payment.failed webhook ignored for already successful payment on gateway order: {}", razorpayOrderId);
                        }
                    });
                }
            } else if ("refund.processed".equalsIgnoreCase(event) || "refund.failed".equalsIgnoreCase(event)) {
                log.info("Processing Razorpay {} webhook event", event);
                if (refundService != null) {
                    refundService.reconcileRefundWebhook(event, root.path("payload"));
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
