package com.novapetzlife.refund;

import com.novapetzlife.order.Order;
import com.novapetzlife.payment.Payment;
import com.novapetzlife.payment.RazorpayClientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Durable Recovery Worker for refund intents left in intermediate or crash states.
 * Scans for:
 * 1. Undispatched intents (status = "INITIATED", where process died before afterCommit).
 * 2. Uncertain gateway dispatches (status = "PROCESSING" with gatewayRefundId == null, e.g. after network timeouts).
 *
 * Enforces strict safety rules:
 * - Concurrency control: locks candidate refund row (findByIdWithLock).
 * - Reconciliation First: queries Razorpay before attempting disbursement.
 * - Zero duplicate payments.
 * - Bounded retry limits: transitions abandoned records to PENDING_MANUAL_REFUND with audit logs.
 */
@Service
public class RefundRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(RefundRecoveryService.class);

    public static final Duration INTENT_AGE_THRESHOLD = Duration.ofSeconds(15);
    public static final Duration LEASE_DURATION = Duration.ofMinutes(5);
    public static final int MAX_RECOVERY_ATTEMPTS = 3;

    private final RefundRepository refundRepository;
    private final RefundService refundService;
    private final RazorpayClientService razorpayClientService;
    private final TransactionTemplate transactionTemplate;

    public RefundRecoveryService(
            RefundRepository refundRepository,
            RefundService refundService,
            RazorpayClientService razorpayClientService,
            PlatformTransactionManager transactionManager) {
        this.refundRepository = refundRepository;
        this.refundService = refundService;
        this.razorpayClientService = razorpayClientService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Periodic sweeper: runs every 60 seconds (initial delay 10s).
     */
    @Scheduled(fixedDelay = 60000, initialDelay = 10000)
    public void scheduledRefundRecoverySweep() {
        try {
            int recovered = processPendingRefundRecovery(Instant.now().minus(INTENT_AGE_THRESHOLD), MAX_RECOVERY_ATTEMPTS);
            if (recovered > 0) {
                log.info("Scheduled refund recovery sweep processed {} refund intents.", recovered);
            }
        } catch (Exception ex) {
            log.error("Error during scheduled refund recovery sweep: {}", ex.getMessage(), ex);
        }
    }

    /**
     * Core recovery logic: scans for candidate refunds created before threshold and processes each safely.
     *
     * @param createdBeforeThreshold age filter to avoid stepping on concurrently executing transactions
     * @param maxAttempts maximum retry attempts before escalating to manual refund
     * @return number of intents processed/recovered
     */
    public int processPendingRefundRecovery(Instant createdBeforeThreshold, int maxAttempts) {
        List<Refund> candidates = refundRepository.findPendingRecoveryCandidates(createdBeforeThreshold);
        if (candidates.isEmpty()) {
            return 0;
        }

        log.info("Found {} pending refund intent candidates for crash/dispatch recovery", candidates.size());
        int processedCount = 0;

        for (Refund candidate : candidates) {
            try {
                boolean recovered = recoverSingleRefund(candidate.getId(), maxAttempts);
                if (recovered) {
                    processedCount++;
                }
            } catch (Exception ex) {
                log.error("Failed to recover refund intent ID {}: {}", candidate.getId(), ex.getMessage(), ex);
            }
        }

        return processedCount;
    }

    /**
     * Safely recovers a single refund intent with row locking, reconciliation first, and bounded retries.
     */
    public boolean recoverSingleRefund(Long refundId, int maxAttempts) {
        Instant now = Instant.now();

        // Step 1: In an independent transaction with row lock, check eligibility, lease expiry, and claim atomically
        RefundActionPlan plan = transactionTemplate.execute(status -> {
            Refund lockedRefund = refundRepository.findByIdWithLock(refundId).orElse(null);
            if (lockedRefund == null) return null;

            String currentStatus = lockedRefund.getStatus();
            boolean isEligibleStatus = "INITIATED".equalsIgnoreCase(currentStatus)
                    || ("PROCESSING".equalsIgnoreCase(currentStatus) && lockedRefund.getGatewayRefundId() == null);

            if (!isEligibleStatus) {
                log.info("Refund {} is no longer in recoverable state (Status: {}, gatewayRefundId: {}). Skipping.",
                        refundId, currentStatus, lockedRefund.getGatewayRefundId());
                return null;
            }

            // Enforce lease ownership: if another worker holds an active, unexpired lease, DO NOT STEAL
            if (lockedRefund.getClaimExpiresAt() != null && lockedRefund.getClaimExpiresAt().isAfter(now)) {
                log.info("Refund {} is currently locked under active lease until {}. Skipping competing recovery.",
                        refundId, lockedRefund.getClaimExpiresAt());
                return null;
            }

            int currentAttempts = lockedRefund.getAttemptCount() > 0 ? lockedRefund.getAttemptCount() : parseAttemptCount(lockedRefund.getReason());
            if (currentAttempts >= maxAttempts) {
                // Escalate to manual review to prevent infinite retries while preserving original business reason
                log.warn("Refund {} exceeded max recovery attempts ({}/{}). Escalating to PENDING_MANUAL_REFUND.",
                        refundId, currentAttempts, maxAttempts);
                lockedRefund.setStatus("PENDING_MANUAL_REFUND");
                lockedRefund.setRecoveryStatus("MANUAL_ESCALATION");
                lockedRefund.setClaimExpiresAt(null);
                String note = " [Recovery abandoned: max attempts " + maxAttempts + " reached - manual intervention required]";
                if (lockedRefund.getReason() == null) {
                    lockedRefund.setReason("Manual intervention required");
                } else if (!lockedRefund.getReason().contains("manual intervention required")) {
                    int maxLen = 255 - note.length();
                    String base = lockedRefund.getReason().length() > maxLen ? lockedRefund.getReason().substring(0, maxLen) : lockedRefund.getReason();
                    lockedRefund.setReason(base + note);
                }
                refundRepository.saveAndFlush(lockedRefund);
                return null;
            }

            // Atomically claim lease inside the row-locked transaction
            String claimToken = java.util.UUID.randomUUID().toString();
            lockedRefund.setClaimToken(claimToken);
            lockedRefund.setStatus("PROCESSING");
            lockedRefund.setClaimExpiresAt(now.plus(LEASE_DURATION));
            lockedRefund.setAttemptCount(currentAttempts + 1);
            lockedRefund.setLastAttemptAt(now);
            lockedRefund.setRecoveryStatus("CLAIMED");

            // Preserve stable idempotency key across attempts and crashes
            if (!StringUtils.hasText(lockedRefund.getIdempotencyKey())) {
                String key = RefundService.buildIdempotencyKey(
                        lockedRefund.getOrder() != null ? lockedRefund.getOrder().getOrderNumber() : "ord"
                );
                lockedRefund.setIdempotencyKey(key);
            }

            // Preserve stable unique receipt across attempts and crashes
            if (!StringUtils.hasText(lockedRefund.getReceipt())) {
                String receipt = RefundService.buildDeterministicReceipt(
                        lockedRefund.getOrder() != null ? lockedRefund.getOrder().getOrderNumber() : "ord",
                        lockedRefund.getId()
                );
                lockedRefund.setReceipt(receipt);
            }

            refundRepository.saveAndFlush(lockedRefund);

            Payment payment = lockedRefund.getPayment();
            Order order = lockedRefund.getOrder();
            String gatewayPaymentId = (payment != null) ? payment.getGatewayPaymentId() : null;
            String orderNumber = (order != null) ? order.getOrderNumber() : String.valueOf(refundId);
            long amountPaise = RefundService.convertToPaiseExact(lockedRefund.getAmount());

            return new RefundActionPlan(refundId, gatewayPaymentId, orderNumber, amountPaise, currentStatus, claimToken);
        });

        if (plan == null) {
            return false;
        }

        // Step 2: Delegate to dispatchGatewayRefund (reconciliation-first, X-Refund-Idempotency header, ZERO DB locks held)
        try {
            log.info("Recovery sweep: dispatching undispatched/uncertain refund {} for payment {} (claimToken: {})",
                    plan.refundId, plan.gatewayPaymentId, plan.claimToken);
            refundService.dispatchGatewayRefund(plan.refundId, plan.claimToken);
            return true;
        } catch (Exception ex) {
            log.error("Recovery dispatch attempt failed for refund {}: {}", plan.refundId, ex.getMessage());
            return false;
        }
    }

    private int parseAttemptCount(String reason) {
        if (reason == null) return 0;
        int idx = reason.indexOf("[Recovery attempt ");
        if (idx != -1) {
            try {
                String sub = reason.substring(idx + "[Recovery attempt ".length());
                int slash = sub.indexOf('/');
                if (slash != -1) {
                    return Integer.parseInt(sub.substring(0, slash).trim());
                }
            } catch (Exception ignored) {}
        }
        return 0;
    }

    private String incrementAttemptInReason(String reason, int nextAttempt, int maxAttempts) {
        String base = (reason != null) ? reason : "Refund intent recovery";
        int idx = base.indexOf("[Recovery attempt ");
        if (idx != -1) {
            base = base.substring(0, idx).trim();
        }
        return base + " [Recovery attempt " + nextAttempt + "/" + maxAttempts + "]";
    }

    private record RefundActionPlan(Long refundId, String gatewayPaymentId, String orderNumber, long amountPaise, String originalStatus, String claimToken) {}
}
