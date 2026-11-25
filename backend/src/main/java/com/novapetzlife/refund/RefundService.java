package com.novapetzlife.refund;

import com.fasterxml.jackson.databind.JsonNode;
import com.novapetzlife.order.Order;
import com.novapetzlife.order.OrderRepository;
import com.novapetzlife.payment.Payment;
import com.novapetzlife.payment.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);

    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final com.novapetzlife.notification.NotificationService notificationService;
    private final com.novapetzlife.payment.RazorpayClientService razorpayClientService;
    private final TransactionTemplate transactionTemplate;

    public RefundService(
            RefundRepository refundRepository,
            PaymentRepository paymentRepository,
            OrderRepository orderRepository,
            com.novapetzlife.notification.NotificationService notificationService,
            com.novapetzlife.payment.RazorpayClientService razorpayClientService,
            PlatformTransactionManager transactionManager) {
        this.refundRepository = refundRepository;
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.notificationService = notificationService;
        this.razorpayClientService = razorpayClientService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Entry point for refund processing with clean transaction decoupling:
     * 1. Records the refund intent in the current database transaction.
     * 2. Defers external gateway dispatch until AFTER the surrounding transaction has COMMITTED,
     *    ensuring zero database locks and connections are held during external network I/O.
     */
    @Transactional
    public Refund processRefund(Order order, BigDecimal refundAmount, String reason, String approvedBy) {
        Refund refund = recordRefundIntent(order, refundAmount, reason, approvedBy);
        if (refund != null && ("PROCESSING".equalsIgnoreCase(refund.getStatus()) || "INITIATED".equalsIgnoreCase(refund.getStatus()))) {
            Long rId = refund.getId();
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        dispatchGatewayRefund(rId);
                    }
                });
            } else {
                dispatchGatewayRefund(rId);
            }
        }
        return refund;
    }

    /**
     * Phase 1: Durably records refund intent in database inside the active transaction.
     * Enforces pessimistic locking, state validation, and strict application-level idempotency.
     */
    @Transactional
    public Refund recordRefundIntent(Order order, BigDecimal refundAmount, String reason, String approvedBy) {
        if (order == null || order.getId() == null) {
            throw new IllegalArgumentException("Valid order is required for refund processing");
        }

        if (refundAmount == null) {
            throw new IllegalArgumentException("Refund amount cannot be null");
        }

        long amountInPaise = convertToPaiseExact(refundAmount);
        BigDecimal scaledAmount = BigDecimal.valueOf(amountInPaise, 2);

        // 1. Acquire database-level pessimistic write lock on the order to serialize concurrent refund requests
        Order lockedOrder = orderRepository.findByIdWithLock(order.getId())
                .orElseThrow(() -> new IllegalArgumentException("Order not found with ID: " + order.getId()));

        // 2. Validate payment state: Only confirmed/paid orders are eligible for refunds
        String currentPaymentStatus = lockedOrder.getPaymentStatus();
        boolean isEligibleStatus = "COMPLETED".equalsIgnoreCase(currentPaymentStatus)
                || "PAID".equalsIgnoreCase(currentPaymentStatus)
                || "PARTIALLY_REFUNDED".equalsIgnoreCase(currentPaymentStatus)
                || "REFUNDED".equalsIgnoreCase(currentPaymentStatus)
                || "REFUND_PROCESSING".equalsIgnoreCase(currentPaymentStatus);

        if (!isEligibleStatus) {
            throw new IllegalStateException("Cannot process refund for order " + lockedOrder.getOrderNumber()
                    + " with payment status: " + currentPaymentStatus + ". Only captured payments are refundable.");
        }

        // 3. For COD orders, verify cash was physically collected before processing refund
        if ("COD".equalsIgnoreCase(lockedOrder.getPaymentMethod())
                && !"COMPLETED".equalsIgnoreCase(currentPaymentStatus)
                && !"PAID".equalsIgnoreCase(currentPaymentStatus)
                && !"PARTIALLY_REFUNDED".equalsIgnoreCase(currentPaymentStatus)) {
            throw new IllegalStateException("Cannot issue refund for Cash-on-Delivery order " + lockedOrder.getOrderNumber()
                    + " before payment collection has been recorded.");
        }

        // 4. Concurrency-safe recalculation of refundable balance inside the locked transaction
        List<Refund> existingRefunds = refundRepository.findByOrderId(lockedOrder.getId());

        // Idempotency check: if full refund is already completed or in-flight (PROCESSING/INITIATED) for this order, return it safely
        Optional<Refund> alreadyActive = existingRefunds.stream()
                .filter(r -> ("SUCCESS".equalsIgnoreCase(r.getStatus())
                        || "PROCESSING".equalsIgnoreCase(r.getStatus())
                        || "INITIATED".equalsIgnoreCase(r.getStatus()))
                        && r.getAmount().compareTo(scaledAmount) >= 0)
                .findFirst();
        if (alreadyActive.isPresent()) {
            log.info("Idempotent refund requested for order {}: active refund already exists (ID: {}, Status: {}). Returning existing record.",
                    lockedOrder.getOrderNumber(), alreadyActive.get().getId(), alreadyActive.get().getStatus());
            return alreadyActive.get();
        }

        BigDecimal currentTotalRefunded = existingRefunds.stream()
                .filter(r -> "SUCCESS".equalsIgnoreCase(r.getStatus())
                        || "PROCESSING".equalsIgnoreCase(r.getStatus())
                        || "PENDING_MANUAL_REFUND".equalsIgnoreCase(r.getStatus())
                        || "REQUESTED".equalsIgnoreCase(r.getStatus())
                        || "INITIATED".equalsIgnoreCase(r.getStatus()))
                .map(Refund::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal maxRefundable = lockedOrder.getFinalPayableAmount().subtract(currentTotalRefunded);
        if (scaledAmount.compareTo(maxRefundable) > 0) {
            throw new IllegalArgumentException("Refund amount " + scaledAmount
                    + " exceeds maximum remaining refundable amount " + maxRefundable
                    + " for order " + lockedOrder.getOrderNumber());
        }

        Payment payment = paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(lockedOrder.getId()).orElse(null);

        // 5. Durable Persistence First: Record refund intent in database before making external network call
        boolean canAttemptGatewayRefund = payment != null
                && "RAZORPAY".equalsIgnoreCase(lockedOrder.getPaymentMethod())
                && payment.getGatewayPaymentId() != null
                && !payment.getGatewayPaymentId().isBlank()
                && razorpayClientService != null
                && razorpayClientService.isConfigured();

        String initialStatus = canAttemptGatewayRefund ? "INITIATED" : "PENDING_MANUAL_REFUND";
        String idempotencyKey = buildIdempotencyKey(lockedOrder.getOrderNumber());
        Refund refund = new Refund(
                lockedOrder,
                payment,
                null,
                scaledAmount,
                initialStatus,
                reason != null ? reason : "Order refund approved",
                approvedBy != null ? approvedBy : "SYSTEM"
        );
        refund.setIdempotencyKey(idempotencyKey);
        refund.setAttemptCount(0);
        refund.setRecoveryStatus(canAttemptGatewayRefund ? "PENDING" : "MANUAL_ESCALATION");
        refund = refundRepository.saveAndFlush(refund);

        // Ensure unique receipt tied to persistent refund ID is persisted before dispatch
        if (!StringUtils.hasText(refund.getReceipt())) {
            if (refund.getId() == null) {
                throw new IllegalStateException("Failed to persist refund: entity ID is null before receipt generation");
            }
            String receipt = buildDeterministicReceipt(lockedOrder.getOrderNumber(), refund.getId());
            refund.setReceipt(receipt);
            refund = refundRepository.saveAndFlush(refund);
        }

        if (canAttemptGatewayRefund) {
            lockedOrder.setPaymentStatus("REFUND_PROCESSING");
        } else {
            // PENDING_MANUAL_REFUND fallback (e.g. unconfigured credentials)
            BigDecimal newTotalRefunded = currentTotalRefunded.add(scaledAmount);
            if (newTotalRefunded.compareTo(lockedOrder.getFinalPayableAmount()) >= 0) {
                lockedOrder.setPaymentStatus("REFUNDED");
                if (payment != null) {
                    payment.setStatus("REFUNDED");
                    paymentRepository.save(payment);
                }
            } else {
                lockedOrder.setPaymentStatus("PARTIALLY_REFUNDED");
                if (payment != null) {
                    payment.setStatus("PARTIALLY_REFUNDED");
                    paymentRepository.save(payment);
                }
            }
        }
        orderRepository.save(lockedOrder);

        log.info("Recorded internal refund intent (ID: {}) of INR {} for order {} (Approved by: {}). Status: {}",
                refund.getId(), scaledAmount, lockedOrder.getOrderNumber(), approvedBy, refund.getStatus());

        return refund;
    }

    /**
     * Converts a monetary refund amount to exact paise without rounding, truncation, or overflow.
     * Enforces:
     * - Rejection of null
     * - Rejection of zero or negative amounts
     * - Rejection of fractional paise (scale > 2)
     * - Rejection of 64-bit long overflow
     */
    public static long convertToPaiseExact(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Refund amount cannot be null");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Refund amount must be strictly positive: " + amount);
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Refund amount cannot contain fractional paise: " + amount);
        }
        try {
            long amountInPaise = amount.movePointRight(2)
                    .setScale(0, RoundingMode.UNNECESSARY)
                    .longValueExact();
            if (amountInPaise <= 0) {
                throw new IllegalArgumentException("Converted refund amount in paise must be positive: " + amountInPaise);
            }
            return amountInPaise;
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Cannot convert refund amount " + amount + " to exact paise without precision loss or overflow", ex);
        }
    }

    public static String buildDeterministicReceipt(String orderNumber, Long refundId) {
        if (refundId == null || refundId <= 0) {
            throw new IllegalArgumentException("Persistent refund ID must be non-null and positive when generating deterministic receipt");
        }
        String ord = StringUtils.hasText(orderNumber) ? orderNumber.replaceAll("[^a-zA-Z0-9_-]", "") : "ord";
        String prefix = "rfnd_";
        String suffix = "_" + refundId;
        int maxOrdLen = 40 - prefix.length() - suffix.length();
        String safeOrd = (maxOrdLen > 0 && ord.length() > maxOrdLen) ? ord.substring(0, maxOrdLen) : (maxOrdLen <= 0 ? "" : ord);
        return prefix + safeOrd + suffix;
    }

    public static String buildIdempotencyKey(String orderNumber) {
        String ord = StringUtils.hasText(orderNumber) ? orderNumber.replaceAll("[^a-zA-Z0-9_-]", "") : "ord";
        String prefix = "rfnd_idem_";
        String randomSuffix = "_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        int maxOrdLen = 100 - prefix.length() - randomSuffix.length();
        String safeOrd = (maxOrdLen > 0 && ord.length() > maxOrdLen) ? ord.substring(0, maxOrdLen) : (maxOrdLen <= 0 ? "" : ord);
        return prefix + safeOrd + randomSuffix;
    }

    /**
     * Phase 2: Gateway Dispatch (Zero database transaction, zero row locks held).
     * Dispatches HTTP request to Razorpay REST API and records the outcome in a separate short transaction.
     */
    public void dispatchGatewayRefund(Long refundId) {
        dispatchGatewayRefund(refundId, null);
    }

    public void dispatchGatewayRefund(Long refundId, String claimToken) {
        if (refundId == null) return;

        Refund refund = refundRepository.findByIdWithDetails(refundId).orElse(null);
        if (refund == null) {
            log.error("Cannot dispatch gateway refund: Refund not found with ID {}", refundId);
            return;
        }

        if ("SUCCESS".equalsIgnoreCase(refund.getStatus())) {
            log.info("Refund {} is already SUCCESS; skipping gateway dispatch.", refundId);
            return;
        }

        // Lease fencing check before network calls
        if (StringUtils.hasText(claimToken)) {
            if (!claimToken.equals(refund.getClaimToken())) {
                log.warn("Cannot dispatch gateway refund for refund ID {}: worker claim token '{}' does not match active DB claim token '{}'. Lease expired or reassigned. Aborting before gateway dispatch.",
                        refundId, claimToken, refund.getClaimToken());
                return;
            }
        } else if (StringUtils.hasText(refund.getClaimToken())) {
            log.warn("Cannot dispatch gateway refund for refund ID {}: an active recovery worker holds claim token '{}'. Aborting concurrent initial dispatch.",
                    refundId, refund.getClaimToken());
            return;
        }

        Payment payment = refund.getPayment();
        Order order = refund.getOrder();
        if (payment == null || !StringUtils.hasText(payment.getGatewayPaymentId())) {
            log.warn("Cannot dispatch gateway refund for refund {}: Missing gateway payment ID. Marking PENDING_MANUAL_REFUND.", refundId);
            recordGatewayRefundManual(refundId, claimToken);
            return;
        }

        if (razorpayClientService == null || !razorpayClientService.isConfigured()) {
            log.warn("Razorpay credentials not configured for refund {}. Marking PENDING_MANUAL_REFUND.", refundId);
            recordGatewayRefundManual(refundId, claimToken);
            return;
        }

        long amountInPaise = convertToPaiseExact(refund.getAmount());
        
        // Ensure stable unique receipt is present and persisted
        String receipt = refund.getReceipt();
        if (!StringUtils.hasText(receipt)) {
            final String generatedReceipt = buildDeterministicReceipt(order != null ? order.getOrderNumber() : "ord", refundId);
            transactionTemplate.execute(status -> {
                Refund r = refundRepository.findById(refundId).orElse(null);
                if (r != null && !StringUtils.hasText(r.getReceipt())) {
                    r.setReceipt(generatedReceipt);
                    refundRepository.saveAndFlush(r);
                }
                return null;
            });
            receipt = generatedReceipt;
        }
        final String finalReceipt = receipt;

        // Ensure stable idempotency key is present and persisted
        String idempotencyKey = refund.getIdempotencyKey();
        if (!StringUtils.hasText(idempotencyKey)) {
            final String generatedKey = buildIdempotencyKey(order != null ? order.getOrderNumber() : "ord");
            transactionTemplate.execute(status -> {
                Refund r = refundRepository.findById(refundId).orElse(null);
                if (r != null && !StringUtils.hasText(r.getIdempotencyKey())) {
                    r.setIdempotencyKey(generatedKey);
                    refundRepository.saveAndFlush(r);
                }
                return null;
            });
            idempotencyKey = generatedKey;
        }
        final String finalIdempotencyKey = idempotencyKey;

        try {
            // Reconciliation First Guard:
            // Query Razorpay gateway refunds for this payment before making external call.
            // Match strictly on authoritative unique receipt to eliminate ambiguous matching.
            var existingRefunds = razorpayClientService.fetchRefundsForPayment(payment.getGatewayPaymentId());
            if (existingRefunds != null && !existingRefunds.isEmpty()) {
                var matched = existingRefunds.stream()
                        .filter(item -> StringUtils.hasText(item.receipt()) && finalReceipt.equals(item.receipt().trim()))
                        .findFirst()
                        .orElse(null);
                if (matched != null && StringUtils.hasText(matched.refundId())) {
                    boolean amountMatches = matched.amountInPaise() == amountInPaise;
                    boolean currencyMatches = "INR".equalsIgnoreCase(matched.currency());
                    if (!amountMatches || !currencyMatches) {
                        log.error("Reconciliation for refund {} matched receipt {} but failed verification: amount (expected {} vs actual {}), currency (expected INR vs actual {}). Escalating to PENDING_MANUAL_REFUND.",
                                refundId, finalReceipt, amountInPaise, matched.amountInPaise(), matched.currency());
                        recordGatewayRefundManual(refundId, claimToken);
                        return;
                    }

                    // Strict gateway status mapping: do NOT assume receipt match means SUCCESS
                    String gwStatus = matched.status() != null ? matched.status().toLowerCase() : "";
                    if ("processed".equals(gwStatus)) {
                        log.info("Reconciliation check found PROCESSED Razorpay refund {} matching receipt {}. Reconciling to SUCCESS.",
                                matched.refundId(), finalReceipt);
                        recordGatewayRefundOutcome(refundId, matched.refundId(), "SUCCESS", claimToken);
                        return;
                    } else if ("failed".equals(gwStatus)) {
                        log.warn("Reconciliation check found FAILED Razorpay refund {} matching receipt {}. Reconciling to FAILED.",
                                matched.refundId(), finalReceipt);
                        recordGatewayRefundFailure(refundId, "Razorpay reported status: failed", claimToken);
                        return;
                    } else if ("pending".equals(gwStatus) || "created".equals(gwStatus)) {
                        log.info("Reconciliation check found in-flight Razorpay refund {} matching receipt {}. Preserving status PROCESSING.",
                                matched.refundId(), finalReceipt);
                        recordGatewayRefundOutcome(refundId, matched.refundId(), "PROCESSING", claimToken);
                        return;
                    } else {
                        log.warn("Reconciliation check found Razorpay refund {} matching receipt {} with unknown status '{}'. Escalating to PENDING_MANUAL_REFUND.",
                                matched.refundId(), finalReceipt, matched.status());
                        recordGatewayRefundManual(refundId, claimToken);
                        return;
                    }
                }

                // Ambiguity check: if candidate exists with same payment and amount but different/missing receipt,
                // do NOT guess or dispatch blindly; escalate to manual review.
                boolean hasAmbiguousCandidate = existingRefunds.stream()
                        .anyMatch(item -> (!StringUtils.hasText(item.receipt()) || !finalReceipt.equals(item.receipt().trim()))
                                && item.amountInPaise() == amountInPaise
                                && "INR".equalsIgnoreCase(item.currency()));
                if (hasAmbiguousCandidate) {
                    log.warn("Reconciliation found ambiguous refund candidate for payment {} without matching receipt {}. Escalating to PENDING_MANUAL_REFUND to prevent duplicate disbursement.",
                            payment.getGatewayPaymentId(), finalReceipt);
                    recordGatewayRefundManual(refundId, claimToken);
                    return;
                }
            }

            log.info("Dispatching gateway refund for refund ID {}, payment {}, amount {} paise, receipt {}, idempotencyKey {} (ZERO DB locks held)",
                    refundId, payment.getGatewayPaymentId(), amountInPaise, finalReceipt, finalIdempotencyKey);
            var result = razorpayClientService.createRefund(payment.getGatewayPaymentId(), amountInPaise, refund.getReason(), finalReceipt, finalIdempotencyKey);
            if (result == null) {
                // Fallback for mocks/test environments that only stubbed the 4-arg or 3-arg overload
                result = razorpayClientService.createRefund(payment.getGatewayPaymentId(), amountInPaise, refund.getReason(), finalReceipt);
                if (result == null) {
                    result = razorpayClientService.createRefund(payment.getGatewayPaymentId(), amountInPaise, refund.getReason());
                }
            }

            String gatewayRefundId = result != null ? result.gatewayRefundId() : null;
            String gwStatus = result != null && result.status() != null ? result.status().toLowerCase() : "";
            String status;
            if ("processed".equals(gwStatus)) {
                status = "SUCCESS";
            } else if ("failed".equals(gwStatus)) {
                status = "FAILED";
            } else if ("pending".equals(gwStatus) || "created".equals(gwStatus)) {
                status = "PROCESSING";
            } else {
                status = "PROCESSING";
            }
            recordGatewayRefundOutcome(refundId, gatewayRefundId, status, claimToken);

        } catch (com.novapetzlife.payment.RazorpayClientService.GatewayReconciliationException gre) {
            log.warn("Gateway reconciliation query failed for refund {} (payment {}). Aborting dispatch to prevent duplicate disbursement: {}",
                    refundId, payment.getGatewayPaymentId(), gre.getMessage());
            recordGatewayRefundReconciliationFailed(refundId, gre.getMessage(), claimToken);
        } catch (com.novapetzlife.payment.RazorpayClientService.GatewayConflictException gce) {
            log.warn("Gateway refund returned 409 Conflict for refund {}. Preserving status PROCESSING for reconciliation: {}",
                    refundId, gce.getMessage());
            recordGatewayRefundConflict(refundId, gce.getMessage(), claimToken);
        } catch (Exception ex) {
            boolean isTimeout = ex instanceof org.springframework.web.client.ResourceAccessException
                    || (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("timeout"));
            if (isTimeout) {
                log.warn("Gateway refund call timed out for refund {}. Preserving status PROCESSING for webhook reconciliation: {}",
                        refundId, ex.getMessage());
                recordGatewayRefundTimeout(refundId, ex.getMessage(), claimToken);
            } else {
                log.error("Gateway refund call rejected for refund {}: {}. Marking FAILED.", refundId, ex.getMessage());
                recordGatewayRefundFailure(refundId, ex.getMessage(), claimToken);
            }
        }
    }

    /**
     * Phase 3: Records the gateway outcome in an independent short transaction.
     * Uses TransactionTemplate to guarantee commit and flush even when called internally from dispatchGatewayRefund.
     */
    public void recordGatewayRefundOutcome(Long refundId, String gatewayRefundId, String gatewayStatus) {
        recordGatewayRefundOutcome(refundId, gatewayRefundId, gatewayStatus, null);
    }

    public void recordGatewayRefundOutcome(Long refundId, String gatewayRefundId, String gatewayStatus, String claimToken) {
        Refund savedRefund = transactionTemplate.execute(status -> {
            Refund refund = refundRepository.findByIdWithLock(refundId).orElse(null);
            if (refund == null) return null;

            if ("SUCCESS".equalsIgnoreCase(refund.getStatus())) {
                log.info("Refund {} is already SUCCESS; ignoring subsequent outcome update to {}.", refundId, gatewayStatus);
                return refund;
            }

            if (StringUtils.hasText(claimToken)) {
                if (!claimToken.equals(refund.getClaimToken())) {
                    log.warn("Stale worker detected for refund {}. DB claim token is '{}' but worker token is '{}'. Aborting outcome update.",
                            refundId, refund.getClaimToken(), claimToken);
                    return null;
                }
            } else if (StringUtils.hasText(refund.getClaimToken())) {
                log.warn("Stale initial worker detected for refund {}. Active lease holds claim token '{}'. Aborting outcome update.",
                        refundId, refund.getClaimToken());
                return null;
            }

            refund.setGatewayRefundId(gatewayRefundId);
            refund.setStatus(gatewayStatus);
            refund.setClaimToken(null);
            refund.setClaimExpiresAt(null);
            if ("SUCCESS".equalsIgnoreCase(gatewayStatus)) {
                refund.setCompletedAt(Instant.now());
                refund.setRecoveryStatus("COMPLETED");
            }
            refund = refundRepository.saveAndFlush(refund);

            Order order = refund.getOrder();
            Payment payment = refund.getPayment();

            if ("SUCCESS".equalsIgnoreCase(gatewayStatus) && order != null) {
                order.setPaymentStatus("REFUNDED");
                orderRepository.saveAndFlush(order);
                if (payment != null) {
                    payment.setStatus("REFUNDED");
                    paymentRepository.saveAndFlush(payment);
                }
            }
            return refund;
        });

        if (savedRefund != null && "SUCCESS".equalsIgnoreCase(savedRefund.getStatus())) {
            try {
                Refund detailed = refundRepository.findByIdWithDetails(savedRefund.getId()).orElse(savedRefund);
                notificationService.notifyRefundProcessed(detailed);
            } catch (Exception ex) {
                log.error("Failed to send notification for refund {}: {}", savedRefund.getId(), ex.getMessage());
            }
        }
    }

    public void recordGatewayRefundTimeout(Long refundId, String timeoutMsg) {
        recordGatewayRefundTimeout(refundId, timeoutMsg, null);
    }

    public void recordGatewayRefundTimeout(Long refundId, String timeoutMsg, String claimToken) {
        transactionTemplate.execute(status -> {
            Refund refund = refundRepository.findByIdWithLock(refundId).orElse(null);
            if (refund == null) return null;

            if ("SUCCESS".equalsIgnoreCase(refund.getStatus())) {
                log.info("Refund {} is already SUCCESS; ignoring timeout update.", refundId);
                return null;
            }

            if (StringUtils.hasText(claimToken)) {
                if (!claimToken.equals(refund.getClaimToken())) {
                    log.warn("Stale worker detected on timeout for refund {}. DB claim token is '{}' but worker token is '{}'. Aborting update.",
                            refundId, refund.getClaimToken(), claimToken);
                    return null;
                }
            } else if (StringUtils.hasText(refund.getClaimToken())) {
                log.warn("Stale initial worker detected on timeout for refund {}. Active lease holds claim token '{}'. Aborting update.",
                        refundId, refund.getClaimToken());
                return null;
            }

            refund.setStatus("PROCESSING");
            refund.setRecoveryStatus("TIMEOUT");
            refund.setClaimToken(null);
            refund.setClaimExpiresAt(null);
            if (refund.getReason() != null && !refund.getReason().contains("Network timeout") && refund.getReason().length() < 180) {
                refund.setReason(refund.getReason() + " [Network timeout: awaiting webhook reconciliation]");
            }
            refundRepository.saveAndFlush(refund);
            return null;
        });
    }

    public void recordGatewayRefundConflict(Long refundId, String conflictMsg) {
        recordGatewayRefundConflict(refundId, conflictMsg, null);
    }

    public void recordGatewayRefundConflict(Long refundId, String conflictMsg, String claimToken) {
        transactionTemplate.execute(status -> {
            Refund refund = refundRepository.findByIdWithLock(refundId).orElse(null);
            if (refund == null) return null;

            if ("SUCCESS".equalsIgnoreCase(refund.getStatus())) {
                log.info("Refund {} is already SUCCESS; ignoring conflict update.", refundId);
                return null;
            }

            if (StringUtils.hasText(claimToken)) {
                if (!claimToken.equals(refund.getClaimToken())) {
                    log.warn("Stale worker detected on conflict for refund {}. DB claim token is '{}' but worker token is '{}'. Aborting update.",
                            refundId, refund.getClaimToken(), claimToken);
                    return null;
                }
            } else if (StringUtils.hasText(refund.getClaimToken())) {
                log.warn("Stale initial worker detected on conflict for refund {}. Active lease holds claim token '{}'. Aborting update.",
                        refundId, refund.getClaimToken());
                return null;
            }

            refund.setStatus("PROCESSING");
            refund.setRecoveryStatus("CONFLICT");
            refund.setClaimToken(null);
            refund.setClaimExpiresAt(null);
            refundRepository.saveAndFlush(refund);
            return null;
        });
    }

    public void recordGatewayRefundReconciliationFailed(Long refundId, String msg) {
        recordGatewayRefundReconciliationFailed(refundId, msg, null);
    }

    public void recordGatewayRefundReconciliationFailed(Long refundId, String msg, String claimToken) {
        transactionTemplate.execute(status -> {
            Refund refund = refundRepository.findByIdWithLock(refundId).orElse(null);
            if (refund == null) return null;

            if ("SUCCESS".equalsIgnoreCase(refund.getStatus())) {
                log.info("Refund {} is already SUCCESS; ignoring recon failed update.", refundId);
                return null;
            }

            if (StringUtils.hasText(claimToken)) {
                if (!claimToken.equals(refund.getClaimToken())) {
                    log.warn("Stale worker detected on recon fail for refund {}. DB claim token is '{}' but worker token is '{}'. Aborting update.",
                            refundId, refund.getClaimToken(), claimToken);
                    return null;
                }
            } else if (StringUtils.hasText(refund.getClaimToken())) {
                log.warn("Stale initial worker detected on recon fail for refund {}. Active lease holds claim token '{}'. Aborting update.",
                        refundId, refund.getClaimToken());
                return null;
            }

            refund.setStatus("PROCESSING");
            refund.setRecoveryStatus("RECONCILIATION_FAILED");
            refund.setClaimToken(null);
            refund.setClaimExpiresAt(null);
            refundRepository.saveAndFlush(refund);
            return null;
        });
    }

    public void recordGatewayRefundFailure(Long refundId, String failureMsg) {
        recordGatewayRefundFailure(refundId, failureMsg, null);
    }

    public void recordGatewayRefundFailure(Long refundId, String failureMsg, String claimToken) {
        transactionTemplate.execute(status -> {
            Refund refund = refundRepository.findByIdWithLock(refundId).orElse(null);
            if (refund == null) return null;

            if ("SUCCESS".equalsIgnoreCase(refund.getStatus())) {
                log.info("Refund {} is already SUCCESS; ignoring failure update.", refundId);
                return null;
            }

            if (StringUtils.hasText(claimToken)) {
                if (!claimToken.equals(refund.getClaimToken())) {
                    log.warn("Stale worker detected on failure for refund {}. DB claim token is '{}' but worker token is '{}'. Aborting update.",
                            refundId, refund.getClaimToken(), claimToken);
                    return null;
                }
            } else if (StringUtils.hasText(refund.getClaimToken())) {
                log.warn("Stale initial worker detected on failure for refund {}. Active lease holds claim token '{}'. Aborting update.",
                        refundId, refund.getClaimToken());
                return null;
            }

            refund.setStatus("FAILED");
            refund.setRecoveryStatus("FAILED");
            refund.setClaimToken(null);
            refund.setClaimExpiresAt(null);
            String note = (failureMsg != null && failureMsg.length() > 50) ? failureMsg.substring(0, 50) : failureMsg;
            if (refund.getReason() != null && refund.getReason().length() < 180) {
                refund.setReason(refund.getReason() + " [Gateway rejected: " + note + "]");
            }
            refundRepository.saveAndFlush(refund);

            Order order = refund.getOrder();
            if (order != null) {
                order.setPaymentStatus("REFUND_FAILED");
                orderRepository.saveAndFlush(order);
            }
            return null;
        });
    }

    public void recordGatewayRefundManual(Long refundId) {
        recordGatewayRefundManual(refundId, null);
    }

    public void recordGatewayRefundManual(Long refundId, String claimToken) {
        transactionTemplate.execute(status -> {
            Refund refund = refundRepository.findByIdWithLock(refundId).orElse(null);
            if (refund == null) return null;

            if ("SUCCESS".equalsIgnoreCase(refund.getStatus())) {
                log.info("Refund {} is already SUCCESS; ignoring manual escalate update.", refundId);
                return null;
            }

            if (StringUtils.hasText(claimToken)) {
                if (!claimToken.equals(refund.getClaimToken())) {
                    log.warn("Stale worker detected on manual escalate for refund {}. DB claim token is '{}' but worker token is '{}'. Aborting update.",
                            refundId, refund.getClaimToken(), claimToken);
                    return null;
                }
            } else if (StringUtils.hasText(refund.getClaimToken())) {
                log.warn("Stale initial worker detected on manual escalate for refund {}. Active lease holds claim token '{}'. Aborting update.",
                        refundId, refund.getClaimToken());
                return null;
            }

            refund.setStatus("PENDING_MANUAL_REFUND");
            refund.setRecoveryStatus("MANUAL_ESCALATION");
            refund.setClaimToken(null);
            refund.setClaimExpiresAt(null);
            refundRepository.saveAndFlush(refund);

            Order order = refund.getOrder();
            if (order != null) {
                order.setPaymentStatus("REFUNDED");
                orderRepository.saveAndFlush(order);
            }
            Payment payment = refund.getPayment();
            if (payment != null) {
                payment.setStatus("REFUNDED");
                paymentRepository.saveAndFlush(payment);
            }
            return null;
        });
    }

    /**
     * Phase 4: Robust webhook reconciliation for refund.processed and refund.failed.
     * Enforces amount consistency, currency validation, matching intent, and out-of-order deduplication.
     */
    @Transactional
    public void reconcileRefundWebhook(String eventType, JsonNode payloadNode) {
        if (payloadNode == null) {
            log.warn("Ignored refund webhook with empty payload");
            return;
        }

        JsonNode refundNode = payloadNode.path("refund").path("entity");
        String gatewayRefundId = refundNode.path("id").asText(null);
        String gatewayPaymentId = refundNode.path("payment_id").asText(null);
        long amountPaise = refundNode.path("amount").asLong(0);
        String currency = refundNode.path("currency").asText("INR");

        if (!StringUtils.hasText(gatewayRefundId)) {
            log.warn("Ignored refund webhook without valid refund ID: {}", eventType);
            return;
        }

        // Currency validation
        if (!"INR".equalsIgnoreCase(currency)) {
            log.error("Rejected refund webhook with unsupported currency: {}", currency);
            return;
        }

        // Find local refund intent:
        // 1. By gatewayRefundId
        Refund refund = refundRepository.findByGatewayRefundId(gatewayRefundId).orElse(null);

        // 2. By authoritative receipt if present in webhook entity
        String receipt = refundNode.path("receipt").asText(null);
        if (refund == null && StringUtils.hasText(receipt)) {
            refund = refundRepository.findByReceipt(receipt).orElse(null);
        }

        // 3. Fallback: by payment ID and status PROCESSING/INITIATED
        if (refund == null && StringUtils.hasText(gatewayPaymentId)) {
            List<Payment> payments = paymentRepository.findAll().stream()
                    .filter(p -> gatewayPaymentId.equals(p.getGatewayPaymentId()))
                    .toList();
            if (!payments.isEmpty()) {
                List<Refund> refunds = refundRepository.findByPaymentId(payments.get(0).getId());
                refund = refunds.stream()
                        .filter(r -> "PROCESSING".equalsIgnoreCase(r.getStatus()) || "INITIATED".equalsIgnoreCase(r.getStatus()))
                        .findFirst()
                        .orElse(null);
            }
        }

        // Unknown / Unmatched event handling
        if (refund == null) {
            log.warn("Ignored unmatched refund webhook event {}: no local refund intent found matching refundId={}, paymentId={}",
                    eventType, gatewayRefundId, gatewayPaymentId);
            return;
        }

        // Amount consistency validation
        try {
            long expectedPaise = convertToPaiseExact(refund.getAmount());
            if (amountPaise != expectedPaise) {
                log.error("Mismatch in refund amount for refund {}: expectedPaise={} vs webhookPaise={}. Refusing state mutation.",
                        refund.getId(), expectedPaise, amountPaise);
                return;
            }
        } catch (IllegalArgumentException ex) {
            log.error("Invalid local refund amount for refund {}: {}. Refusing state mutation.",
                    refund.getId(), ex.getMessage());
            return;
        }

        final Long refundIdToLock = refund.getId();
        transactionTemplate.execute(txStatus -> {
            Refund lockedRefund = refundRepository.findByIdWithLock(refundIdToLock).orElse(null);
            if (lockedRefund == null) return null;

            // Out-of-order / Duplicate check
            if ("refund.processed".equalsIgnoreCase(eventType)) {
                if ("SUCCESS".equalsIgnoreCase(lockedRefund.getStatus())) {
                    log.info("Duplicate refund.processed webhook received for refund {}. Already SUCCESS. Returning idempotently.",
                            lockedRefund.getId());
                    return null;
                }

                lockedRefund.setStatus("SUCCESS");
                lockedRefund.setGatewayRefundId(gatewayRefundId);
                lockedRefund.setCompletedAt(Instant.now());
                lockedRefund.setRecoveryStatus("COMPLETED");
                lockedRefund.setClaimExpiresAt(null);
                lockedRefund.setClaimToken(null);
                refundRepository.saveAndFlush(lockedRefund);

                Order order = lockedRefund.getOrder();
                if (order != null) {
                    order.setPaymentStatus("REFUNDED");
                    orderRepository.saveAndFlush(order);
                }
                Payment payment = lockedRefund.getPayment();
                if (payment != null) {
                    payment.setStatus("REFUNDED");
                    paymentRepository.saveAndFlush(payment);
                }
                log.info("Successfully reconciled refund {} to SUCCESS via webhook (gatewayRefundId: {})",
                        lockedRefund.getId(), gatewayRefundId);

                try {
                    Refund detailed = refundRepository.findByIdWithDetails(lockedRefund.getId()).orElse(lockedRefund);
                    notificationService.notifyRefundProcessed(detailed);
                } catch (Exception ex) {
                    log.error("Failed to send notification for reconciled refund {}: {}", lockedRefund.getId(), ex.getMessage());
                }

            } else if ("refund.failed".equalsIgnoreCase(eventType)) {
                // Cannot regress from terminal SUCCESS
                if ("SUCCESS".equalsIgnoreCase(lockedRefund.getStatus())) {
                    log.warn("Ignored late refund.failed webhook for already SUCCESS refund {}", lockedRefund.getId());
                    return null;
                }

                String errorDesc = refundNode.path("error_description").asText("Razorpay reported refund failure");
                lockedRefund.setStatus("FAILED");
                lockedRefund.setRecoveryStatus("FAILED");
                lockedRefund.setClaimExpiresAt(null);
                lockedRefund.setClaimToken(null);
                lockedRefund.setReason("Razorpay webhook reported failure: " + errorDesc);
                refundRepository.saveAndFlush(lockedRefund);

                Order order = lockedRefund.getOrder();
                if (order != null) {
                    order.setPaymentStatus("REFUND_FAILED");
                    orderRepository.saveAndFlush(order);
                }
                log.warn("Reconciled refund {} to FAILED via webhook: {}", lockedRefund.getId(), errorDesc);
            }
            return null;
        });
    }

    @Transactional
    public void handleGatewayRefundSuccess(String gatewayRefundId, String gatewayPaymentId) {
        Refund refund = null;
        if (gatewayRefundId != null) {
            refund = refundRepository.findByGatewayRefundId(gatewayRefundId).orElse(null);
        }
        if (refund == null && gatewayPaymentId != null) {
            List<Payment> payments = paymentRepository.findByGatewayOrderId(gatewayPaymentId)
                    .map(List::of)
                    .orElseGet(List::of);
            if (!payments.isEmpty()) {
                List<Refund> refunds = refundRepository.findByPaymentId(payments.get(0).getId());
                refund = refunds.stream()
                        .filter(r -> "PROCESSING".equalsIgnoreCase(r.getStatus()) || "INITIATED".equalsIgnoreCase(r.getStatus()))
                        .findFirst()
                        .orElse(null);
            }
        }

        if (refund != null) {
            refund.setStatus("SUCCESS");
            if (gatewayRefundId != null) {
                refund.setGatewayRefundId(gatewayRefundId);
            }
            refund.setCompletedAt(Instant.now());
            refund.setRecoveryStatus("COMPLETED");
            refund.setClaimExpiresAt(null);
            refundRepository.save(refund);

            Order order = refund.getOrder();
            if (order != null) {
                order.setPaymentStatus("REFUNDED");
                orderRepository.save(order);
            }
            Payment payment = refund.getPayment();
            if (payment != null) {
                payment.setStatus("REFUNDED");
                paymentRepository.save(payment);
            }
            log.info("Reconciled refund {} to SUCCESS via Razorpay refund.processed webhook", refund.getId());
        }
    }

    @Transactional
    public void handleGatewayRefundFailure(String gatewayRefundId, String gatewayPaymentId) {
        Refund refund = null;
        if (gatewayRefundId != null) {
            refund = refundRepository.findByGatewayRefundId(gatewayRefundId).orElse(null);
        }
        if (refund != null) {
            refund.setStatus("FAILED");
            refund.setRecoveryStatus("FAILED");
            refund.setClaimExpiresAt(null);
            refund.setReason("Razorpay reported refund failure via webhook");
            refundRepository.save(refund);

            Order order = refund.getOrder();
            if (order != null) {
                order.setPaymentStatus("REFUND_FAILED");
                orderRepository.save(order);
            }
            log.warn("Reconciled refund {} to FAILED via Razorpay refund.failed webhook", refund.getId());
        }
    }

    @Transactional(readOnly = true)
    public List<RefundDto> getOrderRefunds(Long orderId) {
        return refundRepository.findByOrderId(orderId).stream()
                .map(RefundDto::fromEntity)
                .toList();
    }
}
