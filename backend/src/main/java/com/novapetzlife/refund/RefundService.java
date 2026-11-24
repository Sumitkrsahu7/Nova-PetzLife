package com.novapetzlife.refund;

import com.novapetzlife.order.Order;
import com.novapetzlife.order.OrderRepository;
import com.novapetzlife.payment.Payment;
import com.novapetzlife.payment.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);

    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final com.novapetzlife.notification.NotificationService notificationService;

    public RefundService(
            RefundRepository refundRepository,
            PaymentRepository paymentRepository,
            OrderRepository orderRepository,
            com.novapetzlife.notification.NotificationService notificationService) {
        this.refundRepository = refundRepository;
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public Refund processRefund(Order order, BigDecimal refundAmount, String reason, String approvedBy) {
        if (order == null || order.getId() == null) {
            throw new IllegalArgumentException("Valid order is required for refund processing");
        }

        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Refund amount must be greater than zero");
        }

        BigDecimal scaledAmount = refundAmount.setScale(2, RoundingMode.HALF_UP);

        // 1. Acquire database-level pessimistic write lock on the order to serialize concurrent refund requests
        Order lockedOrder = orderRepository.findByIdWithLock(order.getId())
                .orElseThrow(() -> new IllegalArgumentException("Order not found with ID: " + order.getId()));

        // 2. Validate payment state: Only confirmed/paid orders are eligible for refunds
        String currentPaymentStatus = lockedOrder.getPaymentStatus();
        boolean isEligibleStatus = "COMPLETED".equalsIgnoreCase(currentPaymentStatus)
                || "PAID".equalsIgnoreCase(currentPaymentStatus)
                || "PARTIALLY_REFUNDED".equalsIgnoreCase(currentPaymentStatus)
                || "REFUNDED".equalsIgnoreCase(currentPaymentStatus);

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
        BigDecimal currentTotalRefunded = existingRefunds.stream()
                .filter(r -> "SUCCESS".equalsIgnoreCase(r.getStatus())
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

        // 5. Explicit status: PENDING_MANUAL_REFUND distinguishes internal ledger approval from live gateway disbursement.
        // gatewayRefundId and completedAt remain NULL until confirmed by gateway API callback.
        Refund refund = new Refund(
                lockedOrder,
                payment,
                null, // No simulated gateway ID
                scaledAmount,
                "PENDING_MANUAL_REFUND",
                reason != null ? reason : "Order refund approved",
                approvedBy != null ? approvedBy : "SYSTEM"
        );
        refund.setCompletedAt(null); // completedAt MUST NOT be set prior to genuine disbursement
        refund = refundRepository.save(refund);

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
        orderRepository.save(lockedOrder);

        log.info("Recorded internal refund request (ID: {}) of INR {} for order {} (Approved by: {}). Order payment status: {}",
                refund.getId(), scaledAmount, lockedOrder.getOrderNumber(), approvedBy, lockedOrder.getPaymentStatus());

        notificationService.notifyRefundProcessed(refund);

        return refund;
    }

    @Transactional(readOnly = true)
    public List<RefundDto> getOrderRefunds(Long orderId) {
        return refundRepository.findByOrderId(orderId).stream()
                .map(RefundDto::fromEntity)
                .toList();
    }
}
