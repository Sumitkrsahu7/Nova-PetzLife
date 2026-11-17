package com.novapetzlife.notification;

import com.novapetzlife.customer.Customer;
import com.novapetzlife.order.Order;
import com.novapetzlife.payment.Payment;
import com.novapetzlife.product.Product;
import com.novapetzlife.refund.Refund;
import com.novapetzlife.returns.ReturnRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public Notification recordAndDispatch(
            Customer customer,
            String recipient,
            String channel,
            String template,
            String subject,
            String content) {

        Notification notification = new Notification(
                customer,
                recipient,
                channel,
                template,
                subject,
                content,
                "SENT"
        );
        notification.setSentAt(Instant.now());
        notification = notificationRepository.save(notification);

        // Simulated Dispatch Logging for Email, SMS, WhatsApp
        log.info("[NOTIFICATION DISPATCH] Channel: {} | Template: {} | To: {} | Subject: {}",
                channel, template, recipient, subject);

        return notification;
    }

    @Transactional
    public void notifyOrderPlaced(Order order) {
        notifyOrderPlaced(order, null);
    }

    @Transactional
    public void notifyOrderPlaced(Order order, String deliveryOtp) {
        Customer customer = order.getCustomer();
        String recipientEmail = (customer != null && customer.getEmail() != null) ? customer.getEmail() : "customer@example.com";
        String recipientPhone = (customer != null && customer.getPhone() != null) ? customer.getPhone() : "+919447100000";

        String otpSection = deliveryOtp != null
                ? "\n\nSECURE 2-FACTOR DELIVERY OTP: " + deliveryOtp + "\nPlease share this 6-digit OTP code with your delivery partner upon package handoff."
                : "";

        // 1. Email Notification
        String emailSubject = "Order Confirmed: #" + order.getOrderNumber() + " - NOVA PETZLIFE";
        String emailContent = String.format(
                "Dear %s,\n\nThank you for shopping at NOVA PETZLIFE, Kuravankonam!\n" +
                        "Your order #%s of ₹%s has been successfully received.\n" +
                        "Delivery window: %s\n" +
                        "Dispatching straight from our store at Petzlife, Kuravankonam Junction, Thiruvananthapuram.%s\n\n" +
                        "Track your order: https://novapetzlife.in/track/%s\n\n" +
                        "Best regards,\nNOVA PETZLIFE Team",
                customer != null ? customer.getFirstName() : "Pet Parent",
                order.getOrderNumber(),
                order.getFinalPayableAmount(),
                order.getDeliverySlot() != null ? order.getDeliverySlot() : "Standard Delivery",
                otpSection,
                order.getOrderNumber()
        );
        recordAndDispatch(customer, recipientEmail, "EMAIL", "ORDER_CONFIRMATION", emailSubject, emailContent);

        // 2. WhatsApp Notification
        String waContent = String.format(
                "🐾 *NOVA PETZLIFE Order Confirmed!*\nOrder #%s of ₹%s is confirmed. We are packing your pet supplies at Kuravankonam.%s Track: https://novapetzlife.in/track/%s",
                order.getOrderNumber(),
                order.getFinalPayableAmount(),
                deliveryOtp != null ? " Your Delivery OTP: " + deliveryOtp + "." : "",
                order.getOrderNumber()
        );
        recordAndDispatch(customer, recipientPhone, "WHATSAPP", "ORDER_CONFIRMATION_WA", "WhatsApp Order Confirmed", waContent);
    }

    @Transactional
    public void notifyPaymentSuccess(Order order, Payment payment) {
        Customer customer = order.getCustomer();
        String recipientEmail = (customer != null && customer.getEmail() != null) ? customer.getEmail() : "customer@example.com";

        String subject = "Payment Received: Order #" + order.getOrderNumber() + " (Razorpay ID: " + payment.getGatewayPaymentId() + ")";
        String content = String.format(
                "Dear Customer,\n\nWe have successfully received your online payment of ₹%s via Razorpay (Transaction ID: %s).\n" +
                        "Order #%s is now processing for fast local delivery from Kuravankonam.\n\nNOVA PETZLIFE",
                payment.getAmount(),
                payment.getGatewayPaymentId(),
                order.getOrderNumber()
        );
        recordAndDispatch(customer, recipientEmail, "EMAIL", "PAYMENT_SUCCESS", subject, content);
    }

    @Transactional
    public void notifyOrderStatusChanged(Order order, String previousStatus, String newStatus) {
        Customer customer = order.getCustomer();
        String recipientEmail = (customer != null && customer.getEmail() != null) ? customer.getEmail() : "customer@example.com";
        String recipientPhone = (customer != null && customer.getPhone() != null) ? customer.getPhone() : "+919447100000";

        String subject = "Order Status Update: #" + order.getOrderNumber() + " is now " + newStatus;
        String content;

        if ("OUT_FOR_DELIVERY".equalsIgnoreCase(newStatus)) {
            content = String.format("Great news! Order #%s has been dispatched from our Kuravankonam store and is out for delivery to your doorstep.", order.getOrderNumber());
            recordAndDispatch(customer, recipientPhone, "SMS", "ORDER_OUT_FOR_DELIVERY", subject, content);
        } else if ("DELIVERED".equalsIgnoreCase(newStatus)) {
            content = String.format("Order #%s has been successfully delivered! Thank you for trusting NOVA PETZLIFE for your pet's care.", order.getOrderNumber());
            recordAndDispatch(customer, recipientEmail, "EMAIL", "ORDER_DELIVERED", subject, content);
        } else if ("CANCELLED".equalsIgnoreCase(newStatus)) {
            content = String.format("Order #%s has been cancelled. Any reserved items have been restocked.", order.getOrderNumber());
            recordAndDispatch(customer, recipientEmail, "EMAIL", "ORDER_CANCELLED", subject, content);
        } else {
            content = String.format("Order #%s status has been updated to %s.", order.getOrderNumber(), newStatus);
            recordAndDispatch(customer, recipientEmail, "EMAIL", "ORDER_STATUS_UPDATE", subject, content);
        }
    }

    @Transactional
    public void notifyReturnRequested(ReturnRequest request) {
        Customer customer = request.getCustomer();
        String recipientEmail = (customer != null && customer.getEmail() != null) ? customer.getEmail() : "customer@example.com";

        String subject = "Return Request Received: #" + request.getRequestNumber();
        String content = String.format(
                "Dear Customer,\n\nYour return request #%s for Order #%s has been received.\n" +
                        "Our store team at Kuravankonam is reviewing your request. A pickup rider will be scheduled shortly.\n\n" +
                        "Estimated refund: ₹%s\n\nNOVA PETZLIFE",
                request.getRequestNumber(),
                request.getOrder().getOrderNumber(),
                request.getEstimatedRefundAmount()
        );
        recordAndDispatch(customer, recipientEmail, "EMAIL", "RETURN_REQUESTED", subject, content);
    }

    @Transactional
    public void notifyRefundProcessed(Refund refund) {
        Customer customer = refund.getOrder().getCustomer();
        String recipientEmail = (customer != null && customer.getEmail() != null) ? customer.getEmail() : "customer@example.com";

        String refundRef = refund.getGatewayRefundId() != null ? refund.getGatewayRefundId() : "REFUND-" + refund.getId();
        String paymentMethod = refund.getOrder() != null && refund.getOrder().getPaymentMethod() != null ? refund.getOrder().getPaymentMethod() : "Original Payment Method";
        String subject = "Refund Processed: ₹" + refund.getAmount() + " (Ref: " + refundRef + ")";
        String content = String.format(
                "Dear Customer,\n\nYour refund of ₹%s for Order #%s has been processed via %s.\n" +
                        "Transaction Reference: %s\n\nNOVA PETZLIFE Kuravankonam",
                refund.getAmount(),
                refund.getOrder().getOrderNumber(),
                paymentMethod,
                refundRef
        );
        recordAndDispatch(customer, recipientEmail, "EMAIL", "REFUND_PROCESSED", subject, content);
    }

    @Transactional
    public void notifyShopOwnerLowStock(Product product, int currentStock, int minThreshold) {
        String ownerEmail = "owner@novapetzlife.com";
        String subject = "⚠️ LOW STOCK ALERT: " + product.getName() + " (" + product.getSku() + ")";
        String content = String.format(
                "Store Management Alert:\n\nProduct '%s' (SKU: %s) stock has reached %d units, which is at or below the minimum threshold of %d units.\n" +
                        "Please arrange restocking at Petzlife, Kuravankonam store.\n\nAutomated Inventory Monitor",
                product.getName(),
                product.getSku(),
                currentStock,
                minThreshold
        );
        recordAndDispatch(null, ownerEmail, "EMAIL", "LOW_STOCK_ALERT", subject, content);
    }

    @Transactional(readOnly = true)
    public List<Notification> getRecentCustomerNotifications(Customer customer) {
        if (customer == null) return List.of();
        return notificationRepository.findTop20ByCustomerIdOrderByCreatedAtDesc(customer.getId());
    }

    @Transactional(readOnly = true)
    public Page<Notification> getAllNotifications(Pageable pageable) {
        return notificationRepository.findAllByOrderByCreatedAtDesc(pageable);
    }
}
