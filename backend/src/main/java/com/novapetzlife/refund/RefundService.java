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
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Refund amount must be greater than zero");
        }

        Payment payment = paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(order.getId()).orElse(null);

        // Generate simulated/real gateway refund reference
        String gatewayRefundId = "rfnd_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);

        Refund refund = new Refund(
                order,
                payment,
                gatewayRefundId,
                refundAmount,
                "SUCCESS",
                reason != null ? reason : "Order refund approved",
                approvedBy != null ? approvedBy : "SYSTEM"
        );
        refund.setCompletedAt(Instant.now());
        refund = refundRepository.save(refund);

        if (payment != null) {
            payment.setStatus("REFUNDED");
            paymentRepository.save(payment);
        }

        // Check total refunded amount
        List<Refund> existingRefunds = refundRepository.findByOrderId(order.getId());
        BigDecimal totalRefunded = existingRefunds.stream()
                .map(Refund::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalRefunded.compareTo(order.getFinalPayableAmount()) >= 0) {
            order.setPaymentStatus("REFUNDED");
        } else {
            order.setPaymentStatus("PARTIALLY_REFUNDED");
        }
        orderRepository.save(order);

        log.info("Processed refund {} of INR {} for order {} (Approved by: {})",
                gatewayRefundId, refundAmount, order.getOrderNumber(), approvedBy);

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
